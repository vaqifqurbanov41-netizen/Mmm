package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.model.FarmField
import com.example.domain.DocCoordTarget
import com.example.domain.FieldMeasurements
import com.example.domain.GeoUtils
import com.example.domain.KmzDocument
import com.example.domain.KmzPlacemark
import com.example.domain.KmzUtils
import com.example.domain.LatLon
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.components.AqroCard
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DangerBannerBox
import com.example.ui.components.EmptyStateMessage
import com.example.ui.components.FieldMapCanvas
import com.example.ui.components.ScreenTopBar
import com.example.ui.components.TabPillsRow
import com.example.ui.components.formatNumber
import com.example.ui.components.rememberGpsLocateAction
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LocalAqroColors
import com.example.ui.viewmodel.DocScanUiState
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DEFAULT_COORD_ROW_COUNT = 8

private data class CoordRowInput(
    val x: String = "",
    val y: String = "",
)

@Composable
fun FieldsScreen(
    language: AppLanguage,
    fields: List<FarmField>,
    mapPoints: List<LatLon>,
    measurements: FieldMeasurements,
    mapCenter: LatLon,
    mapZoom: Int,
    isSatellite: Boolean,
    gpsLocation: LatLon?,
    gpsAccuracyM: Float?,
    docScanState: DocScanUiState,
    onAddPoint: (LatLon) -> Unit,
    onMovePoint: (Int, LatLon) -> Unit,
    onUndoPoint: () -> Unit,
    onClearPoints: () -> Unit,
    onSetPolygon: (List<LatLon>) -> Unit,
    onCenterChanged: (LatLon) -> Unit,
    onZoomChanged: (Int) -> Unit,
    onToggleLayer: () -> Unit,
    onUpdateGpsFix: (Double, Double, Float) -> Unit,
    onScanDocument: (Bitmap, AppLanguage, (Double?) -> Unit) -> Unit,
    onSaveField: suspend (String, String, Double, Double, Double, String, List<String>) -> Unit,
    onFocusFieldOnMap: (FarmField) -> Unit,
    onDeleteField: suspend (String) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableIntStateOf(0) }
    var gpsStatusBanner by remember { mutableStateOf<String?>(null) }
    var isLocating by remember { mutableStateOf(false) }

    // Coords / UTM tab & dialogs state
    var useUtm by remember { mutableStateOf(true) }
    var utmZone by remember { mutableIntStateOf(38) }
    var latLonRows by remember { mutableStateOf(List(DEFAULT_COORD_ROW_COUNT) { CoordRowInput() }) }
    var utmRows by remember { mutableStateOf(List(DEFAULT_COORD_ROW_COUNT) { CoordRowInput() }) }
    var coordsError by remember { mutableStateOf<String?>(null) }
    var showCoordinatesDialog by remember { mutableStateOf(false) }
    var showBatchCoords by remember { mutableStateOf(true) }
    var isMapFullscreen by remember { mutableStateOf(false) }
    var activeKmzDoc by remember { mutableStateOf<KmzDocument?>(null) }
    var pendingSaveKmzBytes by remember { mutableStateOf<ByteArray?>(null) }
    var kmzStatusMessage by remember { mutableStateOf<String?>(null) }

    // Doc scan tab state
    var docBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingDocCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDocCameraFile by remember { mutableStateOf<File?>(null) }
    var docCameraDenied by remember { mutableStateOf(false) }

    // Form state
    var fieldName by remember { mutableStateOf("") }
    var fieldCrop by remember { mutableStateOf("") }
    var fieldSizeInput by remember { mutableStateOf("") }
    var fieldWidthInput by remember { mutableStateOf("") }
    var fieldLengthInput by remember { mutableStateOf("") }
    var fieldLocation by remember { mutableStateOf("") }
    var formMessage by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var fieldPendingDelete by remember { mutableStateOf<FarmField?>(null) }

    // Keep text fields synchronized with polygon measurements when points exist
    LaunchedEffect(measurements) {
        if (mapPoints.size >= 3 && measurements.areaHa > 0) {
            fieldSizeInput = formatNumber(measurements.areaHa, 4)
            fieldWidthInput = measurements.widthM.roundToInt().toString()
            fieldLengthInput = measurements.lengthM.roundToInt().toString()
        }
    }

    // Route scanned X/Y coordinates strictly into 1st col (X) and 2nd col (Y) in sequential order
    LaunchedEffect(docScanState.scanId) {
        if (docScanState.scanId > 0L) {
            when (docScanState.coordTarget) {
                DocCoordTarget.UTM_38T -> {
                    val pairs = resolvePairsForUi(docScanState.scannedPairs, docScanState.utmLinesText)
                    if (pairs.isNotEmpty()) {
                        utmZone = if (docScanState.utmZone == 39) 39 else 38
                        useUtm = true
                        utmRows = padRowsToMinimum(pairs.map { CoordRowInput(it.first, it.second) })
                        showBatchCoords = true
                        coordsError = null
                    }
                }
                DocCoordTarget.LAT_LON -> {
                    val geoPairsFromRaw = docScanState.scannedPairs.takeIf { list ->
                        list.isNotEmpty() && list.all { (x, y) ->
                            val xv = x.replace(',', '.').toDoubleOrNull()
                            val yv = y.replace(',', '.').toDoubleOrNull()
                            xv != null && yv != null && abs(xv) <= 90.0 && abs(yv) <= 180.0
                        }
                    } ?: emptyList()
                    val pairs = geoPairsFromRaw.ifEmpty {
                        resolvePairsForUi(emptyList(), docScanState.latLonLinesText)
                    }
                    if (pairs.isNotEmpty()) {
                        useUtm = false
                        latLonRows = padRowsToMinimum(pairs.map { CoordRowInput(it.first, it.second) })
                        showBatchCoords = true
                        coordsError = null
                    }
                }
            }
            val areaHa = docScanState.detectedAreaHa
            if (areaHa != null && areaHa > 0) {
                fieldSizeInput = formatNumber(areaHa, 4)
            }
        }
    }

    var pendingInsertGps by remember { mutableStateOf(false) }
    val gpsLocateAction = rememberGpsLocateAction(
        onFixSuccess = { lat, lon, acc ->
            onUpdateGpsFix(lat, lon, acc)
            val utm = GeoUtils.latLonToUtm(lat, lon)
            gpsStatusBanner = String.format(
                language.locale,
                strings.gpsFoundFormat,
                acc.roundToInt(),
                formatNumber(lat, 6),
                formatNumber(lon, 6),
                utm.zone,
                utm.easting.roundToInt().toString(),
                utm.northing.roundToInt().toString(),
            )
            if (pendingInsertGps) {
                if (showBatchCoords) {
                    if (useUtm) {
                        utmZone = if (utm.zone == 39) 39 else 38
                        val entry = CoordRowInput(
                            x = String.format(Locale.US, "%.2f", utm.easting),
                            y = String.format(Locale.US, "%.2f", utm.northing),
                        )
                        utmRows = insertIntoFirstEmptyOrAppend(utmRows, entry)
                    } else {
                        val entry = CoordRowInput(
                            x = String.format(Locale.US, "%.6f", lat),
                            y = String.format(Locale.US, "%.6f", lon),
                        )
                        latLonRows = insertIntoFirstEmptyOrAppend(latLonRows, entry)
                    }
                } else {
                    onAddPoint(LatLon(lat, lon))
                }
            }
        },
        onStatusMessage = { msg -> gpsStatusBanner = msg },
        onLoadingChange = { loading ->
            isLocating = loading
            if (loading) {
                gpsStatusBanner = strings.btnLocating
            }
        },
    )

    val triggerLocate: (Boolean) -> Unit = { insertToCoords ->
        pendingInsertGps = insertToCoords
        gpsLocateAction()
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            docBitmap = decodeUriToBitmap(context, uri)
            docCameraDenied = false
        }
    }

    val docTakePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingDocCameraUri
        val file = pendingDocCameraFile
        if (success && uri != null) {
            docBitmap = decodeUriToBitmap(context, uri)
            docCameraDenied = false
        }
        if (file != null) {
            runCatching { if (file.exists()) file.delete() }
        }
        pendingDocCameraUri = null
        pendingDocCameraFile = null
    }

    fun launchDocCamera() {
        runCatching {
            val dir = File(context.filesDir, "field_photos").apply { if (!exists()) mkdirs() }
            val tempFile = File(dir, "doc_scan_${UUID.randomUUID().toString().take(8)}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            pendingDocCameraFile = tempFile
            pendingDocCameraUri = uri
            docTakePictureLauncher.launch(uri)
        }.onFailure {
            pendingDocCameraFile = null
            pendingDocCameraUri = null
        }
    }

    val docCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            docCameraDenied = false
            launchDocCamera()
        } else {
            docCameraDenied = true
        }
    }

    val kmzOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val parsed = KmzUtils.parseKmzUri(context, uri, fallbackName = "KMZ")
            if (parsed != null && parsed.placemarks.isNotEmpty()) {
                kmzStatusMessage = null
                activeKmzDoc = parsed
            } else {
                kmzStatusMessage = strings.kmzParseError
            }
        }
    }

    val kmzSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.google-earth.kmz")
    ) { uri: Uri? ->
        val bytes = pendingSaveKmzBytes
        pendingSaveKmzBytes = null
        if (uri != null && bytes != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out -> out.write(bytes) }
                kmzStatusMessage = strings.kmzSavedSuccess
            }
        }
    }

    fun buildActiveKmzPlacemarks(): Pair<String, List<KmzPlacemark>>? {
        if (mapPoints.isNotEmpty()) {
            val docName = fieldName.trim().ifBlank { "AqroMeslehet_Field" }
            return docName to listOf(
                KmzPlacemark(
                    name = docName,
                    description = fieldCrop.trim().ifBlank { "" },
                    points = mapPoints,
                )
            )
        }
        val fieldPlacemarks = fields.mapNotNull { field ->
            val pts = field.coords.mapNotNull { GeoUtils.decodeCoord(it) }
            if (pts.isEmpty()) null else KmzPlacemark(
                name = field.name,
                description = "${field.crop} • ${formatNumber(field.sizeHa, 2)} ha",
                points = pts,
            )
        }
        if (fieldPlacemarks.isEmpty()) return null
        return "AqroMeslehet_Fields" to fieldPlacemarks
    }

    fun shareKmzPlacemarks(docName: String, placemarks: List<KmzPlacemark>) {
        runCatching {
            val (_, shareUri) = KmzUtils.writeKmzToCacheFile(context, docName, placemarks)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.google-earth.kmz"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, "$docName.kmz")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, strings.btnKmzShare))
        }
    }

    CoordinatesEntryDialog(
        visible = showCoordinatesDialog,
        initialCenter = mapCenter,
        gpsLocation = gpsLocation,
        onRequestGpsFill = { applyFill ->
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                fetchSingleGpsFix(
                    context = context,
                    onSuccess = { lat, lon, acc ->
                        onUpdateGpsFix(lat, lon, acc)
                        applyFill(lat, lon)
                    },
                    onFailure = { gpsStatusBanner = strings.gpsUnavailable },
                )
            } else {
                triggerLocate(false)
            }
        },
        onAddCoordinate = { point, centerOnPoint ->
            onAddPoint(point)
            if (centerOnPoint) {
                onCenterChanged(point)
                onZoomChanged(maxOf(mapZoom, 16))
            }
        },
        onDismiss = { showCoordinatesDialog = false },
    )

    fun syncRowsFromPolygon(pts: List<LatLon>) {
        if (pts.isEmpty()) return
        val llList = pts.map {
            CoordRowInput(
                x = String.format(Locale.US, "%.6f", it.lat),
                y = String.format(Locale.US, "%.6f", it.lon),
            )
        }
        val utmList = pts.map {
            val u = GeoUtils.latLonToUtm(it.lat, it.lon)
            CoordRowInput(
                x = String.format(Locale.US, "%.2f", u.easting),
                y = String.format(Locale.US, "%.2f", u.northing),
            )
        }
        latLonRows = padRowsToMinimum(llList)
        utmRows = padRowsToMinimum(utmList)
    }

    KmzViewerDialog(
        document = activeKmzDoc,
        isSatellite = isSatellite,
        onToggleLayer = onToggleLayer,
        onLoadToMap = { placemark ->
            onSetPolygon(placemark.points)
            val (fitCenter, fitZoom) = GeoUtils.computeBoundsCenterAndZoom(placemark.points)
            onCenterChanged(fitCenter)
            onZoomChanged(fitZoom)
            syncRowsFromPolygon(placemark.points)
            activeKmzDoc = null
            activeTab = 0
        },
        onSavePlacemarkAsField = { placemark ->
            onSetPolygon(placemark.points)
            val (fitCenter, fitZoom) = GeoUtils.computeBoundsCenterAndZoom(placemark.points)
            onCenterChanged(fitCenter)
            onZoomChanged(fitZoom)
            syncRowsFromPolygon(placemark.points)
            fieldName = placemark.name
            val m = GeoUtils.calculateMeasurements(placemark.points)
            if (m.areaHa > 0) {
                fieldSizeInput = formatNumber(m.areaHa, 4)
                fieldWidthInput = m.widthM.roundToInt().toString()
                fieldLengthInput = m.lengthM.roundToInt().toString()
            }
            activeKmzDoc = null
            activeTab = 0
        },
        onShareKmz = { doc -> shareKmzPlacemarks(doc.documentName, doc.placemarks) },
        onDismiss = { activeKmzDoc = null },
    )

    if (isMapFullscreen) {
        Dialog(
            onDismissRequest = { isMapFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("fullscreen_map_dialog"),
            ) {
                FieldMapCanvas(
                    center = mapCenter,
                    zoom = mapZoom,
                    isSatellite = isSatellite,
                    points = mapPoints,
                    gpsLocation = gpsLocation,
                    onCenterChanged = onCenterChanged,
                    onZoomChanged = onZoomChanged,
                    onAddPoint = onAddPoint,
                    onMovePoint = onMovePoint,
                    onToggleLayer = onToggleLayer,
                    onLocateMe = { triggerLocate(false) },
                    isFullscreen = true,
                    onToggleFullscreen = { isMapFullscreen = false },
                    modifier = Modifier.fillMaxSize(),
                )

                // Top-center live parcel measurement HUD
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 108.dp, start = 12.dp, end = 12.dp),
                ) {
                    Text(
                        text = "${formatNumber(measurements.areaHa, 4)} ha • ${formatNumber(measurements.areaSot, 1)} sot • ${mapPoints.size} pts",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }

                // Bottom floating map controls
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(8.dp),
                    ) {
                        Button(
                            onClick = { showCoordinatesDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("fullscreen_coords_btn"),
                        ) {
                            Icon(Icons.Filled.PinDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(strings.btnCoordinatesModal, maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = { kmzOpenLauncher.launch(arrayOf("*/*")) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("fullscreen_kmz_btn"),
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(strings.btnKmzOpen, maxLines = 1, softWrap = false)
                        }
                        IconButton(
                            onClick = onUndoPoint,
                            modifier = Modifier.size(48.dp).testTag("fullscreen_undo_btn"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = strings.undo)
                        }
                        IconButton(
                            onClick = onClearPoints,
                            modifier = Modifier.size(48.dp).testTag("fullscreen_clear_btn"),
                        ) {
                            Icon(Icons.Filled.Clear, contentDescription = strings.clear)
                        }
                        IconButton(
                            onClick = { isMapFullscreen = false },
                            modifier = Modifier.size(48.dp).testTag("fullscreen_exit_btn"),
                        ) {
                            Icon(Icons.Filled.FullscreenExit, contentDescription = strings.btnExitFullscreenMap)
                        }
                    }
                }
            }
        }
    }

    ConfirmDeleteDialog(
        visible = fieldPendingDelete != null,
        itemLabel = fieldPendingDelete?.name.orEmpty(),
        onConfirm = {
            val target = fieldPendingDelete
            fieldPendingDelete = null
            if (target != null && target.id.isNotBlank()) {
                scope.launch { onDeleteField(target.id) }
            }
        },
        onDismiss = { fieldPendingDelete = null },
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("fields_screen_list"),
    ) {
        item {
            ScreenTopBar(title = strings.fieldsScreenTitle, onBack = onBack)
            TabPillsRow(
                tabs = listOf(strings.tabMapDraw, strings.tabCoordsUtm, strings.tabDocScan),
                selectedIndex = activeTab,
                onSelect = { activeTab = it },
            )
        }

        if (gpsStatusBanner != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = extra.pale,
                    border = BorderStroke(1.dp, extra.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isLocating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = gpsStatusBanner!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        when (activeTab) {
            0 -> {
                item {
                    FieldMapCanvas(
                        center = mapCenter,
                        zoom = mapZoom,
                        isSatellite = isSatellite,
                        points = mapPoints,
                        gpsLocation = gpsLocation,
                        onCenterChanged = onCenterChanged,
                        onZoomChanged = onZoomChanged,
                        onAddPoint = onAddPoint,
                        onMovePoint = onMovePoint,
                        onToggleLayer = onToggleLayer,
                        onLocateMe = { triggerLocate(false) },
                        onToggleFullscreen = { isMapFullscreen = true },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.mapHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.muted,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val actionBtnPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    val actionTextStyle = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    ) {
                        Button(
                            onClick = { showCoordinatesDialog = true },
                            contentPadding = actionBtnPadding,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("open_coordinates_dialog_btn"),
                        ) {
                            Icon(Icons.Filled.PinDrop, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = strings.btnCoordinatesModal,
                                style = actionTextStyle,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = onUndoPoint,
                            contentPadding = actionBtnPadding,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("map_undo_btn"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = strings.undo,
                                style = actionTextStyle,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    ) {
                        OutlinedButton(
                            onClick = onClearPoints,
                            contentPadding = actionBtnPadding,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("map_clear_btn"),
                        ) {
                            Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = strings.clear,
                                style = actionTextStyle,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                kmzOpenLauncher.launch(arrayOf("*/*"))
                            },
                            contentPadding = actionBtnPadding,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("open_kmz_file_btn"),
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = strings.btnKmzOpen,
                                style = actionTextStyle,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val payload = buildActiveKmzPlacemarks()
                                if (payload == null) {
                                    kmzStatusMessage = strings.kmzNeedPointsError
                                } else {
                                    kmzStatusMessage = null
                                    shareKmzPlacemarks(payload.first, payload.second)
                                }
                            },
                            contentPadding = actionBtnPadding,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("share_kmz_btn"),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = strings.btnKmzShare,
                                style = actionTextStyle,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val payload = buildActiveKmzPlacemarks()
                                if (payload == null) {
                                    kmzStatusMessage = strings.kmzNeedPointsError
                                } else {
                                    kmzStatusMessage = null
                                    pendingSaveKmzBytes = KmzUtils.buildKmzBytes(payload.first, payload.second)
                                    kmzSaveLauncher.launch("${payload.first}.kmz")
                                }
                            },
                            contentPadding = actionBtnPadding,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("save_kmz_btn"),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = strings.btnKmzSave,
                                style = actionTextStyle,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (kmzStatusMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = extra.pale,
                            border = BorderStroke(1.dp, extra.line),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = kmzStatusMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }

            1 -> {
                item {
                    AqroCard {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        ) {
                            Button(
                                onClick = { showCoordinatesDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = 48.dp)
                                    .testTag("coords_tab_add_btn"),
                            ) {
                                Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(strings.btnCoordinatesModal, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { triggerLocate(true) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = 48.dp)
                                    .testTag("insert_gps_quick_btn"),
                            ) {
                                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(strings.coordFillGpsBtn, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        if (mapPoints.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                mapPoints.forEachIndexed { idx, pt ->
                                    val u = GeoUtils.latLonToUtm(pt.lat, pt.lon)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = extra.pale,
                                        border = BorderStroke(1.dp, extra.line),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "#${idx + 1} • ${formatNumber(pt.lat, 5)}, ${formatNumber(pt.lon, 5)}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(
                                                    text = "UTM ${GeoUtils.formatUtmZone(pt.lat, u.zone)}  E: ${u.easting.roundToInt()}  N: ${u.northing.roundToInt()}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = extra.muted,
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    onSetPolygon(mapPoints.filterIndexed { i, _ -> i != idx })
                                                },
                                            ) {
                                                Icon(Icons.Filled.Delete, contentDescription = strings.delete, tint = DangerRed)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { showBatchCoords = !showBatchCoords },
                            modifier = Modifier.testTag("toggle_batch_coords_btn"),
                        ) {
                            Text(strings.coordBatchToggleBtn)
                        }

                        if (showBatchCoords) {
                            Spacer(modifier = Modifier.height(6.dp))
                            TabPillsRow(
                                tabs = listOf("Lat/Lon", "UTM"),
                                selectedIndex = if (useUtm) 1 else 0,
                                onSelect = { useUtm = it == 1 },
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            if (!useUtm) {
                                Text(strings.latLonPointsLabel, style = MaterialTheme.typography.labelMedium, color = extra.muted)
                                Spacer(modifier = Modifier.height(6.dp))
                                SeparatedCoordColumnTable(
                                    rows = latLonRows,
                                    firstColHeader = strings.coordFirstColHeader,
                                    secondColHeader = strings.coordSecondColHeader,
                                    firstFieldLabel = strings.coordLatXLabel,
                                    secondFieldLabel = strings.coordLonYLabel,
                                    firstPlaceholder = "40.6782",
                                    secondPlaceholder = "46.3606",
                                    rowTagPrefix = "lat_lon",
                                    onRowsChanged = { latLonRows = it; coordsError = null },
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { latLonRows = latLonRows + CoordRowInput() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("add_lat_lon_row_btn"),
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text(strings.btnAddCoordRow)
                                }
                            } else {
                                Text(strings.utmZoneLabel, style = MaterialTheme.typography.labelMedium, color = extra.muted)
                                Spacer(modifier = Modifier.height(6.dp))
                                TabPillsRow(
                                    tabs = listOf("38T", "39T"),
                                    selectedIndex = if (utmZone == 39) 1 else 0,
                                    onSelect = { utmZone = if (it == 1) 39 else 38 },
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(strings.utmPointsLabel, style = MaterialTheme.typography.labelMedium, color = extra.muted)
                                Spacer(modifier = Modifier.height(6.dp))
                                SeparatedCoordColumnTable(
                                    rows = utmRows,
                                    firstColHeader = strings.coordFirstColHeader,
                                    secondColHeader = strings.coordSecondColHeader,
                                    firstFieldLabel = strings.coordUtmXLabel,
                                    secondFieldLabel = strings.coordUtmYLabel,
                                    firstPlaceholder = "703195.81",
                                    secondPlaceholder = "4437081.26",
                                    rowTagPrefix = "utm",
                                    onRowsChanged = { utmRows = it; coordsError = null },
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { utmRows = utmRows + CoordRowInput() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("add_utm_row_btn"),
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text(strings.btnAddCoordRow)
                                }
                            }

                            if (coordsError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                DangerBannerBox(text = coordsError!!)
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val parsed = if (!useUtm) {
                                        parseLatLonRows(latLonRows)
                                    } else {
                                        parseUtmRows(utmRows, utmZone)
                                    }
                                    if (parsed.size < 3) {
                                        coordsError = strings.minThreePointsError
                                    } else {
                                        coordsError = null
                                        onSetPolygon(parsed)
                                        val (fitCenter, fitZoom) = GeoUtils.computeBoundsCenterAndZoom(parsed)
                                        onCenterChanged(fitCenter)
                                        onZoomChanged(fitZoom)
                                        activeTab = 0
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("show_coords_on_map_btn"),
                            ) {
                                Text(strings.btnShowOnMap)
                            }
                        }
                    }
                }
            }

            2 -> {
                item {
                    AqroCard {
                        Text(strings.docScanUploadLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        ) {
                            Button(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        docCameraDenied = false
                                        launchDocCamera()
                                    } else {
                                        docCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = 48.dp)
                                    .testTag("scan_field_doc_camera_btn"),
                            ) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(strings.btnCaptureDocumentCamera, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = {
                                    docPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = 48.dp)
                                    .testTag("pick_field_doc_btn"),
                            ) {
                                Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(strings.btnPickDocumentImage, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        if (docCameraDenied) {
                            Spacer(modifier = Modifier.height(8.dp))
                            DangerBannerBox(text = strings.cameraPermissionDenied)
                        }

                        docBitmap?.let { bmp ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = strings.tabDocScan,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    onScanDocument(bmp, language) { detectedHa ->
                                        if (detectedHa != null && detectedHa > 0) {
                                            fieldSizeInput = formatNumber(detectedHa, 4)
                                        }
                                    }
                                },
                                enabled = !docScanState.isScanning,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("scan_field_doc_btn"),
                            ) {
                                if (docScanState.isScanning) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(strings.btnScanFieldDoc)
                                }
                            }
                        }

                        if (docScanState.summary != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = extra.pale,
                                border = BorderStroke(1.dp, extra.line),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("doc_scan_result_card"),
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(12.dp),
                                ) {
                                    Text(
                                        text = buildString {
                                            if (docScanState.detectedAreaHa != null) {
                                                append("${strings.docScanResultPrefix} ${formatNumber(docScanState.detectedAreaHa, 4)} ha\n")
                                            }
                                            append(docScanState.summary)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    val routingNote = when {
                                        docScanState.coordTarget == DocCoordTarget.UTM_38T && docScanState.utmLinesText.isNotBlank() ->
                                            strings.docScanUtm38Applied
                                        docScanState.coordTarget == DocCoordTarget.LAT_LON && docScanState.latLonLinesText.isNotBlank() ->
                                            strings.docScanLatLonApplied
                                        else -> null
                                    }
                                    if (routingNote != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        ) {
                                            Text(
                                                text = routingNote,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                                    .testTag("doc_scan_routing_badge"),
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                        ) {
                                            OutlinedButton(
                                                onClick = { activeTab = 1 },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .heightIn(min = 48.dp)
                                                    .testTag("doc_open_coords_btn"),
                                            ) {
                                                Text(strings.docScanOpenCoordsBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Button(
                                                onClick = { activeTab = 0 },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .heightIn(min = 48.dp)
                                                    .testTag("doc_open_map_btn"),
                                            ) {
                                                Text(strings.docScanOpenMapBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (docScanState.error != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            DangerBannerBox(text = docScanState.error)
                        }
                    }
                }
            }
        }

        // Live Polygon Measurement Metrics
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                MetricBox(
                    label = strings.metricAreaHa,
                    value = "${formatNumber(measurements.areaHa, 4)} ha",
                    sub = "${formatNumber(measurements.areaSot, 1)} sot • ${measurements.areaM2.roundToInt()} m²",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                MetricBox(
                    label = strings.metricWidth,
                    value = "${measurements.widthM.roundToInt()} ${strings.mUnit}",
                    sub = strings.metricWidthSub,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                MetricBox(
                    label = strings.metricLength,
                    value = "${measurements.lengthM.roundToInt()} ${strings.mUnit}",
                    sub = String.format(language.locale, strings.metricPerimeterPrefix, measurements.perimeterM.roundToInt()),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                val firstPtText = mapPoints.firstOrNull()?.let {
                    "${formatNumber(it.lat, 4)}, ${formatNumber(it.lon, 4)}"
                } ?: "—"
                MetricBox(
                    label = strings.firstPointPrefix,
                    value = firstPtText,
                    sub = "${mapPoints.size} pts",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        // Save Field Form
        item {
            AqroCard {
                Text(strings.saveFieldCardTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = fieldName,
                    onValueChange = { fieldName = it; formError = null },
                    label = { Text(strings.fieldNameLabel) },
                    placeholder = { Text(strings.fieldNamePlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("field_name_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fieldCrop,
                    onValueChange = { fieldCrop = it; formError = null },
                    label = { Text(strings.fieldCropLabel) },
                    placeholder = { Text(strings.fieldCropPlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("field_crop_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                ) {
                    OutlinedTextField(
                        value = fieldSizeInput,
                        onValueChange = { fieldSizeInput = it; formError = null },
                        label = { Text(strings.fieldSizeLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text(strings.fieldSizePlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("field_size_input"),
                    )
                    OutlinedTextField(
                        value = fieldLocation,
                        onValueChange = { fieldLocation = it },
                        label = { Text(strings.fieldLocationLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text(strings.fieldLocationPlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                ) {
                    OutlinedTextField(
                        value = fieldWidthInput,
                        onValueChange = { fieldWidthInput = it },
                        label = { Text(strings.fieldWidthLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text(strings.fieldAutoPlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    OutlinedTextField(
                        value = fieldLengthInput,
                        onValueChange = { fieldLengthInput = it },
                        label = { Text(strings.fieldLengthLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text(strings.fieldAutoPlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }

                if (formError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DangerBannerBox(text = formError!!)
                }
                if (formMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(formMessage!!, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val sizeVal = fieldSizeInput.replace(",", ".").toDoubleOrNull()
                            ?: measurements.areaHa.takeIf { it > 0 }
                        if (fieldName.isBlank() || fieldCrop.isBlank() || sizeVal == null || sizeVal <= 0) {
                            formError = strings.fillRequiredFields
                        } else {
                            val wVal = fieldWidthInput.replace(",", ".").toDoubleOrNull() ?: measurements.widthM
                            val lVal = fieldLengthInput.replace(",", ".").toDoubleOrNull() ?: measurements.lengthM
                            val encodedCoords = mapPoints.map { GeoUtils.encodeCoord(it) }
                            scope.launch {
                                onSaveField(fieldName, fieldCrop, sizeVal, wVal, lVal, fieldLocation, encodedCoords)
                                fieldName = ""
                                fieldCrop = ""
                                fieldSizeInput = ""
                                fieldWidthInput = ""
                                fieldLengthInput = ""
                                fieldLocation = ""
                                formError = null
                                formMessage = strings.fieldSavedToast
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_field_btn"),
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(strings.btnAddField)
                }
            }
        }

        // Saved Fields List
        item {
            Text(
                text = strings.savedFieldsTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        if (fields.isEmpty()) {
            item {
                AqroCard {
                    EmptyStateMessage(icon = Icons.Filled.Map, message = strings.noFieldsEmpty)
                }
            }
        } else {
            items(fields, key = { it.id }) { field ->
                AqroCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${field.name} — ${field.crop}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${formatNumber(field.sizeHa, 2)} ha (${field.widthM.roundToInt()}×${field.lengthM.roundToInt()} ${strings.mUnit})" +
                                    if (field.location.isNotBlank()) " • ${field.location}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.muted,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (field.coords.isNotEmpty()) {
                                val decodedPts = field.coords.mapNotNull { GeoUtils.decodeCoord(it) }
                                OutlinedButton(
                                    onClick = {
                                        onFocusFieldOnMap(field)
                                        activeTab = 0
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(strings.btnViewOnMap, style = MaterialTheme.typography.labelMedium)
                                }
                                if (decodedPts.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            shareKmzPlacemarks(
                                                docName = field.name,
                                                placemarks = listOf(
                                                    KmzPlacemark(
                                                        name = field.name,
                                                        description = "${field.crop} • ${formatNumber(field.sizeHa, 2)} ha",
                                                        points = decodedPts,
                                                    )
                                                ),
                                            )
                                        },
                                        modifier = Modifier.testTag("field_share_kmz_${field.id}"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Share,
                                            contentDescription = strings.btnKmzShare,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { fieldPendingDelete = field }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = strings.delete,
                                    tint = DangerRed,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
) {
    val extra = LocalAqroColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extra.line),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = extra.muted)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = extra.muted)
        }
    }
}

@Composable
private fun SeparatedCoordColumnTable(
    rows: List<CoordRowInput>,
    firstColHeader: String,
    secondColHeader: String,
    firstFieldLabel: String,
    secondFieldLabel: String,
    firstPlaceholder: String,
    secondPlaceholder: String,
    rowTagPrefix: String,
    onRowsChanged: (List<CoordRowInput>) -> Unit,
) {
    val extra = LocalAqroColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Separated X / Y column headers
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "#",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = extra.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(24.dp),
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = firstColHeader,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = secondColHeader,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        rows.forEachIndexed { idx, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = extra.pale,
                    border = BorderStroke(1.dp, extra.line),
                    modifier = Modifier.width(24.dp),
                ) {
                    Text(
                        text = "${idx + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                OutlinedTextField(
                    value = row.x,
                    onValueChange = { incoming ->
                        onRowsChanged(applyColumnPaste(rows, idx, incoming, isFirstColumn = true))
                    },
                    label = { Text(firstFieldLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    placeholder = { Text(firstPlaceholder, maxLines = 1) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${rowTagPrefix}_x_input_$idx"),
                )
                OutlinedTextField(
                    value = row.y,
                    onValueChange = { incoming ->
                        onRowsChanged(applyColumnPaste(rows, idx, incoming, isFirstColumn = false))
                    },
                    label = { Text(secondFieldLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    placeholder = { Text(secondPlaceholder, maxLines = 1) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${rowTagPrefix}_y_input_$idx"),
                )
            }
        }
    }
}

private fun padRowsToMinimum(
    source: List<CoordRowInput>,
    minSize: Int = DEFAULT_COORD_ROW_COUNT,
): List<CoordRowInput> {
    if (source.size >= minSize) return source
    return source + List(minSize - source.size) { CoordRowInput() }
}

private fun insertIntoFirstEmptyOrAppend(
    rows: List<CoordRowInput>,
    entry: CoordRowInput,
): List<CoordRowInput> {
    val emptyIdx = rows.indexOfFirst { it.x.isBlank() && it.y.isBlank() }
    return if (emptyIdx >= 0) {
        rows.mapIndexed { idx, cur -> if (idx == emptyIdx) entry else cur }
    } else {
        rows + entry
    }
}

private fun resolvePairsForUi(
    pairs: List<Pair<String, String>>,
    fallbackLinesText: String,
): List<Pair<String, String>> {
    if (pairs.isNotEmpty()) return pairs
    if (fallbackLinesText.isBlank()) return emptyList()
    return fallbackLinesText.lines().mapNotNull { line ->
        val parts = line.trim().replace(";", ",").split(Regex("[,\\s]+")).filter { it.isNotBlank() }
        if (parts.size >= 2) parts[0] to parts[1] else null
    }
}

private fun applyColumnPaste(
    rows: List<CoordRowInput>,
    startIdx: Int,
    incoming: String,
    isFirstColumn: Boolean,
): List<CoordRowInput> {
    val hasMultiLine = incoming.contains('\n') || incoming.contains('\r') || incoming.contains('\t') || incoming.contains(';')
    if (!hasMultiLine) {
        return rows.mapIndexed { idx, cur ->
            if (idx != startIdx) cur
            else if (isFirstColumn) cur.copy(x = incoming) else cur.copy(y = incoming)
        }
    }
    val lines = incoming.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (lines.isEmpty()) return rows
    val neededSize = maxOf(rows.size, startIdx + lines.size)
    val mutable = MutableList(neededSize) { i -> rows.getOrElse(i) { CoordRowInput() } }
    lines.forEachIndexed { offset, line ->
        val parts = line.replace(";", "\t").split(Regex("[\\t]+")).map { it.trim() }.filter { it.isNotBlank() }
        val rowIdx = startIdx + offset
        val cur = mutable[rowIdx]
        mutable[rowIdx] = if (parts.size >= 2) {
            CoordRowInput(x = parts[0], y = parts[1])
        } else {
            val single = line.trim()
            if (isFirstColumn) cur.copy(x = single) else cur.copy(y = single)
        }
    }
    return mutable
}

private fun parseLatLonRows(rows: List<CoordRowInput>): List<LatLon> =
    rows.mapNotNull { row ->
        val sx = row.x.trim().replace(",", ".")
        val sy = row.y.trim().replace(",", ".")
        if (sx.isBlank() || sy.isBlank()) return@mapNotNull null
        val xVal = sx.toDoubleOrNull() ?: return@mapNotNull null
        val yVal = sy.toDoubleOrNull() ?: return@mapNotNull null
        when {
            abs(xVal) <= 90.0 && abs(yVal) <= 180.0 -> LatLon(xVal, yVal)
            xVal in 1_000_000.0..9_999_999.9 && yVal in 100_000.0..999_999.9 ->
                GeoUtils.utmToLatLon(easting = yVal, northing = xVal, zone = 38)
            else -> null
        }
    }

private fun parseUtmRows(rows: List<CoordRowInput>, zone: Int): List<LatLon> =
    rows.mapNotNull { row ->
        val sx = row.x.trim().replace(",", ".")
        val sy = row.y.trim().replace(",", ".")
        if (sx.isBlank() || sy.isBlank()) return@mapNotNull null
        val xVal = sx.toDoubleOrNull() ?: return@mapNotNull null
        val yVal = sy.toDoubleOrNull() ?: return@mapNotNull null
        val (easting, northing) = if (xVal >= 1_000_000.0 && yVal < 1_000_000.0) {
            yVal to xVal
        } else {
            xVal to yVal
        }
        GeoUtils.utmToLatLon(easting, northing, zone)
    }

@SuppressLint("MissingPermission")
fun fetchSingleGpsFix(
    context: Context,
    onSuccess: (Double, Double, Float) -> Unit,
    onFailure: () -> Unit,
) {
    com.example.ui.components.fetchRobustGpsFix(
        context = context,
        onSuccess = onSuccess,
        onFailure = onFailure,
    )
}

fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? = try {
    val orientation = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    val maxDim = 2048
    var sampleSize = 1
    if (bounds.outWidth > maxDim || bounds.outHeight > maxDim) {
        val halfW = bounds.outWidth / 2
        val halfH = bounds.outHeight / 2
        while ((halfW / sampleSize) >= maxDim && (halfH / sampleSize) >= maxDim) {
            sampleSize *= 2
        }
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, opts)
    } ?: return null

    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(270f)
                postScale(-1f, 1f)
            }
            else -> return rawBitmap
        }
    }
    val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
    if (rotated !== rawBitmap && !rawBitmap.isRecycled) {
        rawBitmap.recycle()
    }
    rotated
} catch (_: Exception) {
    null
}
