package io.nekohasekai.sfa.dams.transfer

import android.net.Uri
import android.provider.OpenableColumns
import io.nekohasekai.sfa.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

enum class TransferKind { UPLOAD, DOWNLOAD }

enum class TransferStatus { RUNNING, DONE, FAILED, CANCELLED }

data class TransferProgress(
    val kind: TransferKind,
    val name: String,
    val transferred: Long,
    val total: Long,
    val status: TransferStatus,
    val message: String? = null,
) {
    val isRunning: Boolean
        get() = status == TransferStatus.RUNNING
}

data class TransferRecord(
    val kind: TransferKind,
    val name: String,
    val detail: String,
    val success: Boolean,
    val timestamp: Long,
)

/**
 * Gestionnaire de transferts de fichiers (téléversement / téléchargement).
 *
 * Les transferts vivent dans un scope applicatif : changer d'écran ne les
 * interrompt pas. Aucune donnée sensible n'est journalisée ; le jeton
 * d'authentification n'est conservé qu'en mémoire pour la durée du transfert.
 */
object A2MTransferManager {
    private const val BUFFER_SIZE = 64 * 1024
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val PROGRESS_STEP = 256L * 1024L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    private val _progress = MutableStateFlow<TransferProgress?>(null)
    val progress: StateFlow<TransferProgress?> = _progress.asStateFlow()

    private val _history = MutableStateFlow<List<TransferRecord>>(emptyList())
    val history: StateFlow<List<TransferRecord>> = _history.asStateFlow()

    fun isRunning(): Boolean = _progress.value?.isRunning == true

    /** Nom et taille d'un document choisi via SAF. */
    fun describe(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "fichier"
        var size = -1L
        try {
            Application.application.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        name = TransferRules.sanitizeFileName(cursor.getString(nameIndex))
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {
            // Nom et taille restent facultatifs.
        }
        return name to size
    }

    fun upload(
        uri: Uri,
        url: String,
        method: String,
        bearerToken: String,
        mimeType: String,
    ) {
        if (isRunning()) return
        val (name, size) = describe(uri)
        val target = TransferRules.normalizeUrl(url) ?: run {
            fail(TransferKind.UPLOAD, name, "Adresse invalide (http/https attendu, sans identifiants).")
            return
        }
        start(TransferKind.UPLOAD, name, size) {
            val resolver = Application.application.contentResolver
            val connection = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = TransferRules.normalizeUploadMethod(method)
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Content-Type", mimeType.ifBlank { "application/octet-stream" })
                if (bearerToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $bearerToken")
                }
                if (size > 0) setFixedLengthStreamingMode(size)
            }
            try {
                val input =
                    resolver.openInputStream(uri)
                        ?: error("Fichier illisible")
                input.use { source ->
                    connection.outputStream.use { sink ->
                        pump(source, sink, null)
                    }
                }
                val code = connection.responseCode
                if (code !in 200..299) error("Le serveur a répondu HTTP $code")
            } finally {
                connection.disconnect()
            }
        }
    }

    fun download(
        url: String,
        destination: Uri,
        fallbackName: String,
    ) {
        if (isRunning()) return
        val target = TransferRules.normalizeUrl(url) ?: run {
            fail(TransferKind.DOWNLOAD, fallbackName, "Adresse invalide (http/https attendu, sans identifiants).")
            return
        }
        start(TransferKind.DOWNLOAD, fallbackName, -1L) {
            val resolver = Application.application.contentResolver
            val connection = (URL(target).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "*/*")
                setRequestProperty("User-Agent", "A2MTUN")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) error("Le serveur a répondu HTTP $code")
                val total =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        connection.contentLengthLong
                    } else {
                        connection.contentLength.toLong()
                    }
                _progress.value = _progress.value?.copy(total = total)
                val stream = connection.inputStream
                val sink =
                    resolver.openOutputStream(destination, "w")
                        ?: error("Destination inaccessible")
                stream.use { source ->
                    sink.use { out ->
                        pump(source, out, total)
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
    }

    /** Réinitialise l'état affiché (après un transfert terminé). */
    fun clear() {
        if (!isRunning()) _progress.value = null
    }

    private fun fail(
        kind: TransferKind,
        name: String,
        message: String,
    ) {
        _progress.value = TransferProgress(kind, name, 0, -1, TransferStatus.FAILED, message)
        record(kind, name, message, false)
    }

    private fun start(
        kind: TransferKind,
        name: String,
        total: Long,
        block: suspend () -> Unit,
    ) {
        _progress.value = TransferProgress(kind, name, 0, total, TransferStatus.RUNNING)
        currentJob =
            scope.launch {
                try {
                    block()
                    val done = _progress.value
                    _progress.value =
                        done?.copy(
                            status = TransferStatus.DONE,
                            transferred = if (done.total > 0) done.total else done.transferred,
                            message = "Transfert terminé.",
                        )
                    val bytes = _progress.value?.let { if (it.total > 0) it.total else it.transferred } ?: 0L
                    record(kind, name, TransferRules.formatBytes(bytes), true)
                } catch (e: CancellationException) {
                    _progress.value =
                        _progress.value?.copy(status = TransferStatus.CANCELLED, message = "Transfert annulé.")
                    record(kind, name, "Annulé", false)
                } catch (e: Exception) {
                    val message = e.message?.take(200) ?: "Erreur inconnue"
                    _progress.value =
                        _progress.value?.copy(status = TransferStatus.FAILED, message = message)
                    record(kind, name, message, false)
                }
            }
    }

    /** Copie avec mise à jour de la progression (au plus toutes les 256 Ko). */
    private suspend fun pump(
        source: java.io.InputStream,
        sink: java.io.OutputStream,
        knownTotal: Long?,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var transferred = 0L
        var lastReported = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = source.read(buffer)
            if (read < 0) break
            sink.write(buffer, 0, read)
            transferred += read
            if (transferred - lastReported >= PROGRESS_STEP) {
                lastReported = transferred
                val total = knownTotal ?: _progress.value?.total ?: -1L
                _progress.value =
                    _progress.value?.copy(transferred = transferred, total = total)
            }
        }
        sink.flush()
        _progress.value =
            _progress.value?.copy(transferred = transferred, total = knownTotal ?: _progress.value?.total ?: -1L)
    }

    private fun record(
        kind: TransferKind,
        name: String,
        detail: String,
        success: Boolean,
    ) {
        val entry = TransferRecord(kind, name, detail, success, System.currentTimeMillis())
        _history.value = (listOf(entry) + _history.value).take(30)
    }
}
