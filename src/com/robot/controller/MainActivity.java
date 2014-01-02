package com.robot.controller;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

public class MainActivity extends Activity {

	private static final String LOG_TAG = "Controller";
	
	private static final String host = "ubuntu";
	private static final int port = 6000;
	
	View leftView, rightView;
	Position leftPosition, rightPosition;
	CommandClient client;
	Socket sock;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		if (client != null) {
			client.stop();
		}
	}
	
	@Override
	public void onStop() {
		super.onStop();
		
		if (client != null) {
			client.stop();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		
		leftView = (View) findViewById(R.id.left_view);
		rightView = (View) findViewById(R.id.right_view);
		
		leftPosition = new Position(leftView.getHeight());
		rightPosition = new Position(rightView.getHeight());
		
		leftView.setOnTouchListener(leftPosition);
		rightView.setOnTouchListener(rightPosition);
		
		client = new CommandClient();
		Thread CommClient = new Thread(client);
		CommClient.setDaemon(true);
		CommClient.start();
	}
	
	class Position implements OnTouchListener {
		private double ly;
		private double height;
		
		public Position(double height) {
			this.height = height;
			this.ly = 0;
		}
		
		@Override
		public boolean onTouch(View view, MotionEvent event) {
			switch (event.getAction() & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN:
				this.ly = event.getY();
				break;
			case MotionEvent.ACTION_MOVE:
				this.ly = event.getY();
				break;
			case MotionEvent.ACTION_UP:
				this.ly = 0;
				break;
			}
			return true;
		}
		
		public double getAbsoluteY() {
			return this.ly;
		}
		
		public double getScaledY() {
			if (this.ly == 0) {
				return 0;
			} else {
				return -2*((this.ly) - (this.height/2))/(this.height);
			}
		}
	}
	
	class CommandClient implements Runnable {
		
		DataOutputStream output;
		
		private boolean isRunning;
		private final byte FORWARD = 0;
		private final byte BACKWARD = 1;
		private final int MIN_DUTY_CYCLE = 50;
		private final int MAX_DUTY_CYCLE = 100;
		
		public CommandClient() {
			this.isRunning = true;
		}
		
		@Override
		public void run() {
			try {
				Log.d(LOG_TAG, "Entering run()");
				InetAddress addr = InetAddress.getByAddress(host, new byte[] {(byte) 192, (byte) 168,2,4});
				sock = new Socket();
				sock.connect(new InetSocketAddress(addr, port), 1000);
				output = new DataOutputStream(sock.getOutputStream());
			} catch (UnknownHostException e) {
				Log.e(LOG_TAG, "UnknownHostException");
				Log.e(LOG_TAG, Log.getStackTraceString(e));
			} catch (ConnectException e) {
				Log.e(LOG_TAG, "Cannot connect to server");
			} catch (IOException e) {
				Log.e(LOG_TAG, "IOEexception creating socket");
				Log.e(LOG_TAG, Log.getStackTraceString(e));
			} catch (NullPointerException e) {
				Log.e(LOG_TAG, "Null pointer Exception, probably because socket could not find server");
			}
		
			while (this.isRunning() & !Thread.currentThread().isInterrupted()) {
				sendCommand(leftPosition, rightPosition);
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					Log.e(LOG_TAG, Log.getStackTraceString(e));
				}
			}
			try {
				Log.d(LOG_TAG, "Exiting thread");
				Thread.currentThread().join();
			} catch (InterruptedException e) {
				Log.e(LOG_TAG, Log.getStackTraceString(e));
			}
		}
		
		public boolean isRunning() {
			return this.isRunning;
		}
		
		public void stop() {
			this.isRunning = false;
		}
		
		private byte[] toDutyCycleAndDirection(double scaledY) {
			
			byte data[] = new byte[2];
			double absY = Math.abs(scaledY);
			
			if (absY > .01) {
				data[0] = (byte) Math.round(absY*(MAX_DUTY_CYCLE - MIN_DUTY_CYCLE) + (MIN_DUTY_CYCLE));
				if (data[0] > MAX_DUTY_CYCLE) {
					data[0] = MAX_DUTY_CYCLE;
				}
				
				if (scaledY < 0) {
					data[1] = BACKWARD;
				} else {
					data[1] = FORWARD;
				}
				
				return data;
				
			} else {
				return new byte[] {0,0};
			}
		}
		
		private void sendCommand(Position left, Position right) {
			
			byte[] leftCommand = toDutyCycleAndDirection(left.getScaledY());
			byte[] rightCommand = toDutyCycleAndDirection(right.getScaledY());
			
			byte[] command = {leftCommand[0], leftCommand[1],
							  rightCommand[0], rightCommand[1]};
			
			try {
				Log.d(LOG_TAG, String.valueOf(command[0]) + ", " +  String.valueOf(command[1])
							   + ", " + String.valueOf(command[2]) + ", " + String.valueOf(command[3]));
				output.write(command, 0, 4);
			} catch (IOException e) {
				Log.e(LOG_TAG, Log.getStackTraceString(e));
			} catch (NullPointerException e) {
				//Do nothing, we've already handled this exception
			}
		}
	}
}
