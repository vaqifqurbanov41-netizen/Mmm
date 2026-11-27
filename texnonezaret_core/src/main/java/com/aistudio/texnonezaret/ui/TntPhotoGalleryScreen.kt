package com.aistudio.texnonezaret.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aistudio.texnonezaret.data.TntCapturedPhoto
import com.aistudio.texnonezaret.ui.theme.LocalTexnoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TntPhotoGalleryScreen(
    vaultState: PhotoVaultUiState,
    onRequestCapturePhoto: () -> Unit,
    onRequestImportGalleryPhoto: () -> Unit,
    onRequestImportEncryptedJpg: () -> Unit,
    onOpenPhoto: (TntCapturedPhoto) -> Unit,
    onClosePhoto: () -> Unit,
    onDeletePhoto: (String) -> Unit,
    onDownloadEncryptedJpg: (TntCapturedPhoto, String?) -> Unit,
    onSharePhoto: (photo: TntCapturedPhoto, encrypted: Boolean, optionalPassword: String?) -> Unit,
    onRequestRefreshDraftGps: () -> Unit,
    onConfirmGalleryImport: (timestampText: String, latText: String, lonText: String, plateTag: String) -> Unit,
    onCancelGalleryImport: () -> Unit,
    onSubmitEncryptedImportPassword: (String) -> Unit,
    onCancelEncryptedImportPassword: () -> Unit,
    onLoadPhotoBytes: suspend (TntCapturedPhoto) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    val extra = LocalTexnoColors.current
    var pendingDeletePhoto by remember { mutableStateOf<TntCapturedPhoto?>(null) }
    var pendingDownloadPhoto by remember { mutableStateOf<TntCapturedPhoto?>(null) }
    var pendingSharePhoto by remember { mutableStateOf<TntCapturedPhoto?>(null) }

    if (pendingDeletePhoto != null) {
        val target = pendingDeletePhoto!!
        AlertDialog(
            onDismissRequest = { pendingDeletePhoto = null },
            title = { Text("Şəkli silmək istəyirsiniz?") },
            text = {
                Text(
                    text = "${target.timestampText} tarixli şəkil qorunan qalereyadan birdəfəlik silinəcək.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.muted,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeletePhoto = null
                        onDeletePhoto(target.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = extra.badText),
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePhoto = null }) {
                    Text("Ləğv et")
                }
            },
        )
    }

    val downloadTarget = pendingDownloadPhoto
    if (downloadTarget != null) {
        TntEncryptedDownloadDialog(
            photo = downloadTarget,
            onConfirmDownload = { password ->
                pendingDownloadPhoto = null
                onDownloadEncryptedJpg(downloadTarget, password)
            },
            onDismiss = { pendingDownloadPhoto = null },
        )
    }

    val shareTarget = pendingSharePhoto
    if (shareTarget != null) {
        TntSharePhotoDialog(
            photo = shareTarget,
            onConfirmShare = { encrypted, password ->
                pendingSharePhoto = null
                onSharePhoto(shareTarget, encrypted, password)
            },
            onDismiss = { pendingSharePhoto = null },
        )
    }

    val importDraft = vaultState.pendingGalleryImport
    if (importDraft != null) {
        TntGalleryImportMetadataDialog(
            draft = importDraft,
            onRequestGpsRefresh = onRequestRefreshDraftGps,
            onConfirm = onConfirmGalleryImport,
            onDismiss = onCancelGalleryImport,
        )
    }

    val unlockPrompt = vaultState.pendingEncryptedImportPrompt
    if (unlockPrompt != null) {
        TntEncryptedJpgUnlockDialog(
            prompt = unlockPrompt,
            onSubmitPassword = onSubmitEncryptedImportPassword,
            onDismiss = onCancelEncryptedImportPassword,
        )
    }

    val previewPhoto = vaultState.selectedPhoto
    if (previewPhoto != null) {
        TntPhotoLightboxDialog(
            photo = previewPhoto,
            onLoadPhotoBytes = onLoadPhotoBytes,
            onRequestDownload = { pendingDownloadPhoto = previewPhoto },
            onRequestShare = { pendingSharePhoto = previewPhoto },
            onRequestDelete = { pendingDeletePhoto = previewPhoto },
            onDismiss = onClosePhoto,
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .widthIn(max = 680.dp)
            .fillMaxWidth()
            .testTag("tnt_gallery_list"),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = extra.surface,
                border = BorderStroke(1.dp, extra.line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(extra.okBg),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EnhancedEncryption,
                                    contentDescription = null,
                                    tint = extra.okText,
                                )
                            }
                            Column {
                                Text(
                                    text = "Qorunan Foto Qalereya (${vaultState.photos.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = extra.ink,
                                )
                                Text(
                                    text = "AES-256-GCM şifrəli · Yalnız tətbiq daxilində saxlanılır",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = extra.muted,
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = onRequestImportGalleryPhoto,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = extra.brand,
                                contentColor = extra.brandInk,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("gallery_import_photo_btn"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Qalereyadan əlavə et",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        OutlinedButton(
                            onClick = onRequestImportEncryptedJpg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, extra.line),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("gallery_import_encrypted_jpg_btn"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EnhancedEncryption,
                                contentDescription = null,
                                tint = extra.okText,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Şifrəli JPG aç",
                                color = extra.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    if (!vaultState.vaultError.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = extra.badBg,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = vaultState.vaultError,
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.badText,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    } else if (!vaultState.vaultMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = extra.okBg,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = vaultState.vaultMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.okText,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        if (vaultState.isProcessingPhoto) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = extra.surface,
                    border = BorderStroke(1.dp, extra.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        CircularProgressIndicator(
                            color = extra.brand,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = "Tarix, dəqiq saat və GPS koordinatları şəklin altına əlavə edilərək şifrələnir...",
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.ink,
                        )
                    }
                }
            }
        }

        if (vaultState.photos.isEmpty() && !vaultState.isProcessingPhoto) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = extra.surface,
                    border = BorderStroke(1.dp, extra.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(extra.okBg),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                tint = extra.brand,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Text(
                            text = "Hələ çəkilmiş şəkil yoxdur",
                            style = MaterialTheme.typography.titleMedium,
                            color = extra.ink,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Çəkilən şəkillərin altında tarix, dəqiq saat və GPS koordinatları əks olunur və yalnız bu qorunan qalereyada saxlanılır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.muted,
                            textAlign = TextAlign.Center,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = onRequestCapturePhoto,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = extra.brand,
                                    contentColor = extra.brandInk,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("gallery_empty_capture_btn"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoCamera,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Şəkil çək", fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = onRequestImportGalleryPhoto,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, extra.line),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("gallery_empty_import_btn"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = extra.brand,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Qalereyadan", color = extra.ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        } else {
            items(vaultState.photos, key = { it.id }) { photo ->
                TntVaultPhotoCard(
                    photo = photo,
                    onLoadPhotoBytes = onLoadPhotoBytes,
                    onOpen = { onOpenPhoto(photo) },
                    onRequestDownload = { pendingDownloadPhoto = photo },
                    onRequestShare = { pendingSharePhoto = photo },
                    onRequestDelete = { pendingDeletePhoto = photo },
                )
            }
        }
    }
}

@Composable
private fun TntVaultPhotoCard(
    photo: TntCapturedPhoto,
    onLoadPhotoBytes: suspend (TntCapturedPhoto) -> ByteArray?,
    onOpen: () -> Unit,
    onRequestDownload: () -> Unit,
    onRequestShare: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val extra = LocalTexnoColors.current
    val decodedBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = photo.id) {
        val raw = onLoadPhotoBytes(photo)
        value = withContext(Dispatchers.Default) {
            raw?.let { bytes ->
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = 2
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
            }
        }
    }

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        color = extra.surface,
        border = BorderStroke(1.dp, extra.line),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_photo_card_${photo.id}"),
    ) {
        Column {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(Color.Black),
            ) {
                val bmp = decodedBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "TNT çəkilmiş şəkil ${photo.timestampText}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CircularProgressIndicator(
                        color = extra.brand,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = extra.brand,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = photo.timestampText,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = extra.ink,
                        )
                    }
                    if (photo.plateTag.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = extra.okBg,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DirectionsCar,
                                    contentDescription = null,
                                    tint = extra.okText,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = photo.plateTag,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = extra.okText,
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = extra.muted,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = photo.coordinatesText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = extra.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = onRequestDownload,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("download_vault_photo_${photo.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "Şifrəli JPG yüklə",
                                tint = extra.brand,
                            )
                        }
                        IconButton(
                            onClick = onRequestShare,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("share_vault_photo_${photo.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Şəkli paylaş",
                                tint = extra.okText,
                            )
                        }
                        IconButton(
                            onClick = onRequestDelete,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("delete_vault_photo_${photo.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Şəkli sil",
                                tint = extra.badText,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TntPhotoLightboxDialog(
    photo: TntCapturedPhoto,
    onLoadPhotoBytes: suspend (TntCapturedPhoto) -> ByteArray?,
    onRequestDownload: () -> Unit,
    onRequestShare: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extra = LocalTexnoColors.current
    var scale by remember(photo.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(photo.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(photo.id) { mutableFloatStateOf(0f) }

    val fullBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = photo.id) {
        val raw = onLoadPhotoBytes(photo)
        value = withContext(Dispatchers.Default) {
            raw?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(photo.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            if (newScale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
            ) {
                val bmp = fullBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Tam ekran TNT şəkli",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY,
                            ),
                    )
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = photo.timestampText,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = photo.coordinatesText,
                        color = Color(0xFFD0D7D0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onRequestDownload,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("lightbox_download_photo_btn"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Şifrəli JPG yüklə",
                            tint = Color.White,
                        )
                    }
                    IconButton(
                        onClick = onRequestShare,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("lightbox_share_photo_btn"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Şəkli paylaş",
                            tint = Color.White,
                        )
                    }
                    IconButton(
                        onClick = onRequestDelete,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Şəkli sil",
                            tint = Color(0xFFFF8A80),
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Bağla",
                            tint = Color.White,
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = extra.okText,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = buildString {
                            append("AES-256 şifrəli · Tətbiq daxili qalereya")
                            if (photo.plateTag.isNotBlank()) append(" · ${photo.plateTag}")
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
