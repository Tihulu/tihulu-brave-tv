package dev.tihulu.youtubetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.tihulu.youtubetv.BuildConfig
import dev.tihulu.youtubetv.data.HomeShelf
import dev.tihulu.youtubetv.data.VideoItem
import dev.tihulu.youtubetv.data.YoutubeRepository
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val TvBackground = Color(0xFF0F0F0F)
private val TvPanel = Color(0xFF1B1B1B)
private val TvCard = Color(0xFF262626)
private val TvFocus = Color(0xFFFFFFFF)
private val TvAccent = Color(0xFFFF0033)
private val TvMuted = Color(0xFFAAAAAA)

private sealed interface AppScreen {
    data object Home : AppScreen
    data object Search : AppScreen
    data class Player(val item: VideoItem) : AppScreen
}

@Composable
fun TihuluYoutubeTvApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = TvBackground,
            surface = TvPanel,
            primary = TvAccent
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = TvBackground
        ) {
            var screen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }

            when (val current = screen) {
                AppScreen.Home -> TvShell(
                    selected = "Home",
                    onHome = { screen = AppScreen.Home },
                    onSearch = { screen = AppScreen.Search }
                ) {
                    HomeScreen(onPlay = { screen = AppScreen.Player(it) })
                }

                AppScreen.Search -> TvShell(
                    selected = "Search",
                    onHome = { screen = AppScreen.Home },
                    onSearch = { screen = AppScreen.Search }
                ) {
                    SearchScreen(onPlay = { screen = AppScreen.Player(it) })
                }

                is AppScreen.Player -> PlayerScreen(
                    item = current.item,
                    onBack = { screen = AppScreen.Home }
                )
            }
        }
    }
}

@Composable
private fun TvShell(
    selected: String,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    content: @Composable () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        SideRail(
            selected = selected,
            onHome = onHome,
            onSearch = onSearch
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            content()
        }
    }
}

@Composable
private fun SideRail(
    selected: String,
    onHome: () -> Unit,
    onSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(190.dp)
            .fillMaxHeight()
            .background(Color(0xEE121212))
            .padding(horizontal = 18.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TvAccent),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color.White, fontSize = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "TIHULU",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
                Text(
                    text = "TV",
                    color = TvMuted,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        RailButton(
            label = "Home",
            selected = selected == "Home",
            onClick = onHome
        )
        RailButton(
            label = "Search",
            selected = selected == "Search",
            onClick = onSearch
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = if (BuildConfig.LOW_RAM_PROFILE) "ARM32 • LOW RAM" else "ARM64 • HQ",
            color = Color(0xFF777777),
            fontSize = 10.sp,
            modifier = Modifier.padding(10.dp)
        )
    }
}

@Composable
private fun RailButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Button(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                focused -> Color.White
                selected -> Color(0xFF333333)
                else -> Color.Transparent
            },
            contentColor = if (focused) Color.Black else Color.White
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun HomeScreen(onPlay: (VideoItem) -> Unit) {
    var shelves by remember { mutableStateOf<List<HomeShelf>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { YoutubeRepository.loadHome() }
            .onSuccess { shelves = it }
            .onFailure { error = it.message ?: "Could not load YouTube discovery feeds." }
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 34.dp, top = 34.dp, end = 38.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Watch what you want.",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Direct playback • no embedded YouTube web player",
                    color = TvMuted,
                    fontSize = 15.sp
                )
            }
        }

        if (loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        error?.let { message ->
            item {
                Text(message, color = Color(0xFFFF9B9B), fontSize = 16.sp)
            }
        }

        shelves.forEach { shelf ->
            item(key = shelf.title) {
                ContentShelf(shelf = shelf, onPlay = onPlay)
            }
        }
    }
}

@Composable
private fun ContentShelf(
    shelf: HomeShelf,
    onPlay: (VideoItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = shelf.title,
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 36.dp)
        ) {
            rowItems(shelf.items, key = { it.url }) { item ->
                VideoCard(
                    item = item,
                    width = if (BuildConfig.LOW_RAM_PROFILE) 285.dp else 310.dp,
                    onClick = { onPlay(item) }
                )
            }
        }
    }
}

@Composable
private fun SearchScreen(onPlay: (VideoItem) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var requestId by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (query.isBlank() || loading) return
        requestId += 1
        val myRequest = requestId
        loading = true
        error = null
        scope.launch {
            runCatching { YoutubeRepository.search(query) }
                .onSuccess {
                    if (requestId == myRequest) results = it
                }
                .onFailure {
                    if (requestId == myRequest) error = it.message ?: "Search failed."
                }
            if (requestId == myRequest) loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 34.dp, top = 30.dp, end = 40.dp, bottom = 32.dp)
    ) {
        Text(
            text = "Search",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(18.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search YouTube") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() })
            )
            Button(
                onClick = { submit() },
                enabled = query.isNotBlank() && !loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Text("Search", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(22.dp))

        if (loading) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(18.dp))
        }

        error?.let {
            Text(it, color = Color(0xFFFF9B9B))
            Spacer(Modifier.height(16.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(if (BuildConfig.LOW_RAM_PROFILE) 3 else 4),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 38.dp)
        ) {
            gridItems(results, key = { it.url }) { item ->
                VideoCard(
                    item = item,
                    width = 270.dp,
                    onClick = { onPlay(item) }
                )
            }
        }
    }
}

@Composable
private fun VideoCard(
    item: VideoItem,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(width)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (focused) Color.White else TvCard,
            contentColor = if (focused) Color.Black else Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (focused) 12.dp else 0.dp
        )
    ) {
        Column {
            Box {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color(0xFF303030)),
                    contentScale = ContentScale.Crop
                )
                if (item.durationSeconds > 0) {
                    Text(
                        text = formatDuration(item.durationSeconds),
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(7.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xD9000000))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(13.dp)) {
                Text(
                    text = item.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = buildMeta(item),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (focused) Color(0xFF444444) else TvMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun buildMeta(item: VideoItem): String {
    val parts = mutableListOf<String>()
    item.channel?.takeIf { it.isNotBlank() }?.let(parts::add)
    if (item.viewCount > 0) {
        parts += NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT)
            .format(item.viewCount) + " views"
    }
    return parts.joinToString(" • ")
}

private fun formatDuration(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
