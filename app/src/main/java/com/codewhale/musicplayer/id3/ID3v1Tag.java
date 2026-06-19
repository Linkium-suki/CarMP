package com.codewhale.musicplayer.id3;

/**
 * ID3v1 tag — simple 128-byte tag appended to the end of an MP3 file.
 *
 * Structure (128 bytes):
 *   [0-2]   Header       "TAG" (3 bytes)
 *   [3-32]  Title        30 bytes (ISO-8859-1, space-padded)
 *   [33-62] Artist       30 bytes
 *   [63-92] Album        30 bytes
 *   [93-96] Year          4 bytes
 *   [97-126] Comment     30 bytes
 *          — if byte[125]==0, byte[126] = track number
 *   [127]   Genre         1 byte (index into ID3v1 genre list)
 */
public class ID3v1Tag {

    public String title   = "";
    public String artist  = "";
    public String album   = "";
    public String year    = "";
    public String comment = "";
    public int    track   = 0;
    public String genre   = "";

    /** Offset in the file where the tag was found (bytes from start), or -1 if not found. */
    public long tagOffset = -1;

    private static final int TAG_SIZE = 128;
    private static final String HEADER = "TAG";

    // ID3v1 pre-defined genres (index 0..125; 126=unknown; 127=unset)
    private static final String[] GENRES = {
        "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk",
        "Grunge", "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies",
        "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno",
        "Industrial", "Alternative", "Ska", "Death Metal", "Pranks",
        "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal",
        "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental",
        "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise",
        "Alternative Rock", "Bass", "Soul", "Punk", "Space",
        "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic",
        "Gothic", "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk",
        "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult",
        "Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle",
        "Native American", "Cabaret", "New Wave", "Psychedelic", "Rave",
        "Showtunes", "Trailer", "Lo-Fi", "Tribal", "Acid Punk",
        "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll",
        "Hard Rock", "Folk", "Folk/Rock", "National Folk", "Swing",
        "Fast-Fusion", "Bebop", "Latin", "Revival", "Celtic",
        "Bluegrass", "Avantgarde", "Gothic Rock", "Progressive Rock",
        "Psychedelic Rock", "Symphonic Rock", "Slow Rock", "Big Band",
        "Chorus", "Easy Listening", "Acoustic", "Humour", "Speech",
        "Chanson", "Opera", "Chamber Music", "Sonata", "Symphony",
        "Booty Bass", "Primus", "Porn Groove", "Satire", "Slow Jam",
        "Club", "Tango", "Samba", "Folklore", "Ballad",
        "Power Ballad", "Rhythmic Soul", "Freestyle", "Duet",
        "Punk Rock", "Drum Solo", "A Cappella", "Euro-House",
        "Dance Hall", "Goa", "Drum & Bass", "Club-House",
        "Hardcore Techno", "Terror", "Indie", "BritPop", "Negerpunk",
        "Polsk Punk", "Beat", "Christian Gangsta Rap", "Heavy Metal",
        "Black Metal", "Crossover", "Contemporary Christian", "Christian Rock",
        "Merengue", "Salsa", "Thrash Metal", "Anime", "JPop",
        "Synthpop", "Abstract", "Art Rock", "Baroque", "Bhangra",
        "Big Beat", "Breakbeat", "Chillout", "Downtempo", "Dub",
        "EBM", "Eclectic", "Electro", "Electroclash", "Emo",
        "Experimental", "Garage", "Global", "IDM", "Illbient",
        "Industro-Goth", "Jam Band", "Krautrock", "Leftfield", "Lounge",
        "Math Rock", "New Romantic", "Nu-Breakz", "Post-Punk", "Post-Rock",
        "Progressive Psytrance", "Progressive Trance", "Psytrance", "Rave",
        "Rock en Español", "Shoegaze", "Space Rock", "Stoner Rock",
        "Techno-Dub", "UK Garage", "Neo Soul", "Trip-Hop2"
    };

    /**
     * Parse an ID3v1 tag from the last 128 bytes of a file.
     * Returns null if no valid ID3v1 tag is found.
     */
    public static ID3v1Tag parse(byte[] last128Bytes, long fileLength) {
        if (last128Bytes == null || last128Bytes.length < TAG_SIZE) {
            return null;
        }

        // Check for "TAG" header at offset 0 of these 128 bytes
        String header = new String(last128Bytes, 0, 3, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!HEADER.equals(header)) {
            return null;
        }

        ID3v1Tag tag = new ID3v1Tag();
        tag.tagOffset = fileLength - TAG_SIZE;

        tag.title   = trimNullAndSpace(last128Bytes,   3, 30);
        tag.artist  = trimNullAndSpace(last128Bytes,  33, 30);
        tag.album   = trimNullAndSpace(last128Bytes,  63, 30);
        tag.year    = trimNullAndSpace(last128Bytes,  93,  4);

        // ID3v1.1: if byte[125] (comment[28]) is 0, byte[126] is track number
        if (last128Bytes[125] == 0) {
            tag.comment = trimNullAndSpace(last128Bytes, 97, 28);
            tag.track   = last128Bytes[126] & 0xFF;
        } else {
            tag.comment = trimNullAndSpace(last128Bytes, 97, 30);
            tag.track   = 0;
        }

        int genreIdx = last128Bytes[127] & 0xFF;
        if (genreIdx >= 0 && genreIdx < GENRES.length) {
            tag.genre = GENRES[genreIdx];
        } else if (genreIdx == 255) {
            tag.genre = "";
        } else {
            tag.genre = "Unknown";
        }

        return tag;
    }

    /**
     * Trim null bytes (0x00) and trailing spaces from the given byte range,
     * then decode as ISO-8859-1.
     */
    private static String trimNullAndSpace(byte[] data, int offset, int length) {
        int end = offset + length;
        // Find first null byte
        int nullPos = end;
        for (int i = offset; i < end; i++) {
            if (data[i] == 0) {
                nullPos = i;
                break;
            }
        }
        // Trim trailing spaces
        while (nullPos > offset && (data[nullPos - 1] == ' ' || data[nullPos - 1] == 0)) {
            nullPos--;
        }
        int len = nullPos - offset;
        if (len <= 0) return "";
        return new String(data, offset, len, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    @Override
    public String toString() {
        return String.format(
            "ID3v1{title='%s', artist='%s', album='%s', year='%s', track=%d, genre='%s'}",
            title, artist, album, year, track, genre
        );
    }
}
