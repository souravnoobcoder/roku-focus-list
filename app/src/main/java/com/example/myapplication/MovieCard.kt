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
import androidx.compose.material3.Text
import coil3.compose.AsyncImage

@Composable
fun MovieCard(
    movie: MovieItem,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(250),
        label = "scale"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1.0f else 0.7f,
        animationSpec = tween(250),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .size(width = 220.dp, height = 140.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = cardAlpha
            }
            .clip(RoundedCornerShape(8.dp))
    ) {
        AsyncImage(
            model = movie.imageUrl,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        Text(
            text = movie.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 18.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 5.dp)
        ) {
            Text(
                text = movie.year,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 5.dp)
                    .size(3.dp)
                    .background(Color.White.copy(alpha = 0.5f), CircleShape)
            )

            Text(
                text = movie.rating,
                color = Color(0xFFFFC107),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
