package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.GeoUtils
import com.example.domain.KmzDocument
import com.example.domain.KmzPlacemark
import com.example.domain.LatLon
import com.example.i18n.LocalStrings
import com.example.ui.components.DangerBannerBox
import com.example.ui.components.FieldMapCanvas
import com.example.ui.components.TabPillsRow
import com.example.ui.components.formatNumber
import com.example.ui.theme.LocalAqroColors
import kotlin.math.roundToInt

enum class CoordEntryMode {
    DDM,
    UTM,
    DECIMAL,
}

@Composable
fun CoordinatesEntryDialog(
    visible: Boolean,
    initialCenter: LatLon,
    gpsLocation: LatLon?,
    onRequestGpsFill: ((Double, Double) -> Unit) -> Unit,
    onAddCoordinate: (LatLon, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val seed = gpsLocation ?: initialCenter
    val seedUtm = remember(seed) { GeoUtils.latLonToUtm(seed.lat, seed.lon) }
    val seedDdmLat = remember(seed) { GeoUtils.decimalToDdm(seed.lat) }
    val seedDdmLon = remember(seed) { GeoUtils.decimalToDdm(seed.lon) }

    var mode by remember { mutableStateOf(CoordEntryMode.UTM) }

    // DDM state
    var ddmLatDeg by remember(seed) { mutableStateOf(seedDdmLat.first.toString()) }
    var ddmLatMin by remember(seed) { mutableStateOf(formatNumber(seedDdmLat.second, 4)) }
    var ddmLonDeg by remember(seed) { mutableStateOf(seedDdmLon.first.toString()) }
    var ddmLonMin by remember(seed) { mutableStateOf(formatNumber(seedDdmLon.second, 4)) }

    // UTM state
    var utmZoneText by remember(seed) { mutableStateOf(GeoUtils.formatUtmZone(seed.lat, seedUtm.zone)) }
    var utmEastingText by remember(seed) { mutableStateOf(seedUtm.easting.roundToInt().toString()) }
    var utmNorthingText by remember(seed) { mutableStateOf(seedUtm.northing.roundToInt().toString()) }

    // Decimal GPS state
    var decLatText by remember(seed) { mutableStateOf(formatNumber(seed.lat, 6)) }
    var decLonText by remember(seed) { mutableStateOf(formatNumber(seed.lon, 6)) }

    var errorText by remember { mutableStateOf<String?>(null) }

    fun applySeedValues(lat: Double, lon: Double) {
        val u = GeoUtils.latLonToUtm(lat, lon)
        val dl = GeoUtils.decimalToDdm(lat)
        val dn = GeoUtils.decimalToDdm(lon)
        ddmLatDeg = dl.first.toString()
        ddmLatMin = formatNumber(dl.second, 4)
        ddmLonDeg = dn.first.toString()
        ddmLonMin = formatNumber(dn.second, 4)
        utmZoneText = GeoUtils.formatUtmZone(lat, u.zone)
        utmEastingText = u.easting.roundToInt().toString()
        utmNorthingText = u.northing.roundToInt().toString()
        decLatText = formatNumber(lat, 6)
        decLonText = formatNumber(lon, 6)
        errorText = null
    }

    fun resolveCurrentLatLon(): LatLon? {
        return when (mode) {
            CoordEntryMode.DDM -> {
                val lDeg = ddmLatDeg.replace(",", ".").trim().toDoubleOrNull() ?: return null
                val lMin = ddmLatMin.replace(",", ".").trim().toDoubleOrNull() ?: return null
                val gDeg = ddmLonDeg.replace(",", ".").trim().toDoubleOrNull() ?: return null
                val gMin = ddmLonMin.replace(",", ".").trim().toDoubleOrNull() ?: return null
                if (lMin < 0 || lMin >= 60 || gMin < 0 || gMin >= 60) return null
                val lat = GeoUtils.ddmToDecimal(lDeg, lMin, lDeg < 0)
                val lon = GeoUtils.ddmToDecimal(gDeg, gMin, gDeg < 0)
                if (kotlin.math.abs(lat) > 90.0 || kotlin.math.abs(lon) > 180.0) null else LatLon(lat, lon)
            }
            CoordEntryMode.UTM -> {
                val e = utmEastingText.replace(",", ".").trim().toDoubleOrNull() ?: return null
                val n = utmNorthingText.replace(",", ".").trim().toDoubleOrNull() ?: return null
                if (e < 100000.0 || e > 950000.0 || n < 0.0 || n > 10000000.0) return null
                val ll = GeoUtils.utmToLatLon(e, n, utmZoneText.ifBlank { "38T" })
                if (ll.lat.isNaN() || ll.lon.isNaN() || kotlin.math.abs(ll.lat) > 85.0) null else ll
            }
            CoordEntryMode.DECIMAL -> {
                val lat = decLatText.replace(",", ".").trim().toDoubleOrNull() ?: return null
                val lon = decLonText.replace(",", ".").trim().toDoubleOrNull() ?: return null
                if (kotlin.math.abs(lat) > 90.0 || kotlin.math.abs(lon) > 180.0) null else LatLon(lat, lon)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("coordinates_entry_dialog"),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = strings.coordsModalTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(
                        onClick = { onRequestGpsFill { lat, lon -> applySeedValues(lat, lon) } },
                        modifier = Modifier.testTag("coords_fill_gps_btn"),
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(strings.coordFillGpsBtn, maxLines = 1)
                    }
                }

                // Option 1: DDM
                CoordRadioRow(
                    label = strings.coordDdmOption,
                    selected = mode == CoordEntryMode.DDM,
                    onClick = { mode = CoordEntryMode.DDM; errorText = null },
                    tag = "coord_mode_ddm",
                )
                if (mode == CoordEntryMode.DDM) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                    ) {
                        OutlinedTextField(
                            value = ddmLatDeg,
                            onValueChange = { ddmLatDeg = it; errorText = null },
                            label = { Text("Lat °") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.9f),
                        )
                        OutlinedTextField(
                            value = ddmLatMin,
                            onValueChange = { ddmLatMin = it; errorText = null },
                            label = { Text("Lat '") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1.3f),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                    ) {
                        OutlinedTextField(
                            value = ddmLonDeg,
                            onValueChange = { ddmLonDeg = it; errorText = null },
                            label = { Text("Lon °") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.9f),
                        )
                        OutlinedTextField(
                            value = ddmLonMin,
                            onValueChange = { ddmLonMin = it; errorText = null },
                            label = { Text("Lon '") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1.3f),
                        )
                    }
                }

                // Option 2: UTM (matching Fields Area Measure screenshot)
                CoordRadioRow(
                    label = strings.coordUtmOption,
                    selected = mode == CoordEntryMode.UTM,
                    onClick = { mode = CoordEntryMode.UTM; errorText = null },
                    tag = "coord_mode_utm",
                )
                if (mode == CoordEntryMode.UTM) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                    ) {
                        OutlinedTextField(
                            value = utmZoneText,
                            onValueChange = { utmZoneText = it.uppercase(); errorText = null },
                            label = { Text(strings.coordUtmZoneInputLabel) },
                            placeholder = { Text("38T") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("coord_utm_zone_input"),
                        )
                        OutlinedTextField(
                            value = utmEastingText,
                            onValueChange = { utmEastingText = it; errorText = null },
                            label = { Text(strings.coordUtmEastingLabel) },
                            placeholder = { Text("614940") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("coord_utm_easting_input"),
                        )
                        OutlinedTextField(
                            value = utmNorthingText,
                            onValueChange = { utmNorthingText = it; errorText = null },
                            label = { Text(strings.coordUtmNorthingLabel) },
                            placeholder = { Text("4504010") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("coord_utm_northing_input"),
                        )
                    }
                }

                // Option 3: Decimal GPS (Lat / Lon)
                CoordRadioRow(
                    label = strings.coordDecimalOption,
                    selected = mode == CoordEntryMode.DECIMAL,
                    onClick = { mode = CoordEntryMode.DECIMAL; errorText = null },
                    tag = "coord_mode_decimal",
                )
                if (mode == CoordEntryMode.DECIMAL) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                    ) {
                        OutlinedTextField(
                            value = decLatText,
                            onValueChange = { raw ->
                                // Smart paste: "40.6782, 46.3606" auto-splits lat & lon
                                val parts = raw.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
                                if (parts.size >= 2 && raw.contains(",")) {
                                    decLatText = parts[0]
                                    decLonText = parts[1]
                                } else {
                                    decLatText = raw
                                }
                                errorText = null
                            },
                            label = { Text(strings.latLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("coord_decimal_lat_input"),
                        )
                        OutlinedTextField(
                            value = decLonText,
                            onValueChange = { decLonText = it; errorText = null },
                            label = { Text(strings.lonLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("coord_decimal_lon_input"),
                        )
                    }
                }

                if (errorText != null) {
                    DangerBannerBox(text = errorText!!)
                }

                val preview = resolveCurrentLatLon()
                if (preview != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = extra.pale,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val u = GeoUtils.latLonToUtm(preview.lat, preview.lon)
                        Text(
                            text = "GPS: ${formatNumber(preview.lat, 5)}, ${formatNumber(preview.lon, 5)}  •  UTM ${GeoUtils.formatUtmZone(preview.lat, u.zone)} ${u.easting.roundToInt()}E ${u.northing.roundToInt()}N",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.muted,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("coords_cancel_btn"),
                    ) {
                        Text(strings.cancel)
                    }
                    TextButton(
                        onClick = {
                            val ll = resolveCurrentLatLon()
                            if (ll == null) {
                                errorText = strings.coordInvalidError
                            } else {
                                onAddCoordinate(ll, false)
                            }
                        },
                        modifier = Modifier.testTag("coords_add_next_btn"),
                    ) {
                        Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(strings.coordAddCornerBtn, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            val ll = resolveCurrentLatLon()
                            if (ll == null) {
                                errorText = strings.coordInvalidError
                            } else {
                                onAddCoordinate(ll, true)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.testTag("coords_done_btn"),
                    ) {
                        Text(strings.coordDoneBtn, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoordRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun KmzViewerDialog(
    document: KmzDocument?,
    isSatellite: Boolean,
    onToggleLayer: () -> Unit,
    onLoadToMap: (KmzPlacemark) -> Unit,
    onSavePlacemarkAsField: (KmzPlacemark) -> Unit,
    onShareKmz: (KmzDocument) -> Unit,
    onDismiss: () -> Unit,
) {
    if (document == null) return
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    var selectedIdx by remember(document) { mutableIntStateOf(0) }
    val selected = document.placemarks.getOrNull(selectedIdx)
    val initialFit = remember(document, selectedIdx) {
        val pts = selected?.points?.takeIf { it.isNotEmpty() }
            ?: document.placemarks.flatMap { it.points }
        GeoUtils.computeBoundsCenterAndZoom(pts)
    }
    var previewCenter by remember(document, selectedIdx) { mutableStateOf(initialFit.first) }
    var previewZoom by remember(document, selectedIdx) { mutableIntStateOf(initialFit.second) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(strings.kmzViewerTitle, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${document.documentName} (${document.placemarks.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("kmz_viewer_dialog"),
            ) {
                if (document.placemarks.size > 1) {
                    TabPillsRow(
                        tabs = document.placemarks.map { it.name },
                        selectedIndex = selectedIdx.coerceIn(0, document.placemarks.lastIndex),
                        onSelect = { selectedIdx = it },
                    )
                }

                if (selected != null) {
                    val others = document.placemarks
                        .filterIndexed { idx, _ -> idx != selectedIdx }
                        .map { it.points }
                    FieldMapCanvas(
                        center = previewCenter,
                        zoom = previewZoom,
                        isSatellite = isSatellite,
                        points = selected.points,
                        gpsLocation = null,
                        onCenterChanged = { previewCenter = it },
                        onZoomChanged = { previewZoom = it },
                        onAddPoint = {},
                        onMovePoint = { _, _ -> },
                        onToggleLayer = onToggleLayer,
                        onLocateMe = {},
                        isReadOnly = true,
                        extraPolygons = others,
                        mapHeightDp = 240,
                    )

                    val metrics = GeoUtils.calculateMeasurements(selected.points)
                    val firstPt = selected.points.firstOrNull()
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = extra.pale,
                        border = BorderStroke(1.dp, extra.line),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Text(
                                text = selected.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (selected.description.isNotBlank()) {
                                Text(
                                    text = selected.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = extra.muted,
                                )
                            }
                            if (selected.points.size >= 3) {
                                Text(
                                    text = "${formatNumber(metrics.areaHa, 4)} ha • ${formatNumber(metrics.areaSot, 1)} sot • ${metrics.areaM2.roundToInt()} m²",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "${metrics.widthM.roundToInt()}×${metrics.lengthM.roundToInt()} ${strings.mUnit} • P: ${metrics.perimeterM.roundToInt()} ${strings.mUnit}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.muted,
                                )
                            }
                            if (firstPt != null) {
                                val u = GeoUtils.latLonToUtm(firstPt.lat, firstPt.lon)
                                Text(
                                    text = "${selected.points.size} pts • ${formatNumber(firstPt.lat, 5)}, ${formatNumber(firstPt.lon, 5)} (UTM ${GeoUtils.formatUtmZone(firstPt.lat, u.zone)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.muted,
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    ) {
                        OutlinedButton(
                            onClick = { onLoadToMap(selected) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("kmz_load_to_map_btn"),
                        ) {
                            Text(strings.kmzLoadToEditorBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = { onSavePlacemarkAsField(selected) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("kmz_save_field_btn"),
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(strings.kmzSaveFieldBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onShareKmz(document) },
                modifier = Modifier.testTag("kmz_viewer_share_btn"),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text(strings.btnKmzShare)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        },
    )
}
