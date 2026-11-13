package br.com.leafcare.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import br.com.leafcare.data.AnalysisEntity
import br.com.leafcare.data.PhotoSyncState
import java.io.File
import coil.request.ImageRequest

/**
 * A restored analysis can reach the screen before its photo finishes
 * downloading. The request changes with the photo state, so Coil retries the
 * same file once it exists instead of keeping the failed load until the
 * screen is recreated.
 */
@Composable
fun rememberAnalysisPhoto(analysis: AnalysisEntity, photo: Any): ImageRequest {
    val context = LocalContext.current
    return remember(photo, analysis.photoSyncStatus) {
        ImageRequest.Builder(context)
            .data(photo)
            .memoryCacheKey("${analysis.photoName}#${analysis.photoSyncStatus}")
            .build()
    }
}

/**
 * True when the photo is not on this device and will not be downloaded: the
 * analysis came back from the account (new install or account switch) but its
 * photo was never sent. Inconclusive photos are never sent (Privacy Policy,
 * section 4), so only the result returns. While a remote photo is still
 * downloading (REMOTE_ONLY) this is false and the usual loading tint shows.
 */
fun isPhotoUnavailable(analysis: AnalysisEntity, photo: Any): Boolean =
    analysis.photoSyncStatus != PhotoSyncState.REMOTE_ONLY && photo is File && !photo.isFile

@Composable
fun rememberPhotoUnavailable(analysis: AnalysisEntity, photo: Any): Boolean =
    remember(photo, analysis.photoSyncStatus) { isPhotoUnavailable(analysis, photo) }
