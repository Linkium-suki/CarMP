package com.codewhale.musicplayer.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A playlist is an ordered list of Songs with a current playback position.
 */
public class Playlist {

    private final List<Song> songs = new ArrayList<>();
    private int currentIndex = -1;
    private String name = "Default";

    // Repeat modes
    public static final int REPEAT_OFF   = 0;
    public static final int REPEAT_ALL   = 1;
    public static final int REPEAT_ONE   = 2;

    private int repeatMode = REPEAT_OFF;
    private boolean shuffle = false;
    private List<Integer> shuffleOrder = null;

    public Playlist() {}

    public Playlist(String name) {
        this.name = name;
    }

    // ── Song management ─────────────────────────────────────────────────

    public void add(Song song) {
        songs.add(song);
        if (currentIndex < 0) currentIndex = 0;
    }

    public void addAll(List<Song> list) {
        songs.addAll(list);
        if (currentIndex < 0 && !songs.isEmpty()) currentIndex = 0;
    }

    public void remove(int index) {
        if (index < 0 || index >= songs.size()) return;
        songs.remove(index);
        if (songs.isEmpty()) {
            currentIndex = -1;
        } else if (currentIndex >= songs.size()) {
            currentIndex = songs.size() - 1;
        } else if (index < currentIndex) {
            currentIndex--;
        }
    }

    public void clear() {
        songs.clear();
        currentIndex = -1;
        shuffleOrder = null;
    }

    public int size() { return songs.size(); }
    public boolean isEmpty() { return songs.isEmpty(); }

    public Song get(int index) {
        if (index < 0 || index >= songs.size()) return null;
        int realIndex = getRealIndex(index);
        return songs.get(realIndex);
    }

    public List<Song> getAll() {
        return Collections.unmodifiableList(songs);
    }

    // ── Navigation ──────────────────────────────────────────────────────

    public Song current() {
        return get(currentIndex);
    }

    public int getCurrentIndex() { return currentIndex; }

    public void setCurrentIndex(int index) {
        if (index >= 0 && index < songs.size()) {
            currentIndex = index;
        }
    }

    public boolean hasNext() {
        if (songs.isEmpty()) return false;
        if (repeatMode == REPEAT_ONE) return true;
        if (repeatMode == REPEAT_ALL) return true;
        if (shuffle && shuffleOrder != null) {
            return true; // shuffled playlists always have a "next"
        }
        return (getNextIndex(currentIndex) != -1);
    }

    public boolean hasPrevious() {
        if (songs.isEmpty()) return false;
        if (repeatMode == REPEAT_ONE) return true;
        if (shuffle && shuffleOrder != null) {
            return true;
        }
        return (getPreviousIndex(currentIndex) != -1);
    }

    public Song next() {
        if (songs.isEmpty()) return null;
        if (repeatMode == REPEAT_ONE) {
            return current();
        }
        int next = getNextIndex(currentIndex);
        if (next < 0) {
            if (repeatMode == REPEAT_ALL) {
                next = 0;
            } else {
                return null;
            }
        }
        currentIndex = next;
        return current();
    }

    public Song previous() {
        if (songs.isEmpty()) return null;
        if (repeatMode == REPEAT_ONE) {
            return current();
        }
        int prev = getPreviousIndex(currentIndex);
        if (prev < 0) {
            if (repeatMode == REPEAT_ALL) {
                prev = songs.size() - 1;
            } else {
                return null;
            }
        }
        currentIndex = prev;
        return current();
    }

    // ── Shuffle ─────────────────────────────────────────────────────────

    public void setShuffle(boolean on) {
        shuffle = on;
        if (on) {
            buildShuffleOrder();
        } else {
            shuffleOrder = null;
        }
    }

    public boolean isShuffle() { return shuffle; }

    private void buildShuffleOrder() {
        shuffleOrder = new ArrayList<>(songs.size());
        for (int i = 0; i < songs.size(); i++) shuffleOrder.add(i);
        Collections.shuffle(shuffleOrder);
        // Move current song to front
        if (currentIndex >= 0 && currentIndex < songs.size()) {
            shuffleOrder.remove((Integer) currentIndex);
            shuffleOrder.add(0, currentIndex);
        }
    }

    // ── Repeat ──────────────────────────────────────────────────────────

    public void setRepeatMode(int mode) { this.repeatMode = mode; }
    public int getRepeatMode() { return repeatMode; }

    public void cycleRepeatMode() {
        repeatMode = (repeatMode + 1) % 3;
    }

    // ── Name ────────────────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    // ── Internal helpers ────────────────────────────────────────────────

    private int getRealIndex(int displayIndex) {
        if (shuffle && shuffleOrder != null && displayIndex >= 0 && displayIndex < shuffleOrder.size()) {
            return shuffleOrder.get(displayIndex);
        }
        return displayIndex;
    }

    private int getNextIndex(int index) {
        if (shuffle && shuffleOrder != null) {
            int pos = shuffleOrder.indexOf(index);
            if (pos >= 0 && pos < shuffleOrder.size() - 1) {
                return shuffleOrder.get(pos + 1);
            }
            return -1;
        }
        if (index < songs.size() - 1) return index + 1;
        return -1;
    }

    private int getPreviousIndex(int index) {
        if (shuffle && shuffleOrder != null) {
            int pos = shuffleOrder.indexOf(index);
            if (pos > 0) {
                return shuffleOrder.get(pos - 1);
            }
            return -1;
        }
        if (index > 0) return index - 1;
        return -1;
    }
}
