package com.codewhale.musicplayer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.codewhale.musicplayer.adapter.SongAdapter;
import com.codewhale.musicplayer.model.Playlist;
import com.codewhale.musicplayer.model.Song;
import com.codewhale.musicplayer.player.MusicService;
import com.codewhale.musicplayer.player.PlayerController;
import com.codewhale.musicplayer.util.FileScanner;
import com.codewhale.musicplayer.util.ImageLoader;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST = 100;

    // Left: song list
    private RecyclerView recyclerView;
    private SongAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyText;

    // Right: now-playing preview panel
    private View nowPlayingPanel;
    private ImageView npPreviewArt;
    private TextView npPreviewTitle, npPreviewArtist;
    private ImageButton npQuickPrev, npQuickPlay, npQuickNext;
    private TextView npOpenFull;

    // Bottom mini player bar
    private View playerBar;
    private ImageView albumArt;
    private TextView songTitle, songArtist;
    private ImageButton btnPrev, btnPlay, btnNext, btnRepeat, btnShuffle;
    private SeekBar seekBar;
    private TextView timeLabel;

    private PlayerController controller;
    private Playlist playlist;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initRecyclerView();
        initPlayerBar();
        initNowPlayingPanel();

        playlist = new Playlist();

        if (hasStoragePermission()) {
            scanMedia();
        } else {
            requestStoragePermission();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (controller == null) {
            controller = new PlayerController(this);
            controller.setOnStateChangeListener(new PlayerController.OnStateChangeListener() {
                @Override
                public void onPlayingChanged(boolean playing) {
                    btnPlay.setImageResource(playing
                        ? R.drawable.ic_pause : R.drawable.ic_play);
                    npQuickPlay.setImageResource(playing
                        ? R.drawable.ic_pause : R.drawable.ic_play);
                }
                @Override
                public void onSongChanged(Song song) {
                    updateNowPlaying(song);
                }
                @Override
                public void onPositionChanged(int position, int duration) {
                    // handled by seek bar binding
                }
            });
            controller.setSeekBar(seekBar);
            controller.setTimeLabel(timeLabel);
        }
        controller.bind();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (controller != null) controller.unbind();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            String path = intent.getData().getPath();
            if (path != null) {
                Song song = new Song(path);
                FileScanner.extractMetadata(song);
                playlist.clear();
                playlist.add(song);
                adapter.setSongs(playlist.getAll());
                playSong(0);
            }
        }
    }

    // ── Init ────────────────────────────────────────────────────────────

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_songs);
        progressBar  = findViewById(R.id.progress_bar);
        emptyText    = findViewById(R.id.text_empty);

        nowPlayingPanel  = findViewById(R.id.now_playing_panel);
        npPreviewArt     = findViewById(R.id.np_preview_art);
        npPreviewTitle   = findViewById(R.id.np_preview_title);
        npPreviewArtist  = findViewById(R.id.np_preview_artist);
        npQuickPrev      = findViewById(R.id.np_quick_prev);
        npQuickPlay      = findViewById(R.id.np_quick_play);
        npQuickNext      = findViewById(R.id.np_quick_next);
        npOpenFull       = findViewById(R.id.np_open_full);

        playerBar  = findViewById(R.id.player_bar);
        albumArt   = findViewById(R.id.img_album_art);
        songTitle  = findViewById(R.id.text_song_title);
        songArtist = findViewById(R.id.text_song_artist);
        btnPrev    = findViewById(R.id.btn_prev);
        btnPlay    = findViewById(R.id.btn_play);
        btnNext    = findViewById(R.id.btn_next);
        btnRepeat  = findViewById(R.id.btn_repeat);
        btnShuffle = findViewById(R.id.btn_shuffle);
        seekBar    = findViewById(R.id.seek_bar);
        timeLabel  = findViewById(R.id.text_time);
    }

    private void initRecyclerView() {
        adapter = new SongAdapter();
        adapter.setOnItemClickListener(new SongAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Song song, int position) {
                playlist.setCurrentIndex(position);
                playSong(position);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void initNowPlayingPanel() {
        npQuickPlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (controller != null) controller.toggle();
            }
        });
        npQuickPrev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (controller != null) controller.previous();
            }
        });
        npQuickNext.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (controller != null) controller.next();
            }
        });
        npOpenFull.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openNowPlaying();
            }
        });
        nowPlayingPanel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openNowPlaying();
            }
        });
    }

    private void initPlayerBar() {
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (controller != null) controller.toggle();
            }
        });
        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (controller != null) controller.previous();
            }
        });
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (controller != null) controller.next();
            }
        });
        btnRepeat.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                playlist.cycleRepeatMode();
                updateRepeatButton();
            }
        });
        btnShuffle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                playlist.setShuffle(!playlist.isShuffle());
                updateShuffleButton();
            }
        });
        playerBar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Song current = playlist.current();
                if (current != null && current.isVideo()) {
                    Intent intent = new Intent(MainActivity.this, VideoPlayerActivity.class);
                    intent.putExtra("filePath", current.filePath);
                    intent.putExtra("title", current.getDisplayTitle());
                    startActivity(intent);
                } else {
                    openNowPlaying();
                }
            }
        });
    }

    // ── Now Playing ─────────────────────────────────────────────────────

    private void openNowPlaying() {
        Intent intent = new Intent(this, NowPlayingActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // ── Permission ──────────────────────────────────────────────────────

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this,
            Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                new String[]{ Manifest.permission.READ_MEDIA_AUDIO }, PERMISSION_REQUEST);
        } else {
            ActivityCompat.requestPermissions(this,
                new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE }, PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanMedia();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
                emptyText.setText(R.string.permission_denied);
                emptyText.setVisibility(View.VISIBLE);
            }
        }
    }

    // ── Scan ────────────────────────────────────────────────────────────

    private void scanMedia() {
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        FileScanner.scan(this, new FileScanner.ScanCallback() {
            @Override public void onProgress(int found, int total) {}
            @Override
            public void onComplete(List<Song> songs) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        progressBar.setVisibility(View.GONE);
                        playlist.clear();
                        playlist.addAll(songs);
                        adapter.setSongs(songs);
                        if (songs.isEmpty()) {
                            emptyText.setText(R.string.no_media_found);
                            emptyText.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    // ── Playback ────────────────────────────────────────────────────────

    private void playSong(int index) {
        Intent serviceIntent = new Intent(this, MusicService.class);
        startService(serviceIntent);

        if (controller != null && controller.isBound()) {
            MusicService svc = controller.getService();
            if (svc != null) {
                svc.setPlaylist(playlist);
                svc.playAtIndex(index);
            }
        } else {
            Intent intent = new Intent(this, MusicService.class);
            intent.setAction(MusicService.ACTION_PLAY_SONG);
            intent.putExtra(MusicService.EXTRA_INDEX, index);
            startService(intent);
        }
    }

    private void updateNowPlaying(Song song) {
        if (song == null) return;

        // Bottom mini bar
        songTitle.setText(song.getDisplayTitle());
        songArtist.setText(song.getDisplayArtist());
        playerBar.setVisibility(View.VISIBLE);

        // Right panel
        npPreviewTitle.setText(song.getDisplayTitle());
        npPreviewArtist.setText(song.getDisplayArtist());

        // Album art
        if (song.albumArt != null && song.albumArt.length > 0) {
            ImageLoader.loadAlbumArt(song.albumArt, albumArt, R.drawable.ic_music_note);
            ImageLoader.loadAlbumArt(song.albumArt, npPreviewArt, R.drawable.ic_music_note);
        } else {
            albumArt.setImageResource(R.drawable.ic_music_note);
            npPreviewArt.setImageResource(R.drawable.ic_music_note);
        }

        // Highlight current song in list
        int idx = playlist.getCurrentIndex();
        if (idx >= 0) {
            recyclerView.smoothScrollToPosition(idx);
        }
    }

    private void updateRepeatButton() {
        int mode = playlist.getRepeatMode();
        btnRepeat.setImageResource(mode == Playlist.REPEAT_ONE
            ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        btnRepeat.setAlpha(mode == Playlist.REPEAT_OFF ? 0.4f : 1.0f);
    }

    private void updateShuffleButton() {
        btnShuffle.setAlpha(playlist.isShuffle() ? 1.0f : 0.4f);
    }

    // ── Menu ────────────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            scanMedia();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
