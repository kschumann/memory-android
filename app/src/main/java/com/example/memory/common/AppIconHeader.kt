package com.example.memory.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.memory.R

// R.mipmap.ic_launcher resolves to an <adaptive-icon> XML on API 26+, which Compose's
// painterResource can't load (it only supports VectorDrawables and rasterized bitmaps). The
// foreground layer is a plain PNG per density, so we composite it over the launcher background
// color ourselves instead. The foreground PNG follows the adaptive-icon safe-zone spec (glyph
// only fills the inner ~66% of the canvas), so it's scaled up 1.5x to fill the visible circle.
@Composable
fun AppIconHeader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colorResource(id = R.color.ic_launcher_background)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(72.dp)
            )
        }
    }
}
