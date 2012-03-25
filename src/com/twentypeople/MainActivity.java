/*
    DroidAlarm
    ---------------------------------------------------------------------------
    v.0.1: Early release
    Copyright (C) 2012 by TwentyPeople Limited.
    http://www.twentypeople.com
    ---------------------------------------------------------------------------

    I hate complicated licenses. So here is my license:

  - Don't sell any software that is using this code without notifying me first.
    I am happy to grant permission on a case-by-case basis depending on whether
    you are trying to rip off this idea, or actually took it to the next level.
    If so, more power to you, go party on it, but tell me first.

  - If you are posting this code, or parts of it, online, try to reference my
    name in it, at least the page where you got this from (likely GitHub).
    Why? Well, I got a lot of the code here from many sources, in fact so many
    that I lost count. So it's up to you to start fresh and give people a
    solid point of reference to complain why this or that doesn't work. The
    benefit is, all of us are discussing issues regarding to this code at the
    same place, it'll help all of us, much more than spreading it around the web.

  - Don't build anything that explodes. Seriously. Please. Also, try not to spy
    on your kids, fiances, girlfriends, or worse, people who are not related.

    ---------------------------------------------------------------------------
    Roman Mittermayr (software@mittermayr.com)
    ---------------------------------------------------------------------------
*/
package com.twentypeople;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import android.view.View;
import android.location.Location;
import android.location.LocationListener;
import android.widget.ImageView;

