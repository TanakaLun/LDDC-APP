package com.example.lldc.network

import com.example.lldc.data.Song
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.lang.reflect.Type

class SongDeserializer : JsonDeserializer<Song> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): Song {
        val jsonObject = json?.asJsonObject ?: JsonObject()

        // Safely get a string value from JsonObject
        fun JsonObject.getString(key: String): String {
            return if (this.has(key) && this.get(key).isJsonPrimitive) {
                this.get(key).asString
            } else {
                ""
            }
        }

        val title = jsonObject.getString("title")
        val album = jsonObject.getString("album")
        val duration = jsonObject.getString("duration")
        val source = jsonObject.getString("source")
        val songInfoJson = jsonObject.getString("song_info_json")

        val artistList = mutableListOf<String>()
        if (jsonObject.has("artist")) {
            val artistElement = jsonObject.get("artist")
            if (artistElement.isJsonArray) {
                artistElement.asJsonArray.forEach { artistList.add(it.asString) }
            } else if (artistElement.isJsonPrimitive) {
                artistList.add(artistElement.asString)
            }
        }

        return Song(
            title = title,
            artist = artistList,
            album = album,
            duration = duration,
            source = source,
            song_info_json = songInfoJson
        )
    }
} 