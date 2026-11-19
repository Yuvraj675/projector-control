package dev.keystone.auto.platform

import android.content.Context
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dev.keystone.auto.vision.GrayImage
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Owns the CameraX use cases: preview, a luminance feed for live marker detection, and still capture. */
class CameraRig(private val context: Context) {
    @Volatile var meta: CameraMeta? = null
        private set

    /** False if this phone could not run live analysis next to preview and capture (the user then shoots manually). */
    @Volatile var liveAnalysis: Boolean = true
        private set
    private var capture: ImageCapture? = null
    private var camera: Camera? = null
    private var provider: ProcessCameraProvider? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /** Binds the camera to the lifecycle. `onFrame` receives small luminance frames on a background thread. */
    fun bind(
        owner: LifecycleOwner, previewView: PreviewView, exposureStops: Double,
        onFrame: (GrayImage) -> Unit, onReady: (CameraMeta?) -> Unit, onError: (String) -> Unit,
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val cap = ImageCapture.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { image ->
                    try {
                        val plane = image.planes[0]
                        onFrame(GrayImage.fromLuma(plane.buffer, image.width, image.height, plane.rowStride, plane.pixelStride))
                    } catch (_: Throwable) {
                    } finally {
                        image.close()
                    }
                }
                p.unbindAll()
                val cam = try {
                    p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, cap, analysis)
                } catch (e: IllegalArgumentException) {
                    // Some phones cannot run all three at once: keep the preview and the shutter, drop the live detection.
                    liveAnalysis = false
                    p.unbindAll()
                    p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, cap)
                }
                camera = cam
                capture = cap
                // The projection is bright: expose below auto so the circles keep clean edges.
                val st = cam.cameraInfo.exposureState
                if (st.isExposureCompensationSupported) {
                    val step = st.exposureCompensationStep.toDouble()
                    val idx = Math.round(exposureStops / step).toInt().coerceIn(st.exposureCompensationRange.lower, st.exposureCompensationRange.upper)
                    cam.cameraControl.setExposureCompensationIndex(idx)
                }
                meta = readCameraMeta(cam.cameraInfo)
                onReady(meta)
            } catch (e: Exception) {
                onError(e.message ?: e.javaClass.simpleName)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePhoto(file: File, onSaved: () -> Unit, onError: (String) -> Unit) {
        val c = capture ?: return onError("Camera not ready")
        c.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onSaved()
                override fun onError(e: ImageCaptureException) = onError(e.message ?: "Capture failed")
            },
        )
    }

    fun release() {
        try { provider?.unbindAll() } catch (_: Exception) { }
        analysisExecutor.shutdown()
    }
}
