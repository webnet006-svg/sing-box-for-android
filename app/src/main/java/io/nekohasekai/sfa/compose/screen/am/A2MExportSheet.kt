package io.nekohasekai.sfa.compose.screen.am

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.nekohasekai.sfa.dams.importer.A2MExportFile
import io.nekohasekai.sfa.dams.importer.ShareExport

/**
 * Feuille d'export d'un serveur : lien de partage standard, liens
 * `a2mtun://` simple et codé, fichiers `.a2m` simple/codé, QR code et
 * partage système (lien ou configuration brute).
 *
 * Les textes affichés masquent les secrets ; les liens copiés et les QR
 * contiennent les vraies valeurs, nécessaires à l'import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun A2MExportSheet(
    profileName: String,
    config: String,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var qr by remember { mutableStateOf<Pair<String, String>?>(null) }

    val exported = remember(config, profileName) { ShareExport.export(config, profileName) }
    var pendingFileCoded by remember { mutableStateOf<Boolean?>(null) }
    val safeFileName =
        remember(profileName) {
            profileName
                .replace(Regex("""[\\/:*?"<>|]+"""), "_")
                .trim()
                .take(48)
                .ifBlank { "serveur" }
        }
    val fileLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(A2MExportFile.MIME_TYPE),
        ) { uri ->
            val coded = pendingFileCoded
            pendingFileCoded = null
            if (uri != null && coded != null) {
                val destination = uri
                val outcome =
                    runCatching {
                        val entry = A2MExportFile.Entry(profileName, config)
                        val text =
                            if (coded) {
                                A2MExportFile.encodeCoded(listOf(entry))
                            } else {
                                A2MExportFile.encodeSimple(listOf(entry))
                            }
                        context.contentResolver.openOutputStream(destination)?.use {
                            it.write(text.toByteArray(Charsets.UTF_8))
                        } ?: error("Impossible d'ouvrir le fichier de destination.")
                    }
                Toast.makeText(
                    context,
                    if (outcome.isSuccess) {
                        "Fichier .a2m enregistré."
                    } else {
                        "Échec de l'enregistrement : ${outcome.exceptionOrNull()?.message.orEmpty()}"
                    },
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = A2MColors.BackgroundMid,
        contentColor = A2MColors.Text,
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Partager « $profileName »",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
            )

            if (exported == null) {
                Text(
                    text =
                        "Ce profil n'a pas de lien de partage standard pris en charge. " +
                            "Vous pouvez exporter sa configuration brute ou un fichier .a2m.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = A2MColors.Muted,
                )
            } else {
                A2MExportRow(
                    title = "Lien de partage (${exported.protocolLabel})",
                    subtitle = ShareExport.maskSensitive(exported.shareUri),
                    onCopy = { clipboard.setText(AnnotatedString(exported.shareUri)) },
                    onQr = { qr = "Lien de partage" to exported.shareUri },
                )
                A2MExportRow(
                    title = "Lien A2mtun:// simple",
                    subtitle = ShareExport.maskSensitive(exported.simpleLink),
                    onCopy = { clipboard.setText(AnnotatedString(exported.simpleLink)) },
                    onQr = { qr = "A2mtun:// simple" to exported.simpleLink },
                )
                A2MExportRow(
                    title = "Lien A2mtun:// codé",
                    subtitle = "Configuration complète compressée (GZIP + base64url, sans chiffrement)",
                    onCopy = { clipboard.setText(AnnotatedString(exported.codedLink)) },
                    onQr = { qr = "A2mtun:// codé" to exported.codedLink },
                )
            }

            A2MFileExportRow(
                title = "Fichier .a2m simple",
                subtitle = "JSON lisible, avec empreinte SHA-256",
                onClick = {
                    pendingFileCoded = false
                    fileLauncher.launch("$safeFileName.a2m")
                },
            )
            A2MFileExportRow(
                title = "Fichier .a2m codé",
                subtitle = "GZIP + base64url, avec empreinte SHA-256 (sans chiffrement)",
                onClick = {
                    pendingFileCoded = true
                    fileLauncher.launch("$safeFileName.a2m")
                },
            )

            if (exported != null) {
                TextButton(
                    onClick = {
                        val send =
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, exported.shareUri)
                            }
                        context.startActivity(
                            android.content.Intent.createChooser(
                                send,
                                "Partager le lien de « $profileName »",
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(text = "  Partager le lien")
                }
            }
            TextButton(
                onClick = {
                    val send =
                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, config)
                        }
                    context.startActivity(
                        android.content.Intent.createChooser(
                            send,
                            "Partager la configuration brute de « $profileName »",
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(text = "  Partager la configuration brute")
            }

            Text(
                text =
                    "Le fichier .a2m codé est compressé (GZIP) puis encodé (base64url), " +
                        "sans chiffrement. L'empreinte SHA-256 vérifie l'intégrité du fichier, " +
                        "pas son origine.",
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Faint,
            )
            Text(
                text =
                    "Les liens contiennent les identifiants du serveur : ne les " +
                        "diffusez qu'à des personnes de confiance. L'affichage les masque, " +
                        "le presse-papiers et le QR code non.",
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Faint,
            )
            Box(modifier = Modifier.size(8.dp))
        }
    }

    qr?.let { (label, payload) ->
        AlertDialog(
            onDismissRequest = { qr = null },
            title = { Text(label) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    A2MQrCodeImage(payload = payload)
                    Text(
                        text = "Scannez avec A2M TUN pour importer le serveur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = A2MColors.Muted,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { qr = null }) {
                    Text(text = "Fermer")
                }
            },
        )
    }
}

@Composable
private fun A2MExportRow(
    title: String,
    subtitle: String,
    onCopy: () -> Unit,
    onQr: () -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(A2MShapes.tile)
            .background(A2MColors.CardBrush)
            .border(1.dp, A2MColors.CardBorder, A2MShapes.tile)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = A2MColors.Text,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = A2MColors.Muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Copier",
                style = MaterialTheme.typography.labelLarge,
                color = A2MColors.Accent,
                modifier = Modifier.clickable(onClick = onCopy),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable(onClick = onQr),
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCode2,
                    contentDescription = null,
                    tint = A2MColors.Accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "QR code",
                    style = MaterialTheme.typography.labelLarge,
                    color = A2MColors.Accent,
                )
            }
        }
    }
}

@Composable
private fun A2MFileExportRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(A2MShapes.tile)
            .background(A2MColors.CardBrush)
            .border(1.dp, A2MColors.CardBorder, A2MShapes.tile)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Save,
            contentDescription = null,
            tint = A2MColors.Accent,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = A2MColors.Text,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Muted,
            )
        }
        Text(
            text = "Enregistrer",
            style = MaterialTheme.typography.labelLarge,
            color = A2MColors.Accent,
        )
    }
}

/** QR code noir sur blanc, généré à la volée (ZXing core). */
@Composable
fun A2MQrCodeImage(
    payload: String,
    size: Int = 640,
) {
    val bitmap = remember(payload) { runCatching { generateQr(payload, size) }.getOrNull() }
    if (bitmap == null) {
        Text(
            text = "Contenu trop long pour un QR code : utilisez « Copier ».",
            style = MaterialTheme.typography.bodySmall,
            color = A2MColors.Danger,
        )
        return
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "QR code de partage",
        modifier = Modifier.size(260.dp),
    )
}

private fun generateQr(
    payload: String,
    size: Int,
): Bitmap {
    val hints =
        mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.MARGIN to 1,
        )
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] =
                if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
