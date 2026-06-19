package com.codewhale.musicplayer.player;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.codewhale.musicplayer.model.Song;

/**
 * Convenience controller that binds to MusicService, receives state updates,
 * and manages seekbar / time-label synchronization.
 *
 * Usage from an Activity:
 *   controller = new PlayerController(this);
 *   controller.bind();
 *   controller.setSeekBar(seekBar);
 *   controller.setTimeLabel(timeText);
 */
public class PlayerController {

    private final Context context;
    private MusicService service;
    private boolean bound = false;

    private SeekBar seekBar;
    private TextView timeLabel;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean userSeeking = false;

    // Callback
    public interface OnStateChangeListener {
        void onPlayingChanged(boolean playing);
        void onSongChanged(Song song);
        void onPositionChanged(int position, int duration);
    }

    private OnStateChangeListener listener;

    // ── Service Connection ──────────────────────────────────────────────

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            MusicService.MusicBinder musicBinder = (MusicService.MusicBinder) binder;
            service = musicBinder.getService();
            bound = true;
            startSeekBarUpdate();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
        }
    };

    // ── State receiver ──────────────────────────────────────────────────

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!MusicService.BROADCAST_STATE.equals(intent.getAction())) return;

            boolean playing = intent.getBooleanExtra(MusicService.EXTRA_PLAYING, false);
            int position = intent.getIntExtra(MusicService.EXTRA_POSITION, 0);
            int duration = intent.getIntExtra(MusicService.EXTRA_DURATION, 0);
            Song song = intent.getParcelableExtra(MusicService.EXTRA_SONG);

            if (!userSeeking && seekBar != null && duration > 0) {
                seekBar.setMax(duration);
                seekBar.setProgress(position);
            }

            if (timeLabel != null && duration > 0) {
                timeLabel.setText(formatTime(position) + " / " + formatTime(duration));
            }

            if (listener != null) {
                listener.onPositionChanged(position, duration);
                if (song != null) listener.onSongChanged(song);
                listener.onPlayingChanged(playing);
            }
        }
    };

    public PlayerController(Context context) {
        this.context = context.getApplicationContext();
    }

    // ── Bind / Unbind ───────────────────────────────────────────────────

    public void bind() {
        Intent intent = new Intent(context, MusicService.class);
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(context)
            .registerReceiver(stateReceiver, new IntentFilter(MusicService.BROADCAST_STATE));
    }

    public void unbind() {
        stopSeekBarUpdate();
        LocalBroadcastManager.getInstance(context).unregisterReceiver(stateReceiver);
        if (bound) {
            context.unbindService(connection);
            bound = false;
        }
    }

    // ── Public controls ─────────────────────────────────────────────────

    public void play()              { sendCommand(MusicService.ACTION_PLAY); }
    public void pause()             { sendCommand(MusicService.ACTION_PAUSE); }
    public void toggle()            { sendCommand(MusicService.ACTION_TOGGLE); }
    public void next()              { sendCommand(MusicService.ACTION_NEXT); }
    public void previous()          { sendCommand(MusicService.ACTION_PREV); }
    public void playSong(int index) { MusicService.sendPlaySong(context, index); }

    public void seekTo(int msec)    { MusicService.sendSeek(context, msec); }

    public boolean isBound()        { return bound && service != null; }
    public MusicService getService() { return service; }

    // ── UI binding ──────────────────────────────────────────────────────

    public void setSeekBar(SeekBar sb) {
        this.seekBar = sb;
        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && timeLabel != null) {
                        timeLabel.setText(formatTime(progress));
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    userSeeking = true;
                }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    userSeeking = false;
                    seekTo(seekBar.getProgress());
                }
            });
        }
    }

    public void setTimeLabel(TextView label) {
        this.timeLabel = label;
    }

    public void setOnStateChangeListener(OnStateChangeListener l) {
        this.listener = l;
    }

    // ── SeekBar auto-update ─────────────────────────────────────────────

    private final Runnable seekUpdater = new Runnable() {
        @Override
        public void run() {
            if (service != null && service.isPlaying() && !userSeeking && seekBar != null) {
                int pos = service.getCurrentPosition();
                int dur = service.getDuration();
                if (dur > 0) {
                    seekBar.setMax(dur);
                    seekBar.setProgress(pos);
                }
                if (timeLabel != null && dur > 0) {
                    timeLabel.setText(formatTime(pos) + " / " + formatTime(dur));
                }
            }
            handler.postDelayed(this, 500);
        }
    };

    private void startSeekBarUpdate() {
        handler.post(seekUpdater);
    }

    private void stopSeekBarUpdate() {
        handler.removeCallbacks(seekUpdater);
    }

    // ── Utility ─────────────────────────────────────────────────────────

    private void sendCommand(String action) {
        MusicService.sendCommand(context, action);
    }

    public static String formatTime(int msec) {
        int sec = msec / 1000;
        int min = sec / 60;
        sec = sec % 60;
        return String.format("%d:%02d", min, sec);
    }
}
