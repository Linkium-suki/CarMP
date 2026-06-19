package com.codewhale.musicplayer.id3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full ID3v2 tag parser supporting v2.2, v2.3, and v2.4.
 *
 * Handles:
 *   - Synchsafe integers (v2.3 / v2.4)
 *   - Unsynchronisation
 *   - Extended headers
 *   - Compressed / encrypted / grouped frames (detection only)
 *   - All four encoding schemes (ISO-8859-1, UTF-16+BOM, UTF-16BE, UTF-8)
 *   - Common frame IDs (TIT2, TPE1, TALB, TYER/TDRC, TRCK, TCON, APIC, USLT, COMM)
 *
 * Usage:
 *   ID3v2Tag tag = ID3v2Tag.parse(fileHeaderBytes);
 */
public class ID3v2Tag {

    // ── Version ─────────────────────────────────────────────────────────
    public final int majorVersion;
    public final int minorVersion;

    // ── Flags ───────────────────────────────────────────────────────────
    public final boolean unsynchronisation;
    public final boolean extendedHeader;
    public final boolean experimental;
    public final boolean footer;       // v2.4 only

    // ── Tag size (bytes after header) ───────────────────────────────────
    public final int tagSize;

    // ── Extended header size (v2.4 uses synchsafe) ──────────────────────
    public int extendedHeaderSize = 0;

    // ── All parsed frames ───────────────────────────────────────────────
    private final Map<String, ID3Frame> frames = new LinkedHashMap<>();

    // ── Parsed text fields (lazy) ───────────────────────────────────────
    private String title   = null;
    private String artist  = null;
    private String album   = null;
    private String year    = null;
    private String track   = null;
    private String genre   = null;
    private String comment = null;
    private String lyrics  = null;
    private byte[] albumArtData = null;
    private String albumArtMime = null;

    /** Offset in the source buffer where the tag starts. */
    public long tagOffset = 0;

    private static final String ID3_HEADER = "ID3";
    private static final int HEADER_SIZE = 10;

    // ────────────────────────────────────────────────────────────────────

    private ID3v2Tag(int major, int minor, boolean unsync, boolean extHdr,
                     boolean experimental, boolean footer, int size) {
        this.majorVersion  = major;
        this.minorVersion  = minor;
        this.unsynchronisation = unsync;
        this.extendedHeader    = extHdr;
        this.experimental      = experimental;
        this.footer            = footer;
        this.tagSize           = size;
    }

