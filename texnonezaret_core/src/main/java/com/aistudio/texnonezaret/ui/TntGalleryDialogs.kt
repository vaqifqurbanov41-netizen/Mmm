package com.aistudio.texnonezaret.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aistudio.texnonezaret.data.TntCapturedPhoto
import com.aistudio.texnonezaret.ui.theme.LocalTexnoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TntEncryptedDownloadDialog(
    photo: TntCapturedPhoto,
    onConfirmDownload: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val extra = LocalTexnoColors.current
    var password by remember(photo.id) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.EnhancedEncryption,
                contentDescription = null,
                tint = extra.brand,
            )
        },
        title = {
            Text("Şifrəli JPG yüklə")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Şəkil AES-256 ilə şifrələnmiş .jpg faylı kimi Downloads/TNT qovluğuna yazılacaq.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.muted,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Əlavə ixrac parolu (istəyə bağlı)") },
                    placeholder = { Text("Boş qalsa yalnız bu cihaz açar") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Parolu gizlət" else "Parolu göstər",
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_export_password_input"),
                )
                Text(
                    text = "Tarix: ${photo.timestampText}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = extra.ink,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmDownload(password.trim().takeIf { it.isNotEmpty() }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = extra.brand,
                    contentColor = extra.brandInk,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_download_encrypted_jpg_btn"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Yüklə (.jpg)")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Ləğv et")
            }
        },
    )
}

@Composable
fun TntSharePhotoDialog(
    photo: TntCapturedPhoto,
    onConfirmShare: (encrypted: Boolean, optionalPassword: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val extra = LocalTexnoColors.current
    var shareEncrypted by remember(photo.id) { mutableStateOf(true) }
    var password by remember(photo.id) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = null,
                tint = extra.brand,
            )
        },
        title = {
            Text("Şəkli paylaş")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = { shareEncrypted = true },
                    shape = RoundedCornerShape(10.dp),
                    color = if (shareEncrypted) extra.okBg else extra.surface,
                    border = BorderStroke(1.dp, if (shareEncrypted) extra.brand else extra.line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_mode_encrypted"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp),
                    ) {
                        RadioButton(
                            selected = shareEncrypted,
                            onClick = { shareEncrypted = true },
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Şifrəli JPG kimi paylaş",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = extra.ink,
                            )
                            Text(
                                text = "AES-256 şifrəli JPG faylı (yalnız TNT tətbiqi ilə açılır)",
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.muted,
                            )
                        }
                    }
                }

                if (shareEncrypted) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Paylaşım parolu (istəyə bağlı)") },
                        placeholder = { Text("Başqa cihazda açmaq üçün parol yazın") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showPassword) "Parolu gizlət" else "Parolu göstər",
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("share_export_password_input"),
                    )
                }

                Surface(
                    onClick = { shareEncrypted = false },
                    shape = RoundedCornerShape(10.dp),
                    color = if (!shareEncrypted) extra.warnBg else extra.surface,
                    border = BorderStroke(1.dp, if (!shareEncrypted) extra.warnText else extra.line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_mode_plain"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp),
                    ) {
                        RadioButton(
                            selected = !shareEncrypted,
                            onClick = { shareEncrypted = false },
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Damğalı JPG kimi paylaş",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = extra.ink,
                            )
                            Text(
                                text = "Altında tarix, saat və koordinat olan açıq şəkil",
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.muted,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmShare(
                        shareEncrypted,
                        if (shareEncrypted) password.trim().takeIf { it.isNotEmpty() } else null,
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = extra.brand,
                    contentColor = extra.brandInk,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_share_jpg_btn"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Paylaş")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Ləğv et")
            }
        },
    )
}

