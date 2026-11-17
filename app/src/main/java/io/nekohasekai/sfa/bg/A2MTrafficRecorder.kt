package io.nekohasekai.sfa.bg

import io.nekohasekai.sfa.dams.traffic.DailyTrafficLog
import io.nekohasekai.sfa.database.Settings

/**
 * Enregistreur de consommation journalière.
 *
 * Le moteur expose des compteurs **cumulés depuis le démarrage du service**.
 * On ne persiste que les deltas : à chaque mise à jour, l'écart avec le relevé
 * précédent est ajouté au seau du jour courant. Un compteur qui recule signale
 * un nouveau démarrage de service : il est pris comme nouvelle référence sans
 * être compté (aucune double facturation).
 *
 * Les écritures en base sont limitées à une toutes les 30 secondes et se font
 * aussi au changement de jour et à l'arrêt du service.
 */
object A2MTrafficRecorder {
    private const val FLUSH_INTERVAL_MS = 30_000L

    private var lastDownload = 0L
    private var lastUpload = 0L
    private var hasSession = false
    private var pendingDownload = 0L
    private var pendingUpload = 0L
    private var lastFlush = 0L
    private var bucketDate = DailyTrafficLog.today()

    /** Appelé à chaque message de statut du moteur (thread quelconque). */
    @Synchronized
    fun record(
        downloadTotal: Long,
        uploadTotal: Long,
    ) {
        val today = DailyTrafficLog.today()
        if (today != bucketDate) {
            flushLocked()
            bucketDate = today
            lastDownload = downloadTotal
            lastUpload = uploadTotal
            hasSession = true
            return
        }
        if (!hasSession || downloadTotal < lastDownload || uploadTotal < lastUpload) {
            lastDownload = downloadTotal
            lastUpload = uploadTotal
            hasSession = true
            return
        }
        pendingDownload += downloadTotal - lastDownload
        pendingUpload += uploadTotal - lastUpload
        lastDownload = downloadTotal
        lastUpload = uploadTotal
        val now = System.currentTimeMillis()
        if (now - lastFlush >= FLUSH_INTERVAL_MS) {
            flushLocked()
        }
    }

    /** Force l'écriture des deltas en attente (arrêt du service, écran de suivi). */
    @Synchronized
    fun flush() {
        flushLocked()
    }

    /** Réinitialise les références quand le service redémarre explicitement. */
    @Synchronized
    fun resetBaseline() {
        flushLocked()
        lastDownload = 0L
        lastUpload = 0L
        hasSession = false
    }

    private fun flushLocked() {
        lastFlush = System.currentTimeMillis()
        if (pendingDownload == 0L && pendingUpload == 0L) return
        try {
            val merged =
                DailyTrafficLog.merge(
                    entries = DailyTrafficLog.parse(Settings.dailyTraffic),
                    date = bucketDate,
                    download = pendingDownload,
                    upload = pendingUpload,
                )
            Settings.dailyTraffic = DailyTrafficLog.serialize(merged)
            pendingDownload = 0L
            pendingUpload = 0L
        } catch (_: Exception) {
            // Une erreur de stockage ne doit jamais interrompre le tunnel.
        }
    }
}
