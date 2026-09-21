package com.trotabares.aguara;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;


public class PlaybackService extends Service {

    public static final String CHANNEL_ID = "aguara_playback";
    public static final String ACTION_START = "com.trotabares.aguara.START_PLAYBACK_SERVICE";
    public static final String ACTION_STOP = "com.trotabares.aguara.STOP_PLAYBACK_SERVICE";

    @Override
    public void onCreate() {
        super.onCreate();
        crearCanalNotificacion();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification notification = crearNotificacion();
        startForeground(1001, notification);

        // En el siguiente paso, el MediaPlayer pasará a vivir aquí.
        return START_STICKY;
    }

    private void crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "AGUARÁ reproducción",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Controles y estado de reproducción de AGUARÁ");

            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification crearNotificacion() {
        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("AGUARÁ")
                .setContentText("Reproducción activa")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .build();
    }

    @Override
    public void onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
