package com.codewhale.musicplayer.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.codewhale.musicplayer.MainActivity;
import com.codewhale.musicplayer.R;
import com.codewhale.musicplayer.model.Playlist;
import com.codewhale.musicplayer.model.Song;

/**
 * Background service for music playback.
 * Manages a single MediaPlayer instance and a Playlist.
 */
public class MusicService extends Service
        implements MediaPlayer.OnPreparedListener,
                   MediaPlayer.OnErrorListener,
                   MediaPlayer.OnCompletionListener {

    // Broadcast actions
    public static final String ACTION_PLAY       = "com.codewhale.musicplayer.PLAY";
    public static final String ACTION_PAUSE      = "com.codewhale.musicplayer.PAUSE";
    public static final String ACTION_TOGGLE     = "com.codewhale.musicplayer.TOGGLE";
    public static final String ACTION_NEXT       = "com.codewhale.musicplayer.NEXT";
    public static final String ACTION_PREV       = "com.codewhale.musicplayer.PREV";
    public static final String ACTION_SEEK       = "com.codewhale.musicplayer.SEEK";
    public static final String ACTION_PLAY_SONG  = "com.codewhale.musicplayer.PLAY_SONG";
    public static final String ACTION_STOP       = "com.codewhale.musicplayer.STOP";

    // Player state broadcast
    public static final String BROADCAST_STATE   = "com.codewhale.musicplayer.STATE";
    public static final String EXTRA_PLAYING     = "playing";
    public static final String EXTRA_SONG        = "song";
    public static final String EXTRA_POSITION    = "position";
    public static final String EXTRA_DURATION    = "duration";
    public static final String EXTRA_INDEX       = "index";
    public static final String EXTRA_REPEAT      = "repeat";
    public static final String EXTRA_SHUFFLE     = "shuffle";

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "music_player";

    private MediaPlayer mediaPlayer;
    private Playlist playlist;
    private boolean isPlaying = false;
    private boolean isPreparing = false;

    private final IBinder binder = new MusicBinder();

    public class MusicBinder extends Binder {
        public MusicService getService() { return MusicService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // Register broadcast receiver for control commands
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_TOGGLE);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_PREV);
        filter.addAction(ACTION_STOP);
        LocalBroadcastManager.getInstance(this).registerReceiver(controlReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                handleAction(action, intent);
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(controlReceiver);
        stopForeground(true);
        releasePlayer();
        super.onDestroy();
    }

    // ── Public API ──────────────────────────────────────────────────────

    public Playlist getPlaylist() {
        if (playlist == null) playlist = new Playlist();
        return playlist;
    }

    public void setPlaylist(Playlist list) {
        this.playlist = list;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public int getCurrentPosition() {
        if (mediaPlayer != null && isPlaying) {
            try { return mediaPlayer.getCurrentPosition(); } catch (Exception e) { return 0; }
        }
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null) {
            try { return mediaPlayer.getDuration(); } catch (Exception e) { return 0; }
        }
        return 0;
    }

    public void play() {
        if (playlist == null || playlist.isEmpty()) return;

        if (isPlaying) return;

        if (mediaPlayer != null && !isPreparing) {
            mediaPlayer.start();
            isPlaying = true;
            broadcastState();
            updateNotification();
            return;
        }

        Song song = playlist.current();
        if (song != null) {
            playSong(song);
        }
    }

    public void playSong(Song song) {
        if (song == null) return;
        releasePlayer();
        isPreparing = true;

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnCompletionListener(this);

        try {
            mediaPlayer.setDataSource(song.filePath);
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            isPreparing = false;
            broadcastState();
        }
    }

    public void playAtIndex(int index) {
        if (playlist == null || index < 0 || index >= playlist.size()) return;
        playlist.setCurrentIndex(index);
        Song song = playlist.current();
        if (song != null) playSong(song);
    }

    public void pause() {
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            broadcastState();
            updateNotification();
        }
    }

    public void toggle() {
        if (isPlaying) pause(); else play();
    }

    public void next() {
        if (playlist == null || playlist.isEmpty()) return;
        Song next = playlist.next();
        if (next != null) playSong(next);
    }

    public void previous() {
        if (playlist == null || playlist.isEmpty()) return;
        // If more than 3 sec into the song, restart current; else go to previous
        if (getCurrentPosition() > 3000) {
            seekTo(0);
        } else {
            Song prev = playlist.previous();
            if (prev != null) playSong(prev);
        }
    }

    public void seekTo(int msec) {
        if (mediaPlayer != null) {
            try { mediaPlayer.seekTo(msec); } catch (Exception e) { /* ignore */ }
        }
    }

    public void stop() {
        releasePlayer();
        isPlaying = false;
        isPreparing = false;
        stopForeground(true);
        stopSelf();
    }

    // ── MediaPlayer callbacks ───────────────────────────────────────────

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPreparing = false;
        mp.start();
        isPlaying = true;
        startForeground(NOTIFICATION_ID, buildNotification());
        broadcastState();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        isPreparing = false;
        isPlaying = false;
        broadcastState();
        // Try next song
        next();
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        isPlaying = false;
        broadcastState();
        // Auto-play next
        if (playlist != null && playlist.hasNext()) {
            next();
        } else if (playlist != null && playlist.getRepeatMode() == Playlist.REPEAT_OFF) {
            // Stop if no repeat
            isPlaying = false;
            broadcastState();
            stopForeground(false);
            updateNotification();
        }
    }

    // ── Broadcast receiver for control commands ─────────────────────────

    private final BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            handleAction(action, intent);
        }
    };

    private void handleAction(String action, Intent intent) {
        switch (action) {
            case ACTION_PLAY:       play(); break;
            case ACTION_PAUSE:      pause(); break;
            case ACTION_TOGGLE:     toggle(); break;
            case ACTION_NEXT:       next(); break;
            case ACTION_PREV:       previous(); break;
            case ACTION_SEEK:
                int pos = intent.getIntExtra("position", 0);
                seekTo(pos);
                break;
            case ACTION_PLAY_SONG:
                int idx = intent.getIntExtra(EXTRA_INDEX, -1);
                if (idx >= 0) playAtIndex(idx);
                break;
            case ACTION_STOP:
                stop();
                break;
        }
    }

    // ── State broadcasting ──────────────────────────────────────────────

    private void broadcastState() {
        Intent intent = new Intent(BROADCAST_STATE);
        intent.putExtra(EXTRA_PLAYING, isPlaying);
        intent.putExtra(EXTRA_POSITION, getCurrentPosition());
        intent.putExtra(EXTRA_DURATION, getDuration());
        if (playlist != null) {
            intent.putExtra(EXTRA_INDEX, playlist.getCurrentIndex());
            intent.putExtra(EXTRA_REPEAT, playlist.getRepeatMode());
            intent.putExtra(EXTRA_SHUFFLE, playlist.isShuffle());
            Song current = playlist.current();
            if (current != null) {
                intent.putExtra(EXTRA_SONG, current);
            }
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    // ── Notification ────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Music Playback",
                NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Music playback controls");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Song song = (playlist != null) ? playlist.current() : null;
        String title = (song != null) ? song.getDisplayTitle() : "Music Player";
        String artist = (song != null) ? song.getDisplayArtist() : "";

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(title)
               .setContentText(artist)
               .setSmallIcon(R.drawable.ic_music_note)
               .setContentIntent(pendingOpen)
               .setOngoing(isPlaying);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            builder.setPriority(Notification.PRIORITY_LOW);
        }

        return builder.build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    // ── Cleanup ─────────────────────────────────────────────────────────

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        isPlaying = false;
        isPreparing = false;
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    public static void sendCommand(Context context, String action) {
        Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    public static void sendSeek(Context context, int msec) {
        Intent intent = new Intent(ACTION_SEEK);
        intent.putExtra("position", msec);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    public static void sendPlaySong(Context context, int index) {
        Intent intent = new Intent(ACTION_PLAY_SONG);
        intent.putExtra(EXTRA_INDEX, index);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
