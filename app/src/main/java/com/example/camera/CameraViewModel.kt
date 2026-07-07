package com.example.camera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

data class CapturedPhoto(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val iso: Int,
    val shutterSpeed: String,
    val focusDistance: Float,
    val whiteBalance: String,
    val exposureComp: Float,
    val aiMode: String,
    val filterName: String,
    val filterIntensity: Float,
    val width: Int,
    val height: Int
)

class CameraViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val context = application.applicationContext

    // Manual controls state
    private val _iso = MutableStateFlow(400)
    val iso: StateFlow<Int> = _iso.asStateFlow()

    private val _shutterSpeedIndex = MutableStateFlow(4) // index for 1/125s
    val shutterSpeedIndex: StateFlow<Int> = _shutterSpeedIndex.asStateFlow()
    val shutterSpeedList = listOf("1/1000s", "1/500s", "1/250s", "1/125s", "1/60s", "1/30s", "1/15s", "1/8s", "1/4s", "1/2s", "1s")
    val shutterSpeedValues = listOf(0.001f, 0.002f, 0.004f, 0.008f, 0.016f, 0.033f, 0.066f, 0.125f, 0.25f, 0.5f, 1.0f)

    private val _focusDistance = MutableStateFlow(0f) // 0.0 (sharp auto) to 1.0 (blurred manual)
    val focusDistance: StateFlow<Float> = _focusDistance.asStateFlow()

    private val _whiteBalanceMode = MutableStateFlow("Auto")
    val whiteBalanceMode: StateFlow<String> = _whiteBalanceMode.asStateFlow()
    val whiteBalanceList = listOf("Auto", "Sunny", "Cloudy", "Incandescent", "Fluorescent")

    private val _exposureComp = MutableStateFlow(0.0f) // -3.0f to +3.0f
    val exposureComp: StateFlow<Float> = _exposureComp.asStateFlow()

    // Mode state
    private val _cameraMode = MutableStateFlow("Photo") // "Photo" (Auto), "Pro", "Smart AI", "Night", "Portrait", "Landscape", "Motion"
    val cameraMode: StateFlow<String> = _cameraMode.asStateFlow()

    // AI & Enhancement settings
    private val _aiEnhancementEnabled = MutableStateFlow(true)
    val aiEnhancementEnabled: StateFlow<Boolean> = _aiEnhancementEnabled.asStateFlow()

    private val _aiEnhancementStrength = MutableStateFlow(0.8f)
    val aiEnhancementStrength: StateFlow<Float> = _aiEnhancementStrength.asStateFlow()

    private val _detectedScene = MutableStateFlow("Daylight")
    val detectedScene: StateFlow<String> = _detectedScene.asStateFlow()

    private val _sceneAnalysis = MutableStateFlow<ImageProcessor.SceneAnalysis?>(null)
    val sceneAnalysis: StateFlow<ImageProcessor.SceneAnalysis?> = _sceneAnalysis.asStateFlow()

    // Filters state
    private val _activeFilter = MutableStateFlow("None")
    val activeFilter: StateFlow<String> = _activeFilter.asStateFlow()

    private val _filterIntensity = MutableStateFlow(0.8f)
    val filterIntensity: StateFlow<Float> = _filterIntensity.asStateFlow()
    val filterList = listOf("None", "Cinema", "Nostalgia", "Monochromatic", "Warm Sunrise", "Cool Twilight")

    // Overlay & Tool states
    private val _gridType = MutableStateFlow("Rule of Thirds") // "None", "Rule of Thirds", "Golden Ratio"
    val gridType: StateFlow<String> = _gridType.asStateFlow()

    private val _showHistogram = MutableStateFlow(true)
    val showHistogram: StateFlow<Boolean> = _showHistogram.asStateFlow()

    private val _showLevel = MutableStateFlow(true)
    val showLevel: StateFlow<Boolean> = _showLevel.asStateFlow()

    // Level indicator orientation angles
    private val _levelRoll = MutableStateFlow(0f)
    val levelRoll: StateFlow<Float> = _levelRoll.asStateFlow()

    // Flash Mode
    private val _flashMode = MutableStateFlow("Off") // "Off", "On", "Auto"
    val flashMode: StateFlow<String> = _flashMode.asStateFlow()

    // Image capture simulation progress
    private val _captureProgress = MutableStateFlow<String?>(null)
    val captureProgress: StateFlow<String?> = _captureProgress.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    // Feed selection: whether the simulation feed is active or real CameraX
    private val _isSimulatorActive = MutableStateFlow(true)
    val isSimulatorActive: StateFlow<Boolean> = _isSimulatorActive.asStateFlow()

    // Live viewport bitmap (used in Simulator mode or as fallback)
    private val _liveFrame = MutableStateFlow<Bitmap?>(null)
    val liveFrame: StateFlow<Bitmap?> = _liveFrame.asStateFlow()

    private val _histogramData = MutableStateFlow(IntArray(256))
    val histogramData: StateFlow<IntArray> = _histogramData.asStateFlow()

    // Captured photos gallery
    private val _gallery = MutableStateFlow<List<CapturedPhoto>>(emptyList())
    val gallery: StateFlow<List<CapturedPhoto>> = _gallery.asStateFlow()

    private val _selectedPhoto = MutableStateFlow<CapturedPhoto?>(null)
    val selectedPhoto: StateFlow<CapturedPhoto?> = _selectedPhoto.asStateFlow()

    // Sensor Manager for physical level indicator
    private var sensorManager: SensorManager? = null
    private var gravitySensor: Sensor? = null
    private var accelerometer: Sensor? = null

    // Background processing job
    private var processingJob: Job? = null
    
    // Cached original bitmaps for simulator modes to avoid loading from disk on every frame
    private var cachedAutoBitmap: Bitmap? = null
    private var cachedPortraitBitmap: Bitmap? = null
    private var cachedNightBitmap: Bitmap? = null

    init {
        loadCachedBitmaps()
        startProcessingLoop()
        setupSensors()
        loadGallery()
    }

    private fun loadCachedBitmaps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                cachedAutoBitmap = loadBitmapFromAsset("scenery_sunset_mountain.jpg")
                cachedPortraitBitmap = loadBitmapFromAsset("portrait_person_smile.jpg")
                cachedNightBitmap = loadBitmapFromAsset("night_city_neon.jpg")
                triggerFrameProcessing()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadBitmapFromAsset(fileName: String): Bitmap? {
        return try {
            val assetManager = context.assets
            val inputStream: InputStream = assetManager.open(fileName)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Start sensor listening for horizon tilt
     */
    private fun setupSensors() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        val sensorToUse = gravitySensor ?: accelerometer
        sensorToUse?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun toggleSimulator(active: Boolean) {
        _isSimulatorActive.value = active
        triggerFrameProcessing()
    }

    fun setCameraMode(mode: String) {
        _cameraMode.value = mode
        
        // Auto adjust settings based on Mode
        when (mode) {
            "Photo" -> {
                _iso.value = 250
                _shutterSpeedIndex.value = 3 // 1/125s
                _focusDistance.value = 0f
                _whiteBalanceMode.value = "Auto"
                _exposureComp.value = 0f
                _detectedScene.value = "Daylight"
            }
            "Night" -> {
                _iso.value = 1600
                _shutterSpeedIndex.value = 8 // 1/4s
                _focusDistance.value = 0f
                _detectedScene.value = "Low Light"
            }
            "Portrait" -> {
                _iso.value = 200
                _shutterSpeedIndex.value = 4 // 1/60s
                _focusDistance.value = 0.05f
                _detectedScene.value = "Portrait Scene"
            }
            "Landscape" -> {
                _iso.value = 100
                _shutterSpeedIndex.value = 2 // 1/250s
                _focusDistance.value = 0f
                _detectedScene.value = "Landscape Vista"
            }
            "Motion" -> {
                _iso.value = 800
                _shutterSpeedIndex.value = 0 // 1/1000s
                _focusDistance.value = 0f
                _detectedScene.value = "Fast Action"
            }
            "Smart AI" -> {
                // Dynamically simulates smart scene selection
                _detectedScene.value = "AI Optimal Scene"
            }
        }
        triggerFrameProcessing()
    }

    fun setIso(value: Int) {
        _iso.value = value
        triggerFrameProcessing()
    }

    fun setShutterSpeedIndex(index: Int) {
        if (index in shutterSpeedList.indices) {
            _shutterSpeedIndex.value = index
            triggerFrameProcessing()
        }
    }

    fun setFocusDistance(value: Float) {
        _focusDistance.value = value
        triggerFrameProcessing()
    }

    fun setWhiteBalanceMode(mode: String) {
        if (mode in whiteBalanceList) {
            _whiteBalanceMode.value = mode
            triggerFrameProcessing()
        }
    }

    fun setExposureComp(value: Float) {
        _exposureComp.value = value
        triggerFrameProcessing()
    }

    fun setAiEnhancementEnabled(enabled: Boolean) {
        _aiEnhancementEnabled.value = enabled
        triggerFrameProcessing()
    }

    fun setAiEnhancementStrength(strength: Float) {
        _aiEnhancementStrength.value = strength
        triggerFrameProcessing()
    }

    fun setActiveFilter(filterName: String) {
        if (filterName in filterList) {
            _activeFilter.value = filterName
            triggerFrameProcessing()
        }
    }

    fun setFilterIntensity(intensity: Float) {
        _filterIntensity.value = intensity
        triggerFrameProcessing()
    }

    fun setGridType(type: String) {
        _gridType.value = type
    }

    fun toggleHistogram(visible: Boolean) {
        _showHistogram.value = visible
    }

    fun toggleLevel(visible: Boolean) {
        _showLevel.value = visible
    }

    fun setFlashMode(mode: String) {
        _flashMode.value = mode
    }

    fun selectPhoto(photo: CapturedPhoto?) {
        _selectedPhoto.value = photo
    }

    fun deletePhoto(photo: CapturedPhoto) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(photo.filePath)
            if (file.exists()) {
                file.delete()
            }
            _gallery.update { list -> list.filter { it.id != photo.id } }
            if (_selectedPhoto.value?.id == photo.id) {
                _selectedPhoto.value = null
            }
        }
    }

    /**
     * Trigger background frame processing for the simulator viewfinder.
     * Running processing asynchronously prevents any interface lag and guarantees fluid interaction.
     */
    private fun triggerFrameProcessing() {
        processingJob?.cancel()
        processingJob = viewModelScope.launch(Dispatchers.Default) {
            val original = when (_cameraMode.value) {
                "Portrait" -> cachedPortraitBitmap
                "Night" -> cachedNightBitmap
                else -> cachedAutoBitmap
            } ?: return@launch

            // Map WB Mode to numeric warmth values
            val warmth = when (_whiteBalanceMode.value) {
                "Sunny" -> 0.25f
                "Cloudy" -> 0.45f
                "Incandescent" -> -0.3f
                "Fluorescent" -> -0.15f
                else -> 0f
            }

            val analysis = ImageProcessor.analyzeScene(original)

            val processed = ImageProcessor.processImage(
                source = original,
                iso = _iso.value,
                shutterSpeed = shutterSpeedValues[_shutterSpeedIndex.value],
                focusDistance = _focusDistance.value,
                whiteBalanceWarmth = warmth,
                exposureComp = _exposureComp.value,
                aiEnhancementEnabled = _aiEnhancementEnabled.value,
                aiEnhancementStrength = _aiEnhancementStrength.value,
                aiSceneMode = when (_cameraMode.value) {
                    "Photo" -> "Auto"
                    "Pro" -> "Auto"
                    else -> _cameraMode.value
                },
                activeFilter = _activeFilter.value,
                filterIntensity = _filterIntensity.value
            )

            // Calculate histogram from the processed frame
            val hist = ImageProcessor.generateHistogram(processed)

            withContext(Dispatchers.Main) {
                _liveFrame.value = processed
                _histogramData.value = hist
                _sceneAnalysis.value = analysis
                _detectedScene.value = analysis.estimatedSceneDescription
            }
        }
    }

    private fun startProcessingLoop() {
        // Run a periodic processing update to simulate sensor variation and live adjustments
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(300) // update periodically for any real-time adjustments or sensor changes
                if (_isSimulatorActive.value && _liveFrame.value == null) {
                    triggerFrameProcessing()
                }
            }
        }
    }

    /**
     * Simulates professional multi-frame capture sequence based on active mode
     */
    fun capturePhoto() {
        if (_isCapturing.value) return
        _isCapturing.value = true

        viewModelScope.launch(Dispatchers.Default) {
            val steps = when (_cameraMode.value) {
                "Night" -> listOf(
                    "Calibrating low-light parameters...",
                    "Capturing exposure 1/4 (Shadows)...",
                    "Capturing exposure 2/4 (Highlights)...",
                    "Capturing exposure 3/4 (Reference)...",
                    "Capturing exposure 4/4 (Denoise map)...",
                    "Merging frames using AI Fusion...",
                    "Applying Neural Noise Reduction...",
                    "Enhancing local contrast details..."
                )
                "Portrait" -> listOf(
                    "Focusing depth sensor...",
                    "Isolating foreground subject...",
                    "Mapping background depth-of-field...",
                    "Capturing details...",
                    "Applying Gaussian Bokeh...",
                    "Enhancing skin textures and dynamic highlights..."
                )
                "Smart AI" -> listOf(
                    "Analyzing scene semantics...",
                    "Detected Scene: Scenic Sunset",
                    "Optimizing contrast & color-grade curves...",
                    "Capturing multi-exposure HDR stack...",
                    "Aligning frames & removing ghosting...",
                    "Applying intelligent local tone-mapping..."
                )
                else -> listOf(
                    "Measuring focus and exposure...",
                    "Capturing standard RAW frame...",
                    "Applying on-device digital development...",
                    "Applying custom color matrix..."
                )
            }

            for (step in steps) {
                _captureProgress.value = step
                delay(350) // Satisfying tactical progress rate
            }

            // Perform the final high-quality capture render
            val original = when (_cameraMode.value) {
                "Portrait" -> cachedPortraitBitmap
                "Night" -> cachedNightBitmap
                else -> cachedAutoBitmap
            }

            if (original != null) {
                val warmth = when (_whiteBalanceMode.value) {
                    "Sunny" -> 0.25f
                    "Cloudy" -> 0.45f
                    "Incandescent" -> -0.3f
                    "Fluorescent" -> -0.15f
                    else -> 0f
                }

                // High quality render
                val finalRender = ImageProcessor.processImage(
                    source = original,
                    iso = _iso.value,
                    shutterSpeed = shutterSpeedValues[_shutterSpeedIndex.value],
                    focusDistance = _focusDistance.value,
                    whiteBalanceWarmth = warmth,
                    exposureComp = _exposureComp.value,
                    aiEnhancementEnabled = _aiEnhancementEnabled.value,
                    aiEnhancementStrength = _aiEnhancementStrength.value,
                    aiSceneMode = when (_cameraMode.value) {
                        "Photo" -> "Auto"
                        "Pro" -> "Auto"
                        else -> _cameraMode.value
                    },
                    activeFilter = _activeFilter.value,
                    filterIntensity = _filterIntensity.value
                )

                // Save captured bitmap to private storage
                saveBitmapToGallery(finalRender)
            }

            withContext(Dispatchers.Main) {
                _captureProgress.value = null
                _isCapturing.value = false
            }
        }
    }

    private suspend fun saveBitmapToGallery(bitmap: Bitmap) {
        withContext(Dispatchers.IO) {
            try {
                val filename = "IMG_${System.currentTimeMillis()}.jpg"
                val file = File(context.filesDir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }

                val photo = CapturedPhoto(
                    filePath = file.absolutePath,
                    iso = _iso.value,
                    shutterSpeed = shutterSpeedList[_shutterSpeedIndex.value],
                    focusDistance = _focusDistance.value,
                    whiteBalance = _whiteBalanceMode.value,
                    exposureComp = _exposureComp.value,
                    aiMode = _cameraMode.value,
                    filterName = _activeFilter.value,
                    filterIntensity = _filterIntensity.value,
                    width = bitmap.width,
                    height = bitmap.height
                )

                _gallery.update { current -> listOf(photo) + current }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadGallery() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = context.filesDir.listFiles { _, name -> name.startsWith("IMG_") && name.endsWith(".jpg") }
                if (files != null) {
                    val photos = files.map { file ->
                        // Deduce mock meta from timestamp in filename or default values
                        CapturedPhoto(
                            filePath = file.absolutePath,
                            timestamp = file.lastModified(),
                            iso = 400,
                            shutterSpeed = "1/125s",
                            focusDistance = 0f,
                            whiteBalance = "Auto",
                            exposureComp = 0f,
                            aiMode = "Photo",
                            filterName = "None",
                            filterIntensity = 0.8f,
                            width = 1024,
                            height = 768
                        )
                    }.sortedByDescending { it.timestamp }
                    
                    withContext(Dispatchers.Main) {
                        _gallery.value = photos
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Sensor Listener implementation
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_showLevel.value) return
        
        // Low-pass filter to smooth sensor jitter
        val alpha = 0.15f
        if (event.sensor.type == Sensor.TYPE_GRAVITY || event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            // Roll is the rotation around the forward/backward axis (tilt left/right)
            val angleRad = Math.atan2((-x).toDouble(), y.toDouble())
            val angleDeg = Math.toDegrees(angleRad).toFloat()
            
            // Map angle range appropriately to reflect horizontal level deviation
            var adjustedRoll = angleDeg
            if (adjustedRoll > 90) adjustedRoll = 180f - adjustedRoll
            else if (adjustedRoll < -90) adjustedRoll = -180f - adjustedRoll
            
            _levelRoll.update { current -> current + alpha * (adjustedRoll - current) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        sensorManager?.unregisterListener(this)
        cachedAutoBitmap?.recycle()
        cachedPortraitBitmap?.recycle()
        cachedNightBitmap?.recycle()
    }
}
