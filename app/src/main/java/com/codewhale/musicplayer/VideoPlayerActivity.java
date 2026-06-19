package com.codewhale.musicplayer;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;

/**
 * Fullscreen video player activity.
 * Uses Android's built-in VideoView for maximum compatibility with API 14+.
 */
public class VideoPlayerActivity extends Activity {

    private VideoView videoView;
    private ProgressBar progressBar;
    private TextView errorText;
    private ImageButton btnBack;

    private String filePath;
    private String title;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_video);

        // Extract extras
        filePath = getIntent().getStringExtra("filePath");
        title = getIntent().getStringExtra("title");

        if (title != null && !title.isEmpty()) {
            setTitle(title);
        }

        initViews();
        playVideo();
    }

    private void initViews() {
        videoView   = findViewById(R.id.video_view);
        progressBar = findViewById(R.id.progress_video);
        errorText   = findViewById(R.id.text_video_error);
        btnBack     = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // MediaController provides play/pause/seek forward/backward controls
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);
    }

    private void playVideo() {
        if (filePath == null) {
            showError("No video file specified");
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            showError("File not found:\n" + filePath);
            return;
        }

        try {
            Uri uri = Uri.fromFile(file);
            videoView.setVideoURI(uri);

            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    progressBar.setVisibility(View.GONE);
                    videoView.start();
                }
            });

            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    showError("Cannot play this video.\nError code: " + what + "/" + extra);
                    return true;
                }
            });

            videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    finish();
                }
            });

        } catch (Exception e) {
            showError("Error opening video:\n" + e.getMessage());
        }
    }

    private void showError(String msg) {
        progressBar.setVisibility(View.GONE);
        errorText.setVisibility(View.VISIBLE);
        errorText.setText(msg);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoView != null) {
            videoView.stopPlayback();
        }
    }
}
