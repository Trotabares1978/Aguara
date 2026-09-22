package com.trotabares.aguara;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AguaraPcmPlayer {

    public interface OnPreparedListener {
        void onPrepared(AguaraPcmPlayer player);
    }

    public interface OnCompletionListener {
        void onCompletion(AguaraPcmPlayer player);
    }

    public interface OnErrorListener {
        boolean onError(AguaraPcmPlayer player, int what, int extra);
    }

    private static volatile AguaraPcmPlayer activePlayer;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();

    private Uri sourceUri;
    private MediaExtractor extractor;
    private MediaCodec codec;
    private AudioTrack audioTrack;
    private Thread worker;

    private volatile boolean released;
    private volatile boolean playing;
    private volatile boolean prepared;
    private volatile long pendingSeekMs = -1L;

    private int durationMs;
    private int sampleRate = 44100;
    private int channelCount = 2;
    private long positionBaseMs;
    private long framesWritten;
    private short[] pcmBuffer;
    private final BandFilter bassFilter = new BandFilter(100f);

    private OnPreparedListener preparedListener;
    private OnCompletionListener completionListener;
    private OnErrorListener errorListener;

    private float preampDb = 0f;
    private boolean limiterEnabled = true;
    private float bassBoost = 0f;
    private float tubeDrive = 0f;
    private float vinylAmount = 0f;
    private long noiseState = 0x1234ABCDL;

    private final BandFilter[] filters = new BandFilter[]{
            new BandFilter(31f),
            new BandFilter(62f),
            new BandFilter(125f),
            new BandFilter(250f),
            new BandFilter(500f),
            new BandFilter(1000f),
            new BandFilter(2000f),
            new BandFilter(4000f),
            new BandFilter(8000f),
            new BandFilter(16000f)
    };

    public AguaraPcmPlayer(Context context) {
        this.context = context.getApplicationContext();
        activePlayer = this;
        cargarEcualizacionGuardada();
    }

    private void cargarEcualizacionGuardada() {
        android.content.SharedPreferences prefs =
                context.getSharedPreferences("aguara", Context.MODE_PRIVATE);

        preampDb = prefs.getFloat("advanced_preamp_db", 0f);
        limiterEnabled = prefs.getBoolean("advanced_limiter", false);
        bassBoost = prefs.getFloat("advanced_bass_boost", 0f);
        tubeDrive = prefs.getFloat("advanced_tube_drive", 0f);
        vinylAmount = prefs.getFloat("advanced_vinyl", 0f);

        for (int i = 0; i < filters.length; i++) {
            float gain = prefs.getFloat("eq_band_" + i, 0f);
            filters[i].setGain(gain);
        }
    }

    private void guardarAudioAvanzado() {
        android.content.SharedPreferences.Editor editor =
                context.getSharedPreferences("aguara", Context.MODE_PRIVATE).edit();
        editor.putFloat("advanced_preamp_db", preampDb);
        editor.putBoolean("advanced_limiter", limiterEnabled);
        editor.putFloat("advanced_bass_boost", bassBoost);
        editor.putFloat("advanced_tube_drive", tubeDrive);
        editor.putFloat("advanced_vinyl", vinylAmount);
        editor.apply();
    }

    private void guardarEcualizacion() {
        android.content.SharedPreferences.Editor editor =
                context.getSharedPreferences("aguara", Context.MODE_PRIVATE).edit();
        for (int i = 0; i < filters.length; i++) {
            editor.putFloat("eq_band_" + i, filters[i].getGain());
        }
        editor.apply();
    }

    public static AguaraPcmPlayer getActivePlayer() {
        return activePlayer;
    }

    public void setDataSource(Context ignored, Uri uri) {
        sourceUri = uri;
    }

    public void setOnPreparedListener(OnPreparedListener listener) {
        preparedListener = listener;
    }

    public void setOnCompletionListener(OnCompletionListener listener) {
        completionListener = listener;
    }

    public void setOnErrorListener(OnErrorListener listener) {
        errorListener = listener;
    }

    public void prepareAsync() {
        worker = new Thread(this::decodeLoop, "AGUARA-AudioEngine");
        worker.start();
    }

    private void decodeLoop() {
        try {
            try { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO); } catch (Exception ignored) {}

            if (sourceUri == null) {
                throw new IllegalStateException("No hay fuente de audio");
            }

            extractor = new MediaExtractor();
            extractor.setDataSource(context, sourceUri, null);

            int track = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    break;
                }
            }

            if (track < 0) {
                throw new IllegalStateException("No hay pista de audio");
            }

            extractor.selectTrack(track);
            MediaFormat inputFormat = extractor.getTrackFormat(track);

            if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                durationMs = (int) Math.min(
                        Integer.MAX_VALUE,
                        inputFormat.getLong(MediaFormat.KEY_DURATION) / 1000L
                );
            }

            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                throw new IllegalStateException("Formato de audio desconocido");
            }

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(inputFormat, null, null, 0);
            codec.start();

            boolean inputDone = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            while (!released) {
                if (pendingSeekMs >= 0) {
                    performPendingSeek();
                    inputDone = false;
                    continue;
                }

                synchronized (lock) {
                    if (!playing && prepared) {
                        try {
                            lock.wait(100);
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }
                }

                if (!inputDone) {
                    int inputIndex = codec.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer inputData = codec.getInputBuffer(inputIndex);
                        if (inputData != null) {
                            inputData.clear();
                            int size = extractor.readSampleData(inputData, 0);

                            if (size < 0) {
                                codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        0,
                                        0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                );
                                inputDone = true;
                            } else {
                                long pts = extractor.getSampleTime();
                                codec.queueInputBuffer(inputIndex, 0, size, pts, 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(info, 10000);

                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    configureOutput(codec.getOutputFormat());

                    prepared = true;
                    OnPreparedListener listener = preparedListener;
                    if (listener != null) {
                        mainHandler.post(() -> listener.onPrepared(this));
                    }
                    continue;
                }

                if (outputIndex >= 0) {
                    ByteBuffer output = codec.getOutputBuffer(outputIndex);

                    if (output != null && info.size > 0 && audioTrack != null) {
                        output.position(info.offset);
                        output.limit(info.offset + info.size);
                        processPcm16(output, info.size);
                    }

                    boolean eos =
                            (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                    codec.releaseOutputBuffer(outputIndex, false);

                    if (eos) {
                        playing = false;
                        if (audioTrack != null) {
                            audioTrack.pause();
                        }

                        OnCompletionListener listener = completionListener;
                        if (listener != null && !released) {
                            mainHandler.post(() -> listener.onCompletion(this));
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            if (!released) {
                OnErrorListener listener = errorListener;
                if (listener != null) {
                    mainHandler.post(() -> listener.onError(this, -1, 0));
                }
            }
        } finally {
            cleanup();
        }
    }

    private void configureOutput(MediaFormat outputFormat) {
        sampleRate = outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ? outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                : 44100;

        channelCount = outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                : 2;

        if (channelCount != 1 && channelCount != 2) {
            throw new IllegalStateException(
                    "Canales no soportados: " + channelCount
            );
        }

        int channelMask = channelCount == 1
                ? AudioFormat.CHANNEL_OUT_MONO
                : AudioFormat.CHANNEL_OUT_STEREO;

        int minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT
        );

        if (minBuffer <= 0) {
            throw new IllegalStateException("AudioTrack no disponible");
        }

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build();

        AudioTrack old = audioTrack;

        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(Math.max(minBuffer * 4, 32768))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        if (old != null) {
            try {
                old.release();
            } catch (Exception ignored) {
            }
        }

        for (BandFilter filter : filters) {
            filter.configure(sampleRate);
        }
        bassFilter.configure(sampleRate);
    }

    private void performPendingSeek() {
        long seek = pendingSeekMs;
        pendingSeekMs = -1L;

        extractor.seekTo(
                Math.max(0, seek) * 1000L,
                MediaExtractor.SEEK_TO_PREVIOUS_SYNC
        );

        codec.flush();

        positionBaseMs = Math.max(0, seek);
        framesWritten = 0;
        resetFilters();

        if (audioTrack != null) {
            audioTrack.pause();
            audioTrack.flush();
        }
    }

    private void processPcm16(ByteBuffer buffer, int size) {
        int samples = size / 2;

        if (pcmBuffer == null || pcmBuffer.length < samples) {
            pcmBuffer = new short[samples];
        }

        buffer.order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .get(pcmBuffer, 0, samples);

        boolean eqActive = false;
        for (BandFilter filter : filters) {
            if (Math.abs(filter.getGain()) > 0.001f) {
                eqActive = true;
                break;
            }
        }

        boolean dspActive = eqActive
                || Math.abs(preampDb) > 0.001f
                || bassBoost > 0.001f
                || tubeDrive > 0.001f
                || vinylAmount > 0.001f
                || limiterEnabled;

        if (!dspActive) {
            if (audioTrack != null && playing) {
                int written = audioTrack.write(
                        pcmBuffer, 0, samples, AudioTrack.WRITE_BLOCKING);
                if (written > 0) framesWritten += written / channelCount;
            }
            return;
        }

        float preampLinear = (float) Math.pow(10.0, preampDb / 20.0);

        for (int i = 0; i < samples; i++) {
            int channel = i % channelCount;
            float sample = pcmBuffer[i] / 32768.0f;

            sample *= preampLinear;

            if (eqActive) {
                for (BandFilter filter : filters) {
                    sample = filter.process(sample, channel);
                }
            }

            if (bassBoost > 0f) {
                float low = bassFilter.process(sample, channel);
                sample += low * (bassBoost / 12f) * 0.35f;
            }

            if (tubeDrive > 0f) {
                float amount = tubeDrive / 12f;
                float drive = 1f + amount * 2.0f;
                float saturated = (float) Math.tanh(sample * drive)
                        / (float) Math.tanh(drive);
                float mix = amount * 0.28f;
                sample = sample * (1f - mix) + saturated * mix;
            }

            if (vinylAmount > 0f) {
                noiseState = noiseState * 1664525L + 1013904223L;
                float noise = (((noiseState >>> 16) & 0x7fff) / 16384f) - 1f;
                sample += noise * (vinylAmount / 100f) * 0.018f;
            }

            if (limiterEnabled && Math.abs(sample) > 0.70f) {
                sample = (float) Math.tanh(sample * 1.25f) * 0.80f;
            }

            if (sample > 1f) sample = 1f;
            if (sample < -1f) sample = -1f;

            pcmBuffer[i] = (short) (sample * 32767f);
        }

        if (audioTrack != null && playing) {
            int written = audioTrack.write(
                    pcmBuffer, 0, samples, AudioTrack.WRITE_BLOCKING);
            if (written > 0) framesWritten += written / channelCount;
        }
    }

    public void start() {
        if (!prepared || released) return;

        synchronized (lock) {
            playing = true;
            if (audioTrack != null) {
                audioTrack.play();
            }
            lock.notifyAll();
        }
    }

    public void pause() {
        synchronized (lock) {
            playing = false;
            if (audioTrack != null) {
                audioTrack.pause();
            }
            lock.notifyAll();
        }
    }

    public boolean isPlaying() {
        return playing;
    }

    public int getDuration() {
        return durationMs;
    }

    public int getCurrentPosition() {
        if (!prepared || audioTrack == null) return 0;

        long head = audioTrack.getPlaybackHeadPosition() & 0xffffffffL;
        long position =
                positionBaseMs
                        + (head * 1000L / Math.max(1, sampleRate));

        return (int) Math.max(
                0,
                Math.min(
                        durationMs > 0 ? durationMs : Integer.MAX_VALUE,
                        position
                )
        );
    }

    public void seekTo(int positionMs) {
        if (released) return;

        pendingSeekMs = Math.max(0, positionMs);

        synchronized (lock) {
            lock.notifyAll();
        }
    }

    public int getAudioSessionId() {
        return audioTrack == null ? 0 : audioTrack.getAudioSessionId();
    }

    public void setBandGain(int band, float gainDb) {
        if (band >= 0 && band < filters.length) {
            filters[band].setGain(gainDb);
            guardarEcualizacion();
        }
    }

    public float getBandGain(int band) {
        return band >= 0 && band < filters.length
                ? filters[band].getGain()
                : 0f;
    }

    public int getBandCount() {
        return filters.length;
    }

    public float getPreampDb() {
        return preampDb;
    }

    public void setPreampDb(float value) {
        preampDb = Math.max(-12f, Math.min(12f, value));
        guardarAudioAvanzado();
    }

    public boolean isLimiterEnabled() {
        return limiterEnabled;
    }

    public void setLimiterEnabled(boolean enabled) {
        limiterEnabled = enabled;
        guardarAudioAvanzado();
    }

    public float getBassBoost() {
        return bassBoost;
    }

    public void setBassBoost(float value) {
        bassBoost = Math.max(0f, Math.min(12f, value));
        guardarAudioAvanzado();
    }

    public float getTubeDrive() {
        return tubeDrive;
    }

    public void setTubeDrive(float value) {
        tubeDrive = Math.max(0f, Math.min(12f, value));
        guardarAudioAvanzado();
    }

    public float getVinylAmount() {
        return vinylAmount;
    }

    public void setVinylAmount(float value) {
        vinylAmount = Math.max(0f, Math.min(100f, value));
        guardarAudioAvanzado();
    }

    private void resetFilters() {
        for (BandFilter filter : filters) {
            filter.reset();
        }
        bassFilter.reset();
    }

    public void release() {
        released = true;
        playing = false;

        synchronized (lock) {
            lock.notifyAll();
        }

        Thread t = worker;
        if (t != null) {
            t.interrupt();
        }

        cleanup();

        if (activePlayer == this) {
            activePlayer = null;
        }
    }

    private synchronized void cleanup() {
        try {
            if (audioTrack != null) {
                audioTrack.pause();
                audioTrack.flush();
                audioTrack.release();
            }
        } catch (Exception ignored) {
        }
        audioTrack = null;

        try {
            if (codec != null) {
                codec.stop();
                codec.release();
            }
        } catch (Exception ignored) {
        }
        codec = null;

        try {
            if (extractor != null) {
                extractor.release();
            }
        } catch (Exception ignored) {
        }
        extractor = null;
    }

    private static class BandFilter {
        private final float freq;
        private float gainDb;
        private float sampleRate = 44100f;

        private float b0;
        private float b1;
        private float b2;
        private float a1;
        private float a2;

        private final float[] x1 = new float[2];
        private final float[] x2 = new float[2];
        private final float[] y1 = new float[2];
        private final float[] y2 = new float[2];

        BandFilter(float frequency) {
            freq = frequency;
            recalculate();
        }

        void configure(float rate) {
            sampleRate = rate;
            recalculate();
        }

        void setGain(float db) {
            gainDb = Math.max(-12f, Math.min(12f, db));
            recalculate();
        }

        float getGain() {
            return gainDb;
        }

        void reset() {
            for (int i = 0; i < 2; i++) {
                x1[i] = 0f;
                x2[i] = 0f;
                y1[i] = 0f;
                y2[i] = 0f;
            }
        }

        private void recalculate() {
            double A = Math.pow(10.0, gainDb / 40.0);
            double omega =
                    2.0 * Math.PI * freq
                            / Math.max(1.0, sampleRate);
            double alpha = Math.sin(omega) / 2.0;
            double cos = Math.cos(omega);

            double bb0 = 1.0 + alpha * A;
            double bb1 = -2.0 * cos;
            double bb2 = 1.0 - alpha * A;

            double aa0 = 1.0 + alpha / A;
            double aa1 = -2.0 * cos;
            double aa2 = 1.0 - alpha / A;

            b0 = (float) (bb0 / aa0);
            b1 = (float) (bb1 / aa0);
            b2 = (float) (bb2 / aa0);
            a1 = (float) (aa1 / aa0);
            a2 = (float) (aa2 / aa0);
        }

        float process(float x, int channel) {
            int ch = channel == 0 ? 0 : 1;

            float y =
                    b0 * x
                            + b1 * x1[ch]
                            + b2 * x2[ch]
                            - a1 * y1[ch]
                            - a2 * y2[ch];

            x2[ch] = x1[ch];
            x1[ch] = x;
            y2[ch] = y1[ch];
            y1[ch] = y;

            return y;
        }
    }
}
