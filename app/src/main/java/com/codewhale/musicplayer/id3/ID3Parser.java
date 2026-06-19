package com.codewhale.musicplayer.id3;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

/**
 * Unified ID3 tag parser — the single entry point for reading ID3 metadata
 * from MP3 files.
 *
 * Automatically detects ID3v2 (at the beginning) and ID3v1 (at the end)
 * and returns a combined result.
 *
 * Usage:
 *   ID3Tag tag = ID3Parser.parse(file);
 *   String title = tag.title;
 *   byte[] cover = tag.albumArt;
 */
public class ID3Parser {

    /** Maximum bytes to read from the start of a file for ID3v2 parsing. */
    private static final int MAX_ID3V2_READ = 256 * 1024; // 256 KB

    public static class ID3Tag {
        public String title     = "";
        public String artist    = "";
        public String album     = "";
        public String year      = "";
        public String track     = "";
        public String genre     = "";
        public String comment   = "";
        public String lyrics    = "";

        /** Raw album art / cover image bytes. Null if no embedded image. */
        public byte[] albumArt = null;

        /** MIME type of the album art (e.g. "image/jpeg", "image/png"). */
        public String albumArtMimeType = "";

        /** Parsed ID3v2 tag (may be null). */
        public ID3v2Tag v2Tag = null;

        /** Parsed ID3v1 tag (may be null). */
        public ID3v1Tag v1Tag = null;

        /** Which tag was the primary source for fields. */
        public String source = "none";

        /**
         * Merge with another tag, filling in blanks from the fallback.
         * v2 fields take priority; v1 is used as fallback.
         */
        void fillFromFallback() {
            if (v1Tag == null) return;
            if (isEmpty(title))   title   = v1Tag.title;
            if (isEmpty(artist))  artist  = v1Tag.artist;
            if (isEmpty(album))   album   = v1Tag.album;
            if (isEmpty(year))    year    = v1Tag.year;
            if (isEmpty(genre))   genre   = v1Tag.genre;
            if (isEmpty(comment)) comment = v1Tag.comment;
            if (track == null || track.isEmpty()) {
                if (v1Tag.track > 0) track = String.valueOf(v1Tag.track);
            }
        }

        private static boolean isEmpty(String s) {
            return s == null || s.isEmpty();
        }

        /** Get first non-empty title-like field (for display). */
        public String getDisplayTitle() {
            return isEmpty(title) ? "Unknown Title" : title;
        }

        public String getDisplayArtist() {
            return isEmpty(artist) ? "Unknown Artist" : artist;
        }

        @Override
        public String toString() {
            return String.format(
                "ID3Tag{title='%s', artist='%s', album='%s', year='%s', track='%s', source=%s}",
                title, artist, album, year, track, source);
        }
    }

    /**
     * Parse ID3 tags from a file.
     * Returns at minimum an empty ID3Tag (never null).
     */
    public static ID3Tag parse(File file) {
        ID3Tag result = new ID3Tag();

        if (file == null || !file.exists() || !file.isFile()) {
            return result;
        }

        long fileLength = file.length();
        if (fileLength < 10) return result;

        // ── ID3v2 (at the beginning) ────────────────────────────────────
        byte[] headBuffer = new byte[(int) Math.min(fileLength, MAX_ID3V2_READ)];
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            raf.readFully(headBuffer);
        } catch (IOException e) {
            try { if (raf != null) raf.close(); } catch (IOException ignored) {}
            // Try InputStream if RandomAccessFile fails
            try {
                FileInputStream fis = new FileInputStream(file);
                int total = 0;
                while (total < headBuffer.length) {
                    int n = fis.read(headBuffer, total, headBuffer.length - total);
                    if (n < 0) break;
                    total += n;
                }
                fis.close();
                if (total < headBuffer.length) {
                    byte[] trimmed = new byte[total];
                    System.arraycopy(headBuffer, 0, trimmed, 0, total);
                    headBuffer = trimmed;
                }
            } catch (IOException e2) {
                return result;
            }
        }

