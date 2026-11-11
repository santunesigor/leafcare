package br.com.leafcare.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import br.com.leafcare.data.AnalysisEntity
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
