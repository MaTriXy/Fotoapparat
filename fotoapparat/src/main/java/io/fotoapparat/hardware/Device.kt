@file:Suppress("DEPRECATION")

package io.fotoapparat.hardware

import android.hardware.Camera
import io.fotoapparat.characteristic.LensPosition
import io.fotoapparat.characteristic.getCharacteristics
import io.fotoapparat.configuration.CameraConfiguration
import io.fotoapparat.configuration.Configuration
import io.fotoapparat.exception.camera.UnsupportedLensException
import io.fotoapparat.hardware.display.Display
import io.fotoapparat.log.Logger
import io.fotoapparat.parameter.ScaleType
import io.fotoapparat.parameter.camera.CameraParameters
import io.fotoapparat.parameter.camera.provide.getCameraParameters
import io.fotoapparat.preview.Frame
import io.fotoapparat.view.CameraRenderer
import io.fotoapparat.view.FocalPointSelector
import kotlinx.coroutines.experimental.CompletableDeferred


/**
 * Phone.
 */
internal open class Device(
        private val logger: Logger,
        private val display: Display,
        open val scaleType: ScaleType,
        open val cameraRenderer: CameraRenderer,
        val focusPointSelector: FocalPointSelector?,
        numberOfCameras: Int = Camera.getNumberOfCameras(),
        initialConfiguration: CameraConfiguration, initialLensPositionSelector: Collection<LensPosition>.() -> LensPosition?
) {

    private val cameras = (0 until numberOfCameras).map { cameraId ->
        CameraDevice(
                logger = logger,
                characteristics = getCharacteristics(cameraId)
        )
    }

    private var lensPositionSelector: Collection<LensPosition>.() -> LensPosition? = initialLensPositionSelector
    private var selectedCameraDevice = CompletableDeferred<CameraDevice>()
    private var savedConfiguration = CameraConfiguration.default()

    init {
        updateLensPositionSelector(initialLensPositionSelector)
        savedConfiguration = initialConfiguration
    }

    /**
     * Selects a camera.
     */
    open fun canSelectCamera(lensPositionSelector: (Collection<LensPosition>) -> LensPosition?): Boolean {
        val selectedCameraDevice = selectCamera(
                availableCameras = cameras,
                lensPositionSelector = lensPositionSelector
        )
        return selectedCameraDevice != null
    }

    /**
     * Selects a camera. Will do nothing if camera cannot be selected.
     */
    open fun selectCamera() {
        logger.recordMethod()

        selectCamera(
                availableCameras = cameras,
                lensPositionSelector = lensPositionSelector
        )
                ?.let {
                    selectedCameraDevice.complete(it)
                }
                ?: selectedCameraDevice.completeExceptionally(UnsupportedLensException())
    }

    /**
     * Clears the selected camera.
     */
    open fun clearSelectedCamera() {
        selectedCameraDevice = CompletableDeferred()
    }

    /**
     * Waits and returns the selected camera.
     */
    open suspend fun awaitSelectedCamera(): CameraDevice {
        return selectedCameraDevice.await()
    }

    /**
     * Returns the selected camera.
     *
     * @throws IllegalStateException If no camera has been yet selected.
     * @throws UnsupportedLensException If no camera could get selected.
     */
    open fun getSelectedCamera(): CameraDevice {
        return try {
            selectedCameraDevice.getCompleted()
        } catch (e: IllegalStateException) {
            throw IllegalStateException("Camera has not started!")
        }
    }

    /**
     * @return `true` if a camera has been selected.
     */
    open fun hasSelectedCamera() = selectedCameraDevice.isCompleted

    /**
     * @return rotation of the screen in degrees.
     */
    open fun getScreenRotation(): Int {
        return display.getRotation()
    }

    /**
     * Updates the desired from the user camera lens position.
     */
    open fun updateLensPositionSelector(newLensPosition: Collection<LensPosition>.() -> LensPosition?) {
        logger.recordMethod()

        lensPositionSelector = newLensPosition
    }

    /**
     * Updates the desired from the user selectors.
     */
    open fun updateConfiguration(newConfiguration: Configuration) {
        logger.recordMethod()

        savedConfiguration = updateConfiguration(
                savedConfiguration = savedConfiguration,
                newConfiguration = newConfiguration
        )
    }

    /**
     * @return The desired from the user selectors.
     */
    open fun getConfiguration(): CameraConfiguration {
        return savedConfiguration
    }

    /**
     * @return The selected [CameraParameters] for the given [CameraDevice].
     */
    open suspend fun getCameraParameters(cameraDevice: CameraDevice): CameraParameters {
        return getCameraParameters(
                cameraConfiguration = savedConfiguration,
                capabilities = cameraDevice.getCapabilities()
        )
    }

    /**
     * @return The frame processor.
     */
    open fun getFrameProcessor(): (Frame) -> Unit {
        return savedConfiguration.frameProcessor
    }

    /**
     * @return The desired from the user camera lens position.
     */
    open fun getLensPositionSelector(): Collection<LensPosition>.() -> LensPosition? {
        return lensPositionSelector
    }

}

/**
 * Updates the device's configuration.
 */
internal fun updateConfiguration(
        savedConfiguration: CameraConfiguration,
        newConfiguration: Configuration
) = CameraConfiguration(
        flashMode = newConfiguration.flashMode ?: savedConfiguration.flashMode,
        focusMode = newConfiguration.focusMode ?: savedConfiguration.focusMode,
        frameProcessor = newConfiguration.frameProcessor ?: savedConfiguration.frameProcessor,
        previewFpsRange = newConfiguration.previewFpsRange ?: savedConfiguration.previewFpsRange,
        sensorSensitivity = newConfiguration.sensorSensitivity ?: savedConfiguration.sensorSensitivity,
        pictureResolution = newConfiguration.pictureResolution ?: savedConfiguration.pictureResolution,
        previewResolution = newConfiguration.previewResolution ?: savedConfiguration.previewResolution
)

/**
 * Selects a camera from the set of available ones.
 */
internal fun selectCamera(
        availableCameras: List<CameraDevice>,
        lensPositionSelector: Collection<LensPosition>.() -> LensPosition?
): CameraDevice? {

    val lensPositions = availableCameras.map { it.characteristics.lensPosition }.toSet()
    val desiredPosition = lensPositionSelector(lensPositions)

    return availableCameras.find { it.characteristics.lensPosition == desiredPosition }
}