@Composable
fun TntGalleryImportMetadataDialog(
    draft: DraftImportedPhoto,
    onRequestGpsRefresh: () -> Unit,
    onConfirm: (timestampText: String, latitudeText: String, longitudeText: String, plateTag: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val extra = LocalTexnoColors.current
    var timestampText by remember(draft.tempFile.absolutePath) { mutableStateOf(draft.timestampText) }
    var latitudeText by remember(draft.tempFile.absolutePath) { mutableStateOf(draft.latitudeText) }
    var longitudeText by remember(draft.tempFile.absolutePath) { mutableStateOf(draft.longitudeText) }
    var plateTag by remember(draft.tempFile.absolutePath) { mutableStateOf(draft.plateTag) }

    LaunchedEffect(draft.latitudeText, draft.longitudeText) {
        if (draft.latitudeText.isNotBlank()) latitudeText = draft.latitudeText
        if (draft.longitudeText.isNotBlank()) longitudeText = draft.longitudeText
    }

    val previewBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = draft.tempFile.absolutePath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = 4
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeFile(draft.tempFile.absolutePath, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = extra.brand,
            )
        },
        title = {
            Text("Şəkil məlumatlarını daxil et")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(extra.bg),
                ) {
                    val bmp = previewBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Seçilmiş şəkil önbaxışı",
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

                Text(
                    text = "Bu məlumatlar digər TNT şəkillərində olduğu kimi şəklin alt zolağına yazılacaq və fayl şifrələnərək qorunan qalereyaya əlavə olunacaq.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.muted,
                )

                OutlinedTextField(
                    value = timestampText,
                    onValueChange = { timestampText = it },
                    label = {
                        Text(
                            if (draft.hasExifTime) {
                                "Tarix və saat (EXIF)"
                            } else {
                                "Tarix və saat (gg/aa/iiii ss:dd:sn)"
                            },
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_meta_timestamp_input"),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = latitudeText,
                        onValueChange = { latitudeText = it },
                        label = { Text("Enlik (Lat)") },
                        placeholder = { Text("40.40926") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_meta_lat_input"),
                    )
                    OutlinedTextField(
                        value = longitudeText,
                        onValueChange = { longitudeText = it },
                        label = { Text("Uzunluq (Lon)") },
                        placeholder = { Text("49.86709") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_meta_lon_input"),
                    )
                }

                OutlinedButton(
                    onClick = onRequestGpsRefresh,
                    enabled = !draft.isFetchingGps,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("import_meta_fetch_gps_btn"),
                ) {
                    if (draft.isFetchingGps) {
                        CircularProgressIndicator(
                            color = extra.brand,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GPS koordinatı alınır...")
                    } else {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cari GPS koordinatını al")
                    }
                }

                OutlinedTextField(
                    value = plateTag,
                    onValueChange = { plateTag = it },
                    label = { Text("DQN / Qeyd (istəyə bağlı)") },
                    placeholder = { Text("Məs: 04BA172") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_meta_plate_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(timestampText, latitudeText, longitudeText, plateTag)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = extra.brand,
                    contentColor = extra.brandInk,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_import_gallery_photo_btn"),
            ) {
                Text("Şifrələ və əlavə et")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Ləğv et")
            }
        },
    )
}

@Composable
fun TntEncryptedJpgUnlockDialog(
    prompt: PendingEncryptedJpgImport,
    onSubmitPassword: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val extra = LocalTexnoColors.current
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.LockOpen,
                contentDescription = null,
                tint = extra.brand,
            )
        },
        title = {
            Text("Şifrəli JPG açılması")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Seçilmiş .jpg faylı parol ilə şifrələnib. TNT qalereyasına əlavə etmək üçün ixrac parolunu daxil edin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.muted,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Şifrəli JPG parolu") },
                    singleLine = true,
                    isError = !prompt.errorMessage.isNullOrBlank(),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Parolu gizlət" else "Parolu göstər",
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("unlock_encrypted_jpg_password_input"),
                )
                if (!prompt.errorMessage.isNullOrBlank()) {
                    Text(
                        text = prompt.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.badText,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmitPassword(password) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = extra.brand,
                    contentColor = extra.brandInk,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_unlock_encrypted_jpg_btn"),
            ) {
                Text("Deşifrə et və əlavə et")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Ləğv et")
            }
        },
    )
}
