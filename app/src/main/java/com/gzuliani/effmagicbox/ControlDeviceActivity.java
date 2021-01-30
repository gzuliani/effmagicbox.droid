package com.gzuliani.effmagicbox;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;

public class ControlDeviceActivity extends AppCompatActivity {

    final private static int BT_TIMEOUT = 250; // ms
    final private static int POLLING_PERIOD = 1000; // ms

    private BluetoothConnection connection;

    private TextView outputPane;

    private ImageButton leftTurnButton;
    private ImageButton rightTurnButton;
    private ImageButton leftParkingButton;
    private ImageButton rightParkingButton;
    private ImageButton brakesLightButton;
    private ImageButton rearFogButton;
    private ImageButton reverseGearButton;
    private ImageButton batteryButton;
    private Button connectButton;

    // device lines - order matters (see Arduino sketch)!
    int[] lines = {
        R.id.left_parking_button,
        R.id.right_parking_button,
        R.id.left_turn_button,
        R.id.right_turn_button,
        R.id.reverse_gear_button,
        R.id.rear_fog_button,
        R.id.brakes_light_button,
        R.id.battery_button
    };

    // line levels
    HashMap<Integer, Boolean> lineStatus = new HashMap<>();

    // device status
    private enum DeviceStatus {
        UNKNOWN,
        READ,
        WRITE,
    }

    private DeviceStatus deviceStatus = DeviceStatus.UNKNOWN;

