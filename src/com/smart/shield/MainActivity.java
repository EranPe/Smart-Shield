package com.smart.shield;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

/**
 * This is the main Activity that displays the current Connection session.
 */
public class MainActivity extends SherlockListActivity {
    // Debugging
    private static final String TAG = "SmartShield";
    private static final boolean D = true;

    // Message types sent from the Connection Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    
	private static final int FM_NOTIFICATION_ID = 10000;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_ENABLE_BT = 1;

    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    
    final int RECIEVE_MESSAGE = 1;		// Status  for Handler
	private BluetoothSocket btSocket = null;
	private ConnectedThread mConnectedThread;
	private StringBuilder sb = new StringBuilder();
	private Handler h;
	
	public static int BluetoothStat = 0;
	
	public static boolean connected = false;
		
	public static boolean notified = false;
	public static int DEFAULT_SOUND = 1;
	public static int DEFAULT_VIBRATE = 1;
	public static int DEFAULT_LIGHTS = 1;

		
	MenuItem Bluetooth;
	
	private ProgressDialog m_ProgressDialog = null; 
	private ArrayList<entry> m_entries = null;
	private entryAdapter m_adapter;
	private Runnable viewEntriess;
	
	// SPP UUID service
	private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	 
	// MAC-address of Bluetooth module (you must edit this line)
	private static String address = "00:12:09:25:91:98";
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");

