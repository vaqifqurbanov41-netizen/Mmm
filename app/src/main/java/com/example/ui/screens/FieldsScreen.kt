package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.FarmField
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
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LocalAqroColors
import com.example.ui.viewmodel.DocScanUiState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    var useUtm by remember { mutableStateOf(false) }
    var utmZone by remember { mutableIntStateOf(38) }
    var latLonText by remember { mutableStateOf("") }
    var utmText by remember { mutableStateOf("") }
    var coordsError by remember { mutableStateOf<String?>(null) }
    var showCoordinatesDialog by remember { mutableStateOf(false) }
    var showBatchCoords by remember { mutableStateOf(false) }
    var activeKmzDoc by remember { mutableStateOf<KmzDocument?>(null) }
    var pendingSaveKmzBytes by remember { mutableStateOf<ByteArray?>(null) }
    var kmzStatusMessage by remember { mutableStateOf<String?>(null) }

    // Doc scan tab state
    var docBitmap by remember { mutableStateOf<Bitmap?>(null) }

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

    val performGpsLocate: (Boolean) -> Unit = { insertIntoCoordInput ->
        isLocating = true
        gpsStatusBanner = strings.btnLocating
        fetchSingleGpsFix(
            context = context,
            onSuccess = { lat, lon, acc ->
                isLocating = false
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
                if (insertIntoCoordInput) {
                    if (showBatchCoords) {
                        if (useUtm) {
                            utmZone = if (utm.zone == 39) 39 else 38
                            val line = "${utm.easting.roundToInt()}, ${utm.northing.roundToInt()}"
                            utmText = if (utmText.isBlank()) line else "${utmText.trim()}\n$line"
                        } else {
                            val line = "${formatNumber(lat, 6)}, ${formatNumber(lon, 6)}"
                            latLonText = if (latLonText.isBlank()) line else "${latLonText.trim()}\n$line"
                        }
                    } else {
                        onAddPoint(LatLon(lat, lon))
                    }
                }
            },
            onFailure = {
                isLocating = false
                gpsStatusBanner = strings.gpsUnavailable
            },
        )
    }

    var pendingInsertGps by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            performGpsLocate(pendingInsertGps)
        } else {
            gpsStatusBanner = strings.gpsPermissionDenied
        }
    }

    val triggerLocate: (Boolean) -> Unit = { insertToCoords ->
        pendingInsertGps = insertToCoords
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            performGpsLocate(insertToCoords)
        } else {
            gpsStatusBanner = strings.gpsPermissionNeeded
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            docBitmap = decodeUriToBitmap(context, uri)
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

    KmzViewerDialog(
        document = activeKmzDoc,
        isSatellite = isSatellite,
        onToggleLayer = onToggleLayer,
        onLoadToMap = { placemark ->
            onSetPolygon(placemark.points)
            val (_, fitZoom) = GeoUtils.computeBoundsCenterAndZoom(placemark.points)
            onZoomChanged(fitZoom)
            activeKmzDoc = null
            activeTab = 0
        },
        onSavePlacemarkAsField = { placemark ->
            onSetPolygon(placemark.points)
            val (_, fitZoom) = GeoUtils.computeBoundsCenterAndZoom(placemark.points)
            onZoomChanged(fitZoom)
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
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.mapHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.muted,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    ) {
                        Button(
                            onClick = { showCoordinatesDialog = true },
                            modifier = Modifier
                                .weight(1.15f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("open_coordinates_dialog_btn"),
                        ) {
                            Icon(Icons.Filled.PinDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(strings.btnCoordinatesModal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onUndoPoint,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("map_undo_btn"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(strings.undo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onClearPoints,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("map_clear_btn"),
                        ) {
                            Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(strings.clear, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    ) {
                        OutlinedButton(
                            onClick = {
                                kmzOpenLauncher.launch(
                                    arrayOf(
                                        "application/vnd.google-earth.kmz",
                                        "application/vnd.google-earth.kml+xml",
                                        "application/octet-stream",
                                        "*/*",
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("open_kmz_file_btn"),
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(strings.btnKmzOpen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
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
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("share_kmz_btn"),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(strings.btnKmzShare, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("save_kmz_btn"),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(strings.btnKmzSave, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = 48.dp)
                                    .testTag("coords_tab_add_btn"),
                            ) {
                                Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(strings.btnCoordinatesModal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { triggerLocate(true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = 48.dp)
                                    .testTag("insert_gps_quick_btn"),
                            ) {
                                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(strings.coordFillGpsBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                OutlinedTextField(
                                    value = latLonText,
                                    onValueChange = { latLonText = it; coordsError = null },
                                    placeholder = { Text(strings.latLonPlaceholder) },
                                    minLines = 3,
                                    modifier = Modifier.fillMaxWidth().testTag("lat_lon_input"),
                                )
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
                                OutlinedTextField(
                                    value = utmText,
                                    onValueChange = { utmText = it; coordsError = null },
                                    placeholder = { Text(strings.utmPlaceholder) },
                                    minLines = 3,
                                    modifier = Modifier.fillMaxWidth().testTag("utm_points_input"),
                                )
                            }

                            if (coordsError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                DangerBannerBox(text = coordsError!!)
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val parsed = if (!useUtm) {
                                        parseLatLonLines(latLonText)
                                    } else {
                                        parseUtmLines(utmText, utmZone)
                                    }
                                    if (parsed.size < 3) {
                                        coordsError = strings.minThreePointsError
                                    } else {
                                        coordsError = null
                                        onSetPolygon(parsed)
                                        onZoomChanged(16)
                                        activeTab = 0
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("show_coords_on_map_btn"),
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
                        OutlinedButton(
                            onClick = {
                                docPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("pick_field_doc_btn"),
                        ) {
                            Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(strings.btnPickDocumentImage)
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
                                modifier = Modifier.fillMaxWidth().testTag("scan_field_doc_btn"),
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
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = buildString {
                                        if (docScanState.detectedAreaHa != null) {
                                            append("${strings.docScanResultPrefix} ${formatNumber(docScanState.detectedAreaHa, 4)} ha\n")
                                        }
                                        append(docScanState.summary)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp),
                                )
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

private fun parseLatLonLines(raw: String): List<LatLon> =
    raw.lines().mapNotNull { line ->
        val clean = line.trim().replace(";", ",")
        if (clean.isBlank()) return@mapNotNull null
        val parts = clean.split(Regex("[,\\s]+")).filter { it.isNotBlank() }
        if (parts.size < 2) return@mapNotNull null
        val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
        val lon = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        if (kotlin.math.abs(lat) > 90.0 || kotlin.math.abs(lon) > 180.0) return@mapNotNull null
        LatLon(lat, lon)
    }

private fun parseUtmLines(raw: String, zone: Int): List<LatLon> =
    raw.lines().mapNotNull { line ->
        val clean = line.trim().replace(";", ",")
        if (clean.isBlank()) return@mapNotNull null
        val parts = clean.split(Regex("[,\\s]+")).filter { it.isNotBlank() }
        if (parts.size < 2) return@mapNotNull null
        val easting = parts[0].toDoubleOrNull() ?: return@mapNotNull null
        val northing = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        GeoUtils.utmToLatLon(easting, northing, zone)
    }

@SuppressLint("MissingPermission")
fun fetchSingleGpsFix(
    context: Context,
    onSuccess: (Double, Double, Float) -> Unit,
    onFailure: () -> Unit,
) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val cts = CancellationTokenSource()
    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
        .addOnSuccessListener { loc ->
            if (loc != null) {
                onSuccess(loc.latitude, loc.longitude, loc.accuracy)
            } else {
                client.lastLocation
                    .addOnSuccessListener { last ->
                        if (last != null) onSuccess(last.latitude, last.longitude, last.accuracy)
                        else onFailure()
                    }
                    .addOnFailureListener { onFailure() }
            }
        }
        .addOnFailureListener { onFailure() }
}

fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
    }
} catch (_: Exception) {
    null
}