    // device polling support
    private final Handler devicePollingScheduler = new Handler();
    private final int devicePolingDelay = POLLING_PERIOD; // milliseconds
    Runnable devicePollingProcess = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control_device);

        Intent intent = getIntent();
        BluetoothDevice device = intent.getExtras().getParcelable("device");

        TextView address = findViewById(R.id.device_address);
        address.setText(getResources().getString(
                R.string.device_id, device.getName(), device.getAddress()));

        outputPane = findViewById(R.id.output_pane);

        leftTurnButton = findViewById(R.id.left_turn_button);
        rightTurnButton = findViewById(R.id.right_turn_button);
        leftParkingButton = findViewById(R.id.left_parking_button);
        rightParkingButton = findViewById(R.id.right_parking_button);
        brakesLightButton = findViewById(R.id.brakes_light_button);
        rearFogButton = findViewById(R.id.rear_fog_button);
        reverseGearButton = findViewById(R.id.reverse_gear_button);
        batteryButton = findViewById(R.id.battery_button);

        connectButton = findViewById(R.id.button_connect);
        connectButton.setText(R.string.button_connect_connect);

        try {
            connection = new BluetoothConnection(device.getAddress());
        } catch (Exception e) {
            outputPane.setText(getResources().getString(R.string.device_error, e.toString()));
        }

        // initialize line levels to `off`
        for (int i : lines)
            lineStatus.put(i, false);

        devicePollingScheduler.postDelayed(devicePollingProcess = () -> {
            if (!connection.isOpen())
                outputPane.setText(R.string.device_disconnected);
            else {
                DeviceStatus newDeviceStatus = peekDeviceStatus();

                // is device status changed?
                if (newDeviceStatus != deviceStatus) {
                    deviceStatus = newDeviceStatus;

                    switch (deviceStatus) {
                        case READ:
                            outputPane.setText(R.string.device_status_read);
                            break;
                        case WRITE:
                            outputPane.setText(R.string.device_status_write);
                            lineStatus.put(R.id.battery_button, false);
                            updateButtonIcons();
                            setLineLevels();
                            break;
                        case UNKNOWN:
                            outputPane.setText(R.string.device_status_unknown);
                            break;
                    }
                }

                // refresh the buttons when in READ mode
                if (deviceStatus == DeviceStatus.READ) {
                    acquireLineLevels();
                    updateButtonIcons();
                }
            }

            // schedule next polling
            devicePollingScheduler.postDelayed(devicePollingProcess, devicePolingDelay);
        }, devicePolingDelay);
    }

    public void leftTurnButtonClicked(View v) {
        buttonClicked(leftTurnButton, R.drawable.left_turn_on, R.drawable.left_turn_off);
    }

    public void rightTurnButtonClicked(View v) {
        buttonClicked(rightTurnButton, R.drawable.right_turn_on, R.drawable.right_turn_off);
    }

    public void leftParkingButtonClicked(View v) {
        buttonClicked(leftParkingButton, R.drawable.left_parking_on, R.drawable.left_parking_off);
    }

    public void rightParkingButtonClicked(View v) {
        buttonClicked(rightParkingButton, R.drawable.right_parking_on, R.drawable.right_parking_off);
    }

    public void brakesLightButtonClicked(View v) {
        buttonClicked(brakesLightButton, R.drawable.brakes_light_on, R.drawable.brakes_light_off);
    }

    public void rearFogButtonClicked(View v) {
        buttonClicked(rearFogButton, R.drawable.rear_fog_on, R.drawable.rear_fog_off);
    }

    public void reverseGearButtonClicked(View v) {
        buttonClicked(reverseGearButton, R.drawable.reverse_gear_on, R.drawable.reverse_gear_off);
    }

    public void connect(View v) {
        try {
            if (connection.isOpen()) {
                connection.close();
                outputPane.setText(R.string.device_disconnected);
            }
            else {
                connection.open();
                outputPane.setText(R.string.device_connected);
            }
        } catch (Exception e) {
            outputPane.setText(getResources().getString(R.string.device_error, e.toString()));
        }
        connectButton.setText(connection.isOpen()
                ? R.string.button_connect_disconnect
                : R.string.button_connect_connect);
    }

    private void buttonClicked(ImageButton button, int onIcon, int offIcon) {
        if (deviceStatus != DeviceStatus.WRITE)
            return;
        lineStatus.put(button.getId(), !lineStatus.get(button.getId()));
        updateButtonIcon(button, onIcon, offIcon);
        setLineLevels();
    }

    private void updateButtonIcon(ImageButton button, int onIcon, int offIcon) {
        if (lineStatus.get(button.getId()))
            button.setImageResource(onIcon);
        else
            button.setImageResource(offIcon);
    }

    synchronized void acquireLineLevels() {
        send("R.");
        String levels = receive(8);

        if (levels.length() != 8)
            return;

        // update line levels
        for (int i = 0; i < lines.length; i++)
            lineStatus.put(lines[i], levels.charAt(i) == 'H');
    }

    synchronized void setLineLevels() {
        // build the command string
        StringBuilder command = new StringBuilder("W");

        for (int line : lines)
            command.append(lineStatus.get(line) ? "H" : "L");

        command.append(".");

        Log.d("ControlDeviceActivity", String.format("Sending command `%s`...", command.toString()));
        send(command.toString());

        String response = receive(3);
        Log.d("ControlDeviceActivity", String.format("Received response `%s`", response));

        if (!response.equals("OK"))
            ; // not clear what to to here...
    }

    private void updateButtonIcons() {
        updateButtonIcon(leftTurnButton, R.drawable.left_turn_on, R.drawable.left_turn_off);
        updateButtonIcon(rightTurnButton, R.drawable.right_turn_on, R.drawable.right_turn_off);
        updateButtonIcon(leftParkingButton, R.drawable.left_parking_on, R.drawable.left_parking_off);
        updateButtonIcon(rightParkingButton, R.drawable.right_parking_on, R.drawable.right_parking_off);
        updateButtonIcon(brakesLightButton, R.drawable.brakes_light_on, R.drawable.brakes_light_off);
        updateButtonIcon(rearFogButton, R.drawable.rear_fog_on, R.drawable.rear_fog_off);
        updateButtonIcon(reverseGearButton, R.drawable.reverse_gear_on, R.drawable.reverse_gear_off);
        updateButtonIcon(batteryButton, R.drawable.battery_on, R.drawable.battery_off);
    }

    synchronized DeviceStatus peekDeviceStatus() {
        if (!connection.isOpen())
            return DeviceStatus.UNKNOWN;

        Log.d("ControlDeviceActivity", "Sending command `M.`...");
        send("M.");
        String mode = receive(1);
        Log.d("ControlDeviceActivity", String.format("Received response `%s`", mode));

        if (mode.equals("R"))
            return DeviceStatus.READ;
        else if (mode.equals("W"))
            return DeviceStatus.WRITE;
        else
            return DeviceStatus.UNKNOWN;
    }

    private String receive(int bytesToRead) {
        String response = "";
        if (!connection.isOpen())
            return response;
        try{
            response = new String(connection.receive(bytesToRead, BT_TIMEOUT));
        } catch (Exception ignored) {
        }
        return response;
    }

    private void send(String command) {
        try{
            connection.send(command.getBytes());
        } catch (Exception ignored) {
        }
    }
}
