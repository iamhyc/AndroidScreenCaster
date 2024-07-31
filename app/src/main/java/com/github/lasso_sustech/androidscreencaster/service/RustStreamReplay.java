package com.github.lasso_sustech.androidscreencaster.service;

import android.content.res.AssetManager;

// Reference: https://stackoverflow.com/questions/32470463/what-is-the-naming-convention-for-java-native-interface-method-and-module-name
public class RustStreamReplay {
    private static final String TAG = "RustStreamReplay";

    private static native void startTx(
            final AssetManager assets,
            final String manifest_file,
            final String ipaddr1_tx, final String ipaddr1_rx,
            final String ipaddr2_tx, final String ipaddr2_rx,
            final double duration,
            final int ipc_port
    );
    private static native void startRx(
        final int port,
        final int duration,
        final boolean calc_rtt,
        final boolean rx_mode,
        final String src_ipaddrs
    );
    private static native void send(final String name, final byte[] data);
    private static native byte[] recv(final int port);

    private static native void start_controller(final String target_uri);
    private static native void set_controller_status(final boolean status);

    public static void startReplay(
            final AssetManager assets,
            String manifest_file, String ipaddr1_tx, String ipaddr1_rx, String ipaddr2_tx, String ipaddr2_rx, double duration, int ipc_port
    ) {
        manifest_file = "manifest/" + manifest_file;
        startTx(assets, manifest_file, ipaddr1_tx, ipaddr1_rx, ipaddr2_tx, ipaddr2_rx, duration, ipc_port);
    }

    public static void startReceiver(int port, int duration, boolean calc_rtt, boolean rx_mode, String src_ipaddrs) {
        startRx(port, duration, calc_rtt, rx_mode, src_ipaddrs);
    }

    public static void sendData(String name, byte[] data) {
        send(name, data);
    }

    public static byte[] recvData(int port) {
        return recv(port);
    }

    public static void startController(String target_uri) {
        start_controller(target_uri);
    }

    public static void setControllerStatus(boolean status) {
        set_controller_status(status);
    }

}
