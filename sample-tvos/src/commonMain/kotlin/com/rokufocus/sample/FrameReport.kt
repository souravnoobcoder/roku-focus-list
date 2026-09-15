package com.rokufocus.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.math.roundToInt

/**
 * Frame timing for the [WindowNanos] after each gesture, so a swipe can be judged by numbers as
 * well as by eye: how many frames were produced, the worst gap between two, and how many gaps
 * missed a 60 Hz vsync. Sampling is on demand only — awaiting frames forces them, which would
 * otherwise keep the device drawing at full rate while idle.
 *
 * @param trigger Bump to start a new sample; 0 means nothing has happened yet.
 */
@Composable
internal fun rememberFrameReport(trigger: Int): String {
    var report by remember { mutableStateOf("") }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        val start = withFrameNanos { it }
        var previous = start
        var frames = 0
        var worstGap = 0L
        var missedVsyncs = 0
        while (true) {
            val now = withFrameNanos { it }
            frames++
            val gap = now - previous
            previous = now
            if (gap > worstGap) worstGap = gap
            if (gap > MissedVsyncNanos) missedVsyncs++
            if (now - start >= WindowNanos) break
        }
        val seconds = (previous - start) / 1_000_000_000.0
        val fps = (frames / seconds).roundToInt()
        val worstMs = (worstGap / 1_000_000.0).roundToInt()
        report = "$fps fps · worst frame $worstMs ms · $missedVsyncs missed vsync"
        println("[roku] frames=$frames over ${(seconds * 1000).roundToInt()}ms fps=$fps worst=${worstMs}ms missed=$missedVsyncs")
    }
    return report
}

private const val WindowNanos = 1_500_000_000L

/** A 60 Hz frame is 16.7 ms; anything past this took two vsyncs. */
private const val MissedVsyncNanos = 20_000_000L
