package com.example.itantra_sih.wifidirect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Parcelable;
import android.util.Log;

import java.util.Collection;

public class WifiDirectBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "WifiDirectReceiver";

    public interface WifiDirectEventListener {
        void onWifiDirectEnabled(boolean isEnabled);
        void onPeersDiscovered(Collection<WifiP2pDevice> peerList);
        void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo);
        void onDisconnected();
        void onThisDeviceChanged(WifiP2pDevice device);
    }

    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final WifiDirectEventListener listener;

    public WifiDirectBroadcastReceiver(WifiP2pManager manager,
                                       WifiP2pManager.Channel channel,
                                       WifiDirectEventListener listener) {
        this.manager = manager;
        this.channel = channel;
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                boolean isEnabled = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED);
                Log.d(TAG, "WIFI_P2P_STATE_CHANGED: isEnabled=" + isEnabled);
                if (listener != null) {
                    listener.onWifiDirectEnabled(isEnabled);
                }
                break;

            case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                Log.d(TAG, "WIFI_P2P_PEERS_CHANGED");
                if (manager != null && channel != null) {
                    try {
                        manager.requestPeers(channel, peers -> {
                            if (listener != null && peers != null) {
                                listener.onPeersDiscovered(peers.getDeviceList());
                            }
                        });
                    } catch (SecurityException e) {
                        Log.e(TAG, "SecurityException requesting peers", e);
                    }
                }
                break;

            case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                Log.d(TAG, "WIFI_P2P_CONNECTION_CHANGED");
                if (manager != null && channel != null) {
                    NetworkInfo networkInfo = getParcelableExtra(
                            intent, WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo.class);
                    if (networkInfo != null && networkInfo.isConnected()) {
                        manager.requestConnectionInfo(channel, info -> {
                            if (listener != null && info != null) {
                                listener.onConnectionInfoAvailable(info);
                            }
                        });
                    } else if (listener != null) {
                        listener.onDisconnected();
                    }
                }
                break;

            case WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION:
                WifiP2pDevice device = getParcelableExtra(
                        intent, WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice.class);
                Log.d(TAG, "WIFI_P2P_THIS_DEVICE_CHANGED: " + (device != null ? device.deviceName : "null"));
                if (listener != null && device != null) {
                    listener.onThisDeviceChanged(device);
                }
                break;
        }
    }

    @SuppressWarnings("deprecation")
    private static <T extends Parcelable> T getParcelableExtra(Intent intent, String key, Class<T> type) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(key, type);
        }
        return intent.getParcelableExtra(key);
    }
}
