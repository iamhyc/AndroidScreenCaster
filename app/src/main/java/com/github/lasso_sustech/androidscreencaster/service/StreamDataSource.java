package com.github.lasso_sustech.androidscreencaster.service;


import static androidx.media3.common.C.LENGTH_UNSET;

import android.net.Uri;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.nio.ByteBuffer;

// Reference: https://github.com/google/ExoPlayer/issues/4212
// Reference: https://stackoverflow.com/questions/61176355/android-exoplayer-direct-feed-raw-mp4-h264-fragments
// Reference: https://developer.android.com/reference/androidx/media3/common/DataReader#read(byte[],int,int)
@UnstableApi
public class StreamDataSource implements DataSource {

    private int port;
    private ByteBuffer buffer = ByteBuffer.allocate(0);

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
        // recv and append to the buffer
        byte[] recvData = RustStreamReplay.recvData(port);
        this.buffer.put(recvData);

        // copy the buffer to the output buffer
        int length = Math.min(readLength, this.buffer.remaining());
        this.buffer.get(buffer, offset, length);
        return length;
    }

}
