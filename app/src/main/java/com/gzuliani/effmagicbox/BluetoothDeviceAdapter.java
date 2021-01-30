package com.gzuliani.effmagicbox;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class BluetoothDeviceAdapter extends ArrayAdapter<BluetoothDevice> {
    public BluetoothDeviceAdapter(Context context, int textViewResourceId, BluetoothDevice [] objects) {
        super(context, textViewResourceId, objects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater)getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        convertView = inflater.inflate(R.layout.bluetooth_device, null);
        TextView name = convertView.findViewById(R.id.name);
        TextView address = convertView.findViewById(R.id.address);
        BluetoothDevice device = getItem(position);
        name.setText(device.getName());
        address.setText(device.getAddress());
        return convertView;
    }
}
