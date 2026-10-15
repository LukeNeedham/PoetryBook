package com.lukeneedham.poetrybook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Colours and type for the pages of the book; deliberately independent of the app's dynamic colour. */
data class PaperColors(
    val paper: Color,
    val ink: Color,
    val faintInk: Color,
    val accent: Color,
)

private val LightPaper = PaperColors(
    paper = Color(0xFFFBF6EA),
    ink = Color(0xFF2B2620),
    faintInk = Color(0xFF8A8174),
    accent = Color(0xFF8B4A2B),
)

private val DarkPaper = PaperColors(
    paper = Color(0xFF211F1B),
    ink = Color(0xFFE9E2D3),
    faintInk = Color(0xFF8F887B),
    accent = Color(0xFFD9A06F),
)

@Composable
fun paperColors(darkTheme: Boolean = isSystemInDarkTheme()): PaperColors =
    if (darkTheme) DarkPaper else LightPaper

val PoemFont: FontFamily = FontFamily.Serif
