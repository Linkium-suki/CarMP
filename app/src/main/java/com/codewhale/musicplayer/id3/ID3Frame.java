package com.codewhale.musicplayer.id3;

/**
 * A single ID3v2 frame.
 *
 * Frames are the building blocks of an ID3v2 tag — each carries one piece of
 * metadata: title, artist, album art, lyrics, etc.
 */
public class ID3Frame {

    /** 3-char frame ID (ID3v2.2) or 4-char frame ID (ID3v2.3/v2.4). */
    public final String frameId;

    /** Raw frame payload (after decompression/unsynchronisation, before encoding-aware decode). */
    public final byte[] data;

    /** Size of the frame as declared in the header. */
    public final int declaredSize;

    // Flags (ID3v2.3+)
    public final boolean tagAlterPreservation;
    public final boolean fileAlterPreservation;
    public final boolean readOnly;
    public final boolean compressed;
    public final boolean encrypted;
    public final boolean grouped;

    public ID3Frame(String frameId, byte[] data, int declaredSize) {
        this.frameId = frameId;
        this.data = data;
        this.declaredSize = declaredSize;

        // Default flags (v2.2 has no flags)
        this.tagAlterPreservation  = false;
        this.fileAlterPreservation = false;
        this.readOnly   = false;
        this.compressed = false;
        this.encrypted  = false;
        this.grouped    = false;
    }

    /** Constructor with flag bytes for ID3v2.3 / v2.4. */
    public ID3Frame(String frameId, byte[] data, int declaredSize,
                    byte statusFlags, byte formatFlags) {
        this.frameId = frameId;
        this.data = data;
        this.declaredSize = declaredSize;

        // Status flags (byte 8)
        this.tagAlterPreservation  = (statusFlags & 0x80) != 0;
        this.fileAlterPreservation = (statusFlags & 0x40) != 0;
        this.readOnly = (statusFlags & 0x20) != 0;

        // Format flags (byte 9)
        this.compressed = (formatFlags & 0x80) != 0;
        this.encrypted  = (formatFlags & 0x40) != 0;
        this.grouped    = (formatFlags & 0x20) != 0;
    }

    // ── Convenience helpers for common frames ──────────────────────────

    /**
     * Decode text frame payload (T*** frames).
     * First byte is encoding indicator:
     *   0x00 = ISO-8859-1
     *   0x01 = UTF-16 with BOM
     *   0x02 = UTF-16BE (no BOM)
     *   0x03 = UTF-8
     */
    public String decodeText() {
        if (data == null || data.length < 2) return "";
        int enc = data[0] & 0xFF;
        int textStart = 1;
        int textLen = data.length - 1;

        // Strip null terminators from end
        while (textLen > 0 && data[textStart + textLen - 1] == 0) {
            textLen--;
        }
        if (textLen <= 0) return "";

        try {
            switch (enc) {
                case 0x00:
                    return new String(data, textStart, textLen, "ISO-8859-1");
                case 0x01: {
                    // UTF-16 with BOM — use Java's builtin
                    String raw = new String(data, textStart, textLen, "UTF-16");
                    // Strip BOM if present
                    if (raw.length() > 0 && raw.charAt(0) == '\uFEFF') {
                        return raw.substring(1);
                    }
                    return raw;
                }
                case 0x02:
                    return new String(data, textStart, textLen, "UTF-16BE");
                case 0x03:
                    return new String(data, textStart, textLen, "UTF-8");
                default:
                    // Fallback: try ISO-8859-1
                    return new String(data, textStart, textLen, "ISO-8859-1");
            }
        } catch (Exception e) {
            return new String(data, textStart, textLen);
        }
    }

