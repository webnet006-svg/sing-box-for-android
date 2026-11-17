package io.nekohasekai.sfa.compose.screen.am

import android.graphics.Bitmap
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
import io.nekohasekai.sfa.dams.importer.ShareExport

/**
 * Feuille d'export d'un serveur : lien de partage standard, liens
 * `a2mtun://` simple et codé, QR code et partage système.
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
                        "Ce profil n'utilise pas un protocole exportable en lien " +
                            "(vless, vmess ou trojan). Vous pouvez partager sa configuration brute.",
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
                    subtitle = "Configuration complète compressée (base64url + gzip)",
                    onCopy = { clipboard.setText(AnnotatedString(exported.codedLink)) },
                    onQr = { qr = "A2mtun:// codé" to exported.codedLink },
                )
            }

            TextButton(
                onClick = {
                    val payload = exported?.shareUri ?: config
                    val send =
                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, payload)
                        }
                    context.startActivity(
                        android.content.Intent.createChooser(send, "Partager « $profileName »"),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(text = "  Partager via une autre application")
            }

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
