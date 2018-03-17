package com.serenegiant;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.media.MediaRecorder;
import android.os.Handler;

import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import org.webrtc.CameraVideoCapturer;
import org.webrtc.Logging;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;

import java.util.Collections;
import java.util.List;

public class UvcCameraCapturer implements CameraVideoCapturer, USBMonitor.OnDeviceConnectListener {
  private static final String TAG = "UvcCameraCapturer";

  private Handler mCameraHandler;
  private SurfaceTextureHelper mSurfaceTextureHelper;
  private CapturerObserver mCapturerObserver;

  private int mDesiredPreviewWidth;
  private int mDesiredPreviewHeight;

  private int mPreviewWidth;
  private int mPreviewHeight;

  private USBMonitor mUSBMonitor;
  private UVCCamera mUVCCamera;
  private USBMonitor.UsbControlBlock mUsbControlBlock;

  private boolean mStartCaptureRequested = false;

  @Override
  public void initialize(final SurfaceTextureHelper surfaceTextureHelper,
                         final Context applicationContext,
                         final CapturerObserver capturerObserver) {
    Logging.d(TAG, "initialize");

    mSurfaceTextureHelper = surfaceTextureHelper;
    mCapturerObserver = capturerObserver;

    mCameraHandler = mSurfaceTextureHelper.getHandler();

    mUSBMonitor = new USBMonitor(applicationContext, this);
    mUSBMonitor.register();
  }

  @Override
  public void startCapture(final int width, final int height, final int frameRate) {
    Logging.d(TAG, "startCapture " + width + "x" + height + "@" + frameRate);

    mDesiredPreviewWidth = width;
    mDesiredPreviewHeight = height;

    mStartCaptureRequested = true;

    mCameraHandler.post(this::tryStartCapture);
  }

  @Override
  public void stopCapture() {
    Logging.d(TAG, "stopCapture");

    mCameraHandler.post(() -> {
      mSurfaceTextureHelper.stopListening();

      if (mUVCCamera != null) {
        mUVCCamera.stopPreview();
        mUVCCamera.close();
        mUVCCamera.destroy();
        mUVCCamera = null;
      }
    });
  }

  @Override
  public void changeCaptureFormat(final int width, final int height, final int framerate) {
    // do not support yet
  }

  @Override
  public void dispose() {
    stopCapture();
    mUSBMonitor.unregister();
  }

  @Override
  public boolean isScreencast() {
    return false;
  }

  @Override
  public void onAttach(final UsbDevice device) {
    Logging.d(TAG, "onAttach");
    mUSBMonitor.requestPermission(device);
  }

  @Override
  public void onDettach(final UsbDevice device) {
    Logging.d(TAG, "onDettach");
  }

  @Override
  public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock,
                        final boolean createNew) {
    Logging.d(TAG, "onConnect");
    mUsbControlBlock = ctrlBlock;

    mCameraHandler.post(this::tryStartCapture);
  }

  @Override
  public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
    Logging.d(TAG, "onDisconnect");
    stopCapture();
  }

  @Override
  public void onCancel(final UsbDevice device) {
    Logging.d(TAG, "onCancel");
  }

  private void tryStartCapture() {
    Logging.d(TAG, "tryStartCapture mStartCaptureRequested? " + mStartCaptureRequested
        + ", mUsbControlBlock=" + mUsbControlBlock);

    if (mStartCaptureRequested && mUsbControlBlock != null) {
      mUVCCamera = new UVCCamera();
      mUVCCamera.open(mUsbControlBlock);

      setPreviewParams(mUVCCamera);

      mUVCCamera.setPreviewTexture(mSurfaceTextureHelper.getSurfaceTexture());
      mUVCCamera.startPreview();

      mCapturerObserver.onCapturerStarted(true);

      mSurfaceTextureHelper.startListening((oesTextureId, transformMatrix, timestampNs) -> {
        checkIsOnCameraThread();

        mCapturerObserver.onTextureFrameCaptured(640, 480, oesTextureId, transformMatrix, 0,
            timestampNs);
      });
    }
  }

  private void setPreviewParams(UVCCamera camera) {
    camera.updateCameraParams();

    camera.setBrightness(100);
    camera.setContrast(70);

    List<Size> sizeList = camera.getSupportedSizeList();
    Logging.d(TAG, "origin sizeList: " + sizeList);

    Size refinedSize = null;

    Collections.sort(sizeList, (o1, o2) -> {
      if (o1.width == o2.width && o1.height == o2.height) {
        return 0;
      }

      if (o1.width > o2.width && o1.height > o2.height) {
        return 1;
      }

      if (o1.width < o2.width && o1.height < o2.height) {
        return -1;
      }

      return o1.width * o1.height > o2.width * o2.height ? 1 : -1;
    });
    Logging.d(TAG, "sorted sizeList: " + sizeList);

    for (Size size : sizeList) {
      if (size.width >= mDesiredPreviewWidth && size.height >= mDesiredPreviewHeight) {
        refinedSize = size;
        break;
      }
    }

    if (refinedSize != null) {
      mPreviewWidth = refinedSize.width;
      mPreviewHeight = refinedSize.height;

      camera.setPreviewSize(refinedSize.width, refinedSize.height);
    } else {
      mPreviewWidth = UVCCamera.DEFAULT_PREVIEW_WIDTH;
      mPreviewHeight = UVCCamera.DEFAULT_PREVIEW_HEIGHT;
    }
  }

  private void checkIsOnCameraThread() {
    if (Thread.currentThread() != mCameraHandler.getLooper().getThread()) {
      throw new IllegalStateException("Wrong thread");
    }
  }

  @Override
  public void switchCamera(CameraSwitchHandler cameraSwitchHandler) {
  }

  @Override
  public void addMediaRecorderToCamera(MediaRecorder mediaRecorder, MediaRecorderHandler mediaRecorderHandler) {
  }

  @Override
  public void removeMediaRecorderFromCamera(MediaRecorderHandler mediaRecorderHandler) {
  }
}