        // Set up the window layout
        setContentView(R.layout.main);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    }
    
    private void motionDetected(){
    	    	
    	Calendar calendar = Calendar.getInstance();
    	Date date = calendar.getTime();
    	SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
    	String formattedDate = format.format(date);
    	
    	addNotification();
    	
    	entry en = new entry();
    	   
    	en.setEntryName("Motion detected!");
    	en.setEntryStatus(formattedDate);
        m_entries.add(en);
                
		m_adapter.clear();
		m_adapter.notifyDataSetChanged();
        
        runOnUiThread(returnRes);
    }
    
	// Add app running notification
	private void addNotification() {
		
		NotificationCompat.Builder builder =
		        new NotificationCompat.Builder(this)
		        .setSmallIcon(R.drawable.alert_status)
		        .setContentTitle("Motion detected!")
		        .setContentText("Tap to open Smart Shield app")
		        .setDefaults(DEFAULT_SOUND | DEFAULT_VIBRATE | DEFAULT_LIGHTS);

		Intent notificationIntent = new Intent(this, MainActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
		builder.setContentIntent(contentIntent);
		
		// Add as notification
		NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		manager.notify(FM_NOTIFICATION_ID, builder.build());
				
		notified = true;
	}

	// Remove notification
	private void removeNotification() {
		NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		manager.cancel(FM_NOTIFICATION_ID);
	}
    
    private class entryAdapter extends ArrayAdapter<entry> {

        private ArrayList<entry> items;

        public entryAdapter(Context context, int textViewResourceId, ArrayList<entry> items) {
                super(context, textViewResourceId, items);
                this.items = items;
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) {
                    LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    v = vi.inflate(R.layout.row, null);
                }
                entry e = items.get(position);
                if (e != null) {
                        TextView tt = (TextView) v.findViewById(R.id.toptext);
                        TextView bt = (TextView) v.findViewById(R.id.bottomtext);
                        if (tt != null) {
                              tt.setText(e.getEntryName());
                              }
                        if(bt != null){
                              bt.setText(e.getEntryStatus());
                              }
                        }
                return v;
                }
        }

    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // SetupConnection() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the connection
        } else if (connected == false) SetupConnection();
        
        m_entries = new ArrayList<entry>();
        this.m_adapter = new entryAdapter(this, R.layout.row, m_entries);
        setListAdapter(this.m_adapter);
        showList();
        
        if (notified == true) {
			removeNotification();
			notified = false;
		}
    }
    
    private void showList() {
        
        viewEntriess = new Runnable(){
            @Override
            public void run() {
                getEntries();
            }
        };
        Thread thread =  new Thread(null, viewEntriess, "MagentoBackground");
        thread.start();
        m_ProgressDialog = ProgressDialog.show(MainActivity.this,    
              "Please wait...", "Retrieving data ...", true);
    }
    
    private Runnable returnRes = new Runnable() {

        @Override
        public void run() {
            if(m_entries != null && m_entries.size() > 0){
                m_adapter.notifyDataSetChanged();
                for(int i=m_entries.size()-1;i>=0;i--)
                m_adapter.add(m_entries.get(i));
            }
            m_ProgressDialog.dismiss();
            m_adapter.notifyDataSetChanged();
        }
    };
	private void getEntries(){
          try{
        	  m_entries = new ArrayList<entry>();
        	          	  
              Log.i("ARRAY", ""+ m_entries.size());
            } catch (Exception e) { 
              Log.e("BACKGROUND_PROC", e.getMessage());
            }
            runOnUiThread(returnRes);
        }

    @Override
    public void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");
           
    }

    private void SetupConnection() {
        Log.d(TAG, "SetupConnection()");

		BluetoothStat = 0;
		supportInvalidateOptionsMenu();
		
	    // Set up a pointer to the remote node using it's address.
	    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
	   
	    // Two things are needed to make a connection:
	    //   A MAC address, which we got above.
	    //   A Service ID or UUID.  In this case we are using the
	    //     UUID for SPP.
	    try {
	      btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
	    } catch (IOException e) {
	      errorExit("Fatal Error", "In onResume() and socket create failed: " + e.getMessage() + ".");
	    }
	   
	    // Discovery is resource intensive.  Make sure it isn't going on
	    // when you attempt to connect and pass your message.
	    mBluetoothAdapter.cancelDiscovery();
	    
	    h = new Handler() {
	    	public void handleMessage(android.os.Message msg) {
	    		switch (msg.what) {
	            case RECIEVE_MESSAGE:													// if receive massage
	            	byte[] readBuf = (byte[]) msg.obj;
	            	String strIncom = new String(readBuf, 0, msg.arg1);					// create string from bytes array
	            	sb.append(strIncom);												// append string
	            	int endOfLineIndex = sb.indexOf("\r\n");							// determine the end-of-line
	            	if (endOfLineIndex > 0) { 											// if end-of-line,
	            		String sbprint = sb.substring(0, endOfLineIndex);				// extract string
	                    sb.delete(0, sb.length());										// and clear 
	                    
	            		if (sbprint.equals("OK")){
	                		BluetoothStat = 2;
	                		connected = true;
	                        invalidateOptionsMenu();
	            	    }
	            		
	            		if (sbprint.equals("Motion detected!")){            			
	            			motionDetected();
	            		}
	                }
	            	//Log.d(TAG, "...String:"+ sb.toString() +  "Byte:" + msg.arg1 + "...");
	            	break;
	    		}
	        };
		};		
		
	    // Establish the connection.  This will block until it connects.
	    Log.d(TAG, "...Connecting...");
		BluetoothStat = 1;
		invalidateOptionsMenu();

	    try {
	      btSocket.connect();
	      Log.d(TAG, "....Connection ok...");
	      
	    } catch (IOException e) {
	      try {
	        btSocket.close();
	      } catch (IOException e2) {
	        errorExit("Fatal Error", "In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
	      }
	    }
	     
	    // Create a data stream so we can talk to server.
	    Log.d(TAG, "...Create Socket...");
	   
	    mConnectedThread = new ConnectedThread(btSocket);
	    mConnectedThread.start();
	    
	    SendMessage();
    }
    
    // Sending message to the arduino system "Bluetooth is ready".
	public void SendMessage() {
		mConnectedThread.write("2");
	}

    @Override
    public void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
        
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
        
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a Setup Connection session
            	SetupConnection();
            } else {
                // User did not enable Bluetooth or an error occured
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
    	// Inflate the menu with the options to show the Map and the List.
    	getSupportMenuInflater().inflate(R.menu.option_menu, menu);

        return true;
	}
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	Bluetooth = menu.findItem(R.id.BluetoothStatus);
		if (BluetoothStat == 0) Bluetooth.setIcon(R.drawable.device_access_bluetooth);
		if (BluetoothStat == 1) Bluetooth.setIcon(R.drawable.device_access_bluetooth_searching);
		if (BluetoothStat == 2) Bluetooth.setIcon(R.drawable.device_access_bluetooth_connected);
        return super.onPrepareOptionsMenu(menu);

    }
    
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.BluetoothStatus:
			// Cancel the connection and try to establish a new connection to device.
			cancel();
			SetupConnection();
			invalidateOptionsMenu();
			return true;
		case R.id.Clear_History:
			getEntries();
			m_adapter.clear();
			m_adapter.notifyDataSetChanged();
			return true;
		case R.id.Preferences:
			runOnUiThread(returnRes);
			return true;
		case R.id.About:
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	  private void errorExit(String title, String message){
		    Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
		    finish();
		  }
	  
	  private class ConnectedThread extends Thread {
		 //   private final BluetoothSocket mmSocket;
		    private final InputStream mmInStream;
		    private final OutputStream mmOutStream;
		 
		    public ConnectedThread(BluetoothSocket socket) {
		   //     mmSocket = socket;
		        InputStream tmpIn = null;
		        OutputStream tmpOut = null;
		 
		        // Get the input stream, using temp objects because
		        // member streams are final
		        try {
		            tmpIn = socket.getInputStream();
		            tmpOut = socket.getOutputStream();
		        } catch (IOException e) { }
		 
		        mmInStream = tmpIn;
		        mmOutStream = tmpOut;
		    }
		 
		    public void run() {
		        byte[] buffer = new byte[256];  // buffer store for the stream
		        int bytes; // bytes returned from read()

		        // Keep listening to the InputStream until an exception occurs
		        while (true) {
		        	try {
		        		// Read from the InputStream
		                bytes = mmInStream.read(buffer);		// Get number of bytes and message in "buffer"
	                    h.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();		// Send to message queue Handler
		            } catch (IOException e) {
		                break;
		            }
		        }
		    }
		    
	        /* Call this from the main activity to send data to the remote device */
	        public void write(String message) {
	            Log.d(TAG, "...Data to send: " + message + "...");
	            byte[] msgBuffer = message.getBytes();
	            try {
	                mmOutStream.write(msgBuffer);
	            } catch (IOException e) {
	                Log.d(TAG, "...Error data send: " + e.getMessage() + "...");     
	              }
	        }
		}
	  
	    /* Call this from the main activity to shutdown the connection */
	    public void cancel() {
	        try {
	        	btSocket.close();
	        } catch (IOException e) { }
	    }
}
