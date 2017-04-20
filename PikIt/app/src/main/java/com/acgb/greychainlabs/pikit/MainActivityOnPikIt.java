package com.acgb.greychainlabs.pikit;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public class MainActivityOnPikIt extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    public BluetoothAdapter blAdapter;
    public static OutputStream outStream = null;
    BluetoothAdapter bAdapter = null;
    public BluetoothSocket mSocket = null;
    public BluetoothDevice mDevice = null;
    public TextView conStatus;
    public ImageView imgStatus;
    public ImageButton button;
    // setting up the process in order
    public int status;

    // SPP UUID service
    public static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static String mac_address = "98:D3:35:00:A6:A9";
    static byte[] msgBuffer = new byte[1024];


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // setting up the xml
        setContentView(R.layout.activity_main_on_pik_it);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // floating action button
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // diaplaying the default bluetooth status not connected
        conStatus = (TextView) findViewById(R.id.StatusTextView);
        conStatus.setText("Not Connected");

        // changing the color of the wheel
        Resources res = getResources();
        Drawable imgDrawableStatus = res.getDrawable(R.drawable.red);
        imgStatus = (ImageView) findViewById(R.id.imageView2);
        imgStatus.setImageDrawable(imgDrawableStatus);

        // calling the bluetooth connectivity method
        bluetoothConnectivity();


        IntentFilter filter1 = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothStatus, filter1);
        registerReceiver(pairingStatus, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(connectionState, filter);

    }

    // calling the following methods
    public void bluetoothConnectivity() {
        blAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothOn();
        pairedWallet();

    }

    // checking whether bluetooth is enabled
    public void bluetoothOn() {
        status = 2;
        if (!blAdapter.isEnabled()) {
            Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnOn, 0);

            Toast.makeText(getApplicationContext(), "Turning on the bluetooth", Toast.LENGTH_LONG).show();


        } else {
            Toast.makeText(getApplicationContext(), "Bluetooth is on", Toast.LENGTH_LONG).show();

        }

    }

    // pairing with the wallet
    public void pairWallet(BluetoothDevice device) {
        try {
            status = 3;
            Method method = device.getClass().getMethod("createBond", (Class[]) null); //pairing the bluetooth
            method.invoke(device, (Object[]) null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // showing the list of paired devices
    // if the wallet is not paired it will manually pair
    public void pairedWallet() {

        int count = 0;

        try {
            if (blAdapter.isEnabled()) {
                bAdapter = BluetoothAdapter.getDefaultAdapter();
                Set<BluetoothDevice> pairedDevice = bAdapter.getBondedDevices(); //list of paired device

                if (pairedDevice.size() > 0) { //list to check the paired devices

                    for (BluetoothDevice device : pairedDevice) {// searching for HC-06

                        if (device.getAddress().equals("98:D3:35:00:A6:A9")) {
                            count = 1;
                            Log.i("count", "1");
                            // calling the connecting methods
                            createSocket();
                            connectToWallet();
                        }
                    }

                }
                if (count == 0) { // HC-06 isn't paired
                    Log.i("Error", "Connect to your wallet");
                    Toast.makeText(getApplicationContext(), "You are pairing with the wallet", Toast.LENGTH_LONG).show();
                    BluetoothDevice device = bAdapter.getRemoteDevice("98:D3:35:00:A6:A9");//selecting the HC-06
                    pairWallet(device);// pairing the Hc-06

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // creating a socket
    public void createSocket() {

        BluetoothDevice device = bAdapter.getRemoteDevice("98:D3:35:00:A6:A9"); //declaring our device

        BluetoothSocket tmp = null; // creating a socket
        mDevice = device;

        try {

            tmp = device.createRfcommSocketToServiceRecord(myUUID); // assigning the UUID to the socket

        } catch (IOException e) {
            Log.e("Error", "Socket's create() method failed", e);
        }
        mSocket = tmp;

    }

    // connecting to the wallet using bluetooth
    public void connectToWallet() {

        // cancel discovery of the all bluetooth adaptors
        bAdapter.cancelDiscovery();
        blAdapter.cancelDiscovery();

        try {
            // Connect to the remote device through the socket. This call blocks
            // until it succeeds or throws an exception.
            mSocket.connect();
            conStatus = (TextView) findViewById(R.id.StatusTextView);
            conStatus.setText("Connected");

            outStream = mSocket.getOutputStream();

            Resources res = getResources();
            Drawable imgDrawableStatus = res.getDrawable(R.drawable.green);
            imgStatus = (ImageView) findViewById(R.id.imageView2);
            imgStatus.setImageDrawable(imgDrawableStatus);

        } catch (IOException connectException) {
            // Unable to connect; close the socket and return.
            try {
                mSocket.close();

            } catch (IOException closeException) {
                Log.e("Error", "Could not close the client socket", closeException);
            }
            return;
        }

    }

    // identifying the states of bluetooth in the device
    private final BroadcastReceiver bluetoothStatus = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            Resources res = getResources();
            Drawable imgDrawableStatus1 = res.getDrawable(R.drawable.red);

            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                switch (state) {
                    // if the state is off
                    case BluetoothAdapter.STATE_OFF:
                        Toast.makeText(getApplicationContext(), "OFF", Toast.LENGTH_LONG).show();
                        conStatus = (TextView) findViewById(R.id.StatusTextView);
                        conStatus.setText("Not Connected");

                        imgStatus = (ImageView) findViewById(R.id.imageView2);
                        imgStatus.setImageDrawable(imgDrawableStatus1);
                        notificationView();

                        break;

                    // if the state is turning off
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Toast.makeText(getApplicationContext(), "TURNING_OFF", Toast.LENGTH_LONG).show();
                        conStatus = (TextView) findViewById(R.id.StatusTextView);
                        conStatus.setText("Not Connected");

                        imgStatus = (ImageView) findViewById(R.id.imageView2);
                        imgStatus.setImageDrawable(imgDrawableStatus1);

                        break;

                    // if the state is on
                    case BluetoothAdapter.STATE_ON:
                        Toast.makeText(getApplicationContext(), "ON", Toast.LENGTH_LONG).show();
                        conStatus = (TextView) findViewById(R.id.StatusTextView);
                        conStatus.setText("Not Connected");

                        imgStatus = (ImageView) findViewById(R.id.imageView2);
                        imgStatus.setImageDrawable(imgDrawableStatus1);

                        break;

                    // if the state is turning on
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Toast.makeText(getApplicationContext(), "TURNING_ON", Toast.LENGTH_LONG).show();
                        conStatus = (TextView) findViewById(R.id.StatusTextView);
                        conStatus.setText("Not Connected");

                        imgStatus = (ImageView) findViewById(R.id.imageView2);
                        imgStatus.setImageDrawable(imgDrawableStatus1);
                        break;
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothStatus);
        unregisterReceiver(pairingStatus);
        unregisterReceiver(connectionState);
    }

    // getting the paring status
    private final BroadcastReceiver pairingStatus = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            Resources res = getResources();
            Drawable imgDrawableStatus1 = res.getDrawable(R.drawable.red);

            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.ERROR);

            // if the state is paired
            if (state == BluetoothDevice.BOND_BONDED) {
                Toast.makeText(getApplicationContext(), "Paired", Toast.LENGTH_LONG).show();
                conStatus = (TextView) findViewById(R.id.StatusTextView);
                conStatus.setText("Not Connected");

                imgStatus = (ImageView) findViewById(R.id.imageView2);
                imgStatus.setImageDrawable(imgDrawableStatus1);

                //if the state is pairing
            } else if (state == BluetoothDevice.BOND_BONDING) {
                Toast.makeText(getApplicationContext(), "Pairing in process", Toast.LENGTH_LONG).show();
                conStatus = (TextView) findViewById(R.id.StatusTextView);
                conStatus.setText("Not Connected");

                imgStatus = (ImageView) findViewById(R.id.imageView2);
                imgStatus.setImageDrawable(imgDrawableStatus1);

                // if the state is unpaired
            } else if (state == BluetoothDevice.BOND_NONE) {
                Toast.makeText(getApplicationContext(), "unpaired", Toast.LENGTH_LONG).show();
                conStatus = (TextView) findViewById(R.id.StatusTextView);
                conStatus.setText("Not Connected");

                imgStatus = (ImageView) findViewById(R.id.imageView2);
                imgStatus.setImageDrawable(imgDrawableStatus1);

                // if the state is undefined or any other mentioned above
            } else {
                Toast.makeText(getApplicationContext(), "undefined", Toast.LENGTH_LONG).show();
                conStatus = (TextView) findViewById(R.id.StatusTextView);
                conStatus.setText("Not Connected");

                imgStatus = (ImageView) findViewById(R.id.imageView2);
                imgStatus.setImageDrawable(imgDrawableStatus1);
            }
        }
    };

    // checking whether the wallet is connected
    private final BroadcastReceiver connectionState = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            Resources res = getResources();
            Drawable imgDrawableStatus = res.getDrawable(R.drawable.green);
            Drawable imgDrawableStatus1 = res.getDrawable(R.drawable.red);

            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);


            // if the device is found
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                Toast.makeText(getApplicationContext(), "Found", Toast.LENGTH_LONG).show();
                conStatus = (TextView) findViewById(R.id.StatusTextView);
                conStatus.setText("Not Connected");

                imgStatus = (ImageView) findViewById(R.id.imageView2);
                imgStatus.setImageDrawable(imgDrawableStatus1);

                // if the device is connected
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_LONG).show();
                conStatus = (TextView) findViewById(R.id.StatusTextView);
                conStatus.setText("Connected");

                imgStatus = (ImageView) findViewById(R.id.imageView2);
                imgStatus.setImageDrawable(imgDrawableStatus);

               // if the discovery is finished
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Toast.makeText(getApplicationContext(), "Found", Toast.LENGTH_LONG).show();

                conStatus = (TextView) findViewById(R.id.StatusTextView);
                conStatus.setText("Not Connected");

                imgStatus = (ImageView) findViewById(R.id.imageView2);
                imgStatus.setImageDrawable(imgDrawableStatus1);

                // if the device is about to disconnect
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {
                Toast.makeText(getApplicationContext(), "About to disconnect", Toast.LENGTH_LONG).show();

                conStatus = (TextView) findViewById(R.id.StatusTextView);
                conStatus.setText("Connected");

                imgStatus = (ImageView) findViewById(R.id.imageView2);
                imgStatus.setImageDrawable(imgDrawableStatus);

                // if the device is disconnected
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_LONG).show();

                conStatus = (TextView) findViewById(R.id.StatusTextView);
                conStatus.setText("Not Connected");

                imgStatus = (ImageView) findViewById(R.id.imageView2);
                imgStatus.setImageDrawable(imgDrawableStatus1);
                notificationView();
            }
        }
    };

    // setting up the notifications
    public void notificationView() {
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivityOnPikIt.class), 0);
        Resources res = getResources();

        Notification notification = new NotificationCompat.Builder(this)
                .setTicker("")
                .setColor(Color.BLACK)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle("PikIt")
                .setContentText("Failed to establish the connection with the wallet.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        notification.defaults |= Notification.DEFAULT_SOUND;
        notification.defaults |= Notification.DEFAULT_VIBRATE;
        notificationManager.notify(0, notification);
    }

    private static void errorExit(String title, String message) {
//        Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
    }


