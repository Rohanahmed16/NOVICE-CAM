package com.example.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    val coroutineScope = rememberCoroutineScope()
    
    // ViewModel states
    val iso by viewModel.iso.collectAsState()
    val shutterIndex by viewModel.shutterSpeedIndex.collectAsState()
    val focusDistance by viewModel.focusDistance.collectAsState()
    val wbMode by viewModel.whiteBalanceMode.collectAsState()
    val exposureComp by viewModel.exposureComp.collectAsState()
    val cameraMode by viewModel.cameraMode.collectAsState()
    val aiEnabled by viewModel.aiEnhancementEnabled.collectAsState()
    val aiStrength by viewModel.aiEnhancementStrength.collectAsState()
    val detectedScene by viewModel.detectedScene.collectAsState()
    val sceneAnalysis by viewModel.sceneAnalysis.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val filterIntensity by viewModel.filterIntensity.collectAsState()
    val gridType by viewModel.gridType.collectAsState()
    val showHistogram by viewModel.showHistogram.collectAsState()
    val showLevel by viewModel.showLevel.collectAsState()
    val levelRoll by viewModel.levelRoll.collectAsState()
    val flashMode by viewModel.flashMode.collectAsState()
    val captureProgress by viewModel.captureProgress.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val isSimulatorActive by viewModel.isSimulatorActive.collectAsState()
    val liveFrame by viewModel.liveFrame.collectAsState()
    val histogramData by viewModel.histogramData.collectAsState()
    val gallery by viewModel.gallery.collectAsState()
    val selectedPhoto by viewModel.selectedPhoto.collectAsState()

    // Screen UI states
    var activeControlTab by remember { mutableStateOf("ISO") } // "ISO", "SHUTTER", "FOCUS", "WB", "EV"
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showFiltersDrawer by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CharcoalBlack
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CharcoalBlack)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. TOP STATUS BAR (Quick controls & Metadata)
                CameraTopBar(
                    isSimulatorActive = isSimulatorActive,
                    flashMode = flashMode,
                    aiEnabled = aiEnabled,
                    detectedScene = detectedScene,
                    cameraMode = cameraMode,
                    onToggleSimulator = { viewModel.toggleSimulator(!isSimulatorActive) },
                    onToggleFlash = {
                        val next = when (flashMode) {
                            "Off" -> "On"
                            "On" -> "Auto"
                            else -> "Off"
                        }
                        viewModel.setFlashMode(next)
                    },
                    onToggleAi = { viewModel.setAiEnhancementEnabled(!aiEnabled) },
                    onOpenSettings = { showSettingsDialog = true }
                )

                // 2. MAIN VIEWPORT (Viewport + Overlays)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black)
                        .testTag("camera_viewfinder_container")
                ) {
                    if (liveFrame != null) {
                        Image(
                            bitmap = liveFrame!!.asImageBitmap(),
                            contentDescription = "Camera Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ChampagneGold)
                        }
                    }

                    // Viewfinder Overlays
                    if (gridType != "None") {
                        GridLinesOverlay(gridType = gridType)
                    }

                    if (showLevel) {
                        HorizonLevelOverlay(rollAngle = levelRoll)
                    }

                    if (showHistogram) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            HistogramOverlay(histogram = histogramData)
                        }
                    }

                    // Real-Time Intelligent Scene-Grader Telemetry Card
                    if (aiEnabled && sceneAnalysis != null) {
                        SceneGradingTelemetryOverlay(analysis = sceneAnalysis!!)
                    }

                    // Indicator Badges
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // AI Scene tag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (aiEnabled) AmberIndicator else Color.Gray)
                                )
                                Text(
                                    text = if (aiEnabled) "AI: $detectedScene" else "Pro Raw Mode",
                                    color = SoftCream,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Mode tag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ChampagneGold.copy(alpha = 0.85f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cameraMode.uppercase(),
                                color = CharcoalBlack,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Live Shooting Parameters readout inside viewport
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("ISO $iso", color = LightChampagne, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(viewModel.shutterSpeedList[shutterIndex], color = LightChampagne, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(if (focusDistance == 0f) "AF-S" else "MF ${String.format("%.1fm", focusDistance * 10f)}", color = LightChampagne, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("EV ${if (exposureComp >= 0) "+" else ""}${String.format("%.1f", exposureComp)}", color = LightChampagne, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    // Capture / Processing Loader Overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isCapturing,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(64.dp),
                                    color = ChampagneGold,
                                    strokeWidth = 5.dp
                                )
                                Text(
                                    text = "PROCESSING IMAGE",
                                    color = ChampagneGold,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                )
                                Text(
                                    text = captureProgress ?: "Optimizing layout parameters...",
                                    color = SoftCream.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    }
                }

                // 3. TACTILE CONTROL DECK (Manual Dials, Mode Dial, Capture controls)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CharcoalBlack)
                        .padding(top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // TAB SELECTOR FOR MANUAL PARAMETERS
                    ManualTabSelector(
                        activeTab = activeControlTab,
                        onTabSelected = { activeControlTab = it },
                        isoValue = iso,
                        shutterValue = viewModel.shutterSpeedList[shutterIndex],
                        focusValue = if (focusDistance == 0f) "AUTO" else "MANUAL",
                        wbValue = wbMode,
                        evValue = "${if (exposureComp >= 0) "+" else ""}${String.format("%.1f", exposureComp)}"
                    )

                    // DYNAMIC DIAL FOR SELECTED PARAMETER
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(DarkWarmStone)
                            .border(1.dp, LightWarmStone.copy(alpha = 0.3f))
                    ) {
                        when (activeControlTab) {
                            "ISO" -> {
                                val isoOptions = listOf(100, 200, 400, 800, 1600, 3200)
                                TactileWheelDial(
                                    options = isoOptions.map { it.toString() },
                                    selectedIndex = isoOptions.indexOf(iso),
                                    onIndexSelected = { viewModel.setIso(isoOptions[it]) }
                                )
                            }
                            "SHUTTER" -> {
                                TactileWheelDial(
                                    options = viewModel.shutterSpeedList,
                                    selectedIndex = shutterIndex,
                                    onIndexSelected = { viewModel.setShutterSpeedIndex(it) }
                                )
                            }
                            "FOCUS" -> {
                                // Slider for fine focus control
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text("AUTO", color = if (focusDistance == 0f) ChampagneGold else SoftCream, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Slider(
                                        value = focusDistance,
                                        onValueChange = { viewModel.setFocusDistance(it) },
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = ChampagneGold,
                                            activeTrackColor = ChampagneGold,
                                            inactiveTrackColor = LightWarmStone
                                        )
                                    )
                                    Text("MACRO", color = if (focusDistance > 0.8f) ChampagneGold else SoftCream, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            "WB" -> {
                                TactileWheelDial(
                                    options = viewModel.whiteBalanceList,
                                    selectedIndex = viewModel.whiteBalanceList.indexOf(wbMode),
                                    onIndexSelected = { viewModel.setWhiteBalanceMode(viewModel.whiteBalanceList[it]) }
                                )
                            }
                            "EV" -> {
                                val evOptions = listOf(-3.0f, -2.5f, -2.0f, -1.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f)
                                TactileWheelDial(
                                    options = evOptions.map { "${if (it >= 0) "+" else ""}${String.format("%.1f", it)}" },
                                    selectedIndex = evOptions.indexOf(exposureComp),
                                    onIndexSelected = { viewModel.setExposureComp(evOptions[it]) }
                                )
                            }
                        }
                    }

                    // ADJUSTABLE FILTERS BAR
                    AnimatedVisibility(visible = showFiltersDrawer) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkWarmStone)
                                .padding(vertical = 8.dp)
                        ) {
                            // Filter intensity slider
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("INTENSITY", color = LightChampagne, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = filterIntensity,
                                    onValueChange = { viewModel.setFilterIntensity(it) },
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = ChampagneGold,
                                        activeTrackColor = ChampagneGold,
                                        inactiveTrackColor = LightWarmStone
                                    )
                                )
                                Text("${(filterIntensity * 100).toInt()}%", color = ChampagneGold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                            
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(viewModel.filterList) { filterName ->
                                    val isSelected = filterName == activeFilter
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) ChampagneGold else MediumWarmStone)
                                            .border(1.dp, if (isSelected) ChampagneGold else LightWarmStone, RoundedCornerShape(12.dp))
                                            .clickable { viewModel.setActiveFilter(filterName) }
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = filterName.uppercase(),
                                            color = if (isSelected) CharcoalBlack else SoftCream,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // CAMERA MODE DIAL
                    CameraModeDial(
                        activeMode = cameraMode,
                        onModeSelected = { viewModel.setCameraMode(it) }
                    )

                    // PRIMARY SHUTTER & CAPTURE CONTROLS
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Action: Gallery / Last image review
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MediumWarmStone)
                                .border(1.5.dp, LightWarmStone, RoundedCornerShape(14.dp))
                                .clickable {
                                    if (gallery.isNotEmpty()) {
                                        viewModel.selectPhoto(gallery.first())
                                    }
                                }
                                .testTag("gallery_shortcut_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (gallery.isNotEmpty()) {
                                AsyncImage(
                                    model = gallery.first().filePath,
                                    contentDescription = "Last Shot Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Gallery Empty",
                                    tint = SoftCream.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // Center Action: Large Brushed Shutter Button
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.2f))
                                .border(4.dp, ChampagneGold, CircleShape)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            if (!isCapturing) {
                                                viewModel.capturePhoto()
                                            }
                                        }
                                    )
                                }
                                .testTag("shutter_capture_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(SoftCream, SoftCream.copy(alpha = 0.9f), LightChampagne)
                                        )
                                    )
                            )
                        }

                        // Right Action: Quick filter toggle button
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (showFiltersDrawer) ChampagneGold else MediumWarmStone)
                                .clickable { showFiltersDrawer = !showFiltersDrawer }
                                .testTag("filter_drawer_toggle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Creative Filters",
                                tint = if (showFiltersDrawer) CharcoalBlack else SoftCream
                            )
                        }
                    }
                }
            }

            // 4. SETTINGS DIALOG overlay
            if (showSettingsDialog) {
                SettingsDialog(
                    aiStrength = aiStrength,
                    gridType = gridType,
                    showHistogram = showHistogram,
                    showLevel = showLevel,
                    onAiStrengthChanged = { viewModel.setAiEnhancementStrength(it) },
                    onGridTypeChanged = { viewModel.setGridType(it) },
                    onToggleHistogram = { viewModel.toggleHistogram(it) },
                    onToggleLevel = { viewModel.toggleLevel(it) },
                    onDismiss = { showSettingsDialog = false }
                )
            }

            // 5. FULL SCREEN PHOTO GALLERY REVIEW overlay
            if (selectedPhoto != null) {
                PhotoReviewOverlay(
                    photo = selectedPhoto!!,
                    galleryList = gallery,
                    onClose = { viewModel.selectPhoto(null) },
                    onDelete = { viewModel.deletePhoto(it) }
                )
            }
        }
    }
}

