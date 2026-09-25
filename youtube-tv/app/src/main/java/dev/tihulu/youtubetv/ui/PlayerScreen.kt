package dev.tihulu.youtubetv.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import dev.tihulu.youtubetv.BuildConfig
import dev.tihulu.youtubetv.data.PlaybackSource
import dev.tihulu.youtubetv.data.VideoItem
import dev.tihulu.youtubetv.data.YoutubeRepository

@Composable
fun PlayerScreen(
    item: VideoItem,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var source by remember(item.url) { mutableStateOf<PlaybackSource?>(null) }
    var error by remember(item.url) { mutableStateOf<String?>(null) }

    LaunchedEffect(item.url) {
        runCatching { YoutubeRepository.resolvePlayback(item) }
            .onSuccess { source = it }
            .onFailure { error = it.message ?: "Playback could not be resolved." }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            error != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Playback error", color = Color.White)
                    Text(error.orEmpty(), color = Color(0xFFB8B8B8))
                }
            }
            source == null -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
            else -> MediaPlayer(source = source!!)
        }
    }
}

@Composable
private fun MediaPlayer(source: PlaybackSource) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val player = remember(source.videoUrl, source.audioUrl) {
        val targetBytes = if (BuildConfig.LOW_RAM_PROFILE) 20 * 1024 * 1024 else 48 * 1024 * 1024
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                6_000,
                if (BuildConfig.LOW_RAM_PROFILE) 18_000 else 35_000,
                1_500,
                2_000
            )
            .setTargetBufferBytes(targetBytes)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
    }

    DisposableEffect(player, source.videoUrl, source.audioUrl) {
        val factory = DefaultMediaSourceFactory(context)
        val videoSource = factory.createMediaSource(MediaItem.fromUri(source.videoUrl))
        val mediaSource = source.audioUrl?.let { audioUrl ->
            val audioSource = factory.createMediaSource(MediaItem.fromUri(audioUrl))
            MergingMediaSource(videoSource, audioSource)
        } ?: videoSource

        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true

        onDispose {
            player.release()
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .focusable(),
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = true
                controllerAutoShow = true
                controllerShowTimeoutMs = 4_000
                keepScreenOn = true
                this.player = player
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus()
            }
        },
        update = { view ->
            view.player = player
        }
    )
}
