package com.example.lldc.data

data class Song(
    val title: String,
    val artist: List<String>,
    val album: String,
    val duration: String,
    val source: String,
    val song_info_json: String
) 