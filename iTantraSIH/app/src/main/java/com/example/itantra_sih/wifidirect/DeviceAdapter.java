package com.example.itantra_sih.wifidirect;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.itantra_sih.R;

import java.util.List;

public class DeviceAdapter extends ArrayAdapter<WifiP2pDevice> {

    public DeviceAdapter(@NonNull Context context, @NonNull List<WifiP2pDevice> devices) {
        super(context, 0, devices);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_device, parent, false);
        }

        WifiP2pDevice device = getItem(position);
        TextView tvDeviceName = convertView.findViewById(R.id.tvDeviceName);
        TextView tvDeviceDetails = convertView.findViewById(R.id.tvDeviceDetails);

        if (device != null) {
            String name = (device.deviceName != null && !device.deviceName.isEmpty())
                    ? device.deviceName : "Unknown Device";
            tvDeviceName.setText(name);

            String statusStr = getDeviceStatusString(device.status);
            tvDeviceDetails.setText(String.format("Status: %s | %s", statusStr, device.deviceAddress));
        }

        return convertView;
    }

    public static String getDeviceStatusString(int deviceStatus) {
        switch (deviceStatus) {
            case WifiP2pDevice.AVAILABLE:
                return "Available";
            case WifiP2pDevice.INVITED:
                return "Invited";
            case WifiP2pDevice.CONNECTED:
                return "Connected";
            case WifiP2pDevice.FAILED:
                return "Failed";
            case WifiP2pDevice.UNAVAILABLE:
                return "Unavailable";
            default:
                return "Unknown";
        }
    }
}
