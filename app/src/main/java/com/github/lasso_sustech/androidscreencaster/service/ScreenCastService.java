package com.github.lasso_sustech.androidscreencaster.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.view.Surface;
import java.util.Date;

import androidx.core.app.NotificationCompat;

import com.github.lasso_sustech.androidscreencaster.consts.ActivityServiceMessage;
import com.github.lasso_sustech.androidscreencaster.consts.ExtraIntent;
import com.github.lasso_sustech.androidscreencaster.writer.IvfWriter;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by sih on 2017-05-31.
 */
public final class ScreenCastService extends Service {

    private static final int FPS = 120;
    private final String TAG = "ScreenCastService";
    private static int FOREGROUND_ID = 1112;
    private String CHANNEL_ID = "ScreenCastServiceChannel";

    private MediaProjectionManager mediaProjectionManager;
    private Handler handler;
    private Messenger crossProcessMessenger;

    private MediaProjection mediaProjection;
    private Surface inputSurface;
    private VirtualDisplay virtualDisplay;
    private MediaCodec.BufferInfo videoBufferInfo;
    private MediaCodec encoder;
    private IvfWriter ivf;

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");

        handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                Log.i(TAG, "Handler got message. what:" + msg.what);
                switch(msg.what) {
                    case ActivityServiceMessage.CONNECTED:
                    case ActivityServiceMessage.DISCONNECTED:
                        break;
                    case ActivityServiceMessage.STOP:
                        stopScreenCapture();
                        stopSelf();
                        break;
                }
                return false;
            }
        });
        crossProcessMessenger = new Messenger(handler);
        return crossProcessMessenger.getBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        System.loadLibrary("replay");

        // create notification channel
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Screen Cast", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
        // start foreground notification
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Screen Cast")
                .setContentText("Screen Cast is running")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT).build();
        if (Build.VERSION.SDK_INT > 28) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(FOREGROUND_ID, notification);
        }

        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        stopScreenCapture();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        final int resultCode = intent.getIntExtra(ExtraIntent.RESULT_CODE.toString(), -1);
        final Intent resultData = intent.getParcelableExtra(ExtraIntent.RESULT_DATA.toString());

        if (resultCode == 0 || resultData == null) { return  START_NOT_STICKY; }

        final String format = intent.getStringExtra(ExtraIntent.VIDEO_FORMAT.toString());
        final int screenWidth = intent.getIntExtra(ExtraIntent.SCREEN_WIDTH.toString(), 640);
        final int screenHeight = intent.getIntExtra(ExtraIntent.SCREEN_HEIGHT.toString(), 360);
        final int screenDpi = intent.getIntExtra(ExtraIntent.SCREEN_DPI.toString(), 96);
        final int bitrate = intent.getIntExtra(ExtraIntent.VIDEO_BITRATE.toString(), 1024000);

        final String ipaddr1_tx = intent.getStringExtra(ExtraIntent.IPADDR1_TX.toString());
        final String ipaddr1_rx = intent.getStringExtra(ExtraIntent.IPADDR1_RX.toString());
        final String ipaddr2_tx = intent.getStringExtra(ExtraIntent.IPADDR2_TX.toString());
        final String ipaddr2_rx = intent.getStringExtra(ExtraIntent.IPADDR2_RX.toString());
        final String manifestFile = intent.getStringExtra(ExtraIntent.MANIFEST_FILE.toString());
        final float duration = intent.getFloatExtra(ExtraIntent.DURATION.toString(), 10);
        final int ipcPort = intent.getIntExtra(ExtraIntent.IPC_PORT.toString(), 11112);

        Log.i(TAG, "Start casting with format:" + format + ", screen:" + screenWidth +"x"+screenHeight +" @ " + screenDpi + " bitrate:" + bitrate);

        RustStreamReplay.startReplay(getAssets(), manifestFile, ipaddr1_tx, ipaddr1_rx, ipaddr2_tx, ipaddr2_rx, duration, ipcPort);

        startScreenCapture(resultCode, resultData, format, screenWidth, screenHeight, screenDpi, bitrate);

        return START_STICKY;
    }

    private void startScreenCapture(int resultCode, Intent resultData, String format, int width, int height, int dpi, int bitrate) {
        this.mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData);

        Log.d(TAG, "startRecording...");

        this.videoBufferInfo = new MediaCodec.BufferInfo();
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(format, width, height);

        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
        mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 0);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        try {

            switch (format) {
                case MediaFormat.MIMETYPE_VIDEO_AVC:
                    // AVC
                    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

                    this.encoder = MediaCodec.createEncoderByType(format);
                    this.encoder.setCallback(new MediaCodec.Callback() {
                        @Override
                        public void onInputBufferAvailable(MediaCodec codec, int inputBufferId) {
                        }

                        @Override
                        public void onOutputBufferAvailable(MediaCodec codec, int outputBufferId, MediaCodec.BufferInfo info) {
                            ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferId);
                            if (info.size > 0 && outputBuffer != null) {
                                outputBuffer.position(info.offset);
                                outputBuffer.limit(info.offset + info.size);
                                byte[] b = new byte[outputBuffer.remaining()];
                                outputBuffer.get(b);
                                sendData(null, b);
                            }
                            if (encoder != null) {
                                encoder.releaseOutputBuffer(outputBufferId, false);
                            }
                            if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                Log.i(TAG, "End of Stream");
                                stopScreenCapture();
                            }
                        }

                        @Override
                        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                            e.printStackTrace();
                        }

                        @Override
                        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                            Log.i(TAG, "onOutputFormatChanged. CodecInfo:" + codec.getCodecInfo().toString() + " MediaFormat:" + format.toString());
                        }
                    });
                    break;
                case MediaFormat.MIMETYPE_VIDEO_VP8:
                    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
                    final int frameSize = width * height * 3 / 2;
                    //VP8
                    byte[] ivfHeader = IvfWriter.makeIvfHeader(0, width, height, 1, bitrate);
                    sendData(null, ivfHeader);

                    this.encoder = MediaCodec.createByCodecName("OMX.google.vp8.encoder");
