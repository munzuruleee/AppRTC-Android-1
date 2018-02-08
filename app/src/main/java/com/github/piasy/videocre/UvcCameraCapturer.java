package com.github.piasy.videocre;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.os.Handler;

import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;

/**
 * Created by Piasy{github.com/Piasy} on 07/02/2018.
 */

public class UvcCameraCapturer implements VideoCapturer, USBMonitor.OnDeviceConnectListener {

  private Handler mCameraHandler;
  private SurfaceTextureHelper mSurfaceTextureHelper;
  private CapturerObserver mCapturerObserver;

  private USBMonitor mUSBMonitor;
  private UVCCamera mUVCCamera;
  private USBMonitor.UsbControlBlock mUsbControlBlock;

  private boolean mStartCaptureRequested = false;

  @Override
  public void initialize(final SurfaceTextureHelper surfaceTextureHelper,
                         final Context applicationContext,
                         final CapturerObserver capturerObserver) {
    mSurfaceTextureHelper = surfaceTextureHelper;
    mCapturerObserver = capturerObserver;

    mCameraHandler = mSurfaceTextureHelper.getHandler();

    mUSBMonitor = new USBMonitor(applicationContext, this);
    mUSBMonitor.register();
  }

  @Override
  public void startCapture(final int width, final int height, final int framerate) {
    mStartCaptureRequested = true;

    mCameraHandler.post(this::tryStartCapture);
  }

  @Override
  public void stopCapture() {
    mCameraHandler.post(() -> {
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
    mUSBMonitor.requestPermission(device);
  }

  @Override
  public void onDettach(final UsbDevice device) {
  }

  @Override
  public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock,
                        final boolean createNew) {
    mUsbControlBlock = ctrlBlock;

    mCameraHandler.post(this::tryStartCapture);
  }

  @Override
  public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
    stopCapture();
  }

  @Override
  public void onCancel(final UsbDevice device) {
  }

  private void tryStartCapture() {
    if (mStartCaptureRequested && mUsbControlBlock != null) {
      mUVCCamera = new UVCCamera();
      mUVCCamera.open(mUsbControlBlock);

      mCapturerObserver.onCapturerStarted(true);

      mUVCCamera.setPreviewTexture(mSurfaceTextureHelper.getSurfaceTexture());
      mUVCCamera.startPreview();
      mUVCCamera.updateCameraParams();

      mSurfaceTextureHelper.startListening((oesTextureId, transformMatrix, timestampNs) -> {
        checkIsOnCameraThread();

        mCapturerObserver.onTextureFrameCaptured(640, 480, oesTextureId, transformMatrix, 0,
            timestampNs);
      });
    }
  }

  private void checkIsOnCameraThread() {
    if (Thread.currentThread() != mCameraHandler.getLooper().getThread()) {
      throw new IllegalStateException("Wrong thread");
    }
  }
}
