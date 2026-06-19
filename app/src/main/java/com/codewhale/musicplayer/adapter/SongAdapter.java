package com.codewhale.musicplayer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.codewhale.musicplayer.R;
import com.codewhale.musicplayer.model.Song;
import com.codewhale.musicplayer.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for the song list.
 */
public class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {

    private List<Song> songs = new ArrayList<>();
    private final int itemLayoutRes;

    public interface OnItemClickListener {
        void onItemClick(Song song, int position);
    }

    private OnItemClickListener listener;

    public SongAdapter() {
        this.itemLayoutRes = R.layout.item_song;
    }

    public void setSongs(List<Song> songs) {
        this.songs = (songs != null) ? songs : new ArrayList<Song>();
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(itemLayoutRes, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Song song = songs.get(position);
        holder.bind(song, position);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAlbumArt;
        TextView  txtTitle;
        TextView  txtArtist;
        TextView  txtDuration;
        ImageView imgVideoIcon;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAlbumArt  = itemView.findViewById(R.id.img_item_album_art);
            txtTitle     = itemView.findViewById(R.id.txt_item_title);
            txtArtist    = itemView.findViewById(R.id.txt_item_artist);
            txtDuration  = itemView.findViewById(R.id.txt_item_duration);
            imgVideoIcon = itemView.findViewById(R.id.img_video_icon);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && listener != null) {
                        listener.onItemClick(songs.get(pos), pos);
                    }
                }
            });
        }

        void bind(Song song, int position) {
            txtTitle.setText(song.getDisplayTitle());
            txtArtist.setText(song.getDisplayArtist());
            txtDuration.setText(song.getDurationString());

            // Video indicator
            if (song.isVideo()) {
                imgVideoIcon.setVisibility(View.VISIBLE);
            } else {
                imgVideoIcon.setVisibility(View.GONE);
            }

            // Album art
            if (song.albumArt != null && song.albumArt.length > 0) {
                ImageLoader.loadAlbumArt(song.albumArt, imgAlbumArt, R.drawable.ic_music_note);
            } else {
                imgAlbumArt.setImageResource(song.isVideo()
                    ? R.drawable.ic_video
                    : R.drawable.ic_music_note);
            }
        }
    }
}
