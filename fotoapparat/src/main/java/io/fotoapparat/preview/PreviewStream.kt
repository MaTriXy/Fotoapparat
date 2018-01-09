@file:Suppress("DEPRECATION")

package io.fotoapparat.preview

import android.graphics.ImageFormat
import android.hardware.Camera
import io.fotoapparat.hardware.frameProcessingExecutor
import io.fotoapparat.parameter.Resolution
import java.util.*

/**
 * Preview stream of Camera.
 */
internal class PreviewStream(private val camera: Camera) {

    private val frameProcessors = LinkedHashSet<(Frame) -> Unit>()

    private var previewResolution: Resolution? = null

    /**
     * CW rotation of frames in degrees.
     */
    var frameOrientation = 0

    /**
     * Clears all processors.
     */
    fun clearProcessors() {
        synchronized(frameProcessors) {
            frameProcessors.clear()
        }
    }

    /**
     * Registers new processor. If processor was already added before, does nothing.
     */
    fun addProcessor(processor: (Frame) -> Unit) {
        synchronized(frameProcessors) {
            frameProcessors.add(processor)
        }
    }

    /**
     * Starts preview stream. After preview is started frame processors will start receiving frames.
     */
    fun start() {
        camera.addFrameToBuffer()

        camera.setPreviewCallbackWithBuffer { data, _ -> dispatchFrameOnBackgroundThread(data) }
    }

    /**
     * Stops preview stream.
     */
    fun stop() {
        camera.setPreviewCallbackWithBuffer(null)
    }

    private fun Camera.addFrameToBuffer() {
        addCallbackBuffer(parameters.allocateBuffer())
    }

    private fun Camera.Parameters.allocateBuffer(): ByteArray {
        ensureNv21Format()

        previewResolution = Resolution(
                previewSize.width,
                previewSize.height
        )

        return ByteArray(previewSize.bytesPerFrame())
    }

    private fun dispatchFrameOnBackgroundThread(data: ByteArray) {
        frameProcessingExecutor.execute {
            synchronized(frameProcessors) {
                dispatchFrame(data)
            }
        }
    }

    private fun dispatchFrame(image: ByteArray) {
        val previewResolution = ensurePreviewSizeAvailable()

        val frame = Frame(
                size = previewResolution,
                image = image,
                rotation = frameOrientation
        )

        frameProcessors.forEach {
            it(frame)
        }

        returnFrameToBuffer(frame)
    }

    private fun ensurePreviewSizeAvailable(): Resolution {
        return previewResolution ?: throw IllegalStateException("previewSize is null. Frame was not added?")
    }

    private fun returnFrameToBuffer(frame: Frame) {
        camera.addCallbackBuffer(
                frame.image
        )
    }

}

private fun Camera.Size.bytesPerFrame(): Int {
    return width * height * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8
}

private fun Camera.Parameters.ensureNv21Format() {
    if (previewFormat != ImageFormat.NV21) {
        throw UnsupportedOperationException("Only NV21 preview format is supported")
    }
}
