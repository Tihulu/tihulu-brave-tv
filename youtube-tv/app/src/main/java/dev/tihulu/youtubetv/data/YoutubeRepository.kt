package dev.tihulu.youtubetv.data

import dev.tihulu.youtubetv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

object YoutubeRepository {
    @Volatile
    private var initialized = false

    fun initialize() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(
                ExtractorDownloader(),
                Localization("en", "GB"),
                ContentCountry("TR")
            )
            initialized = true
        }
    }

    suspend fun loadHome(): List<HomeShelf> = withContext(Dispatchers.IO) {
        initialize()
        val limit = if (BuildConfig.LOW_RAM_PROFILE) 10 else 16
        listOf(
            "Live now" to "live",
            "Trending gaming" to "trending_gaming",
            "Trending music" to "trending_music"
        ).mapNotNull { (label, id) ->
            runCatching {
                val extractor = ServiceList.YouTube
                    .getKioskList()
                    .getExtractorById(id, null)
                extractor.fetchPage()
                val items = KioskInfo.getInfo(extractor)
                    .getRelatedItems()
                    .take(limit)
                    .map { it.toVideoItem() }
                HomeShelf(label, items)
            }.getOrNull()?.takeIf { it.items.isNotEmpty() }
        }
    }

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        initialize()
        if (query.isBlank()) return@withContext emptyList()

        val service = ServiceList.YouTube
        val handler = service.getSearchQHFactory().fromQuery(
            query.trim(),
            listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
            ""
        )
        val limit = if (BuildConfig.LOW_RAM_PROFILE) 24 else 40

        SearchInfo.getInfo(service, handler)
            .getRelatedItems()
            .asSequence()
            .filterIsInstance<StreamInfoItem>()
            .take(limit)
            .map { it.toVideoItem() }
            .toList()
    }

    suspend fun resolvePlayback(item: VideoItem): PlaybackSource = withContext(Dispatchers.IO) {
        initialize()
        val info = StreamInfo.getInfo(ServiceList.YouTube, item.url)
        val maxHeight = if (BuildConfig.LOW_RAM_PROFILE) 1080 else 2160

        val muxed = info.getVideoStreams()
            .filter(VideoStream::isUrl)
            .filter { resolutionHeight(it) in 1..maxHeight }
            .maxByOrNull(::resolutionHeight)

        // Keep the low-memory build on one muxed media pipeline whenever possible.
        if (BuildConfig.LOW_RAM_PROFILE && muxed != null) {
            return@withContext PlaybackSource(
                title = info.name,
                videoUrl = muxed.content,
                qualityLabel = muxed.resolution
            )
        }

        val videoOnly = info.getVideoOnlyStreams()
            .filter(VideoStream::isUrl)
            .filter { resolutionHeight(it) in 1..maxHeight }
            .maxByOrNull(::resolutionHeight)

        val audio = info.getAudioStreams()
            .filter(AudioStream::isUrl)
            .maxByOrNull { it.averageBitrate }

        if (videoOnly != null && audio != null) {
            return@withContext PlaybackSource(
                title = info.name,
                videoUrl = videoOnly.content,
                audioUrl = audio.content,
                qualityLabel = videoOnly.resolution
            )
        }

        if (muxed != null) {
            return@withContext PlaybackSource(
                title = info.name,
                videoUrl = muxed.content,
                qualityLabel = muxed.resolution
            )
        }

        val hls = info.hlsUrl
        if (!hls.isNullOrBlank()) {
            return@withContext PlaybackSource(
                title = info.name,
                videoUrl = hls,
                qualityLabel = "HLS"
            )
        }

        val dash = info.dashMpdUrl
        if (!dash.isNullOrBlank()) {
            return@withContext PlaybackSource(
                title = info.name,
                videoUrl = dash,
                qualityLabel = "DASH"
            )
        }

        error("No playable stream was exposed for this video.")
    }

    private fun resolutionHeight(stream: VideoStream): Int {
        return Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE)
            .find(stream.resolution)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: stream.height.takeIf { it > 0 }
            ?: 0
    }

    private fun StreamInfoItem.toVideoItem(): VideoItem {
        val thumbnail = thumbnails
            .maxByOrNull { image ->
                val width = image.width.coerceAtLeast(1)
                val height = image.height.coerceAtLeast(1)
                width.toLong() * height.toLong()
            }
            ?.url

        return VideoItem(
            title = name,
            url = url,
            thumbnailUrl = thumbnail,
            channel = uploaderName,
            durationSeconds = duration,
            viewCount = viewCount
        )
    }
}
