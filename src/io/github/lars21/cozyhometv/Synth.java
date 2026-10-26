package io.github.lars21.cozyhometv;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** Tiny UI sounds made in code (no audio files): a short tone with a quick fade, played from a static buffer. */
final class Synth {
    static final int RATE = 22050;

    /** A tone that glides from f0 to f1 Hz over ms milliseconds. wave: 's' sine, 'q' square, 't' triangle. */
    static short[] tone(float f0, float f1, int ms, char wave, float vol) {
        int n = RATE * ms / 1000; short[] out = new short[n]; double ph = 0;
        for (int i = 0; i < n; i++) {
            float u = i / (float) n, f = f0 + (f1 - f0) * u; ph += 2 * Math.PI * f / RATE;
            double s = Math.sin(ph); if (wave == 'q') s = s >= 0 ? .6 : -.6; else if (wave == 't') s = 2 / Math.PI * Math.asin(s);
            float env = Math.min(1f, i / (RATE * .002f)) * (1 - u) * (1 - u); // 2 ms attack, soft decay
            out[i] = (short) (s * env * vol * 32767);
        }
        return out;
    }

    AudioTrack track; short[] loaded;

    void play(short[] pcm) {
        if (pcm == null) return;
        try {
            if (track == null || loaded != pcm) {
                release();
                track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.length * 2).build();
                track.write(pcm, 0, pcm.length); loaded = pcm;
            } else { track.stop(); track.reloadStaticData(); }
            track.play();
        } catch (Exception e) { release(); }
    }

    void release() { if (track != null) { try { track.release(); } catch (Exception ignored) {} } track = null; loaded = null; }
}