        if (raf != null) {
            try { raf.close(); } catch (IOException ignored) {}
        }

        ID3v2Tag v2 = ID3v2Tag.parse(headBuffer, 0, headBuffer.length);
        if (v2 != null && !v2.isEmpty()) {
            result.source = "id3v2." + v2.majorVersion + "." + v2.minorVersion;
            result.title   = nvl(v2.getTitle());
            result.artist  = nvl(v2.getArtist());
            result.album   = nvl(v2.getAlbum());
            result.year    = nvl(v2.getYear());
            result.track   = nvl(v2.getTrack());
            result.genre   = nvl(v2.getGenre());
            result.comment = nvl(v2.getComment());
            result.lyrics  = nvl(v2.getLyrics());
            result.albumArt = v2.getAlbumArt();
            result.albumArtMimeType = v2.getAlbumArtMimeType();
            result.v2Tag = v2;
        }

        // ── ID3v1 (at the end) ──────────────────────────────────────────
        if (fileLength > 128) {
            byte[] tail = new byte[128];
            try {
                FileInputStream fis = new FileInputStream(file);
                long skip = fis.skip(fileLength - 128);
                if (skip == fileLength - 128) {
                    int n = fis.read(tail);
                    if (n == 128) {
                        ID3v1Tag v1 = ID3v1Tag.parse(tail, fileLength);
                        if (v1 != null) {
                            result.v1Tag = v1;
                            if (result.source.equals("none")) {
                                result.source = "id3v1";
                                result.title   = nvl(v1.title);
                                result.artist  = nvl(v1.artist);
                                result.album   = nvl(v1.album);
                                result.year    = nvl(v1.year);
                                result.genre   = nvl(v1.genre);
                                result.comment = nvl(v1.comment);
                                if (v1.track > 0) result.track = String.valueOf(v1.track);
                            } else {
                                result.fillFromFallback();
                            }
                        }
                    }
                }
                fis.close();
            } catch (IOException e) {
                // ID3v1 parse failed — v2 result is still valid
            }
        }

        return result;
    }

    /**
     * Parse ID3 tags from a byte buffer in memory (streaming-friendly).
     * The buffer must contain the entire file.
     */
    public static ID3Tag parseFromBytes(byte[] fileData) {
        ID3Tag result = new ID3Tag();

        if (fileData == null || fileData.length < 10) return result;

        // ID3v2
        ID3v2Tag v2 = ID3v2Tag.parse(fileData, 0, fileData.length);
        if (v2 != null && !v2.isEmpty()) {
            result.source = "id3v2." + v2.majorVersion + "." + v2.minorVersion;
            result.title   = nvl(v2.getTitle());
            result.artist  = nvl(v2.getArtist());
            result.album   = nvl(v2.getAlbum());
            result.year    = nvl(v2.getYear());
            result.track   = nvl(v2.getTrack());
            result.genre   = nvl(v2.getGenre());
            result.comment = nvl(v2.getComment());
            result.lyrics  = nvl(v2.getLyrics());
            result.albumArt = v2.getAlbumArt();
            result.albumArtMimeType = v2.getAlbumArtMimeType();
            result.v2Tag = v2;
        }

        // ID3v1
        if (fileData.length > 128) {
            byte[] tail = new byte[128];
            System.arraycopy(fileData, fileData.length - 128, tail, 0, 128);
            ID3v1Tag v1 = ID3v1Tag.parse(tail, fileData.length);
            if (v1 != null) {
                result.v1Tag = v1;
                if (result.source.equals("none")) {
                    result.source = "id3v1";
                    result.title   = nvl(v1.title);
                    result.artist  = nvl(v1.artist);
                    result.album   = nvl(v1.album);
                    result.year    = nvl(v1.year);
                    result.genre   = nvl(v1.genre);
                    result.comment = nvl(v1.comment);
                    if (v1.track > 0) result.track = String.valueOf(v1.track);
                } else {
                    result.fillFromFallback();
                }
            }
        }

        return result;
    }

    private static String nvl(String s) {
        return (s == null) ? "" : s;
    }
}
