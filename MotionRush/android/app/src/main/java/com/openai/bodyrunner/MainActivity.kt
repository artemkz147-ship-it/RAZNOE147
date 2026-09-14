package com.openai.bodyrunner

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : ComponentActivity(), PoseLandmarkerHelper.Listener {
    private lateinit var webView: WebView
    private lateinit var previewContainer: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var previewStatus: TextView
    private lateinit var cameraExecutor: ExecutorService

    private var cameraProvider: ProcessCameraProvider? = null
    private var poseHelper: PoseLandmarkerHelper? = null
    private val gestureEngine = MotionGestureEngine()

    @Volatile
    private var latestPose: MotionPose? = null

    @Volatile
    private var trackingRequested = false

    @Volatile
    private var poseVisible = false

    private var noPoseReported = false
    private var lastPoseSeenAt = 0L
    private var lastTrackerUiAt = 0L
    private var lastResultAt = 0L
    private var smoothedFps = 0f

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) initializeNativeTracking() else {
            trackingRequested = false
            setPreviewStatus("НЕТ ДОСТУПА")
            sendStatus("permission-denied", "Разрешение на камеру не выдано")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        val root = FrameLayout(this)
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
                allowContentAccess = false
                setSupportZoom(false)
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest,
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
            }
            addJavascriptInterface(MotionBridge(), "AndroidMotion")
            WebView.setWebContentsDebuggingEnabled(true)
        }
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        previewContainer = FrameLayout(this).apply {
            visibility = View.GONE
            elevation = dp(20).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.rgb(7, 18, 41))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(2), Color.rgb(101, 239, 255))
            }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            isClickable = false
            isFocusable = false
        }
        previewContainer.addView(
            previewView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        previewStatus = TextView(this).apply {
            text = "КАМЕРА"
            setTextColor(Color.WHITE)
            textSize = 9f
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(0xAA030816.toInt())
        }
        previewContainer.addView(
            previewStatus,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(28),
                Gravity.BOTTOM,
            ),
        )

        root.addView(
            previewContainer,
            FrameLayout.LayoutParams(dp(126), dp(172), Gravity.END or Gravity.BOTTOM).apply {
                marginEnd = dp(12)
                bottomMargin = dp(18)
            },
        )

        setContentView(root)
        webView.loadUrl("https://appassets.androidplatform.net/assets/web/index.html")
    }

    private fun requestTracking() {
        if (trackingRequested) {
            sendStatus("initializing", "Камера уже запускается")
            return
        }
        trackingRequested = true
        gestureEngine.reset()
        latestPose = null
        poseVisible = false
        noPoseReported = false
        lastPoseSeenAt = 0L
        lastTrackerUiAt = 0L
        lastResultAt = 0L
        smoothedFps = 0f

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initializeNativeTracking()
        } else {
            setPreviewStatus("РАЗРЕШЕНИЕ")
            sendStatus("requesting-permission", "Нужно разрешение на фронтальную камеру")
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initializeNativeTracking() {
        runOnUiThread {
            previewContainer.visibility = View.VISIBLE
            setPreviewStatus("ЗАПУСК ИИ")
            sendStatus("initializing", "Запускаю нативное распознавание тела")
        }

        cameraExecutor.execute {
            try {
                poseHelper?.close()
                poseHelper = PoseLandmarkerHelper(applicationContext, this)
                runOnUiThread { bindCamera() }
            } catch (error: Throwable) {
                trackingRequested = false
                setPreviewStatus("ОШИБКА ИИ")
                sendStatus("error", error.message ?: "Не удалось запустить MediaPipe")
            }
        }
    }

    private fun bindCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(480, 360),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        )
                    )
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val helper = poseHelper
                    if (helper == null) imageProxy.close()
                    else {
                        try {
                            helper.detectLiveStream(imageProxy, true)
                        } catch (error: Throwable) {
                            runCatching { imageProxy.close() }
                            sendStatus("error", error.message ?: "Ошибка обработки кадра")
                        }
                    }
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis,
                )

                setPreviewStatus("ИЩУ ТЕЛО")
                sendStatus("camera-ready", "Фронтальная камера запущена")
            } catch (error: Throwable) {
                trackingRequested = false
                setPreviewStatus("ОШИБКА КАМЕРЫ")
                sendStatus("error", error.message ?: "Не удалось открыть фронтальную камеру")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onPose(pose: MotionPose, timestampMs: Long, width: Int, height: Int) {
        latestPose = pose
        val now = SystemClock.uptimeMillis()
        lastPoseSeenAt = now

        if (!poseVisible || noPoseReported) {
            poseVisible = true
            noPoseReported = false
            sendStatus("pose-found", "Тело распознано")
        }

        if (lastResultAt > 0L) {
            val dt = (now - lastResultAt).coerceAtLeast(1L)
            val instantFps = 1000f / dt.toFloat()
            smoothedFps = if (smoothedFps == 0f) instantFps else smoothedFps * 0.82f + instantFps * 0.18f
        }
        lastResultAt = now

        if (now - lastTrackerUiAt >= 450L) {
            lastTrackerUiAt = now
            val fps = smoothedFps.coerceIn(0f, 60f).roundToInt()
            val backend = poseHelper?.backendLabel ?: "ИИ"
            val latency = (now - timestampMs).coerceAtLeast(0L)
            setPreviewStatus("ТЕЛО • $backend • ${fps}FPS • ${latency}мс")
        }

        val actions = gestureEngine.update(pose, timestampMs)
        for (action in actions) sendAction(action)
    }

    override fun onNoPose() {
        val now = SystemClock.uptimeMillis()
        if (lastPoseSeenAt > 0L && now - lastPoseSeenAt < 450L) return
        if (noPoseReported) return

        noPoseReported = true
        poseVisible = false
        setPreviewStatus("ВСТАНЬ В КАДР")
        sendStatus("no-pose", "Отойди так, чтобы были видны плечи, таз и ноги")
    }

    override fun onError(message: String) {
        setPreviewStatus("ОШИБКА ИИ")
        sendStatus("error", message)
    }

    private fun calibrateCurrentPose(): Boolean {
        val pose = latestPose ?: return false
        return gestureEngine.calibrate(pose)
    }

    private fun sendAction(action: String) {
        runOnUiThread {
            if (!::webView.isInitialized) return@runOnUiThread
            webView.evaluateJavascript(
                "window.onNativeMotionAction?.(${JSONObject.quote(action)})",
                null,
            )
        }
    }

    private fun sendStatus(code: String, message: String = "") {
        runOnUiThread {
            if (!::webView.isInitialized) return@runOnUiThread
            webView.evaluateJavascript(
                "window.onNativeMotionStatus?.(${JSONObject.quote(code)}, ${JSONObject.quote(message)})",
                null,
            )
        }
    }

    private fun setPreviewStatus(text: String) {
        runOnUiThread {
            if (::previewStatus.isInitialized) previewStatus.text = text
        }
    }

    private fun stopTracking() {
        trackingRequested = false
        gestureEngine.reset()
        latestPose = null
        poseVisible = false
        noPoseReported = false
        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraExecutor.execute {
            poseHelper?.close()
            poseHelper = null
        }
        previewContainer.visibility = View.GONE
        sendStatus("stopped", "Камера остановлена")
    }

    inner class MotionBridge {
        @JavascriptInterface
        fun startTracking() {
            runOnUiThread { requestTracking() }
        }

        @JavascriptInterface
        fun calibrate() {
            val ok = this@MainActivity.calibrateCurrentPose()
            runOnUiThread {
                val script = "window.onNativeCalibrationResult?.(${if (ok) "true" else "false"})"
                webView.evaluateJavascript(script, null)
                if (ok) {
                    setPreviewStatus("ГОТОВО")
                    sendStatus("calibrated", "Калибровка завершена")
                }
            }
        }

        @JavascriptInterface
        fun setPreviewVisible(visible: Boolean) {
            runOnUiThread {
                previewContainer.visibility = if (visible) View.VISIBLE else View.GONE
            }
        }

        @JavascriptInterface
        fun stopTracking() {
            runOnUiThread { this@MainActivity.stopTracking() }
        }
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        poseHelper?.close()
        poseHelper = null
        cameraExecutor.shutdownNow()
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("AndroidMotion")
            webView.loadUrl("about:blank")
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
