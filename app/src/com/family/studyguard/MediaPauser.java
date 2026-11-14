package com.family.studyguard;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

/**
 * 弹题时把声音掐掉并抢占音频焦点，让大多数视频播放器自动暂停；
 * 答完题再恢复原来的音量。
 */
public class MediaPauser {

    private final Context ctx;
    private AudioManager am;
    private Object focusRequest;
    private int prevVolume = -1;
    private boolean paused = false;

    public MediaPauser(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public void pause() {
        if (paused) return;
        paused = true;
        try {
            if (am == null) am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            prevVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            requestFocus();
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private void requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build();
            AudioFocusRequest req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .build();
            focusRequest = req;
            am.requestAudioFocus(req);
        } else {
            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    @SuppressWarnings("deprecation")
    public void resume() {
        if (!paused) return;
        paused = false;
        try {
            if (am == null) return;
            if (prevVolume >= 0) am.setStreamVolume(AudioManager.STREAM_MUSIC, prevVolume, 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest instanceof AudioFocusRequest) {
                am.abandonAudioFocusRequest((AudioFocusRequest) focusRequest);
            } else {
                am.abandonAudioFocus(null);
            }
        } catch (Throwable ignored) {
        }
        prevVolume = -1;
    }
}
