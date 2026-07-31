package com.printready.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    // xl = 8dp — cards, primary buttons
    large = RoundedCornerShape(8.dp),
    // full = 12dp — chips, icon circles
    extraLarge = RoundedCornerShape(12.dp),
    // lg = 4dp — input fields, small pills
    medium = RoundedCornerShape(4.dp),
    // DEFAULT = 2dp — badges, margin guide corners
    small = RoundedCornerShape(2.dp)
)
