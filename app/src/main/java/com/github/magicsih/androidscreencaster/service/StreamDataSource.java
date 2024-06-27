package com.github.magicsih.androidscreencaster.service;


import static androidx.media3.common.C.LENGTH_UNSET;

import android.net.Uri;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;

// Reference: https://github.com/google/ExoPlayer/issues/4212
// Reference: https://stackoverflow.com/questions/61176355/android-exoplayer-direct-feed-raw-mp4-h264-fragments
// Reference: https://developer.android.com/reference/androidx/media3/common/DataReader#read(byte[],int,int)
@UnstableApi
public class StreamDataSource implements DataSource {

    @Override
    public void addTransferListener(TransferListener transferListener) {

    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
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
        return 0;
    }

}
