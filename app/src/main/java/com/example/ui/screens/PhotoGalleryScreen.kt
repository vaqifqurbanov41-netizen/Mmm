package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.LocalGalleryPhoto
import com.example.data.local.LocalRecordsStore
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.components.AqroCard
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DangerBannerBox
import com.example.ui.components.EmptyStateMessage
import com.example.ui.components.ScreenTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalAqroColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhotoGalleryScreen(
    language: AppLanguage,
    store: LocalRecordsStore,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current
    val photos by store.photos.collectAsStateWithLifecycle()

    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var lastAutoSavedFileName by remember { mutableStateOf<String?>(null) }
    var permissionDeniedBanner by remember { mutableStateOf<String?>(null) }
    var renameTargetFileName by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var selectedPhoto by remember { mutableStateOf<LocalGalleryPhoto?>(null) }
    var pendingDeletePhoto by remember { mutableStateOf<LocalGalleryPhoto?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCaptureFile
        pendingCaptureFile = null
        if (file != null) {
            val autoTitle = store.generateDefaultPhotoTitle(language = language, isScan = false)
            if (success && store.finalizeCapturedPhoto(file, caption = autoTitle, language = language)) {
                statusBanner = String.format(language.locale, strings.galleryAutoSavedFormat, autoTitle)
                lastAutoSavedFileName = file.name
                permissionDeniedBanner = null
            } else {
                runCatching { if (file.exists()) file.delete() }
            }
        }
    }

    val launchCamera: () -> Unit = {
        runCatching {
            val (uri, file) = store.createCameraPhotoTarget()
            pendingCaptureFile = file
            takePictureLauncher.launch(uri)
        }.onFailure {
            pendingCaptureFile = null
            statusBanner = it.localizedMessage ?: strings.cameraPermissionNeeded
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedBanner = null
            launchCamera()
        } else {
            permissionDeniedBanner = strings.cameraPermissionDenied
        }
    }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val autoTitle = store.generateDefaultPhotoTitle(language = language, isScan = false)
            val saved = store.importPickedPhoto(uri, caption = autoTitle, language = language)
            if (saved != null) {
                statusBanner = String.format(language.locale, strings.galleryAutoSavedFormat, saved.caption)
                lastAutoSavedFileName = saved.fileName
            }
        }
    }

    fun sharePhoto(photo: LocalGalleryPhoto) {
        runCatching {
            val shareUri = store.getShareUri(photo.file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                if (photo.caption.isNotBlank()) {
                    putExtra(Intent.EXTRA_TEXT, photo.caption)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, strings.btnSharePhoto))
        }
    }

    if (renameTargetFileName != null) {
        AlertDialog(
            onDismissRequest = { renameTargetFileName = null },
            title = { Text(strings.galleryRenameTitle, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text(strings.photoNameLabel) },
                    placeholder = { Text(strings.photoCaptionHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("photo_caption_input"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val fileName = renameTargetFileName
                        renameTargetFileName = null
                        if (fileName != null && store.renamePhoto(fileName, renameDraft)) {
                            statusBanner = strings.photoNameRenamedMsg
                        }
                    },
                    modifier = Modifier.testTag("save_photo_caption_btn"),
                ) {
                    Text(strings.save, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetFileName = null }) {
                    Text(strings.cancel)
                }
            },
        )
    }

    ConfirmDeleteDialog(
        visible = pendingDeletePhoto != null,
        itemLabel = pendingDeletePhoto?.caption?.ifBlank { pendingDeletePhoto?.fileName.orEmpty() }.orEmpty(),
        onConfirm = {
            val target = pendingDeletePhoto
            pendingDeletePhoto = null
            if (target != null) {
                if (selectedPhoto?.fileName == target.fileName) {
                    selectedPhoto = null
                }
                store.deletePhoto(target.fileName)
            }
        },
        onDismiss = { pendingDeletePhoto = null },
    )

    if (selectedPhoto != null) {
        val current = photos.firstOrNull { it.fileName == selectedPhoto?.fileName } ?: selectedPhoto!!
        var editCaption by remember(current.fileName) { mutableStateOf(current.caption) }
        val formattedTime = remember(current.capturedAtMillis, language) {
            SimpleDateFormat("dd MMM yyyy, HH:mm", language.locale).format(Date(current.capturedAtMillis))
        }
        Dialog(
            onDismissRequest = { selectedPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelLarge,
                            color = extra.muted,
                        )
                        IconButton(onClick = { selectedPhoto = null }) {
                            Icon(Icons.Filled.Close, contentDescription = strings.cancel)
                        }
                    }

                    AsyncImage(
                        model = current.file,
                        contentDescription = current.caption.ifBlank { strings.galleryTitle },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 380.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(extra.pale),
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = editCaption,
                            onValueChange = { editCaption = it },
                            label = { Text(strings.photoNameLabel) },
                            placeholder = { Text(strings.photoCaptionHint) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                if (store.renamePhoto(current.fileName, editCaption)) {
                                    statusBanner = strings.photoNameRenamedMsg
                                }
                            },
                        ) {
                            Text(strings.galleryRenameBtn)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = { sharePhoto(current) },
                            modifier = Modifier.weight(1f).testTag("share_photo_btn"),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(strings.btnSharePhoto)
                        }
                        OutlinedButton(
                            onClick = { pendingDeletePhoto = current },
                            border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("delete_photo_btn"),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = strings.delete, tint = DangerRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(strings.delete, color = DangerRed)
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("photo_gallery_screen"),
    ) {
        item {
            ScreenTopBar(title = strings.galleryTitle, onBack = onBack)
        }

        item {
            AqroCard(containerColor = extra.pale) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = String.format(language.locale, strings.photoCountFormat, photos.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                launchCamera()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("take_photo_btn"),
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(strings.btnTakePhoto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            pickPhotoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("import_photo_btn"),
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(strings.btnImportPhoto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (statusBanner != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = statusBanner!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        val targetFile = lastAutoSavedFileName
                        if (targetFile != null) {
                            val found = photos.firstOrNull { it.fileName == targetFile }
                            if (found != null) {
                                TextButton(
                                    onClick = {
                                        renameTargetFileName = found.fileName
                                        renameDraft = found.caption
                                    },
                                ) {
                                    Text(strings.galleryRenameBtn)
                                }
                            }
                        }
                    }
                }
                if (permissionDeniedBanner != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DangerBannerBox(text = permissionDeniedBanner!!)
                }
            }
        }

        if (photos.isEmpty()) {
            item {
                AqroCard {
                    EmptyStateMessage(icon = Icons.Filled.PhotoLibrary, message = strings.galleryEmpty)
                }
            }
        } else {
            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val cols = if (maxWidth >= 600.dp) 3 else 2
                    val rows = photos.chunked(cols)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rows.forEach { rowPhotos ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                rowPhotos.forEach { photo ->
                                    GalleryPhotoTile(
                                        photo = photo,
                                        language = language,
                                        onOpen = { selectedPhoto = photo },
                                        onRename = {
                                            renameTargetFileName = photo.fileName
                                            renameDraft = photo.caption
                                        },
                                        onShare = { sharePhoto(photo) },
                                        onDelete = { pendingDeletePhoto = photo },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(cols - rowPhotos.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryPhotoTile(
    photo: LocalGalleryPhoto,
    language: AppLanguage,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val dateLabel = remember(photo.capturedAtMillis, language) {
        SimpleDateFormat("dd.MM.yy HH:mm", language.locale).format(Date(photo.capturedAtMillis))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extra.line),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .testTag("gallery_photo_card_${photo.fileName}"),
    ) {
        Column {
            AsyncImage(
                model = photo.file,
                contentDescription = photo.caption.ifBlank { dateLabel },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f)
                    .background(extra.pale),
            )
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = photo.caption.ifBlank { dateLabel },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (photo.caption.isNotBlank()) {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.muted,
                        maxLines = 1,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(
                        onClick = onRename,
                        modifier = Modifier.size(36.dp).testTag("rename_photo_btn_${photo.fileName}"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = strings.galleryRenameBtn,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = strings.btnSharePhoto,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = strings.delete,
                            tint = DangerRed,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