/**
 * Top Control Bar
 */
@Composable
fun CameraTopBar(
    isSimulatorActive: Boolean,
    flashMode: String,
    aiEnabled: Boolean,
    detectedScene: String,
    cameraMode: String,
    onToggleSimulator: () -> Unit,
    onToggleFlash: () -> Unit,
    onToggleAi: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(CharcoalBlack)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Quick Simulator Fallback Switch
        IconButton(onClick = onToggleSimulator) {
            Icon(
                imageVector = if (isSimulatorActive) Icons.Rounded.Dashboard else Icons.Rounded.CameraAlt,
                contentDescription = "Toggle Viewfinder Mode",
                tint = if (isSimulatorActive) ChampagneGold else SoftCream
            )
        }

        // Quick flash toggle
        IconButton(onClick = onToggleFlash) {
            val icon = when (flashMode) {
                "On" -> Icons.Default.FlashOn
                "Auto" -> Icons.Default.FlashAuto
                else -> Icons.Default.FlashOff
            }
            Icon(
                imageVector = icon,
                contentDescription = "Flash mode",
                tint = if (flashMode != "Off") ChampagneGold else SoftCream
            )
        }

        // App Logo
        Text(
            text = "APERTURE AI",
            color = ChampagneGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.SansSerif
        )

        // Smart AI enhancement override status
        IconButton(onClick = onToggleAi) {
            Icon(
                imageVector = if (aiEnabled) Icons.Default.AutoAwesome else Icons.Default.AutoMode,
                contentDescription = "AI Enhancement Toggle",
                tint = if (aiEnabled) AmberIndicator else SoftCream.copy(alpha = 0.5f)
            )
        }

        // Settings Button
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = SoftCream
            )
        }
    }
}