import java.io.IOException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity implements SensorEventListener
{    
    static Integer GPS_UPDATE_INTERVAL = 60000; // 1 Minute
    static String  PHONENUMBER         = "+0123456789"; // ENTER YOUR SAFE PHONE NUMBER HERE (RECEIVES TEXT MESSAGES)
    
    private SensorManager sensorManager;
    private Sensor sensor;
    float trainedX, trainedY, trainedZ;
    Boolean trackingMode;
    Date alarmTriggered;
    LocationManager locationManager;
    ImageView statusImage;
    Boolean alarmEnabled;
    Location currentLocation;
    Timer updater;
    LocationListener locationListener;

    private Runnable updateLocation = new Runnable() {
        public void run() {
            if (currentLocation != null) {
                String sms;
                sms = "Long: "+currentLocation.getLongitude() + " Lat: "+currentLocation.getLatitude() + " Acc: "+currentLocation.getAccuracy()+" B: "+currentLocation.getBearing();
                Log.d("Sensor", "SMS ["+sms.length()+"]: "+sms);
                sendSMS(PHONENUMBER, sms);
            } else {
                Log.d("Sensor", "No location available yet");
            }
        }
    };

    private void triggerTimer() {
        this.runOnUiThread(updateLocation);
    }


    @SuppressWarnings("deprecation")
    private void sendSMS(String phoneNumber, String message)
    {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, Object.class), 0);
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(phoneNumber, null, message, pi, null);
    }


    public void playSound(Context context) throws IllegalArgumentException, SecurityException, IllegalStateException,
            IOException {
        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        MediaPlayer mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setDataSource(context, soundUri);
        final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager.getStreamVolume(AudioManager.STREAM_RING) != 0) {
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
            mMediaPlayer.setLooping(false);
            mMediaPlayer.prepare();
            mMediaPlayer.start();
        }
    }


    public void onAccuracyChanged(Sensor theSensor, int accuracy) {
        
    }

    public void enterTrackingMode() {

        if (trackingMode) return;

        trackingMode = true;

        // Turn on GPS
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);


        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                currentLocation = location;
                Log.d("Sensor", "Latitude: " + location.getLatitude() + " Longitude: " + location.getLongitude());
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {  }

            public void onProviderEnabled(String provider) { }

            public void onProviderDisabled(String provider)  {  }
        };



        // Register the listener with the Location Manager to receive location updates can be either gps provider
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            Log.d("Sensor", "Provider: GPS_NETWORK_PROVIDER");
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 50, locationListener);
        }
        else {
            Log.d("Sensor", "Provider: GPS_SATELLITE");
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 50, locationListener);
        }

        currentLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        // Trigger a timer that sends location updates via SMSmyTimer = new Timer();
        updater = new Timer();
        updater.schedule(new TimerTask() {
            @Override
            public void run() {
                triggerTimer();
            }

        }, GPS_UPDATE_INTERVAL, GPS_UPDATE_INTERVAL);

        Log.d("Sensor", "GPS Tracking enabled");
    }

    
    public void onSensorChanged(SensorEvent event) {

        // Is the Alarm even ON?
        if (!alarmEnabled) return;

        // Has an alarm already been triggered and are we tracking already?
        if (trackingMode) return;
        
        // no values set, initialize first
        if (trainedX==0) {
            trainedX = event.values[0];
            trainedY = event.values[1];
            trainedZ = event.values[2];
            return;
        }     
        
        // Calculate whether we had a major move
        float currentStatus = event.values[0] + event.values[1] + event.values[2];
        float lastStatus = trainedX + trainedY + trainedZ;
        float difference = 0;

        // Adjust our weighed values
        trainedX = (trainedX + event.values[0]) / 2;
        trainedY = (trainedY + event.values[1]) / 2;
        trainedZ = (trainedZ + event.values[2]) / 2;

        difference = Math.abs(lastStatus - currentStatus);
        
        if (difference<1.8) return;
        
        //Log.d("Sensor", "x: " + event.values[0] + ", y: " + event.values[1] + ", z: " + event.values[2]);
        //Log.d("Sensor", "Diff: "+ (currentStatus-lastStatus));
        
        // Check when last alarm was triggered
        Date currentAlarm = new Date();
        long seconds = (currentAlarm.getTime()-alarmTriggered.getTime()) / 1000;
        
        if (seconds<10) return;

        // Ok, we are cleared to trigger the alarm!
        alarmTriggered = currentAlarm;
        try {
            enterTrackingMode();
            playSound(getApplicationContext());
            sendSMS(PHONENUMBER, "Alarm Triggered: Your bike is being moved right now or was pushed over.");
            Log.d("Sensor", "ALARM TRIGGERED");
        } catch (Exception e) { }

    }


    public void turnAlarmOFF() {
        trackingMode = false;
        alarmEnabled = false;
        alarmTriggered = new Date();
        locationManager.removeUpdates(locationListener);
        statusImage.setImageResource(com.twentypeople.R.drawable.off);
        updater.cancel();
        trainedX = 0;
        trainedY = 0;
        trainedZ = 0;
    }

    public void turnAlarmON() {
        trackingMode = false;
        alarmEnabled = true;
        alarmTriggered = new Date();
        statusImage.setImageResource(com.twentypeople.R.drawable.activated);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(com.twentypeople.R.layout.main);

        statusImage = (ImageView) findViewById(com.twentypeople.R.id.statusImage);

        statusImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (alarmEnabled) {
                    turnAlarmOFF();
                } else {
                    turnAlarmON();
                }
            }
        });

        alarmEnabled = true;

        trainedX = 0;
        trainedY = 0;
        trainedZ = 0;

        trackingMode = false;

        alarmTriggered = new Date();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);

        View view = getWindow().getDecorView().findViewById(android.R.id.content);
        //view.setKeepScreenOn(true);

        // Install SMS listener
        BroadcastReceiver SMSbr = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                // Called every time a new sms is received
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    Object[] pdus = (Object[]) bundle.get("pdus");
                    final SmsMessage[] messages = new SmsMessage[pdus.length];
                    for (int i = 0; i < pdus.length; i++)
                        messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);

                    if (messages.length > -1) {
                        String sms = messages[0].getMessageBody();
                        Log.d("Sensor", "SMS received: "+sms);

                        if (sms.contains("STOP")) {
                            turnAlarmOFF();
                        }

                        if (sms.contains("START")) {
                            turnAlarmON();
                        }

                    }
                }
            }
        };

        IntentFilter SMSfilter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        this.registerReceiver(SMSbr, SMSfilter);

    }
}