//    public BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
//        if (Build.VERSION.SDK_INT >= 10) {
//            try {
//                final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[]{UUID.class});
//                return (BluetoothSocket) m.invoke(device, MY_UUID);
//            } catch (Exception e) {
//                Log.e(TAG, "Could not create Insecure RFComm Connection", e);
//            }
//        }
//        return device.createRfcommSocketToServiceRecord(MY_UUID);
//    }

    // sending the byte value to the arduino chip
    public static void sendData(String message) throws IOException {

        msgBuffer = message.getBytes();

        try {
            outStream.write(msgBuffer);
        } catch (IOException e) {
            String msg = "In onResume() and an exception occurred during write: " + e.getMessage();
            if (mac_address.equals("98:D3:35:00:A6:A9"))
                msg = msg + ".\n\nUpdate your server address from 00:00:00:00:00:00 to the correct address on line 35 in the java code";
            msg = msg + ".\n\nCheck that the SPP UUID: " + myUUID.toString() + " exists on server.\n\n";

            errorExit("Fatal Error", msg);
        }


    }


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_activity_on_pik_it, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_AboutUs) {
            // Handle the camera action


        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void addListenerOnBluetoothButton(View v) {

        button = (ImageButton) findViewById(R.id.connect);

        button.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // button.setColorFilter(Color.BLUE);
                bluetoothConnectivity();

            }

        });


    }

    // functionality of unlock button
    public void addListenerOnUnlockButton(View v) {

        button = (ImageButton) findViewById(R.id.unlock);


        button.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // open a new activity to the pin
               Intent intent = new Intent(MainActivityOnPikIt.this, PinScreen.class);
                startActivity(intent);

            }

        });

    }

    // functionality of lock button (send 0)
    public void addListenerOnLockButton(View v) {

        button = (ImageButton) findViewById(R.id.lock);

        button.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                try {
                    sendData("0");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Toast.makeText(getApplicationContext(), "Wallet Locked", Toast.LENGTH_LONG).show();


            }

        });


    }




}