//                this.encoder = MediaCodec.createEncoderByType(format);
                    this.encoder.setCallback(new MediaCodec.Callback() {
                        @Override
                        public void onInputBufferAvailable(MediaCodec codec, int inputBufIndex) {
                        }

                        @Override
                        public void onOutputBufferAvailable(MediaCodec codec, int outputBufferId, MediaCodec.BufferInfo info) {
                            ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferId);
                            if (info.size > 0 && outputBuffer != null) {
                                outputBuffer.position(info.offset);
                                outputBuffer.limit(info.offset + info.size);

                                byte[] header = IvfWriter.makeIvfFrameHeader(outputBuffer.remaining(), info.presentationTimeUs);
                                byte[] b = new byte[outputBuffer.remaining()];
                                outputBuffer.get(b);

                                sendData(header, b);
                            }
                            if (encoder != null) {
                                encoder.releaseOutputBuffer(outputBufferId, false);
                            }
                            if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                Log.i(TAG, "End of Stream");
                                stopScreenCapture();
                            }
                        }

                        @Override
                        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                            e.printStackTrace();
                        }

                        @Override
                        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                            Log.i(TAG, "onOutputFormatChanged. CodecInfo:" + codec.getCodecInfo().toString() + " MediaFormat:" + format.toString());
                        }
                    });
                    break;
                default:
                    throw new RuntimeException("Unknown Media Format. You need to add mimetype to string.xml and else if statement");
            }

            this.encoder.configure(mediaFormat
                                    , null // surface
                                    , null // crypto
                                    , MediaCodec.CONFIGURE_FLAG_ENCODE);

            this.inputSurface = this.encoder.createInputSurface();
            this.encoder.start();

        } catch (IOException e) {
            Log.e(TAG, "Failed to initial encoder, e: " + e);
            releaseEncoders();
        }

        this.virtualDisplay = this.mediaProjection.createVirtualDisplay("Recording Display", width, height, dpi, 0, this.inputSurface, null, null);
    }

    private void sendData(byte[] header, byte[] data) {
        // final long date = new Date().getTime();

        if(header != null) {
            byte[] headerAndBody = new byte[header.length + data.length];
            System.arraycopy(header, 0, headerAndBody, 0, header.length);
            System.arraycopy(data, 0, headerAndBody, header.length, data.length);
            RustStreamReplay.sendData("stream://test", headerAndBody);
            // Log.i(TAG, String.format("%d %d", date, header.length+data.length));
        } else{
            RustStreamReplay.sendData("stream://test", data);
            // Log.i(TAG, String.format("%d %d", date, data.length));
        }
    }

    private void stopScreenCapture() {
        releaseEncoders();
        if (virtualDisplay == null) {
            return;
        }
        virtualDisplay.release();
        virtualDisplay = null;
    }

    private void releaseEncoders() {

        if (encoder != null) {
            encoder.stop();
            encoder.release();
            encoder = null;
        }
        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        if(ivf != null) {
            ivf = null;
        }

        videoBufferInfo = null;
    }

}
