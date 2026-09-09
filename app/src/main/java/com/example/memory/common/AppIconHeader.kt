package com.example.memory.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.memory.R

// R.mipmap.ic_launcher resolves to an <adaptive-icon> XML on API 26+, which Compose's
// painterResource can't load (it only supports VectorDrawables and rasterized bitmaps). The
// foreground layer is a plain PNG per density, so we composite it over the launcher background
// color ourselves instead. Measured directly against the actual asset (not the usual adaptive-icon
// 66% safe-zone assumption, which doesn't hold here): the glyph only occupies the inner ~50% of
// the canvas, so it needs to be scaled up 2x to fill the visible circle. That has to be
// requiredSize, not size: a plain Box clamps a child's measured size down to its own bounds by
// default, which was silently capping the "oversized" image back down to exactly fill the circle
// with no overflow left for the clip to crop - i.e. the scale factor was never actually taking
// effect. requiredSize ignores that incoming constraint so the image truly renders oversized and
// clip(CircleShape) has something to crop.
@Composable
fun AppIcon(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            // Matches the TopAppBar's own background (colorScheme.surface) rather than the
            // launcher icon's XML color resource, so the circle blends into the header bar in
            // both light and dark theme instead of only coincidentally matching light mode.
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 2f)
        )
    }
}

// MainActivity calls enableEdgeToEdge(), so this top-bar area is drawn from y=0 under the status
// bar. consumeWindowInsets marks the status bar inset as already handled so the TopAppBar/
// CenterAlignedTopAppBar passed as `content` (which pads for it automatically via its own default
// `windowInsets` param) doesn't pad again for the same inset and double the gap.
@Composable
fun ScreenTopBar(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .statusBarsPadding()
            .consumeWindowInsets(WindowInsets.statusBars)
    ) {
        content()
    }
}
