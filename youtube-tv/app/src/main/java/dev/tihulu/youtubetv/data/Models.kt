package dev.tihulu.youtubetv.data

data class VideoItem(
    val title: String,
    val url: String,
    val thumbnailUrl: String?,
    val channel: String?,
    val durationSeconds: Long,
    val viewCount: Long
)

data class HomeShelf(
    val title: String,
    val items: List<VideoItem>
)

data class PlaybackSource(
    val title: String,
    val videoUrl: String,
    val audioUrl: String? = null,
    val qualityLabel: String = ""
)