    /**
     * Decode comment frame (COMM/COM).
     * Structure: encoding(1), language(3), short_desc(null-term), text.
     */
    public String decodeComment() {
        if (data == null || data.length < 5) return "";
        int enc = data[0] & 0xFF;
        // Skip language (3 bytes)
        int pos = 4;
        // Skip null-terminated short description
        while (pos < data.length - 1) {
            if (enc == 0x01 || enc == 0x02) {
                // UTF-16: null is 2 bytes
                if (data[pos] == 0 && data[pos + 1] == 0) { pos += 2; break; }
                pos += 2;
            } else {
                if (data[pos] == 0) { pos++; break; }
                pos++;
            }
        }
        if (pos >= data.length) return "";

        int textStart = pos;
        int textLen = data.length - textStart;
        while (textLen > 0 && data[textStart + textLen - 1] == 0) textLen--;
        if (textLen <= 0) return "";

        try {
            switch (enc) {
                case 0x00: return new String(data, textStart, textLen, "ISO-8859-1");
                case 0x01: return new String(data, textStart, textLen, "UTF-16");
                case 0x02: return new String(data, textStart, textLen, "UTF-16BE");
                case 0x03: return new String(data, textStart, textLen, "UTF-8");
                default:   return new String(data, textStart, textLen, "ISO-8859-1");
            }
        } catch (Exception e) {
            return new String(data, textStart, textLen);
        }
    }

    /**
     * Extract MIME type from an APIC/PIC frame.
     */
    public String decodeApicMimeType() {
        if (data == null || data.length < 2) return "image/";
        int enc = data[0] & 0xFF;
        int pos = 1;
        // Read null-terminated MIME type string (always ISO-8859-1 per spec)
        int mimeStart = pos;
        while (pos < data.length && data[pos] != 0) pos++;
        if (pos <= mimeStart) return "image/jpeg";
        return new String(data, mimeStart, pos - mimeStart);
    }

    /**
     * Extract raw image data from an APIC/PIC frame.
     * APIC structure: encoding(1), MIME(null-term), pic_type(1), desc(null-term), image_data
     */
    public byte[] decodeApicImageData() {
        if (data == null || data.length < 4) return null;
        int enc = data[0] & 0xFF;
        int pos = 1;
        // Skip MIME type
        while (pos < data.length && data[pos] != 0) pos++;
        pos++; // skip null terminator
        // Skip picture type (1 byte)
        if (pos >= data.length) return null;
        pos++; // skip picture type byte
        // Skip null-terminated description
        while (pos < data.length - 1) {
            if (enc == 0x01 || enc == 0x02) {
                if (data[pos] == 0 && data[pos + 1] == 0) { pos += 2; break; }
                pos += 2;
            } else {
                if (data[pos] == 0) { pos++; break; }
                pos++;
            }
        }
        if (pos >= data.length) return null;
        int imageLen = data.length - pos;
        byte[] imageData = new byte[imageLen];
        System.arraycopy(data, pos, imageData, 0, imageLen);
        return imageData;
    }

    /**
     * Decode USLT (unsynchronized lyrics) frame.
     * Structure: encoding(1), language(3), content_descriptor(null-term), lyrics.
     */
    public String decodeLyrics() {
        if (data == null || data.length < 5) return "";
        int enc = data[0] & 0xFF;
        int pos = 4;
        // Skip null-terminated content descriptor
        while (pos < data.length - 1) {
            if (enc == 0x01 || enc == 0x02) {
                if (data[pos] == 0 && data[pos + 1] == 0) { pos += 2; break; }
                pos += 2;
            } else {
                if (data[pos] == 0) { pos++; break; }
                pos++;
            }
        }
        if (pos >= data.length) return "";
        int textLen = data.length - pos;
        try {
            switch (enc) {
                case 0x00: return new String(data, pos, textLen, "ISO-8859-1");
                case 0x01: return new String(data, pos, textLen, "UTF-16");
                case 0x02: return new String(data, pos, textLen, "UTF-16BE");
                case 0x03: return new String(data, pos, textLen, "UTF-8");
                default:   return new String(data, pos, textLen, "ISO-8859-1");
            }
        } catch (Exception e) {
            return new String(data, pos, textLen);
        }
    }
}
