package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.ai.ReferenceImageMatcher
import com.example.data.model.CropProtectionCatalog
import com.example.data.model.CropThreatEntry
import com.example.data.model.DiseaseVisualCatalog
import com.example.data.model.DiseaseVisualProfile
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.theme.LocalAqroColors

@Composable
fun RealDiseasePhotoThumbnail(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 74.dp,
    cornerRadius: Dp = 14.dp,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val extra = LocalAqroColors.current
    val imageLoader = remember(context) { ReferenceImageMatcher.getImageLoader(context) }
    val request = remember(context, imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .build()
    }

    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = extra.pale,
        border = BorderStroke(1.dp, extra.line),
        modifier = modifier.size(size),
    ) {
        SubcomposeAsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(cornerRadius)),
            loading = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().background(extra.pale),
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
            error = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().background(extra.pale),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = contentDescription,
                        tint = extra.muted,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
        )
    }
}

@Composable
fun DiseaseComparisonCandidateRow(
    threat: CropThreatEntry,
    profile: DiseaseVisualProfile,
    similarityPct: Int?,
    language: AppLanguage,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val extra = LocalAqroColors.current
    val strings = LocalStrings.current
    val cropName = CropProtectionCatalog.crops.firstOrNull { it.id == threat.cropId }?.name?.get(language).orEmpty()
    val threatTitle = threat.name.get(language)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extra.line),
        modifier = modifier
            .fillMaxWidth()
            .let { base ->
                if (onClick != null) base.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick) else base
            },
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(10.dp),
        ) {
            RealDiseasePhotoThumbnail(
                imageUrl = DiseaseVisualCatalog.resizedUrl(profile.referenceImageUrl, 360),
                contentDescription = "$cropName — $threatTitle",
                size = 76.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "$cropName • $threatTitle",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 6.dp),
                    )
                    if (similarityPct != null) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = extra.pale,
                        ) {
                            Text(
                                text = "~$similarityPct%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${DiseaseVisualCatalog.patternLabel(profile.patternType, language)} (${profile.plantPart.get(language)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = profile.visualCue.get(language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "🧪 ${threat.activeIngredient.get(language)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = profile.imageAttribution,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = extra.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (onClick != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Compare,
                                contentDescription = strings.scannerRealPhotoCompareBtn,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = strings.scannerRealPhotoCompareBtn,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiseasePhotoComparisonDialog(
    threat: CropThreatEntry,
    profile: DiseaseVisualProfile,
    scannedBitmap: Bitmap?,
    similarityPct: Int?,
    language: AppLanguage,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val extra = LocalAqroColors.current
    val strings = LocalStrings.current
    val imageLoader = remember(context) { ReferenceImageMatcher.getImageLoader(context) }
    val cropName = CropProtectionCatalog.crops.firstOrNull { it.id == threat.cropId }?.name?.get(language).orEmpty()
    val threatTitle = threat.name.get(language)
    val fullResUrl = remember(profile.referenceImageUrl) {
        DiseaseVisualCatalog.resizedUrl(profile.referenceImageUrl, 800)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$cropName • $threatTitle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (similarityPct != null) {
                        Text(
                            text = "${strings.scannerRealPhotoSimilarityLabel}: ~$similarityPct%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = strings.cancel)
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (scannedBitmap != null) {
                    Text(
                        text = strings.scannerYourPhotoLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Image(
                        bitmap = scannedBitmap.asImageBitmap(),
                        contentDescription = strings.scannerYourPhotoLabel,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(155.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }

                Text(
                    text = "${strings.scannerReferencePhotoLabel} (${profile.imageAttribution})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = extra.pale,
                    border = BorderStroke(1.dp, extra.line),
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context).data(fullResUrl).crossfade(true).build(),
                        imageLoader = imageLoader,
                        contentDescription = "$cropName — $threatTitle",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                        loading = {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                        },
                        error = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.BrokenImage,
                                        contentDescription = null,
                                        tint = extra.muted,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = strings.scannerReferenceOfflineHint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = extra.muted,
                                    )
                                }
                            }
                        },
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = extra.pale,
                    border = BorderStroke(1.dp, extra.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(10.dp),
                    ) {
                        Text(
                            text = "${DiseaseVisualCatalog.patternLabel(profile.patternType, language)} • ${profile.plantPart.get(language)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = profile.visualCue.get(language),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "🧪 ${strings.pestActiveIngredientLabel}: ${threat.activeIngredient.get(language)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = threat.guidance.get(language),
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.muted,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_disease_compare_dialog_btn"),
            ) {
                Text(strings.coordDoneBtn, fontWeight = FontWeight.Bold)
            }
        },
        modifier = Modifier.testTag("disease_photo_comparison_dialog"),
    )
}
