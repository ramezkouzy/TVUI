package com.ramez.shasha.data

data class Account(val server: String, val username: String, val password: String) {
    /** Server without trailing slash, with a scheme. */
    val base: String
        get() {
            var s = server.trim().trimEnd('/')
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            return s
        }
}

data class Category(val id: String, val name: String)

data class LiveChannel(
    val id: Int,
    val num: Int,
    val name: String,
    val icon: String,
    val categoryId: String,
)

data class Movie(
    val id: Int,
    val name: String,
    val poster: String,
    val categoryId: String,
    val rating: String,
    val added: Long,
    val ext: String,
    val year: String = "",
)

data class Series(
    val id: Int,
    val name: String,
    val cover: String,
    val categoryId: String,
    val plot: String,
    val year: String,
    val lastModified: Long,
)

data class MovieInfo(
    val plot: String,
    val cast: String,
    val director: String,
    val genre: String,
    val duration: String,
    val year: String,
    val backdrop: String,
    val poster: String,
    val tmdbId: String,
    val ext: String,
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val ext: String,
    val plot: String,
    val image: String,
)

data class SeriesInfo(
    val plot: String,
    val cast: String,
    val genre: String,
    val year: String,
    val backdrop: String,
    val tmdbId: String,
    val seasons: Map<Int, List<Episode>>,
)

data class EpgItem(val title: String, val start: Long, val end: Long)

/** What to play, plus what the subtitle search needs to know about it. */
data class PlayRequest(
    val key: String,          // unique id for resume + subtitle cache, e.g. "movie:123"
    val url: String,
    val title: String,
    val isLive: Boolean,
    val image: String = "",
    val tmdbId: String = "",
    val searchName: String = "",
    val year: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val channelIndex: Int = -1,
)

/** A movie or episode the user stopped partway through. */
data class ResumeEntry(
    val key: String,
    val title: String,
    val image: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val request: PlayRequest,
)
