package com.sesliplanlayici.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sesliplanlayici.app.R

// Both fonts are variable; each weight is a separate instance of the same file.
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val Bricolage = FontFamily(
    variableFont(R.font.bricolage_grotesque, FontWeight.SemiBold),
    variableFont(R.font.bricolage_grotesque, FontWeight.Bold),
)

private val Figtree = FontFamily(
    variableFont(R.font.figtree, FontWeight.Normal),
    variableFont(R.font.figtree, FontWeight.Medium),
    variableFont(R.font.figtree, FontWeight.SemiBold),
)

private fun heading(size: Int, lineHeight: Double, weight: FontWeight) = TextStyle(
    fontFamily = Bricolage,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
)

private fun body(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontFamily = Figtree,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

// Sizes follow docs/design: screen titles 28–40, card titles 18, body 15, chips 14, captions 12–13.
internal val AppTypography = Typography(
    displayLarge = heading(60, 1.0, FontWeight.SemiBold),
    displayMedium = heading(40, 1.05, FontWeight.Bold),
    headlineLarge = heading(32, 1.1, FontWeight.Bold),
    headlineMedium = heading(30, 1.15, FontWeight.Bold),
    headlineSmall = heading(28, 1.3, FontWeight.SemiBold),
    titleLarge = body(18, 24, FontWeight.SemiBold),
    titleMedium = body(16, 22, FontWeight.SemiBold),
    titleSmall = body(14, 20, FontWeight.SemiBold),
    bodyLarge = body(16, 22, FontWeight.Normal),
    bodyMedium = body(15, 21, FontWeight.Normal),
    bodySmall = body(13, 18, FontWeight.Normal),
    labelLarge = body(17, 22, FontWeight.SemiBold),
    labelMedium = body(14, 18, FontWeight.Medium),
    labelSmall = body(12, 16, FontWeight.SemiBold).copy(letterSpacing = 0.06.em),
)
