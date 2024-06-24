package com.github.magicsih.androidscreencaster.service;

import android.content.res.AssetManager;

public class RustStreamReplay {
    private static native void start(
            final AssetManager assets,
            final String manifest_file,
            final String ipaddr1,
            final String ipaddr2,
            final float duration,
            final int ipc_port
    );

    private static native void send(final String name, final byte[] data);

    public static void startReplay(
            final AssetManager assets,
            String manifest_file, String ipaddr1, String ipaddr2, float duration, int ipc_port
    ) {
        start(assets, manifest_file, ipaddr1, ipaddr2, duration, ipc_port);
    }

    public static void sendData(String name, byte[] data) {
        send(name, data);
    }
}
