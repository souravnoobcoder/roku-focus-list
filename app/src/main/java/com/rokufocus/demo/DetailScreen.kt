package com.rokufocus.demo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

// focusProperties.exit is the supported containment hook; experimental in this Compose version.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DetailScreen(
    movie: MovieItem,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val playFocusRequester = remember { FocusRequester() }

    // Grab focus when the detail screen appears — onto the primary action, like a real title page.
    LaunchedEffect(visible) {
        if (visible) {
            delay(100)
            try { playFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(200)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Back || event.key == Key.Escape)
                    ) {
                        onDismiss()
                        true
                    } else false
                }
                // Focus stays inside the overlay: without this, a stray D-pad press would walk
                // focus onto the still-composed browse column behind it.
                .focusProperties { exit = { FocusRequester.Cancel } }
                .background(Color(0xFF0E0E0E))
        ) {
            // Hero image — top half
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                AsyncImage(
                    model = movie.imageUrl,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Gradient scrim over image
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.5f to Color.Transparent,
                                1f to Color(0xFF0E0E0E),
                            )
                        )
                )
            }

            // Content overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 48.dp, end = 48.dp, top = 260.dp)
            ) {
                Text(
                    text = movie.title,
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 42.sp,
                )

                Spacer(Modifier.height(12.dp))

                // Metadata row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetadataBadge(text = movie.year)

                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(Color.White.copy(alpha = 0.4f), CircleShape)
                    )

                    Text(
                        text = "${movie.rating} / 10",
                        color = Color(0xFFFFC107),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(Color.White.copy(alpha = 0.4f), CircleShape)
                    )

                    MetadataBadge(text = "HD")
                    MetadataBadge(text = "5.1")
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "A captivating story that takes audiences on an unforgettable journey. " +
                        "Featuring stunning visuals and powerful performances, this film has " +
                        "earned critical acclaim and a devoted following worldwide.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.width(600.dp),
                )

                Spacer(Modifier.height(32.dp))

                // Action buttons — real focus targets: LEFT/RIGHT moves between them via the
                // platform's own focus search, nothing hand-rolled.
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ActionButton(
                        label = "Play",
                        primary = true,
                        modifier = Modifier.focusRequester(playFocusRequester)
                    )
                    ActionButton(label = "My List", primary = false)
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    text = "Press BACK to return",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun MetadataBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(150), label = "btn_scale")
    val background by animateColorAsState(
        when {
            primary -> Color.White
            focused -> Color.White.copy(alpha = 0.3f)
            else -> Color.White.copy(alpha = 0.15f)
        },
        tween(150),
        label = "btn_bg"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(
                width = 2.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = 32.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) Color.Black else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun DetailScreenPreview() {
    DetailScreen(
        movie = MovieItem(
            id = 1,
            title = "Interstellar",
            year = "2014",
            rating = "8.7",
            imageUrl = ""
        ),
        visible = true,
        onDismiss = {}
    )
}