    /**
     * Attempt to parse an ID3v2 tag from the start of a byte buffer.
     * Returns null if no valid ID3v2 header is found.
     *
     * The buffer should contain at minimum the header (10 bytes) plus the tag body.
     */
    public static ID3v2Tag parse(byte[] data, int offset, int maxLen) {
        if (data == null || maxLen < HEADER_SIZE) return null;

        // --- Header ---
        String sig = new String(data, offset, 3, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!ID3_HEADER.equals(sig)) return null;

        int major = data[offset + 3] & 0xFF;
        int minor = data[offset + 4] & 0xFF;

        // Only parse v2.x
        if (major < 2 || major > 4) return null;

        byte flags = data[offset + 5];

        boolean unsync, extHdr, experimental, footer;
        if (major == 2) {
            // v2.2 flags
            unsync       = (flags & 0x80) != 0;
            extHdr       = false; // v2.2 has no extended header flag in the header
            experimental = false;
            footer       = false;
        } else {
            // v2.3 / v2.4 flags
            unsync       = (flags & 0x80) != 0;
            extHdr       = (flags & 0x40) != 0;
            experimental = (flags & 0x20) != 0;
            footer       = (major == 4) && ((flags & 0x10) != 0);
        }

        int size = readSynchsafeInt(data, offset + 6);
        if (size <= 0) return null;

        ID3v2Tag tag = new ID3v2Tag(major, minor, unsync, extHdr, experimental, footer, size);
        tag.tagOffset = offset;

        int bodyStart = offset + HEADER_SIZE;
        int bodyEnd   = Math.min(bodyStart + size, maxLen);
        if (bodyEnd > data.length) bodyEnd = data.length;

        byte[] body = new byte[bodyEnd - bodyStart];
        System.arraycopy(data, bodyStart, body, 0, body.length);

        // Apply global unsynchronisation if flag is set (v2.2 does not have this flag)
        if (unsync && major >= 3) {
            body = deunsynchronise(body);
        }

        int bodyPos = 0;

        // --- Extended header ---
        if (extHdr) {
            if (bodyPos + 4 > body.length) return tag;
            int extSize;
            if (major == 4) {
                extSize = readSynchsafeInt(body, bodyPos);
            } else {
                extSize = readInt32BE(body, bodyPos);
            }
            tag.extendedHeaderSize = extSize;
            if (extSize < 0) extSize = 0;
            bodyPos += 4 + Math.min(extSize, body.length - bodyPos - 4);
            if (bodyPos > body.length) bodyPos = body.length;
        }

        // --- Parse frames ---
        while (bodyPos < body.length) {
            // ID3v2.2 frame ID = 3 chars; v2.3+ = 4 chars
            int idLen = (major == 2) ? 3 : 4;
            if (bodyPos + idLen > body.length) break;

            String frameId = new String(body, bodyPos, idLen, java.nio.charset.StandardCharsets.ISO_8859_1);
            bodyPos += idLen;

            // Check for padding (null bytes)
            if (frameId.charAt(0) == 0) {
                // Padding reached — skip remaining nulls
                while (bodyPos < body.length && body[bodyPos] == 0) bodyPos++;
                break;
            }

            // Only accept valid frame IDs: [A-Z][A-Z0-9]...
            if (!isValidFrameId(frameId, major)) {
                // Invalid frame — scan forward to find next valid one or padding
                bodyPos = skipToNextValidFrame(body, bodyPos, major);
                continue;
            }

            // Frame size
            if (bodyPos + 4 > body.length) break;
            int frameSize;
            if (major == 4) {
                frameSize = readSynchsafeInt(body, bodyPos);
            } else {
                frameSize = readInt32BE(body, bodyPos);
            }
            bodyPos += 4;

            // Sanity check
            if (frameSize < 0 || frameSize > body.length - bodyPos) {
                frameSize = Math.min(frameSize, body.length - bodyPos);
                if (frameSize < 0) break;
            }

            // Flags (v2.3+)
            byte statusFlags = 0, formatFlags = 0;
            if (major >= 3) {
                if (bodyPos + 2 > body.length) break;
                statusFlags = body[bodyPos];
                formatFlags = body[bodyPos + 1];
                bodyPos += 2;
            }

            // Read frame data
            if (bodyPos + frameSize > body.length) {
                frameSize = body.length - bodyPos;
                if (frameSize <= 0) break;
            }
            byte[] frameData = new byte[frameSize];
            System.arraycopy(body, bodyPos, frameData, 0, frameSize);
            bodyPos += frameSize;

            // Handle per-frame unsynchronisation (v2.3+ data length indicator)
            // For simplicity, skip encrypted/compressed frames
            boolean compressed = (major >= 3) && ((formatFlags & 0x80) != 0);
            boolean encrypted  = (major >= 3) && ((formatFlags & 0x40) != 0);

            if (compressed || encrypted) {
                // We don't decompress/decrypt — store as-is but mark
                ID3Frame frame = new ID3Frame(frameId, frameData, frameSize, statusFlags, formatFlags);
                tag.frames.put(frameId, frame);
                continue;
            }

            ID3Frame frame = new ID3Frame(frameId, frameData, frameSize, statusFlags, formatFlags);
            tag.frames.put(frameId, frame);
        }

        return tag;
    }

    // ── Public accessors (lazy decode from frames) ─────────────────────

    public String getTitle() {
        if (title == null) title = getTextFrame("TIT2", "TT2");
        return title;
    }

    public String getArtist() {
        if (artist == null) artist = getTextFrame("TPE1", "TP1");
        return artist;
    }

    public String getAlbum() {
        if (album == null) album = getTextFrame("TALB", "TAL");
        return album;
    }

    public String getYear() {
        if (year == null) {
            year = getTextFrame("TYER", "TYE"); // v2.3
            if (year.isEmpty()) year = getTextFrame("TDRC", null); // v2.4
        }
        return year;
    }

    public String getTrack() {
        if (track == null) track = getTextFrame("TRCK", "TRK");
        return track;
    }

    public String getGenre() {
        if (genre == null) genre = getTextFrame("TCON", "TCO");
        return genre;
    }

