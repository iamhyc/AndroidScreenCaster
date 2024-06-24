package com.github.magicsih.androidscreencaster.service;

import android.content.res.AssetManager;

public class RustStreamReplay {
    private static native void start_tx(
            final AssetManager assets,
            final String manifest_file,
            final String ipaddr1_tx, final String ipaddr1_rx,
            final String ipaddr2_tx, final String ipaddr2_rx,
            final float duration,
            final int ipc_port
    );
    private static native void start_rx(
        final int port,
        final int duration,
        final boolean calc_rtt
    );

    private static native void send(final String name, final byte[] data);

    public static void startReplay(
            final AssetManager assets,
            String manifest_file, String ipaddr1_tx, String ipaddr1_rx, String ipaddr2_tx, String ipaddr2_rx, float duration, int ipc_port
    ) {
        start_tx(assets, manifest_file, ipaddr1_tx, ipaddr1_rx, ipaddr2_tx, ipaddr2_rx, duration, ipc_port);
    }

    public static void startReceiver(int port, int duration, boolean calc_rtt) {
        start_rx(port, duration, calc_rtt);
    }

    public static void sendData(String name, byte[] data) {
        send(name, data);
    }
}
