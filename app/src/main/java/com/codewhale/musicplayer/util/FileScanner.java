package com.codewhale.musicplayer.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.codewhale.musicplayer.id3.ID3Parser;
import com.codewhale.musicplayer.model.Song;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Scans storage for media files and extracts metadata.
 *
 * Uses Android MediaStore for fast scanning, falls back to manual
 * directory walk for files not indexed by the system.
 */
public class FileScanner {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Common audio/video extensions
    private static final Set<String> AUDIO_EXTS = new HashSet<>(Arrays.asList(
        ".mp3", ".wav", ".ogg", ".flac", ".m4a", ".aac", ".wma", ".opus"
    ));

    private static final Set<String> VIDEO_EXTS = new HashSet<>(Arrays.asList(
        ".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm", ".3gp", ".ts"
    ));

    /**
     * Callback for scan results — called on the caller's thread after each batch.
     */
    public interface ScanCallback {
        void onProgress(int found, int total);
        void onComplete(List<Song> songs);
        void onError(String message);
    }

    /**
     * Scan both MediaStore and common directories for media files.
     */
    public static void scan(final Context context, final ScanCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                List<Song> songs = new ArrayList<>();

                try {
                    // 1. Scan via MediaStore (fast)
                    songs.addAll(scanMediaStore(context));

                    // 2. Scan common directories (catch files not indexed)
                    songs.addAll(scanDirectories());

                    // 3. Deduplicate by file path
                    songs = deduplicate(songs);

                    // 4. Extract ID3 tags (already done during scan for MediaStore results,
                    //    but manual scan files need it)
                    for (Song song : songs) {
                        if (song.title == null || song.title.equals(new File(song.filePath).getName())) {
                            extractMetadata(song);
                        }
                    }
                } catch (Exception e) {
                    if (callback != null) {
                        callback.onError(e.getMessage());
                    }
                    return;
                }

                final List<Song> result = songs;
                if (callback != null) {
                    callback.onComplete(result);
                }
            }
        });
    }

    /**
     * Scan using Android MediaStore.
     */
    private static List<Song> scanMediaStore(Context context) {
        List<Song> songs = new ArrayList<>();

        // Audio
        Uri audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] audioProj = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK
        };

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                audioUri, audioProj, null, null,
                MediaStore.Audio.Media.TITLE + " ASC");
            if (cursor != null && cursor.moveToFirst()) {
                int idCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int durCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
                int yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR);
                int trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK);

                do {
                    String path = cursor.getString(dataCol);
                    if (TextUtils.isEmpty(path)) continue;

                    Song song = new Song();
                    song.id = cursor.getLong(idCol);
                    song.filePath = path;
                    song.title = cursor.getString(titleCol);
                    song.artist = cursor.getString(artistCol);
                    song.album = cursor.getString(albumCol);
                    song.durationMs = cursor.getLong(durCol);
                    song.fileSize = cursor.getLong(sizeCol);
                    song.year = cursor.getString(yearCol);
                    try {
                        int trk = cursor.getInt(trackCol);
                        if (trk > 0) song.track = String.valueOf(trk);
                    } catch (Exception ignored) {}
                    song.mediaType = Song.TYPE_AUDIO;

                    // Try to enrich with ID3 tags
                    extractMetadata(song);

                    songs.add(song);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            // MediaStore query failed — proceed with directory scan
        } finally {
            if (cursor != null) cursor.close();
        }

        // Video
        Uri videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] videoProj = {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        };

        cursor = null;
        try {
            cursor = context.getContentResolver().query(
                videoUri, videoProj, null, null,
                MediaStore.Video.Media.TITLE + " ASC");
            if (cursor != null && cursor.moveToFirst()) {
                int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE);
                int durCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);

                do {
                    String path = cursor.getString(dataCol);
                    if (TextUtils.isEmpty(path)) continue;

                    Song song = new Song();
                    song.filePath = path;
                    song.title = cursor.getString(titleCol);
                    song.durationMs = cursor.getLong(durCol);
                    song.fileSize = cursor.getLong(sizeCol);
                    song.mediaType = Song.TYPE_VIDEO;

                    songs.add(song);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            // OK
        } finally {
            if (cursor != null) cursor.close();
        }

        return songs;
    }

    /**
     * Walk common media directories.
     */
    private static List<Song> scanDirectories() {
        List<Song> songs = new ArrayList<>();

        // Common directories
        String[] dirs = {
            Environment.getExternalStorageDirectory().getAbsolutePath() + "/Music",
            Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download",
            Environment.getExternalStorageDirectory().getAbsolutePath() + "/Movies",
            Environment.getExternalStorageDirectory().getAbsolutePath() + "/Video",
            "/storage/sdcard1/Music",
            "/storage/extSdCard/Music",
            "/mnt/sdcard/Music"
        };

        for (String dir : dirs) {
            walkDir(new File(dir), songs, 0);
        }

        // Also walk the root of external storage (shallow)
        File root = Environment.getExternalStorageDirectory();
        if (root.exists()) {
            File[] children = root.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isFile() && isMediaFile(child.getName())) {
                        addFile(child, songs);
                    }
                }
            }
        }

        return songs;
    }

    private static void walkDir(File dir, List<Song> songs, int depth) {
        if (depth > 3 || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                walkDir(f, songs, depth + 1);
            } else if (f.isFile() && isMediaFile(f.getName())) {
                addFile(f, songs);
            }
        }
    }

    private static void addFile(File file, List<Song> songs) {
        Song song = new Song();
        song.filePath = file.getAbsolutePath();
        song.fileSize = file.length();
        song.title = stripExtension(file.getName());

        String name = file.getName().toLowerCase();
        if (isVideoExt(name)) {
            song.mediaType = Song.TYPE_VIDEO;
        }

        songs.add(song);
    }

    /**
     * Extract full ID3 metadata from a Song's file.
     */
    public static void extractMetadata(Song song) {
        if (song == null || song.filePath == null) return;
        File f = new File(song.filePath);
        if (!f.exists() || f.length() < 10) return;

        // Only parse ID3 for MP3 files
        if (!song.filePath.toLowerCase().endsWith(".mp3")) return;

        ID3Parser.ID3Tag tag = ID3Parser.parse(f);
        if (tag != null) {
            if (!TextUtils.isEmpty(tag.title))   song.title   = tag.title;
            if (!TextUtils.isEmpty(tag.artist))  song.artist  = tag.artist;
            if (!TextUtils.isEmpty(tag.album))   song.album   = tag.album;
            if (!TextUtils.isEmpty(tag.year))    song.year    = tag.year;
            if (!TextUtils.isEmpty(tag.track))   song.track   = tag.track;
            if (!TextUtils.isEmpty(tag.genre))   song.genre   = tag.genre;
            if (!TextUtils.isEmpty(tag.comment)) song.comment = tag.comment;
            if (!TextUtils.isEmpty(tag.lyrics))  song.lyrics  = tag.lyrics;
            if (tag.albumArt != null && tag.albumArt.length > 0) {
                song.albumArt = tag.albumArt;
                song.albumArtMimeType = tag.albumArtMimeType;
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private static boolean isMediaFile(String name) {
        String lower = name.toLowerCase();
        return isAudioExt(lower) || isVideoExt(lower);
    }

    private static boolean isAudioExt(String lower) {
        for (String ext : AUDIO_EXTS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private static boolean isVideoExt(String lower) {
        for (String ext : VIDEO_EXTS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }

    private static List<Song> deduplicate(List<Song> songs) {
        Set<String> seen = new HashSet<>();
        List<Song> unique = new ArrayList<>();
        for (Song s : songs) {
            if (s.filePath != null && seen.add(s.filePath)) {
                unique.add(s);
            }
        }
        return unique;
    }
}
