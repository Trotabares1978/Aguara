package com.trotabares.aguara;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Binder;
import android.os.IBinder;

public class PlaybackService extends Service {

    public static final String CHANNEL_ID = "aguara_playback";

    public static final String ACTION_START =
            "com.trotabares.aguara.START_PLAYBACK_SERVICE";

    public static final String ACTION_STOP =
            "com.trotabares.aguara.STOP_PLAYBACK_SERVICE";

    public static final String ACTION_PAUSE =
            "com.trotabares.aguara.PAUSE_PLAYBACK";

    public static final String ACTION_RESUME =
            "com.trotabares.aguara.RESUME_PLAYBACK";

    public static final String EXTRA_AUDIO_URI =
            "audio_uri";

    private MediaPlayer reproductor;

    private final IBinder binder = new LocalBinder();

    public interface PlaybackListener {
        void onPlaybackPrepared(int duracion);
        void onPlaybackCompleted();
        void onPlaybackError();
    }

    private PlaybackListener playbackListener;

    public void setPlaybackListener(PlaybackListener listener) {
        playbackListener = listener;
    }

    public void quitarPlaybackListener() {
        playbackListener = null;
    }

    public class LocalBinder extends Binder {
        public PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        crearCanalNotificacion();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {

        if (intent != null) {

            String accion = intent.getAction();

            if (ACTION_STOP.equals(accion)) {

                detenerReproduccion();

                stopForeground(
                        STOP_FOREGROUND_REMOVE
                );

                stopSelf();

                return START_NOT_STICKY;
            }

            if (ACTION_PAUSE.equals(accion)) {
                pausar();
            }

            else if (ACTION_RESUME.equals(accion)) {
                reanudar();
            }

            else if (ACTION_START.equals(accion)) {

                String uriTexto =
                        intent.getStringExtra(
                                EXTRA_AUDIO_URI
                        );

                if (uriTexto != null &&
                        !uriTexto.trim().isEmpty()) {

                    reproducir(
                            Uri.parse(uriTexto)
                    );
                }
            }
        }

        startForeground(
                1001,
                crearNotificacion()
        );

        return START_STICKY;
    }

    public void reproducir(Uri audio) {

        detenerReproduccion();

        try {

            reproductor = new MediaPlayer();

            reproductor.setDataSource(
                    this,
                    audio
            );

            reproductor.setOnPreparedListener(
                    mp -> {
                        if (playbackListener != null) {
                            playbackListener.onPlaybackPrepared(
                                    mp.getDuration()
                            );
                        }

                        mp.start();
                    }
            );

            reproductor.setOnCompletionListener(
                    mp -> {
                        if (playbackListener != null) {
                            playbackListener.onPlaybackCompleted();
                        }
                    }
            );

            reproductor.setOnErrorListener(
                    (mp, what, extra) -> {
                        if (playbackListener != null) {
                            playbackListener.onPlaybackError();
                        }
                        return false;
                    }
            );

            reproductor.prepareAsync();

        } catch (Exception e) {

            if (playbackListener != null) {
                playbackListener.onPlaybackError();
            }

            detenerReproduccion();
        }
    }

    public void pausar() {

        if (reproductor == null) {
            return;
        }

        try {

            if (reproductor.isPlaying()) {
                reproductor.pause();
            }

        } catch (Exception ignored) {
        }
    }

    public void reanudar() {

        if (reproductor == null) {
            return;
        }

        try {

            if (!reproductor.isPlaying()) {
                reproductor.start();
            }

        } catch (Exception ignored) {
        }
    }

    public void detenerReproduccion() {

        if (reproductor == null) {
            return;
        }

        try {
            reproductor.stop();
        } catch (Exception ignored) {
        }

        try {
            reproductor.release();
        } catch (Exception ignored) {
        }

        reproductor = null;
    }

    public boolean estaReproduciendo() {
        if (reproductor == null) {
            return false;
        }

        try {
            return reproductor.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    public int obtenerPosicion() {
        if (reproductor == null) {
            return 0;
        }

        try {
            return reproductor.getCurrentPosition();
        } catch (Exception e) {
            return 0;
        }
    }

    public int obtenerDuracion() {
        if (reproductor == null) {
            return 0;
        }

        try {
            return reproductor.getDuration();
        } catch (Exception e) {
            return 0;
        }
    }

    public void buscar(int posicion) {
        if (reproductor == null) {
            return;
        }

        try {
            reproductor.seekTo(Math.max(0, posicion));
        } catch (Exception ignored) {
        }
    }

    private void crearCanalNotificacion() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "AGUARÁ reproducción",
                            NotificationManager.IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "Estado de reproducción de AGUARÁ"
            );

            NotificationManager manager =
                    (NotificationManager)
                            getSystemService(
                                    NOTIFICATION_SERVICE
                            );

            if (manager != null) {
                manager.createNotificationChannel(
                        channel
                );
            }
        }
    }

    private Notification crearNotificacion() {

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            builder =
                    new Notification.Builder(
                            this,
                            CHANNEL_ID
                    );

        } else {

            builder =
                    new Notification.Builder(this);
        }

        return builder
                .setSmallIcon(
                        android.R.drawable.ic_media_play
                )
                .setContentTitle("AGUARÁ")
                .setContentText(
                        "Reproducción activa"
                )
                .setOngoing(true)
                .setCategory(
                        Notification.CATEGORY_TRANSPORT
                )
                .build();
    }

    @Override
    public void onDestroy() {

        detenerReproduccion();

        stopForeground(
                STOP_FOREGROUND_REMOVE
        );

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
