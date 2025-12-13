package com.example.lldc.ui.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lldc.LyricsActivity
import com.example.lldc.R
import com.example.lldc.data.Song
import com.google.gson.Gson

class SongAdapter(
    private val context: Context,
    private var songs: List<Song>
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.bind(song)
        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, LyricsActivity::class.java).apply {
                putExtra(LyricsActivity.EXTRA_SONG_INFO_JSON, song.song_info_json)
                putExtra(LyricsActivity.EXTRA_SONG_TITLE, song.title)
                putExtra(LyricsActivity.EXTRA_SONG_ARTIST, song.artist.joinToString(" / "))
            }
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return songs.size
    }

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val songTitleTextView: TextView = itemView.findViewById(R.id.songTitleTextView)
        private val songArtistTextView: TextView = itemView.findViewById(R.id.songArtistTextView)
        private val songAlbumTextView: TextView = itemView.findViewById(R.id.songAlbumTextView)
        private val songDurationTextView: TextView = itemView.findViewById(R.id.songDurationTextView)
        private val songSourceTextView: TextView = itemView.findViewById(R.id.songSourceTextView)

        fun bind(song: Song) {
            songTitleTextView.text = song.title
            // Convert artist list to a single string for display
            songArtistTextView.text = song.artist.joinToString(" / ")
            songAlbumTextView.text = song.album
            songDurationTextView.text = song.duration
            songSourceTextView.text = "来源: ${song.source}"
        }
    }
}