package com.trotabares.aguara;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AguaraPcmPlayer {

    public interface OnPreparedListener { void onPrepared(AguaraPcmPlayer player); }
    public interface OnCompletionListener { void onCompletion(AguaraPcmPlayer player); }
    public interface OnErrorListener { boolean onError(AguaraPcmPlayer player, int what, int extra); }

    private static volatile AguaraPcmPlayer activePlayer;

    private final Context context;
    private Uri sourceUri;
    private MediaExtractor extractor;
    private MediaCodec codec;
    private AudioTrack audioTrack;
    private Thread worker;

    private volatile boolean released;
    private volatile boolean playing;
    private volatile boolean prepared;
    private volatile boolean endOfStream;
    private volatile long pendingSeekMs = -1L;

    private int durationMs;
    private int sampleRate = 44100;
    private int channelCount = 2;
    private long positionBaseMs;
    private long framesWritten;

    private OnPreparedListener preparedListener;
    private OnCompletionListener completionListener;
    private OnErrorListener errorListener;

    private final BandFilter[] filters = new BandFilter[]{\n            new BandFilter(60f),\n            new BandFilter(250f),\n            new BandFilter(1000f),\n            new BandFilter(4000f),\n            new BandFilter(12000f)\n    };
    private final Object lock = new Object();

    public AguaraPcmPlayer(Context context) {
        this.context = context.getApplicationContext();
        for (int i = 0; i < filters.length; i++) {
            filters[i] = new BandFilter();
        }
        activePlayer = this;
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
            extractor = new MediaExtractor();
            extractor.setDataSource(context, sourceUri, null);

            int track = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    break;
                }
            }
            if (track < 0) throw new IllegalStateException("No hay pista de audio");

            extractor.selectTrack(track);
            MediaFormat inputFormat = extractor.getTrackFormat(track);

            if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                durationMs = (int)Math.min(Integer.MAX_VALUE,
                        inputFormat.getLong(MediaFormat.KEY_DURATION) / 1000L);
            }

            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(inputFormat, null, null, 0);
            codec.start();

            boolean formatReady = false;
            boolean inputDone = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            ByteBuffer inputData = null;

            while (!released) {
                if (pendingSeekMs >= 0) {
                    long seek = pendingSeekMs;
                    pendingSeekMs = -1L;
                    extractor.seekTo(seek * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    codec.flush();
                    inputDone = false;
                    endOfStream = false;
                    positionBaseMs = seek;
                    framesWritten = 0;
                    if (audioTrack != null) {
                        audioTrack.pause();
                        audioTrack.flush();
                    }
                    resetFilters();
                    continue;
                }

                synchronized (lock) {
                    if (!playing && prepared) {
                        try { lock.wait(100); } catch (InterruptedException ignored) {}
                        continue;
                    }
                }

                if (!inputDone) {
                    int inputIndex = codec.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        inputData = codec.getInputBuffer(inputIndex);
                        if (inputData == null) continue;
                        inputData.clear();
                        int size = extractor.readSampleData(inputData, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long pts = extractor.getSampleTime();
                            codec.queueInputBuffer(inputIndex, 0, size, pts, 0);
                            extractor.advance();
                        }
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(info, 10000);

                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat out = codec.getOutputFormat();
                    sampleRate = out.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                            ? out.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
                    channelCount = out.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                            ? out.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
                    if (channelCount != 1 && channelCount != 2) {
                        throw new IllegalStateException("Canales no soportados: " + channelCount);
                    }

                    int channelMask = channelCount == 1
                            ? AudioFormat.CHANNEL_OUT_MONO
                            : AudioFormat.CHANNEL_OUT_STEREO;

                    int minBuffer = AudioTrack.getMinBufferSize(
                            sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT);
                    if (minBuffer <= 0) throw new IllegalStateException("AudioTrack no disponible");

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
                            .setBufferSizeInBytes(Math.max(minBuffer * 2, 16384))
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build();

                    if (old != null) {
                        try { old.release(); } catch (Exception ignored) {}
                    }

                    for (BandFilter f : filters) f.configure(sampleRate);
                    prepared = true;

                    if (preparedListener != null) {
                        preparedListener.onPrepared(this);
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

                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(outputIndex, false);

                    if (eos) {
                        endOfStream = true;
                        playing = false;
                        if (audioTrack != null) audioTrack.pause();
                        if (completionListener != null && !released) {
                            completionListener.onCompletion(this);
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            if (!released && errorListener != null) {
                errorListener.onError(this, -1, 0);
            }
        } finally {
            cleanup();
        }
    }

    private void processPcm16(ByteBuffer buffer, int size) {
        int samples = size / 2;
        short[] pcm = new short[samples];
        buffer.order(ByteOrder.nativeOrder()).asShortBuffer().get(pcm);

        for (int i = 0; i < pcm.length; i++) {
            float sample = pcm[i] / 32768.0f;
            for (BandFilter filter : filters) {
                sample = filter.process(sample);
            }
            if (sample > 1f) sample = 1f;
            if (sample < -1f) sample = -1f;
            pcm[i] = (short)(sample * 32767f);
        }

        if (audioTrack != null && playing) {
            int written = audioTrack.write(pcm, 0, pcm.length, AudioTrack.WRITE_BLOCKING);
            if (written > 0) {
                framesWritten += written / channelCount;
            }
        }
    }

    public void start() {
        if (!prepared || released) return;
        synchronized (lock) {
            playing = true;
            if (audioTrack != null) audioTrack.play();
            lock.notifyAll();
        }
    }

    public void pause() {
        synchronized (lock) {
            playing = false;
            if (audioTrack != null) audioTrack.pause();
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
        if (!prepared) return 0;
        long head = audioTrack == null ? 0 : (audioTrack.getPlaybackHeadPosition() & 0xffffffffL);
        long pos = positionBaseMs + (head * 1000L / Math.max(1, sampleRate));
        return (int)Math.max(0, Math.min(durationMs > 0 ? durationMs : Integer.MAX_VALUE, pos));
    }

    public void seekTo(int positionMs) {
        if (released) return;
        pendingSeekMs = Math.max(0, positionMs);
        synchronized (lock) { lock.notifyAll(); }
    }

    public int getAudioSessionId() {
        return audioTrack == null ? 0 : audioTrack.getAudioSessionId();
    }

    public void setBandGain(int band, float gainDb) {
        if (band >= 0 && band < filters.length) {
            filters[band].setGain(gainDb);
        }
    }

    public float getBandGain(int band) {
        return band >= 0 && band < filters.length ? filters[band].getGain() : 0f;
    }

    public int getBandCount() { return filters.length; }

    private void resetFilters() {
        for (BandFilter f : filters) f.reset();
    }

    public void release() {
        released = true;
        playing = false;
        synchronized (lock) { lock.notifyAll(); }
        Thread t = worker;
        if (t != null) t.interrupt();
        cleanup();
        if (activePlayer == this) activePlayer = null;
    }

    private synchronized void cleanup() {
        try { if (audioTrack != null) { audioTrack.pause(); audioTrack.flush(); audioTrack.release(); } } catch (Exception ignored) {}
        audioTrack = null;
        try { if (codec != null) { codec.stop(); codec.release(); } } catch (Exception ignored) {}
        codec = null;
        try { if (extractor != null) extractor.release(); } catch (Exception ignored) {}
        extractor = null;
    }

    private static class BandFilter {
        private float gainDb;
        private float sampleRate = 44100f;
        private float freq = 1000f;
        private float z1;
        private float z2;

        void configure(float rate) { sampleRate = rate; }
        void setGain(float db) { gainDb = Math.max(-12f, Math.min(12f, db)); }
        float getGain() { return gainDb; }
        void reset() { z1 = z2 = 0f; }

        float process(float x) {
            // Simple first-order tilt per band. Five cascaded stages give
            // a lightweight, fully local DSP without device Equalizer APIs.
            float normalized = freq / Math.max(1f, sampleRate);
            float alpha = Math.max(0.001f, Math.min(0.25f, normalized * 0.35f));
            float target = (float)Math.pow(10.0, gainDb / 20.0);
            float y = x + alpha * (target - 1f) * (x - z1);
            z2 = z1;
            z1 = y;
            return y;
        }
    }
}
