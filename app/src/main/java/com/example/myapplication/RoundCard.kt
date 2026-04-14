package com.example.myapplication

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@Composable
fun RoundCard(
    movie: MovieItem,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(250),
        label = "round_scale"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1.0f else 0.65f,
        animationSpec = tween(250),
        label = "round_alpha"
    )

    val progressFraction = movie.rating.removeSuffix("%").toFloatOrNull()?.div(100f) ?: 0f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = cardAlpha
        }
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
        ) {
            AsyncImage(
                model = movie.imageUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Progress arc overlay at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(130.dp, 4.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .size((130 * progressFraction).dp, 4.dp)
                        .background(Color(0xFFE50914))
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = movie.title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
