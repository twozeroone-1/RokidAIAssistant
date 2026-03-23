package com.example.rokidglasses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.rokidglasses.viewmodel.GlassesDisplayStage

@Composable
fun SleepModeIndicator(
    stage: GlassesDisplayStage,
    modifier: Modifier = Modifier,
) {
    val activeIndex = when (stage) {
        GlassesDisplayStage.IDLE -> 0
        GlassesDisplayStage.CAPTURING_INPUT -> 1
        GlassesDisplayStage.SENDING -> 2
        GlassesDisplayStage.ANALYZING -> 3
        GlassesDisplayStage.OUTPUT -> 4
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == activeIndex) 10.dp else 8.dp)
                    .background(
                        color = if (index == activeIndex) Color.White else Color.White.copy(alpha = 0.2f),
                        shape = CircleShape,
                    )
            )
        }
    }
}