    public String getComment() {
        if (comment == null) {
            ID3Frame f = frames.get("COMM");
            if (f == null && majorVersion <= 2) f = frames.get("COM");
            comment = (f != null) ? f.decodeComment() : "";
        }
        return comment;
    }

    public String getLyrics() {
        if (lyrics == null) {
            ID3Frame f = frames.get("USLT");
            if (f == null && majorVersion <= 2) f = frames.get("ULT");
            lyrics = (f != null) ? f.decodeLyrics() : "";
        }
        return lyrics;
    }

    public byte[] getAlbumArt() {
        if (albumArtData == null && albumArtMime == null) {
            ID3Frame f = frames.get("APIC");
            if (f == null && majorVersion <= 2) f = frames.get("PIC");
            if (f != null) {
                albumArtMime = f.decodeApicMimeType();
                albumArtData = f.decodeApicImageData();
            }
        }
        return albumArtData;
    }

    public String getAlbumArtMimeType() {
        if (albumArtMime == null) getAlbumArt(); // trigger extraction
        return albumArtMime != null ? albumArtMime : "";
    }

    public Map<String, ID3Frame> getFrames() {
        return Collections.unmodifiableMap(frames);
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }

    // ── Internal helpers ────────────────────────────────────────────────

    private String getTextFrame(String v23Id, String v22Id) {
        ID3Frame f = frames.get(v23Id);
        if (f == null && v22Id != null) f = frames.get(v22Id);
        return (f != null) ? f.decodeText() : "";
    }

    // ── Static utilities ────────────────────────────────────────────────

    /**
     * Read a 4-byte synchsafe integer (each byte uses 7 bits; MSB is always 0).
     */
    public static int readSynchsafeInt(byte[] data, int offset) {
        if (data == null || offset + 4 > data.length) return 0;
        return ((data[offset] & 0x7F) << 21)
             | ((data[offset + 1] & 0x7F) << 14)
             | ((data[offset + 2] & 0x7F) << 7)
             |  (data[offset + 3] & 0x7F);
    }

    /**
     * Read a normal 4-byte big-endian unsigned integer.
     */
    public static int readInt32BE(byte[] data, int offset) {
        if (data == null || offset + 4 > data.length) return 0;
        return ((data[offset] & 0xFF) << 24)
             | ((data[offset + 1] & 0xFF) << 16)
             | ((data[offset + 2] & 0xFF) << 8)
             |  (data[offset + 3] & 0xFF);
    }

    /**
     * Validate a frame ID: [A-Z] followed by [A-Z0-9]…
     */
    private static boolean isValidFrameId(String id, int major) {
        if (id == null) return false;
        int len = (major == 2) ? 3 : 4;
        if (id.length() < len) return false;
        for (int i = 0; i < len; i++) {
            char c = id.charAt(i);
            if (i == 0) {
                if (c < 'A' || c > 'Z') return false;
            } else {
                if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) return false;
            }
        }
        return true;
    }

    /**
     * Skip forward to the next valid frame or padding.
     */
    private static int skipToNextValidFrame(byte[] body, int pos, int major) {
        int idLen = (major == 2) ? 3 : 4;
        int max = body.length - idLen - 4;
        for (int i = pos; i < max; i++) {
            if (body[i] == 0) return i; // padding
            String candidate = new String(body, i, idLen);
            if (isValidFrameId(candidate, major)) {
                return i;
            }
        }
        return body.length;
    }

    /**
     * De-unsynchronise: replace every occurrence of 0xFF 0x00 with 0xFF.
     */
    private static byte[] deunsynchronise(byte[] data) {
        int count = 0;
        for (int i = 0; i < data.length - 1; i++) {
            if ((data[i] & 0xFF) == 0xFF && data[i + 1] == 0x00) count++;
        }
        if (count == 0) return data;
        byte[] out = new byte[data.length - count];
        int j = 0;
        for (int i = 0; i < data.length; i++) {
            if (i < data.length - 1 && (data[i] & 0xFF) == 0xFF && data[i + 1] == 0x00) {
                out[j++] = (byte) 0xFF;
                i++; // skip the 0x00
            } else {
                out[j++] = data[i];
            }
        }
        return out;
    }

    @Override
    public String toString() {
        return String.format(
            "ID3v2.%d.%d{size=%d, frames=%d, unsync=%s, ext_hdr=%s}",
            majorVersion, minorVersion, tagSize, frames.size(), unsynchronisation, extendedHeader
        );
    }
}
