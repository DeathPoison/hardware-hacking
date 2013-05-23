package com.tinkerforge.garagecontrol;

import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
 
import com.tinkerforge.BrickletIndustrialQuadRelay;
import com.tinkerforge.IPConnection;
import com.tinkerforge.TinkerforgeException;

public class MainActivity extends Activity {
    final Context context = this;
	private IPConnection ipcon = null;
    private BrickletIndustrialQuadRelay relay = null;
    private EditText host;
    private EditText port;
    private EditText uid;
    private Button connect;
    private Button trigger;

    enum ConnectResult {
        SUCCESS,
        NO_CONNECTION,
        NO_DEVICE
    }

    class ConnectAsyncTask extends AsyncTask<Void, Void, ConnectResult> {
        private ProgressDialog progressDialog;
        private String currentHost;
        private String currentPort;
        private String currentUID;

        @Override
        protected void onPreExecute() {
        	currentHost = host.getText().toString();
        	currentPort = port.getText().toString();
        	currentUID = uid.getText().toString();
        	
        	if (currentHost.length() == 0 || currentPort.length() == 0 || currentUID.length() == 0) {
        		AlertDialog.Builder builder = new AlertDialog.Builder(context);
        		builder.setMessage("Host/Port/UID cannot be empty"); 
        		builder.create().show();
        		cancel(true);
        		return;
        	}

        	host.setEnabled(false);
        	port.setEnabled(false);
        	uid.setEnabled(false);
    		connect.setEnabled(false);
    		trigger.setEnabled(false);

        	progressDialog = new ProgressDialog(context);
        	progressDialog.setMessage("Connecting to " + currentHost + ":" + currentPort);
        	progressDialog.setCancelable(false);
        	progressDialog.show();
        }

        protected ConnectResult doInBackground(Void... params) {
        	ipcon = new IPConnection();
            relay = new BrickletIndustrialQuadRelay(currentUID, ipcon);

            try {
                ipcon.connect(currentHost, Integer.parseInt(currentPort));
            } catch(java.net.UnknownHostException e) {
                return ConnectResult.NO_CONNECTION;
            } catch(java.io.IOException e) {
                return ConnectResult.NO_CONNECTION;
            } catch(com.tinkerforge.AlreadyConnectedException e) {
                return ConnectResult.NO_CONNECTION;
            }

            try {
	            if (relay.getIdentity().deviceIdentifier != BrickletIndustrialQuadRelay.DEVICE_IDENTIFIER) {
	            	ipcon.disconnect();
	            	return ConnectResult.NO_DEVICE;
	            }
            } catch (com.tinkerforge.TinkerforgeException e1) {
            	try {
					ipcon.disconnect();
				} catch (com.tinkerforge.NotConnectedException e2) {
				}

            	return ConnectResult.NO_DEVICE;
            }

            return ConnectResult.SUCCESS;
        }

        @Override
        protected void onPostExecute(ConnectResult result) {
        	progressDialog.dismiss();

        	if (result == ConnectResult.SUCCESS) {
        		connect.setText("Disconnect");
        		connect.setOnClickListener(new DisconnectClickListener());
        		connect.setEnabled(true);
        		trigger.setEnabled(true);
        	} else {
        		AlertDialog.Builder builder = new AlertDialog.Builder(context);

        		if (result == ConnectResult.NO_CONNECTION) {
            		builder.setMessage("Could not connect to " + currentHost + ":" + currentPort); 
        		} else { // ConnectResult.NO_DEVICE
        			builder.setMessage("Could not find Industrial Quad Relay Bricklet [" + currentUID + "]"); 
        		}

        		builder.setCancelable(false); 
        		builder.setPositiveButton("Retry", new DialogInterface.OnClickListener() {  
        		    @Override
        		    public void onClick(DialogInterface dialog, int which) {
        		        dialog.dismiss();
        		        new ConnectAsyncTask().execute();
        		    }
        		});
        		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {  
        		    @Override  
        		    public void onClick(DialogInterface dialog, int which) {  
        	        	host.setEnabled(true);
        	        	port.setEnabled(true);
        	        	uid.setEnabled(true);
        		        connect.setText("Connect");
        		        connect.setOnClickListener(new ConnectClickListener());
                		connect.setEnabled(true);
        		        dialog.dismiss();
        		    }  
        		});  
        		builder.create().show(); 
        	}
        }
    }

    class DisconnectAsyncTask extends AsyncTask<Void, Void, Boolean> {
        protected Boolean doInBackground(Void... params) {
            try {
                ipcon.disconnect();
                return true;
            } catch(TinkerforgeException e) {
            	return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
        	if (result) {
	        	host.setEnabled(true);
	        	port.setEnabled(true);
	        	uid.setEnabled(true);
		        connect.setText("Connect");
		        connect.setOnClickListener(new ConnectClickListener());
	    		connect.setEnabled(true);
	    		trigger.setEnabled(false);
        	}
        }
    }

    class TriggerAsyncTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            try {
            	relay.setMonoflop(1 << 0, 1 << 0, 1500);
            } catch (TinkerforgeException e) {
            }
            
            return null;
        }
    }

    class ConnectClickListener implements OnClickListener {
        public void onClick(View v) {
        	new ConnectAsyncTask().execute();
        }
    }

    class DisconnectClickListener implements OnClickListener {
        public void onClick(View v) {
        	new DisconnectAsyncTask().execute();
        }
    }

    class TriggerClickListener implements OnClickListener {
        public void onClick(View v) {
        	new TriggerAsyncTask().execute();
        }
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
  
		host = (EditText)findViewById(R.id.host);
		port = (EditText)findViewById(R.id.port);
		uid = (EditText)findViewById(R.id.uid);
		connect = (Button)findViewById(R.id.connect);
		trigger = (Button)findViewById(R.id.trigger);

		SharedPreferences settings = getPreferences(0);
	    host.setText(settings.getString("host", "192.168.178.46"));
	    port.setText(settings.getString("port", "4223"));
	    uid.setText(settings.getString("uid", "ctG"));

	    connect.setOnClickListener(new ConnectClickListener());
		trigger.setOnClickListener(new TriggerClickListener());
		trigger.setEnabled(false);
		
		if (savedInstanceState != null && savedInstanceState.getBoolean("connected", false)) {
			new ConnectAsyncTask().execute();
		}
	}

    @Override
	protected void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);

    	outState.putBoolean("connected", ipcon != null && ipcon.getConnectionState() == IPConnection.CONNECTION_STATE_CONNECTED);
    }
	
    @Override
    protected void onStop() {
    	super.onStop();

    	SharedPreferences settings = getPreferences(0);
		SharedPreferences.Editor editor = settings.edit();

		editor.putString("host", host.getText().toString());
		editor.putString("port", port.getText().toString());
		editor.putString("uid", uid.getText().toString());
		editor.commit();
    }
}
