package com.tuandat.appgamebluetooth;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

public class MainActivity extends AppCompatActivity {


    private TextView status;
    private Button btnConnect, btnSend;
    private ImageView imgvMyCmd, imgvOpponentCmd;

    private Dialog dialog;


    private BluetoothAdapter bluetoothAdapter;

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_OBJECT = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final String DEVICE_OBJECT = "device_name";

    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final int DISCOVERABLE_DURATION_VALUE = 60;
    
    private GameController gameController;
    private BluetoothDevice connectingDevice;
    private ArrayAdapter<String> discoveredDevicesAdapter;

    public enum COMMAND {
        ROCK,
        PAPER,
        SCISSORS,
        NONE
    }

    public enum RESULT {
        WIN,
        LOSE,
        DRAW
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        findViewsByIds();

        //check device support bluetooth or not
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available!", Toast.LENGTH_SHORT).show();
            finish();
        }

        //show bluetooth devices dialog when click connect button
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPrinterPickDialog();
            }
        });

        //set chat adapter


    }

    private Handler handler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case GameController.STATE_CONNECTED:
                            setStatus("Connected to: " + connectingDevice.getName());
                            btnConnect.setEnabled(false);
                            break;
                        case GameController.STATE_CONNECTING:
                            setStatus("Connecting...");
                            btnConnect.setEnabled(false);
                            break;
                        case GameController.STATE_LISTEN:
                        case GameController.STATE_NONE:
                            setStatus("Not connected");
                            btnConnect.setEnabled(true);
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;

                    String writeMessage = new String(writeBuf);
                    btnSend.setEnabled(false);
                    isSent = true;
                    GameProcessing();
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;

                    String readMessage = new String(readBuf, 0, msg.arg1);
                    if (readMessage.equals(COMMAND.ROCK.toString())) {
                        opponentCmd = COMMAND.ROCK;
                    } else if (readMessage.equals(COMMAND.PAPER.toString())) {
                        opponentCmd = COMMAND.PAPER;
                    } else if (readMessage.equals(COMMAND.SCISSORS.toString())) {
                        opponentCmd = COMMAND.SCISSORS;
                    }
                    GameProcessing();
                    break;
                case MESSAGE_DEVICE_OBJECT:
                    connectingDevice = msg.getData().getParcelable(DEVICE_OBJECT);
                    Toast.makeText(getApplicationContext(), "Connected to " + connectingDevice.getName(),
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString("toast"),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
            return false;
        }
    });

    private void showPrinterPickDialog() {
        dialog = new Dialog(this);
        dialog.setContentView(R.layout.layout_bluetooth);
        dialog.setTitle("Bluetooth Devices");

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();

        //Initializing bluetooth adapters
        ArrayAdapter<String> pairedDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        discoveredDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        //locate listviews and attatch the adapters
        ListView listView = (ListView) dialog.findViewById(R.id.pairedDeviceList);
        ListView listView2 = (ListView) dialog.findViewById(R.id.discoveredDeviceList);
        listView.setAdapter(pairedDevicesAdapter);
        listView2.setAdapter(discoveredDevicesAdapter);

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(discoveryFinishReceiver, filter);

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(discoveryFinishReceiver, filter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                pairedDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            pairedDevicesAdapter.add(getString(R.string.none_paired));
        }

        //Handling listview item click event
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                bluetoothAdapter.cancelDiscovery();
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);

                connectToDevice(address);
                dialog.dismiss();
            }

        });

        listView2.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                bluetoothAdapter.cancelDiscovery();
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);

                connectToDevice(address);
                dialog.dismiss();
            }
        });

        dialog.findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    private void connectToDevice(String deviceAddress) {
        bluetoothAdapter.cancelDiscovery();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        gameController.connect(device);
    }

    private void findViewsByIds() {
        status = (TextView) findViewById(R.id.status);
        btnConnect = (Button) findViewById(R.id.btn_connect);
        imgvMyCmd = (ImageView) findViewById(R.id.imgvMyCmd);
        imgvOpponentCmd = (ImageView) findViewById(R.id.imgvOpponentCmd);
        btnSend = findViewById(R.id.btn_send);

        imgvMyCmd.setBackgroundResource(R.layout.shape_border);
        imgvOpponentCmd.setBackgroundResource(R.layout.shape_border);

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (myCmd.equals(COMMAND.NONE)) {
                    Toast.makeText(MainActivity.this, "Please choose an icon", Toast.LENGTH_SHORT).show();
                } else {
                    //TODO: here
                    sendMessage(myCmd);

                }
            }
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BLUETOOTH:
                if (resultCode == Activity.RESULT_OK) {
                    gameController = new GameController(this, handler);
                } else {
                    Toast.makeText(this, "Bluetooth still disabled, turn off application!", Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    private void sendMessage(COMMAND command) {
        if (gameController.getState() != GameController.STATE_CONNECTED) {
            Toast.makeText(this, "Connection was lost!", Toast.LENGTH_SHORT).show();
            return;
        }
        String message = command.toString();
        if (message.length() > 0) {
            byte[] send = message.getBytes();
            gameController.write(send);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        } else {
            gameController = new GameController(this, handler);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (gameController != null) {
            if (gameController.getState() == GameController.STATE_NONE) {
                gameController.start();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (gameController != null)
            gameController.stop();
    }

    private final BroadcastReceiver discoveryFinishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    discoveredDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (discoveredDevicesAdapter.getCount() == 0) {
                    discoveredDevicesAdapter.add(getString(R.string.none_found));
                }
            }
        }
    };

    protected void makeDiscoverable() {
        // Make local device discoverable
        Intent discoverableIntent = new
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION_VALUE);
        startActivityForResult(discoverableIntent, 2);
    }


    public void btnShowDevice_Click(View view) {
        makeDiscoverable();
    }




    boolean isSent = false;

    private COMMAND myCmd = COMMAND.NONE, opponentCmd = COMMAND.NONE;

    public void Paper_Click(View view) {
        myCmd = COMMAND.PAPER;
        ImageView imgv = (ImageView) view;
        imgvMyCmd.setImageResource(R.drawable.baoimage);
        imgvOpponentCmd.setImageResource(android.R.color.transparent);
    }

    public void Rock_Click(View view) {
        myCmd = COMMAND.ROCK;
        ImageView imgv = (ImageView) view;
        imgvMyCmd.setImageResource(R.drawable.buaimage);
        imgvOpponentCmd.setImageResource(android.R.color.transparent);
    }

    public void Scissors_Click(View view) {
        myCmd = COMMAND.SCISSORS;
        ImageView imgv = (ImageView) view;
        imgvMyCmd.setImageResource(R.drawable.keoimage);
        imgvOpponentCmd.setImageResource(android.R.color.transparent);
    }

    RESULT CheckResult() {
        RESULT result;

        if (myCmd.equals(opponentCmd))
            result = RESULT.DRAW;
        else if (myCmd.equals(COMMAND.ROCK) && opponentCmd.equals(COMMAND.SCISSORS))
            result = RESULT.WIN;
        else if (myCmd.equals(COMMAND.SCISSORS) && opponentCmd.equals(COMMAND.PAPER))
            result = RESULT.WIN;
        else if (myCmd.equals(COMMAND.PAPER) && opponentCmd.equals(COMMAND.ROCK))
            result = RESULT.WIN;
        else
            result = RESULT.LOSE;
        myCmd = opponentCmd = COMMAND.NONE;
        return result;
    }

    void GameProcessing() {
        if (!isSent || opponentCmd.equals(COMMAND.NONE))
            return;
        switch (opponentCmd) {
            case PAPER:
                imgvOpponentCmd.setImageResource(R.drawable.baoimage);
                break;
            case SCISSORS:
                imgvOpponentCmd.setImageResource(R.drawable.keoimage);
                break;
            case ROCK:
                imgvOpponentCmd.setImageResource(R.drawable.buaimage);
                break;
        }
        RESULT result = CheckResult();
        Log.e("GameProcessing", result.name());
        switch (result) {
            case WIN:

                Toast.makeText(this, "YOU WON!", Toast.LENGTH_LONG).show();
                break;
            case LOSE:

                Toast.makeText(this, "YOU LOST!", Toast.LENGTH_LONG).show();
                break;
            case DRAW:

                Toast.makeText(this, "YOU DREW!", Toast.LENGTH_LONG).show();
                break;
        }
        isSent = false;
        btnSend.setEnabled(true);

    }
}
