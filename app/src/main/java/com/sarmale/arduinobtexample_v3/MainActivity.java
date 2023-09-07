package com.sarmale.arduinobtexample_v3;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {
    // Global variables we will use in the
    private static final String TAG = "FrugalLogs";
    private static final int REQUEST_ENABLE_BT = 1;

    private static final int REQUEST_CODE = 22;

    Button btnpicture;

    ImageView imageView;

    ActivityResultLauncher<Intent> activityResultLauncher;

    //We will use a Handler to get the BT Connection statys
    public static Handler handler;
    private final static int ERROR_READ = 0; // used in bluetooth handler to identify message update
    BluetoothDevice arduinoBTModule = null;

    ProgressBar loadingProgressBar;

    UUID arduinoUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //We declare a default UUID to create the global variable

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Intances of BT Manager and BT Adapter needed to work with BT in Android.
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        //Intances of the Android UI elements that will will use during the execution of the APP
        TextView btReadings = findViewById(R.id.btReadings);
        Button connectToDevice = (Button) findViewById(R.id.connectToDevice);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        Button clearValues = (Button) findViewById(R.id.refresh);
        btnpicture = findViewById(R.id.btncamera_id);
        imageView = findViewById(R.id.image);
        Log.d(TAG, "Begin Execution");


        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {

                    case ERROR_READ:
                        String arduinoMsg = msg.obj.toString(); // Read message from Arduino
                        btReadings.setText(arduinoMsg);
                        break;
                }
            }
        };

        // Set a listener event on a button to clear the texts
        clearValues.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btReadings.setText("");
                imageView.setImageDrawable(null);
            }
        });

        // Create an Observable from RxAndroid
        //The code will be executed when an Observer subscribes to the the Observable
        final Observable<String> connectToBTObservable = Observable.create(emitter -> {
            Log.d(TAG, "Calling connectThread class");
            //Call the constructor of the ConnectThread class
            //Passing the Arguments: an Object that represents the BT device,
            // the UUID and then the handler to update the UI
            ConnectThread connectThread = new ConnectThread(arduinoBTModule, arduinoUUID, handler);
            connectThread.run();
            //Check if Socket connected
            if (connectThread.getMmSocket().isConnected()) {
                Log.d(TAG, "Calling ConnectedThread class");
                //The pass the Open socket as arguments to call the constructor of ConnectedThread
                ConnectedThread connectedThread = new ConnectedThread(connectThread.getMmSocket());
                connectedThread.run();
                if (connectedThread.getValueRead() != null) {
                    // If we have read a value from the Arduino
                    // we call the onNext() function
                    //This value will be observed by the observer
                    emitter.onNext(connectedThread.getValueRead());
                }
                //We just want to stream 1 value, so we close the BT stream
                connectedThread.cancel();
            }
            // SystemClock.sleep(5000); // simulate delay
            //Then we close the socket connection
            connectThread.cancel();
            //We could Override the onComplete function
            emitter.onComplete();

        });


        connectToDevice.setOnClickListener(new View.OnClickListener() {
            // Display all the linked BT Devices
            @Override
            public void onClick(View view) {
                loadingProgressBar.setVisibility(View.VISIBLE);
                // Check if the phone supports BT
                if (bluetoothAdapter == null) {
                    Log.d(TAG, "Device doesn't support Bluetooth");
                    return; // Exit early if Bluetooth is not supported
                }

                Log.d(TAG, "Device supports Bluetooth");

                // Check BT enabled. If disabled, we ask the user to enable BT
                if (!bluetoothAdapter.isEnabled()) {
                    Log.d(TAG, "Bluetooth is disabled");
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // Request Bluetooth permissions first if not granted
                        Log.d(TAG, "Before requesting Bluetooth permissions");
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_ENABLE_BT);
                        Log.d(TAG, "Requesting Bluetooth permissions");
                        Log.d(TAG, "After requesting Bluetooth permissions");
                        // After requesting permissions, the user will be prompted to enable Bluetooth
                    } else {
                        // We have Bluetooth permissions, so directly request enabling Bluetooth
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                        Log.d(TAG, "Bluetooth is enabled now");
                    }
                } else {
                    // Bluetooth is already enabled, check permissions
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_ENABLE_BT);
                        Log.d(TAG, "Requesting Bluetooth permissions");
                    } else {
                        // Bluetooth is enabled, and we have permissions, proceed to search devices
                        Log.d(TAG, "Bluetooth is enabled");
                        String btDevicesString = "";
                        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

                        if (pairedDevices.size() > 0) {
                            // There are paired devices. Get the name and address of each paired device.
                            for (BluetoothDevice device : pairedDevices) {
                                String deviceName = device.getName();
                                String deviceHardwareAddress = device.getAddress(); // MAC address
                                Log.d(TAG, "deviceName:" + deviceName);
                                Log.d(TAG, "deviceHardwareAddress:" + deviceHardwareAddress);
                                //We append all devices to a String that we will display in the UI
                                btDevicesString = btDevicesString + deviceName + " || " + deviceHardwareAddress + "\n";
                                //If we find the HC 05 device (the Arduino BT module)
                                //We assign the device value to the Global variable BluetoothDevice
                                //We enable the button "Connect to HC 05 device"
                                if (deviceName.equals("HC-05")) {
                                    Log.d(TAG, "HC-05 found");
                                    arduinoUUID = device.getUuids()[0].getUuid();
                                    arduinoBTModule = device;
                                    btReadings.setText("");
                                    if (arduinoBTModule != null) {
                                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_ENABLE_BT);
                                        } else {
                                            connectToBTObservable
                                                    .observeOn(AndroidSchedulers.mainThread())
                                                    .subscribeOn(Schedulers.io())
                                                    .subscribe(valueRead -> {
                                                        btReadings.setText(valueRead);
                                                        loadingProgressBar.setVisibility(View.INVISIBLE);
                                                        clearValues.setVisibility(View.VISIBLE);
                                                    });
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Log.d(TAG, "Button Pressed");
                }
            }
        });

        btnpicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                activityResultLauncher.launch(cameraIntent);
            }
        });

        activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {

                Bundle extras = result.getData().getExtras();
                Uri imageUri;

                Bitmap imageBitmap = (Bitmap) extras.get("data");

                WeakReference<Bitmap> result_1 = new WeakReference<>(Bitmap.createScaledBitmap(imageBitmap,

                                imageBitmap.getWidth(), imageBitmap.getHeight(), false).

                        copy(Bitmap.Config.RGB_565, true));

                Bitmap bm = result_1.get();

                imageUri = saveImage(bm, MainActivity.this);

                imageView.setImageURI(imageUri);
                clearValues.setVisibility(View.VISIBLE);
            }
        });

    }

    private Uri saveImage(Bitmap image, MainActivity context) {

        File imagefolder = new File(context.getCacheDir(), "images");
        Uri uri = null;

        try {
            imagefolder.mkdirs();
            File file = new File(imagefolder, "captured_image.jpg");
            FileOutputStream stream = new FileOutputStream(file);
            image.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            stream.flush();
            stream.close();
            uri = FileProvider.getUriForFile(context.getApplicationContext(), "com.sarmale.arduinobtexample_v3.provider", file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return uri;

    }

}