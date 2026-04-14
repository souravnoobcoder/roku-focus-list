package com.example.myapplication

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@Composable
fun PortraitCard(
    movie: MovieItem,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(250),
        label = "portrait_scale"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1.0f else 0.7f,
        animationSpec = tween(250),
        label = "portrait_alpha"
    )

    Box(
        modifier = modifier
            .size(width = 150.dp, height = 220.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = cardAlpha
            }
            .clip(RoundedCornerShape(12.dp))
    ) {
        AsyncImage(
            model = movie.imageUrl,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient scrim — taller for portrait layout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )

        Text(
            text = movie.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, end = 10.dp, bottom = 24.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 8.dp)
        ) {
            Text(
                text = movie.year,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(3.dp)
                    .background(Color.White.copy(alpha = 0.5f), CircleShape)
            )

            Text(
                text = movie.rating,
                color = Color(0xFFFFC107),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
