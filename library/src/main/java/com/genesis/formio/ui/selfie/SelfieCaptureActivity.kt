package com.genesis.formio.ui.selfie

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SelfieCaptureActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PHOTO_PATH = "selfie_photo_path"
        private const val REQUEST_CAMERA = 1001
        private const val CHALLENGE_TIMEOUT_MS = 5000L
        private const val COUNTDOWN_TICK_MS    = 80L
        private const val STEP_OK_PAUSE_MS     = 700L
        private const val FAIL_PAUSE_MS        = 1500L
    }

    // ── Challenge definitions ──────────────────────────────────────────────────
    private enum class Challenge(val instruction: String, val subInstruction: String) {
        BLINK(      "Parpadea",            "Cierra y abre los ojos"),
        SMILE(      "Sonríe",              "Muestra una sonrisa"),
        TURN_LEFT(  "Gira a tu izquierda", "Mueve la cabeza hacia la izquierda"),
        TURN_RIGHT( "Gira a tu derecha",   "Mueve la cabeza hacia la derecha")
    }

    // ── Internal state machine ─────────────────────────────────────────────────
    private enum class Step { SEARCHING, FACE_FOUND, CHALLENGE, STEP_PAUSE, FAIL_PAUSE, DONE }

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: FaceOvalOverlayView
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    private val handler = Handler(Looper.getMainLooper())

    // Current session
    private var step = Step.SEARCHING
    private var challenges = listOf<Challenge>()
    private var challengeIndex = 0
    private var capturing = false

    // Countdown
    private var countdownElapsedMs = 0L
    private var countdownRunnable: Runnable? = null

    // Blink sub-state
    private var blinkSawClosed = false

    private val faceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
        )
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp   = resources.displayMetrics.density
        val root = FrameLayout(this)

        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }

        overlayView = FaceOvalOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val btnClose = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            val pad = (16 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).also { it.topMargin = (36 * dp).toInt(); it.marginStart = (16 * dp).toInt() }
            setColorFilter(Color.WHITE)
            setOnClickListener { setResult(RESULT_CANCELED); finish() }
        }

        root.addView(previewView)
        root.addView(overlayView)
        root.addView(btnClose)
        setContentView(root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else { setResult(RESULT_CANCELED); finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCountdown()
        cameraExecutor.shutdown()
        faceDetector.close()
        handler.removeCallbacksAndMessages(null)
    }

    // ── Camera setup ───────────────────────────────────────────────────────────

    private fun startCamera() {
        ProcessCameraProvider.getInstance(this).addListener({
            val provider = ProcessCameraProvider.getInstance(this).get()
            val preview  = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { it.setAnalyzer(cameraExecutor) { proxy -> analyzeFrame(proxy) } }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, imageCapture, analysis
                )
            } catch (_: Exception) { setResult(RESULT_CANCELED); finish() }
        }, ContextCompat.getMainExecutor(this))
    }

    // ── Frame analysis ─────────────────────────────────────────────────────────

    @androidx.camera.core.ExperimentalGetImage
    private fun analyzeFrame(proxy: ImageProxy) {
        if (capturing || step == Step.STEP_PAUSE || step == Step.FAIL_PAUSE || step == Step.DONE) {
            proxy.close(); return
        }
        val mediaImage = proxy.image ?: run { proxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                val face = faces.firstOrNull()
                when (step) {
                    Step.SEARCHING -> {
                        if (face != null) {
                            step = Step.FACE_FOUND
                            handler.post {
                                overlayView.state = FaceOvalOverlayView.State.FACE_FOUND
                            }
                            // Brief moment to show "Analyzing" then start challenges
                            handler.postDelayed({ beginSession() }, 800)
                        }
                    }
                    Step.CHALLENGE -> {
                        if (face == null) {
                            // Face lost — reset
                            handler.post { enterFail() }
                            return@addOnSuccessListener
                        }
                        val current = challenges[challengeIndex]
                        val completed = checkChallenge(
                            challenge  = current,
                            leftEye    = face.leftEyeOpenProbability  ?: 1f,
                            rightEye   = face.rightEyeOpenProbability ?: 1f,
                            smile      = face.smilingProbability      ?: 0f,
                            eulerY     = face.headEulerAngleY
                        )
                        if (completed) handler.post { onChallengeCompleted() }
                    }
                    else -> { /* pauses handled via handler */ }
                }
            }
            .addOnCompleteListener { proxy.close() }
    }

    private fun checkChallenge(
        challenge: Challenge,
        leftEye: Float, rightEye: Float,
        smile: Float, eulerY: Float
    ): Boolean = when (challenge) {
        Challenge.BLINK -> {
            val closed = leftEye < 0.30f && rightEye < 0.30f
            val open   = leftEye > 0.70f && rightEye > 0.70f
            if (closed) blinkSawClosed = true
            blinkSawClosed && open
        }
        Challenge.SMILE       -> smile > 0.75f
        Challenge.TURN_LEFT   -> eulerY >  22f
        Challenge.TURN_RIGHT  -> eulerY < -22f
    }

    // ── Session flow ───────────────────────────────────────────────────────────

    private fun beginSession() {
        // Pick 2 different random challenges
        val pool = Challenge.entries.toMutableList()
        pool.shuffle()
        challenges     = pool.take(2)
        challengeIndex = 0
        startChallenge()
    }

    private fun startChallenge() {
        blinkSawClosed     = false
        countdownElapsedMs = 0L
        step               = Step.CHALLENGE
        val c = challenges[challengeIndex]

        handler.post {
            overlayView.state              = FaceOvalOverlayView.State.CHALLENGE
            overlayView.challengeText      = c.instruction
            overlayView.subChallengeText   = c.subInstruction
            overlayView.countdownProgress  = 1f
        }
        startCountdown()
    }

    private fun onChallengeCompleted() {
        stopCountdown()
        step = Step.STEP_PAUSE

        overlayView.state = FaceOvalOverlayView.State.STEP_OK

        handler.postDelayed({
            challengeIndex++
            if (challengeIndex < challenges.size) {
                startChallenge()
            } else {
                // Both challenges passed → liveness confirmed
                step = Step.DONE
                capturing = true
                overlayView.state = FaceOvalOverlayView.State.LIVENESS_OK
                handler.postDelayed({ capturePhoto() }, 600)
            }
        }, STEP_OK_PAUSE_MS)
    }

    private fun enterFail() {
        stopCountdown()
        step = Step.FAIL_PAUSE
        overlayView.state = FaceOvalOverlayView.State.FAIL

        handler.postDelayed({
            step = Step.SEARCHING
            overlayView.state = FaceOvalOverlayView.State.SEARCHING
        }, FAIL_PAUSE_MS)
    }

    // ── Countdown timer ────────────────────────────────────────────────────────

    private fun startCountdown() {
        stopCountdown()
        val tick = object : Runnable {
            override fun run() {
                if (step != Step.CHALLENGE) return
                countdownElapsedMs += COUNTDOWN_TICK_MS
                val progress = 1f - (countdownElapsedMs.toFloat() / CHALLENGE_TIMEOUT_MS)
                overlayView.countdownProgress = progress.coerceAtLeast(0f)

                if (countdownElapsedMs >= CHALLENGE_TIMEOUT_MS) {
                    enterFail()
                } else {
                    handler.postDelayed(this, COUNTDOWN_TICK_MS)
                    countdownRunnable = this
                }
            }
        }
        countdownRunnable = tick
        handler.postDelayed(tick, COUNTDOWN_TICK_MS)
    }

    private fun stopCountdown() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
    }

    // ── Photo capture ──────────────────────────────────────────────────────────

    private fun capturePhoto() {
        overlayView.state = FaceOvalOverlayView.State.CAPTURING
        val capture = imageCapture ?: run { setResult(RESULT_CANCELED); finish(); return }
        val file = File(filesDir.also { it.mkdirs() }, "selfie_${UUID.randomUUID()}.jpg")

        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_PHOTO_PATH, file.absolutePath))
                    finish()
                }
                override fun onError(exc: ImageCaptureException) {
                    capturing = false
                    step = Step.SEARCHING
                    overlayView.state = FaceOvalOverlayView.State.SEARCHING
                }
            }
        )
    }
}
