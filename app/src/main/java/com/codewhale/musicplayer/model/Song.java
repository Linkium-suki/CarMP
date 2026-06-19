package com.codewhale.musicplayer.model;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

/**
 * Represents a single media file (audio or video) with its ID3 metadata.
 */
public class Song implements Parcelable {

    public static final int TYPE_AUDIO = 0;
    public static final int TYPE_VIDEO = 1;

    public long id;
    public String filePath;
    public String title;
    public String artist;
    public String album;
    public String year;
    public String track;
    public String genre;
    public String comment;
    public String lyrics;
    public long durationMs;
    public long fileSize;
    public int mediaType; // TYPE_AUDIO or TYPE_VIDEO
    public byte[] albumArt;
    public String albumArtMimeType;

    /** Cached URI (lazy). */
    private transient Uri cachedUri;

    public Song() {
        this.mediaType = TYPE_AUDIO;
    }

    public Song(String filePath) {
        this();
        this.filePath = filePath;
        this.title = new File(filePath).getName();
    }

    public Uri getUri() {
        if (cachedUri == null && filePath != null) {
            cachedUri = Uri.fromFile(new File(filePath));
        }
        return cachedUri;
    }

    public String getDisplayTitle() {
        return (title != null && !title.isEmpty()) ? title : new File(filePath).getName();
    }

    public String getDisplayArtist() {
        return (artist != null && !artist.isEmpty()) ? artist : "Unknown Artist";
    }

    public String getDurationString() {
        long totalSec = durationMs / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format("%d:%02d", min, sec);
    }

    public boolean isVideo() {
        return mediaType == TYPE_VIDEO;
    }

    // ── Parcelable ─────────────────────────────────────────────────────

    protected Song(Parcel in) {
        id = in.readLong();
        filePath = in.readString();
        title = in.readString();
        artist = in.readString();
        album = in.readString();
        year = in.readString();
        track = in.readString();
        genre = in.readString();
        comment = in.readString();
        lyrics = in.readString();
        durationMs = in.readLong();
        fileSize = in.readLong();
        mediaType = in.readInt();
        albumArtMimeType = in.readString();
        int artLen = in.readInt();
        if (artLen > 0) {
            albumArt = new byte[artLen];
            in.readByteArray(albumArt);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(filePath);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeString(year);
        dest.writeString(track);
        dest.writeString(genre);
        dest.writeString(comment);
        dest.writeString(lyrics);
        dest.writeLong(durationMs);
        dest.writeLong(fileSize);
        dest.writeInt(mediaType);
        dest.writeString(albumArtMimeType);
        if (albumArt != null) {
            dest.writeInt(albumArt.length);
            dest.writeByteArray(albumArt);
        } else {
            dest.writeInt(0);
        }
    }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<Song> CREATOR = new Creator<Song>() {
        @Override
        public Song createFromParcel(Parcel in) { return new Song(in); }
        @Override
        public Song[] newArray(int size) { return new Song[size]; }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Song)) return false;
        Song other = (Song) o;
        if (filePath != null) return filePath.equals(other.filePath);
        return other.filePath == null;
    }

    @Override
    public int hashCode() {
        return filePath != null ? filePath.hashCode() : 0;
    }
}
