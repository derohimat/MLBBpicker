package ai.zasha.mlbbpicker.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ScreenCaptureManager(
    private val context: Context,
    private val resultData: Intent
) {
    private val tag = "ScreenCaptureManager"
    private val mediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    var onTextDetected: ((String) -> Unit)? = null

    @Suppress("DEPRECATION")
    @SuppressLint("WrongConstant")
    fun startCapture() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        mediaProjection =
            mediaProjectionManager.getMediaProjection(android.app.Activity.RESULT_OK, resultData)

        imageReader = ImageReader.newInstance(
            metrics.widthPixels, metrics.heightPixels,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )

        startPeriodicCapture()
    }

    private fun startPeriodicCapture() {
        captureJob = scope.launch {
            while (true) {
                delay(3000) // Capture every 3 seconds
                captureAndProcess()
            }
        }
    }

    private fun captureAndProcess() {
        val image: Image = imageReader?.acquireLatestImage() ?: return

        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop the bitmap to the area where draft names appear (dummy crop for now)
            // val croppedBitmap = Bitmap.createBitmap(bitmap, x, y, w, h)

            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { text ->
                    val detectedText = text.text
                    Log.d(tag, "Detected text: $detectedText")
                    onTextDetected?.invoke(detectedText)
                }
                .addOnFailureListener { e ->
                    Log.e(tag, "OCR Failed", e)
                }
        } catch (e: Exception) {
            Log.e(tag, "Error processing image", e)
        } finally {
            image.close()
        }
    }

    fun stopCapture() {
        captureJob?.cancel()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }
}
