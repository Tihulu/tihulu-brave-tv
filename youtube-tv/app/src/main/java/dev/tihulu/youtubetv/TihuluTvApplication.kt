package dev.tihulu.youtubetv

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import dev.tihulu.youtubetv.data.YoutubeRepository

class TihuluTvApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        YoutubeRepository.initialize()
    }

    override fun newImageLoader(context: Context): ImageLoader {
        val cacheFraction = if (BuildConfig.LOW_RAM_PROFILE) 0.08 else 0.18
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, cacheFraction)
                    .build()
            }
            .crossfade(!BuildConfig.LOW_RAM_PROFILE)
            .build()
    }
}
