package com.github.lasso_sustech.androidscreencaster;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ToggleButton;

import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

import java.util.List;
import java.util.ArrayList;

import com.github.lasso_sustech.androidscreencaster.consts.ActivityServiceMessage;
import com.github.lasso_sustech.androidscreencaster.consts.ExtraIntent;
import com.github.lasso_sustech.androidscreencaster.service.ScreenCastService;
import com.github.lasso_sustech.androidscreencaster.service.RustStreamReplay;
import com.github.lasso_sustech.androidscreencaster.service.StreamDataSource;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private static final String PREFERENCE_KEY = "default";
    private static final String PREFERENCE_MANIFEST = "manifest";
    private static final String PREFERENCE_SPINNER_FORMAT = "spinner_format";
    private static final String PREFERENCE_SPINNER_RESOLUTION = "spinner_resolution";
    private static final String PREFERENCE_SPINNER_BITRATE = "spinner_bitrate";

    private static final String STATE_RESULT_CODE = "result_code";
    private static final String STATE_RESULT_DATA = "result_data";

    private static final int ACTIVITY_RESULT_REQUEST_MEDIA_PROJECTION = 300;

    private int stateResultCode;
    private Intent stateResultData;

    private Context context;
    private Messenger messenger;

    private MediaProjectionManager mediaProjectionManager;
    private ServiceConnection serviceConnection;
    private Messenger serviceMessenger;

    @UnstableApi
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        System.loadLibrary("replay");

        setContentView(R.layout.activity_main);

        if(savedInstanceState != null) {
            this.stateResultCode = savedInstanceState.getInt(STATE_RESULT_CODE);
            this.stateResultData = savedInstanceState.getParcelable(STATE_RESULT_DATA);
        }

        this.context = this;
        this.mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        this.messenger = new Messenger(new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                Log.i(TAG, "Handler got message : " + msg.what);
                return false;
            }
        }));

        this.serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.i(TAG, name + " service is connected.");

                serviceMessenger = new Messenger(service);
                Message msg = Message.obtain(null, ActivityServiceMessage.CONNECTED);
                msg.replyTo = messenger;
                try {
                    serviceMessenger.send(msg);
                } catch (RemoteException e) {
                    Log.e(TAG,"Failed to send message due to:" + e.toString());
                    e.printStackTrace();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i(TAG, name + " service is disconnected.");
                serviceMessenger = null;
            }
        };


        final ToggleButton toggleButton = (ToggleButton) findViewById(R.id.toggleButton);
        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.d(TAG, "Start button clicked.");

                    final CheckBox checkBox_tx = (CheckBox) findViewById(R.id.checkBox_tx);
                    final CheckBox checkBox_rx = (CheckBox) findViewById(R.id.checkBox_rx);

                    if (checkBox_tx.isChecked()) {
                        startCaptureScreen();
                    }

                    if (checkBox_rx.isChecked()) {
                        final EditText editText_duration = (EditText) findViewById(R.id.editText_duration);
                        final int duration = Integer.parseInt(editText_duration.getText().toString());

                        final int[][] rx_items = {
                            {R.id.checkBox_rtt1, R.id.checkBox_rx1, R.id.editText_rx1},
                            {R.id.checkBox_rtt2, R.id.checkBox_rx2, R.id.editText_rx2},
                            {R.id.checkBox_rtt3, R.id.checkBox_rx3, R.id.editText_rx3},
                            {R.id.checkBox_rtt4, R.id.checkBox_rx4, R.id.editText_rx4},
                        };

                        for (int[] rx_port : rx_items) {
                            final EditText editText_rx = (EditText) findViewById(rx_port[2]);
                            if (!editText_rx.getText().toString().isEmpty()) {
                                final int port = Integer.parseInt(editText_rx.getText().toString());
                                final boolean calc_rtt = ((CheckBox) findViewById(rx_port[0])).isChecked();
                                final boolean rx_mode  = ((CheckBox) findViewById(rx_port[1])).isChecked();
                                RustStreamReplay.startReceiver(port, duration, calc_rtt, rx_mode);
                            }
                        }

                        final EditText editText_stream = (EditText) findViewById(R.id.editText_rx1);
                        if (!editText_stream.getText().toString().isEmpty()) {
                            final int stream_port = Integer.parseInt(editText_stream.getText().toString());
                            final Uri dataUri = new Uri.Builder().scheme("stream").encodedAuthority("test:" + stream_port).build();
                            // initialize ExoPlayer
                            final ExoPlayer player = new ExoPlayer.Builder(context).build();
                            final StreamDataSource dataSource = new StreamDataSource();
                            try {
                                dataSource.open(new DataSpec(dataUri));
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to open data source due to:" + e.toString());
                            }
                            DataSource.Factory dataSourceFactory = new DataSource.Factory() {
                                @Override
                                public DataSource createDataSource() {
                                    return dataSource;
                                }
                            };
                            // Reference: https://developer.android.com/media/media3/exoplayer/progressive
                            // Reference: https://developer.android.com/media/media3/exoplayer/shrinking#java
                            MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                                                            .createMediaSource(MediaItem.fromUri(dataUri));
                            player.setMediaSource(mediaSource);
                            player.prepare();

                            // set player to PlayerView
                            final PlayerView playerView = (PlayerView) findViewById(R.id.player_view);
                            playerView.setPlayer(player);
                            player.play();
                        }
                    }

                    if (!checkBox_tx.isChecked() && !checkBox_rx.isChecked()) {
                        toggleButton.setChecked(false);
                        Log.e(TAG, "Neither tx nor rx is checked.");
                    }

                } else {
                    stopScreenCapture();
                }
            }
        });

        setSpinnerFromResId(R.array.options_format_keys, R.id.spinner_video_format, PREFERENCE_SPINNER_FORMAT);
        setSpinnerFromResId(R.array.options_resolution_keys,R.id.spinner_video_resolution, PREFERENCE_SPINNER_RESOLUTION);
        setSpinnerFromResId(R.array.options_bitrate_keys, R.id.spinner_video_bitrate, PREFERENCE_SPINNER_BITRATE);

        // setup manifest_spinner entries from "*manifest/.json" assets files
        final AssetManager assets = this.getAssets();
        List<CharSequence> manifestFiles = new ArrayList<>();
        try {
            final String[] assetFiles = assets.list("manifest");
            for (String file: assetFiles) {
                if (file.endsWith(".json")) {
                    manifestFiles.add(file);
                }
            }
            ArrayAdapter<CharSequence> manifestAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, manifestFiles);
            manifestAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            setSpinnerFromArrayAdapter(manifestAdapter, R.id.spinner_manifest, PREFERENCE_MANIFEST);
        } catch (Exception e) {
            Log.e(TAG, "Failed to list assets due to:" + e.toString());
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (stateResultData != null) {
            outState.putInt(STATE_RESULT_CODE, stateResultCode);
            outState.putParcelable(STATE_RESULT_DATA, stateResultData);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ACTIVITY_RESULT_REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Log.i(TAG, "User didn't allow.");
            } else {
                Log.d(TAG, "Starting screen capture");
                stateResultCode = resultCode;
                stateResultData = data;
                startCaptureScreen();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService();
    }

    private void unbindService() {
        if (serviceMessenger != null) {
            try {
                Message msg = Message.obtain(null, ActivityServiceMessage.DISCONNECTED);
                msg.replyTo = messenger;
                serviceMessenger.send(msg);
            } catch (RemoteException e) {
                Log.d(TAG, "Failed to send unregister message to service, e: " + e.toString());
                e.printStackTrace();
            }
            unbindService(serviceConnection);
        }
    }

    private void setSpinnerFromResId(final int textArrayOptionResId, final int textViewResId, final String preferenceId) {
        Log.d(TAG, "Setting spinner opt_id:" + textArrayOptionResId + " view_id:" + textViewResId + " pref_id:" + preferenceId);
        
        ArrayAdapter<CharSequence> arrayAdapter = ArrayAdapter.createFromResource(this, textArrayOptionResId, android.R.layout.simple_spinner_item);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        setSpinnerFromArrayAdapter(arrayAdapter, textViewResId, preferenceId);
    }

    private void setSpinnerFromArrayAdapter(final ArrayAdapter<CharSequence> arrayAdapter, final int textViewResId, final String preferenceId) {
        final Spinner spinner = (Spinner) findViewById(textViewResId);
        spinner.setAdapter(arrayAdapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                context.getSharedPreferences(PREFERENCE_KEY, 0).edit().putInt(preferenceId, position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                context.getSharedPreferences(PREFERENCE_KEY, 0).edit().putInt(preferenceId, 0).apply();
            }
        });
        spinner.setSelection(context.getSharedPreferences(PREFERENCE_KEY, 0).getInt(preferenceId, 0));
    }

    private void startService() {
        final String ipaddr1_tx = ((EditText) findViewById(R.id.editText_target1_tx)).getText().toString();
        final String ipaddr1_rx = ((EditText) findViewById(R.id.editText_target1_rx)).getText().toString();
        final String ipaddr2_tx = ((EditText) findViewById(R.id.editText_target2_tx)).getText().toString();
        final String ipaddr2_rx = ((EditText) findViewById(R.id.editText_target2_rx)).getText().toString();

        final Spinner manifestSpinner = (Spinner) findViewById(R.id.spinner_manifest);
        final String manifest_file = manifestSpinner.getSelectedItem().toString();

        final EditText editText_duration = (EditText) findViewById(R.id.editText_duration);
        final float duration = Float.parseFloat(editText_duration.getText().toString());

        final EditText editText_ipc_port = (EditText) findViewById(R.id.editText_ipc_port);
        final int ipc_port = Integer.parseInt(editText_ipc_port.getText().toString());

        Log.i(TAG, "Starting cast service");

        final Intent intent = new Intent(this, ScreenCastService.class);

        if(stateResultCode != 0 && stateResultData != null) {
            final Spinner videoFormatSpinner = (Spinner) findViewById(R.id.spinner_video_format);
            final Spinner videoResolutionSpinner = (Spinner) findViewById(R.id.spinner_video_resolution);
            final Spinner videoBitrateSpinner = (Spinner) findViewById(R.id.spinner_video_bitrate);

            int screenWidth = 0, screenHeight = 0, screenDpi = 0;
            if ( videoResolutionSpinner.getSelectedItem().toString() == "Native" ) {
                DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
                screenHeight = displayMetrics.heightPixels;
                screenWidth = displayMetrics.widthPixels;
                screenDpi = displayMetrics.densityDpi;
            } else {
                final String[] videoResolutions = getResources().getStringArray(R.array.options_resolution_values)[videoResolutionSpinner.getSelectedItemPosition()].split(",");
                screenWidth = Integer.parseInt(videoResolutions[0]);
                screenHeight = Integer.parseInt(videoResolutions[1]);
                screenDpi = Integer.parseInt(videoResolutions[2]);
            }

            final String videoFormat = getResources().getStringArray(R.array.options_format_values)[videoFormatSpinner.getSelectedItemPosition()];
            final int videoBitrate = getResources().getIntArray(R.array.options_bitrate_values)[videoBitrateSpinner.getSelectedItemPosition()];

            Log.i(TAG, "VideoFormat:" + videoFormat);
            Log.i(TAG, "Bitrate:" + videoBitrate);
            Log.i(TAG, "ScreenWidth:" + screenWidth);
            Log.i(TAG, "ScreenHeight:" + screenHeight);
            Log.i(TAG, "ScreenDpi:" + screenDpi);

            intent.putExtra(ExtraIntent.RESULT_CODE.toString(), stateResultCode);
            intent.putExtra(ExtraIntent.RESULT_DATA.toString(), stateResultData);
            intent.putExtra(ExtraIntent.IPADDR1_TX.toString(), ipaddr1_tx);
            intent.putExtra(ExtraIntent.IPADDR1_RX.toString(), ipaddr1_rx);
            intent.putExtra(ExtraIntent.IPADDR2_TX.toString(), ipaddr2_tx);
            intent.putExtra(ExtraIntent.IPADDR2_RX.toString(), ipaddr2_rx);
            intent.putExtra(ExtraIntent.MANIFEST_FILE.toString(), manifest_file);
            intent.putExtra(ExtraIntent.DURATION.toString(), duration);
            intent.putExtra(ExtraIntent.IPC_PORT.toString(), ipc_port);
            //
            intent.putExtra(ExtraIntent.VIDEO_FORMAT.toString(), videoFormat);
            intent.putExtra(ExtraIntent.SCREEN_WIDTH.toString(), screenWidth);
            intent.putExtra(ExtraIntent.SCREEN_HEIGHT.toString(), screenHeight);
            intent.putExtra(ExtraIntent.SCREEN_DPI.toString(), screenDpi);
            intent.putExtra(ExtraIntent.VIDEO_BITRATE.toString(), videoBitrate);
        }

        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void startCaptureScreen() {
        if (stateResultCode != 0 && stateResultData != null) {
            startService();
        } else {
            Log.d(TAG, "Requesting confirmation");
            startActivityForResult(
                    mediaProjectionManager.createScreenCaptureIntent(), ACTIVITY_RESULT_REQUEST_MEDIA_PROJECTION);
        }
    }

    private void stopScreenCapture() {
        if (serviceMessenger == null) {
            return;
        }

        Message msg = Message.obtain(null, ActivityServiceMessage.STOP);
        msg.replyTo = messenger;
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException:" + e.toString());
            e.printStackTrace();
        }
    }
}