/**
 * Tactical Grid lines Overlays
 */
@Composable
fun BoxScope.GridLinesOverlay(gridType: String) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val strokeWidth = 1.dp.toPx()
        val color = Color.White.copy(alpha = 0.25f)

        if (gridType == "Rule of Thirds") {
            // Draw vertical thirds
            drawLine(color, Offset(width / 3, 0f), Offset(width / 3, height), strokeWidth)
            drawLine(color, Offset(2 * width / 3, 0f), Offset(2 * width / 3, height), strokeWidth)

            // Draw horizontal thirds
            drawLine(color, Offset(0f, height / 3), Offset(width, height / 3), strokeWidth)
            drawLine(color, Offset(0f, 2 * height / 3), Offset(width, 2 * height / 3), strokeWidth)
        } else if (gridType == "Golden Ratio") {
            // Golden ratio constant ~0.382 and ~0.618
            val p1 = 0.382f
            val p2 = 0.618f

            drawLine(color, Offset(width * p1, 0f), Offset(width * p1, height), strokeWidth)
            drawLine(color, Offset(width * p2, 0f), Offset(width * p2, height), strokeWidth)

            drawLine(color, Offset(0f, height * p1), Offset(width, height * p1), strokeWidth)
            drawLine(color, Offset(0f, height * p2), Offset(width, height * p2), strokeWidth)
        }
    }
}

