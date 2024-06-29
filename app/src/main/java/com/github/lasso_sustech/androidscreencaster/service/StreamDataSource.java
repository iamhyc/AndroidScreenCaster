package com.github.lasso_sustech.androidscreencaster.service;

import android.net.Uri;
import android.util.Log;
import static androidx.media3.common.C.LENGTH_UNSET;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

// Reference: https://github.com/google/ExoPlayer/issues/4212
// Reference: https://stackoverflow.com/questions/61176355/android-exoplayer-direct-feed-raw-mp4-h264-fragments
// Reference: https://developer.android.com/reference/androidx/media3/common/DataReader#read(byte[],int,int)
@UnstableApi
public class StreamDataSource implements DataSource {

    private int port;
    private Queue<Byte> buffer = new LinkedList<>();

    @Override
    public void addTransferListener(TransferListener transferListener) {}

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        this.port = dataSpec.uri.getPort();
        return LENGTH_UNSET;
    }

    @Override
    public void close() throws IOException {}

    @Override
    public Uri getUri() {
        return Uri.EMPTY;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (this.buffer.size() < readLength) {
            // recv and append to the buffer
            byte[] recvData = RustStreamReplay.recvData(port);
            for (byte b : recvData) {
                this.buffer.add(b);
            }
        }

        // copy the buffer to the output buffer
        int length = Math.min(readLength, this.buffer.size());
        for (int i = 0; i < length; i++) {
            buffer[offset + i] = this.buffer.poll();
        }
        
        // Log.d("StreamDataSource", "Read " + length + " bytes with offset " + offset);

        return length;
    }

}
