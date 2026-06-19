package com.codewhale.musicplayer;

import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.codewhale.musicplayer.model.Playlist;
import com.codewhale.musicplayer.model.Song;
import com.codewhale.musicplayer.player.MusicService;
import com.codewhale.musicplayer.player.PlayerController;

/**
 * Full-screen Now Playing activity — SPlayer-style:
 * Large album art, scrolling lyrics, sleek playback controls.
 * Designed for landscape car head units (800x480 / 1024x600).
 */
public class NowPlayingActivity extends AppCompatActivity {

    // ── UI ──────────────────────────────────────────────────────────────
    private ImageView albumArt;
    private TextView lyricsText;
    private ScrollView lyricsScroll;
    private TextView titleText, artistText, albumText;
    private SeekBar seekBar;
    private TextView timeCurrent, timeTotal;
    private ImageButton btnPrev, btnPlay, btnNext, btnRepeat, btnShuffle;
    private View overlayGradient;

    // ── Playback ────────────────────────────────────────────────────────
    private PlayerController controller;
    private Playlist playlist;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean userSeeking = false;
    private Song currentSong;
    private ObjectAnimator discRotation;
    private int lyricsScrollPos = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Immersive fullscreen
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_now_playing);
        initViews();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (controller == null) {
            controller = new PlayerController(this);
            controller.setSeekBar(seekBar);
            controller.setOnStateChangeListener(new PlayerController.OnStateChangeListener() {
                @Override public void onPlayingChanged(boolean playing) {
                    updatePlayButton(playing);
                    if (playing) startDiscRotation();
                    else pauseDiscRotation();
                }
                @Override public void onSongChanged(Song song) {
                    updateSongInfo(song);
                    startDiscRotation();
                }
                @Override public void onPositionChanged(int pos, int dur) {
                    if (!userSeeking && dur > 0) {
                        seekBar.setMax(dur);
                        seekBar.setProgress(pos);
                        timeCurrent.setText(PlayerController.formatTime(pos));
                        timeTotal.setText(PlayerController.formatTime(dur));
                    }
                    // Auto-scroll lyrics
                    autoScrollLyrics(dur, pos);
                }
            });
        }
        controller.bind();

        // Listen for state broadcasts directly (for lyrics)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(stateReceiver, new IntentFilter(MusicService.BROADCAST_STATE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (controller != null) controller.unbind();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stateReceiver);
        handler.removeCallbacks(lyricsScroller);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (discRotation != null) discRotation.cancel();
    }

    // ── Init ────────────────────────────────────────────────────────────

    private void initViews() {
        albumArt     = findViewById(R.id.np_album_art);
        lyricsText   = findViewById(R.id.np_lyrics);
        lyricsScroll = findViewById(R.id.np_lyrics_scroll);
        titleText    = findViewById(R.id.np_title);
        artistText   = findViewById(R.id.np_artist);
        albumText    = findViewById(R.id.np_album);
        seekBar      = findViewById(R.id.np_seekbar);
        timeCurrent  = findViewById(R.id.np_time_current);
        timeTotal    = findViewById(R.id.np_time_total);
        btnPrev      = findViewById(R.id.np_prev);
        btnPlay      = findViewById(R.id.np_play);
        btnNext      = findViewById(R.id.np_next);
        btnRepeat    = findViewById(R.id.np_repeat);
        btnShuffle   = findViewById(R.id.np_shuffle);
        overlayGradient = findViewById(R.id.np_overlay);

        // Back button
        findViewById(R.id.np_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        // Play/Pause
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (controller != null) controller.toggle();
            }
        });

        // Prev
        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (controller != null) controller.previous();
            }
        });

        // Next
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (controller != null) controller.next();
            }
        });

        // Repeat
        btnRepeat.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (controller == null || controller.getService() == null) return;
                Playlist pl = controller.getService().getPlaylist();
                pl.cycleRepeatMode();
                updateRepeatButton(pl);
            }
        });

        // Shuffle
        btnShuffle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (controller == null || controller.getService() == null) return;
                Playlist pl = controller.getService().getPlaylist();
                pl.setShuffle(!pl.isShuffle());
                btnShuffle.setAlpha(pl.isShuffle() ? 1.0f : 0.4f);
            }
        });

        // SeekBar
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) timeCurrent.setText(PlayerController.formatTime(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                userSeeking = false;
                controller.seekTo(sb.getProgress());
            }
        });

        // Click album art for immersive mode toggle
        albumArt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                toggleImmersive();
            }
        });
    }

    // ── State receiver ──────────────────────────────────────────────────

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!MusicService.BROADCAST_STATE.equals(intent.getAction())) return;

            Song song = intent.getParcelableExtra(MusicService.EXTRA_SONG);
            boolean playing = intent.getBooleanExtra(MusicService.EXTRA_PLAYING, false);
            String lyrics = intent.getStringExtra(MusicService.EXTRA_LYRICS);

            if (song != null && (currentSong == null || !song.equals(currentSong))) {
                updateSongInfo(song);
            }

            if (lyrics != null && !lyrics.isEmpty()) {
                lyricsText.setText(lyrics);
                lyricsText.setVisibility(View.VISIBLE);
                lyricsScrollPos = 0;
                lyricsScroll.scrollTo(0, 0);
            }

            updatePlayButton(playing);
        }
    };

    // ── Song info ───────────────────────────────────────────────────────

    private void updateSongInfo(Song song) {
        if (song == null) return;
        currentSong = song;

        titleText.setText(song.getDisplayTitle());
        artistText.setText(song.getDisplayArtist());
        albumText.setText((song.album != null && !song.album.isEmpty())
            ? song.album : " ");

        // Load album art
        if (song.albumArt != null && song.albumArt.length > 0) {
            loadAlbumArtLarge(song.albumArt);
        } else {
            albumArt.setImageResource(R.drawable.ic_music_note);
            albumArt.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        }

        // Load lyrics from Song model
        String lyrics = (song.lyrics != null && !song.lyrics.isEmpty())
            ? song.lyrics : null;
        if (lyrics != null) {
            lyricsText.setText(lyrics);
            lyricsText.setVisibility(View.VISIBLE);
            lyricsScrollPos = 0;
            lyricsScroll.scrollTo(0, 0);
        } else {
            lyricsText.setVisibility(View.GONE);
        }

        // Update repeat/shuffle
        if (controller != null && controller.getService() != null) {
            Playlist pl = controller.getService().getPlaylist();
            updateRepeatButton(pl);
            btnShuffle.setAlpha(pl.isShuffle() ? 1.0f : 0.4f);
        }

        startDiscRotation();
    }

    // ── Album art ───────────────────────────────────────────────────────

    private void loadAlbumArtLarge(final byte[] artData) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(artData, 0, artData.length, opts);

                    int sampleSize = 1;
                    int targetW = 512;
                    if (opts.outWidth > targetW) {
                        sampleSize = opts.outWidth / targetW;
                    }

                    opts = new BitmapFactory.Options();
                    opts.inSampleSize = sampleSize;
                    final Bitmap bmp = BitmapFactory.decodeByteArray(artData, 0, artData.length, opts);

                    if (bmp != null) {
                        albumArt.post(new Runnable() {
                            @Override
                            public void run() {
                                albumArt.setImageBitmap(bmp);
                                albumArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            }
                        });
                    }
                } catch (Exception | OutOfMemoryError ignored) {
                    albumArt.post(new Runnable() {
                        @Override public void run() {
                            albumArt.setImageResource(R.drawable.ic_music_note);
                            albumArt.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        }
                    });
                }
            }
        }).start();
    }

    // ── Disc rotation animation ─────────────────────────────────────────

    private void startDiscRotation() {
        if (discRotation != null) discRotation.cancel();
        discRotation = ObjectAnimator.ofFloat(albumArt, "rotation", 0f, 360f);
        discRotation.setDuration(20000);
        discRotation.setInterpolator(new LinearInterpolator());
        discRotation.setRepeatCount(ObjectAnimator.INFINITE);
        discRotation.start();
    }

    private void pauseDiscRotation() {
        if (discRotation != null) discRotation.pause();
    }

    private void resumeDiscRotation() {
        if (discRotation != null && discRotation.isPaused()) discRotation.resume();
    }

    // ── Lyrics auto-scroll ──────────────────────────────────────────────

    private void autoScrollLyrics(int duration, int position) {
        if (duration <= 0 || lyricsText.getVisibility() != View.VISIBLE) return;
        // For unsynchronized lyrics, scroll proportionally
        float fraction = (float) position / duration;
        int maxScroll = Math.max(0, lyricsText.getHeight() - lyricsScroll.getHeight() + 80);
        int targetY = (int) (maxScroll * fraction);
        if (Math.abs(targetY - lyricsScrollPos) > 20) {
            lyricsScrollPos = targetY;
            lyricsScroll.smoothScrollTo(0, targetY);
        }
    }

    private final Runnable lyricsScroller = new Runnable() {
        @Override public void run() {
            if (controller != null && controller.getService() != null) {
                int pos = controller.getService().getCurrentPosition();
                int dur = controller.getService().getDuration();
                autoScrollLyrics(dur, pos);
            }
            handler.postDelayed(this, 1000);
        }
    };

    // ── Play button state ───────────────────────────────────────────────

    private void updatePlayButton(boolean playing) {
        btnPlay.setImageResource(playing
            ? R.drawable.ic_pause_circle
            : R.drawable.ic_play_circle);
        if (playing) resumeDiscRotation();
        else pauseDiscRotation();
    }

    private void updateRepeatButton(Playlist pl) {
        switch (pl.getRepeatMode()) {
            case Playlist.REPEAT_OFF:
                btnRepeat.setImageResource(R.drawable.ic_repeat);
                btnRepeat.setAlpha(0.3f);
                break;
            case Playlist.REPEAT_ALL:
                btnRepeat.setImageResource(R.drawable.ic_repeat);
                btnRepeat.setAlpha(1.0f);
                break;
            case Playlist.REPEAT_ONE:
                btnRepeat.setImageResource(R.drawable.ic_repeat_one);
                btnRepeat.setAlpha(1.0f);
                break;
        }
    }

    // ── Immersive toggle ────────────────────────────────────────────────

    private boolean immersive = true;
    private void toggleImmersive() {
        immersive = !immersive;
        if (immersive) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            overlayGradient.setVisibility(View.GONE);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_VISIBLE);
            overlayGradient.setVisibility(View.VISIBLE);
        }
    }
}
