package app.rengaine.ui.login

import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

private const val TAG = "QrScannerView"

/**
 * Flux caméra (CameraX) + reconnaissance de QR code (ZXing, pur Java —
 * aucune dépendance à Google Play Services). Repris de Musique.
 *
 * Première implémentation basée sur ML Kit Barcode Scanning (modèle
 * "bundled" embarqué), abandonnée : `BarcodeScanning.getClient(...)`
 * plantait au premier appel avec un `NullPointerException` **interne à
 * ML Kit** (`com.google.android.gms.internal.mlkit_vision_barcode.zzg`),
 * reproduit sur un OnePlus Android 16 (API 36) mais pas sur Android 12 —
 * signe d'un problème de compatibilité côté Google avec cette version
 * d'Android, pas quelque chose de corrigible côté app (mapping.txt du
 * build release utilisé pour désobfusquer la trace et confirmer que
 * l'exception vient bien de l'intérieur de ML Kit, pas de notre code).
 * ZXing n'a pas cette dépendance interne GMS et n'a pas ce risque.
 *
 * [onResult] est appelé pour CHAQUE code décodé tant que ce composable
 * reste affiché — y compris plusieurs fois pour le même code tant qu'il
 * reste dans le champ de la caméra. C'est à l'appelant de retirer ce
 * composable de la composition (ex. en repassant à l'écran du formulaire)
 * dès qu'un résultat valide est obtenu, ce qui coupe la caméra via
 * [DisposableEffect].
 */
@Composable
fun QrScannerView(onResult: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        val executor = ContextCompat.getMainExecutor(context)
        val reader = MultiFormatReader()
        // TRY_HARDER et analyse en 1080p : repris de Musique, où le QR
        // (dense) passait sous le radar en 640 × 480, la résolution choisie
        // par défaut par CameraX. Le QR de Rengaine est plus court (une
        // adresse et un code), mais une adresse longue reste possible.
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
        )
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1920, 1080),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { imageProxy ->
                    decodeQrCode(reader, hints, imageProxy)?.let(onResult)
                    imageProxy.close()
                }
                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }.onFailure { Log.e(TAG, "bindToLifecycle failed", it) }
            },
            executor,
        )

        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    AndroidView(modifier = modifier.fillMaxSize(), factory = { previewView })
}

/** Ne lit que le plan Y (luminance) de l'image YUV_420_888 — suffisant
 * pour la détection de QR code, pas besoin de la chrominance. Le
 * `rowStride` du plan (souvent > largeur réelle à cause de l'alignement
 * mémoire) est passé comme largeur du buffer source à ZXing, qui recadre
 * lui-même sur la zone utile — c'est l'usage documenté de
 * `PlanarYUVLuminanceSource` pour ce cas.
 *
 * Deux tentatives par image : la normale, puis la **luminance inversée**.
 * Un QR code standard est sombre sur fond clair, et c'est la seule
 * convention que ZXing sait lire ; or un QR affiché dans un terminal (ou
 * par un site en thème sombre) est très souvent blanc sur fond noir, donc
 * invisible pour le décodeur sans cette seconde passe. L'inversion ne coûte
 * rien quand un code lisible est présent : la première passe réussit et on
 * ne fait jamais la seconde.
 */
private fun decodeQrCode(
    reader: MultiFormatReader,
    hints: Map<DecodeHintType, Any>,
    imageProxy: ImageProxy,
): String? {
    val plane = imageProxy.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val source = PlanarYUVLuminanceSource(
        bytes,
        plane.rowStride,
        imageProxy.height,
        0,
        0,
        imageProxy.width,
        imageProxy.height,
        false,
    )
    return decodeSource(reader, hints, source) ?: decodeSource(reader, hints, source.invert())
}

/** `reader.decode(image, hints)` (2 arguments) est utilisé volontairement
 * plutôt que `decode(image)` seul : ce dernier appelle en interne
 * `setHints(null)` avant de décoder, ce qui écrasait silencieusement le
 * filtrage `POSSIBLE_FORMATS = QR_CODE` posé une seule fois à la création
 * du reader — pas la cause du bug initial (MultiFormatReader retombe sur
 * un jeu de lecteurs par défaut qui inclut quand même QRCodeReader), mais
 * un bug réel qui élargit inutilement chaque tentative de décodage à
 * d'autres formats (1D, DataMatrix, Aztec, PDF417...). */
private fun decodeSource(
    reader: MultiFormatReader,
    hints: Map<DecodeHintType, Any>,
    source: LuminanceSource,
): String? = try {
    reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
} catch (_: NotFoundException) {
    null
} catch (e: Exception) {
    Log.e(TAG, "unexpected decode error", e)
    null
} finally {
    reader.reset()
}