/**
 * Real-time Physical Horizon Level Line
 */
@Composable
fun BoxScope.HorizonLevelOverlay(rollAngle: Float) {
    val isPerfectLevel = rollAngle.absoluteValue < 1.0f
    val animatedColor by animateColorAsState(
        targetValue = if (isPerfectLevel) ChampagneGold else CoolBlueLevel.copy(alpha = 0.8f),
        animationSpec = tween(150)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Draw the leveling wings
        Canvas(
            modifier = Modifier
                .size(200.dp, 40.dp)
                .rotate(-rollAngle)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val wingLength = 40.dp.toPx()
            val strokeWidth = 2.dp.toPx()

            // Left wing line
            drawLine(
                color = animatedColor,
                start = Offset(center.x - wingLength - 10.dp.toPx(), center.y),
                end = Offset(center.x - 10.dp.toPx(), center.y),
                strokeWidth = strokeWidth
            )

            // Right wing line
            drawLine(
                color = animatedColor,
                start = Offset(center.x + 10.dp.toPx(), center.y),
                end = Offset(center.x + wingLength + 10.dp.toPx(), center.y),
                strokeWidth = strokeWidth
            )

            // Center static level notch reference
            drawCircle(
                color = animatedColor,
                radius = 4.dp.toPx(),
                center = center
            )
        }
    }
}

/**
 * Real-time Luminance Histogram overlay
 */
@Composable
fun HistogramOverlay(histogram: IntArray) {
    Canvas(
        modifier = Modifier
            .size(110.dp, 60.dp)
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(4.dp)
    ) {
        val maxVal = histogram.maxOrNull() ?: 1
        val barCount = histogram.size
        val width = size.width
        val height = size.height
        val barWidth = width / barCount

        // Standard smooth ambient line path, or simply drawing tightly nested vertical bars
        for (i in 0 until barCount) {
            val barHeight = (histogram[i].toFloat() / maxVal) * height
            drawLine(
                color = ChampagneGold.copy(alpha = 0.7f),
                start = Offset(i * barWidth, height),
                end = Offset(i * barWidth, height - barHeight),
                strokeWidth = barWidth + 0.5f
            )
        }
    }
}

