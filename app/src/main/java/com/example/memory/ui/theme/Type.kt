package com.example.memory.ui.theme

import androidx.compose.material3.Typography

// Material typography styles with the Assistant font family applied throughout.
val Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Assistant),
        displayMedium = base.displayMedium.copy(fontFamily = Assistant),
        displaySmall = base.displaySmall.copy(fontFamily = Assistant),
        headlineLarge = base.headlineLarge.copy(fontFamily = Assistant),
        headlineMedium = base.headlineMedium.copy(fontFamily = Assistant),
        headlineSmall = base.headlineSmall.copy(fontFamily = Assistant),
        titleLarge = base.titleLarge.copy(fontFamily = Assistant),
        titleMedium = base.titleMedium.copy(fontFamily = Assistant),
        titleSmall = base.titleSmall.copy(fontFamily = Assistant),
        bodyLarge = base.bodyLarge.copy(fontFamily = Assistant),
        bodyMedium = base.bodyMedium.copy(fontFamily = Assistant),
        bodySmall = base.bodySmall.copy(fontFamily = Assistant),
        labelLarge = base.labelLarge.copy(fontFamily = Assistant),
        labelMedium = base.labelMedium.copy(fontFamily = Assistant),
        labelSmall = base.labelSmall.copy(fontFamily = Assistant),
    )
}