/**
 * Real-Time Intelligent Scene Grading Telemetry Card
 */
@Composable
fun BoxScope.SceneGradingTelemetryOverlay(analysis: ImageProcessor.SceneAnalysis) {
    Card(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(top = 80.dp, end = 12.dp)
            .width(140.dp)
            .testTag("scene_grading_telemetry"),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, ChampagneGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "SCENE CO-PROCESSOR",
                color = ChampagneGold,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                fontFamily = FontFamily.Monospace
            )
            
            // Average Luminance
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("AMBIENT", color = SoftCream.copy(alpha = 0.6f), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                    Text("${(analysis.averageLuma * 100).toInt()}%", color = SoftCream, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                LinearProgressIndicator(
                    progress = { analysis.averageLuma },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = ChampagneGold,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }

            // Highlights and Shadow clipping level
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("CLIP HI/LO", color = SoftCream.copy(alpha = 0.6f), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                    Text("${(analysis.highlightClipping * 100).toInt()}%/${(analysis.shadowClipping * 100).toInt()}%", color = SoftCream, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(analysis.highlightClipping)
                            .height(2.dp)
                            .background(AmberIndicator)
                    )
                }
            }

            // Smart dynamic correction suggestions
            val dynamicAction = when {
                analysis.suggestedExposureBias > 0.15f -> "SHADOW LIFT +${String.format("%.1f", analysis.suggestedExposureBias)}EV"
                analysis.suggestedExposureBias < -0.15f -> "RECOVER HIGHLIGHTS ${String.format("%.1f", analysis.suggestedExposureBias)}EV"
                else -> "LIGHTING OPTIMAL"
            }
            Text(
                text = "⚡ $dynamicAction",
                color = AmberIndicator,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            // Detected Color Balance R, G, B channels
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("COLOR GAINS", color = SoftCream.copy(alpha = 0.6f), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // R bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(Color.Red.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(minOf(1f, analysis.rRatio / 2f))
                                .height(4.dp)
                                .background(Color.Red)
                        )
                    }
                    // G bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(Color.Green.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(minOf(1f, analysis.gRatio / 2f))
                                .height(4.dp)
                                .background(Color.Green)
                        )
                    }
                    // B bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(Color.Blue.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(minOf(1f, analysis.bRatio / 2f))
                                .height(4.dp)
                                .background(Color.Blue)
                        )
                    }
                }
            }

            // Grading profile
            val gradingProfile = when {
                analysis.estimatedSceneDescription.contains("Sunset") -> "Kodak Portra 400"
                analysis.estimatedSceneDescription.contains("Night") -> "Cine Neon LUT"
                analysis.estimatedSceneDescription.contains("Forest") -> "Fujifilm Velvia"
                else -> "Aperture Standard"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("LUT", color = SoftCream.copy(alpha = 0.6f), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                Text(gradingProfile.uppercase(), color = ChampagneGold, fontSize = 7.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/**
 * Tab Row selector for fine parameters adjusting
 */
@Composable
fun ManualTabSelector(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    isoValue: Int,
    shutterValue: String,
    focusValue: String,
    wbValue: String,
    evValue: String
) {
    val tabs = listOf(
        "ISO" to isoValue.toString(),
        "SHUTTER" to shutterValue,
        "FOCUS" to focusValue,
        "WB" to wbValue,
        "EV" to evValue
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEach { (tabName, displayVal) ->
            val isSelected = activeTab == tabName
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MediumWarmStone else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) ChampagneGold.copy(alpha = 0.4f) else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onTabSelected(tabName) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tabName,
                    color = if (isSelected) ChampagneGold else SoftCream.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayVal,
                    color = if (isSelected) SoftCream else SoftCream.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Tactile Horizontal Wheel Dial for manual controls
 */
@Composable
fun TactileWheelDial(
    options: List<String>,
    selectedIndex: Int,
    onIndexSelected: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Smooth scroll to selection when index programmatically updates
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in options.indices) {
            listState.animateScrollToItem(maxOf(0, selectedIndex - 2))
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Active Center Line Indicator
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(ChampagneGold.copy(alpha = 0.85f))
        )

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 140.dp), // Pushes endpoints to align in middle
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            itemsIndexed(options) { idx, option ->
                val isSelected = idx == selectedIndex
                Column(
                    modifier = Modifier
                        .clickable { onIndexSelected(idx) }
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Wheel hashmark ticks
                    Box(
                        modifier = Modifier
                            .width(1.5.dp)
                            .height(if (isSelected) 16.dp else 8.dp)
                            .background(if (isSelected) ChampagneGold else SoftCream.copy(alpha = 0.3f))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = option,
                        color = if (isSelected) ChampagneGold else SoftCream.copy(alpha = 0.5f),
                        fontSize = if (isSelected) 13.sp else 11.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Mode selector carousel (resembling professional camera mode dials)
 */
@Composable
fun CameraModeDial(
    activeMode: String,
    onModeSelected: (String) -> Unit
) {
    val modes = listOf("PHOTO", "PRO MANUAL", "SMART AI", "NIGHT", "PORTRAIT", "LANDSCAPE", "MOTION")
    val displayModes = listOf("PHOTO", "PRO", "SMART AI", "NIGHT", "PORTRAIT", "LANDSCAPE", "MOTION")
    
    val listState = rememberLazyListState()
    
    val currentIndex = modes.indexOf(activeMode)
    LaunchedEffect(currentIndex) {
        if (currentIndex != -1) {
            listState.animateScrollToItem(maxOf(0, currentIndex - 2))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(CharcoalBlack),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 120.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(modes) { index, mode ->
                val isSelected = mode == activeMode
                Column(
                    modifier = Modifier
                        .clickable { onModeSelected(mode) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = displayModes[index],
                        color = if (isSelected) ChampagneGold else SoftCream.copy(alpha = 0.4f),
                        fontSize = if (isSelected) 12.sp else 10.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(ChampagneGold)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom Settings Dialog
 */
@Composable
fun SettingsDialog(
    aiStrength: Float,
    gridType: String,
    showHistogram: Boolean,
    showLevel: Boolean,
    onAiStrengthChanged: (Float) -> Unit,
    onGridTypeChanged: (String) -> Unit,
    onToggleHistogram: (Boolean) -> Unit,
    onToggleLevel: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(DarkWarmStone)
                .border(1.dp, LightWarmStone, RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "CAMERA PARAMETERS",
                    color = ChampagneGold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )

                Divider(color = LightWarmStone.copy(alpha = 0.4f))

                // AI Processing Strength Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AI Enhancement Power", color = SoftCream, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("${(aiStrength * 100).toInt()}%", color = ChampagneGold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = aiStrength,
                        onValueChange = onAiStrengthChanged,
                        colors = SliderDefaults.colors(
                            thumbColor = ChampagneGold,
                            activeTrackColor = ChampagneGold,
                            inactiveTrackColor = LightWarmStone
                        )
                    )
                }

                // Grid lines Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Composition Overlays", color = SoftCream, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    
                    val grids = listOf("None", "Rule of Thirds", "Golden Ratio")
                    var expanded by remember { mutableStateOf(false) }
                    
                    Box {
                        Button(
                            onClick = { expanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MediumWarmStone)
                        ) {
                            Text(gridType, color = SoftCream, fontSize = 11.sp)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(MediumWarmStone)
                        ) {
                            grids.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type, color = SoftCream) },
                                    onClick = {
                                        onGridTypeChanged(type)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Histogram Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Luminance Histogram", color = SoftCream, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = showHistogram,
                        onCheckedChange = onToggleHistogram,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CharcoalBlack,
                            checkedTrackColor = ChampagneGold,
                            uncheckedThumbColor = LightWarmStone,
                            uncheckedTrackColor = MediumWarmStone
                        )
                    )
                }

                // Level Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dynamic Horizon Level", color = SoftCream, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = showLevel,
                        onCheckedChange = onToggleLevel,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CharcoalBlack,
                            checkedTrackColor = ChampagneGold,
                            uncheckedThumbColor = LightWarmStone,
                            uncheckedTrackColor = MediumWarmStone
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold)
                ) {
                    Text("APPLY AND SAVE", color = CharcoalBlack, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/**
 * Full screen photo gallery overlay
 */
@Composable
fun PhotoReviewOverlay(
    photo: CapturedPhoto,
    galleryList: List<CapturedPhoto>,
    onClose: () -> Unit,
    onDelete: (CapturedPhoto) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    // We can allow users to change filters post-capture! This meets "optional filters applied before or after capturing"
    var selectedFilter by remember { mutableStateOf(photo.filterName) }
    var filterPower by remember { mutableStateOf(photo.filterIntensity) }
    var imageBitmapState by remember { mutableStateOf<Bitmap?>(null) }

    // Load bitmap once
    LaunchedEffect(photo.filePath, selectedFilter, filterPower) {
        val original = BitmapFactory.decodeFile(photo.filePath)
        if (original != null) {
            val processed = ImageProcessor.processImage(
                source = original,
                iso = photo.iso,
                shutterSpeed = 0.008f, // mock representation
                focusDistance = photo.focusDistance,
                whiteBalanceWarmth = 0f,
                exposureComp = photo.exposureComp,
                aiEnhancementEnabled = true,
                aiEnhancementStrength = 0.8f,
                aiSceneMode = photo.aiMode,
                activeFilter = selectedFilter,
                filterIntensity = filterPower
            )
            imageBitmapState = processed
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBlack)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = SoftCream)
                }

                Text(
                    text = "PHOTOGRAPHY GALLERY",
                    color = ChampagneGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Photo", tint = RedRecording)
                }
            }

            // Big Photo Display Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (imageBitmapState != null) {
                    Image(
                        bitmap = imageBitmapState!!.asImageBitmap(),
                        contentDescription = "Full Review",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    CircularProgressIndicator(color = ChampagneGold)
                }
            }

            // Post-Capture Edit Controls Drawer (Allows applying filters AFTER CAPTURE)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkWarmStone)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "POST-CAPTURE FILTERS",
                    color = ChampagneGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                // Slider for post filter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Filter Strength", color = SoftCream, fontSize = 11.sp)
                    Slider(
                        value = filterPower,
                        onValueChange = { filterPower = it },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = ChampagneGold,
                            activeTrackColor = ChampagneGold,
                            inactiveTrackColor = LightWarmStone
                        )
                    )
                    Text("${(filterPower * 100).toInt()}%", color = ChampagneGold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }

                // Filter items selection list
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val filters = listOf("None", "Cinema", "Nostalgia", "Monochromatic", "Warm Sunrise", "Cool Twilight")
                    items(filters) { filterName ->
                        val isSelected = filterName == selectedFilter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ChampagneGold else MediumWarmStone)
                                .clickable { selectedFilter = filterName }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = filterName.uppercase(),
                                color = if (isSelected) CharcoalBlack else SoftCream,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Bottom metadata info card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkWarmStone),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "METADATA (EXIF DATA)",
                        color = ChampagneGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("ISO: ${photo.iso}", color = SoftCream.copy(alpha = 0.8f), fontSize = 12.sp)
                            Text("Shutter: ${photo.shutterSpeed}", color = SoftCream.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                        Column {
                            Text("AI Mode: ${photo.aiMode}", color = SoftCream.copy(alpha = 0.8f), fontSize = 12.sp)
                            Text("Focus: ${if (photo.focusDistance == 0f) "Auto" else "Manual"}", color = SoftCream.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Delete confirmation dialog
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Photo", color = SoftCream) },
                text = { Text("Are you sure you want to permanently delete this photo from your private gallery?", color = SoftCream.copy(alpha = 0.8f)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDelete(photo)
                            showDeleteConfirm = false
                        }
                    ) {
                        Text("DELETE", color = RedRecording, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("CANCEL", color = SoftCream)
                    }
                },
                containerColor = DarkWarmStone
            )
        }
    }
}
