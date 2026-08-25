package com.lagradost.cloudstream3.ui.result.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme

enum class BadgeSize {
    Large,
    Medium,
    Small
}

val GoldColor = Color(0xFFFFD700)
val GoldDarkColor = Color(0xFFC59B27)

@Composable
fun Top10SquareBadge(
    modifier: Modifier = Modifier,
    rank: Int? = null,
    size: BadgeSize = BadgeSize.Small
) {
    val colors = MovieDetailsTheme.colors
    val isTop3 = rank != null && rank in 1..3

    val badgeBgColor = if (isTop3) GoldColor else colors.primary
    val badgeTextColor = if (isTop3) Color.Black else colors.onPrimary

    val (badgeSize, cornerRadius, topFontSize, numberFontSize) = when (size) {
        BadgeSize.Large -> Quad(32.dp, 4.dp, 10.sp, 15.sp)
        BadgeSize.Medium -> Quad(28.dp, 3.5.dp, 9.sp, 13.5.sp)
        BadgeSize.Small -> Quad(24.dp, 3.dp, 7.5.sp, 11.5.sp)
    }

    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(RoundedCornerShape(cornerRadius))
            .background(badgeBgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "TOP",
                color = badgeTextColor,
                fontSize = topFontSize,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center,
                lineHeight = topFontSize
            )
            Text(
                text = "10",
                color = badgeTextColor,
                fontSize = numberFontSize,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
                textAlign = TextAlign.Center,
                lineHeight = numberFontSize
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun Top10RankLabel(
    rank: Int,
    isMovie: Boolean,
    modifier: Modifier = Modifier,
    size: BadgeSize = BadgeSize.Small,
    customRankText: String? = null
) {
    val colors = MovieDetailsTheme.colors
    val typography = MovieDetailsTheme.typography

    val labelText = customRankText ?: if (isMovie) {
        "#$rank in Movies Today"
    } else {
        "#$rank in TV Shows Today"
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Top10SquareBadge(rank = rank, size = size)
        Text(
            text = labelText,
            style = if (size == BadgeSize.Large) typography.mediumTitle2 else typography.mediumBody,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
    }
}

@Composable
fun ContentPillBadge(
    text: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    val colors = MovieDetailsTheme.colors
    val bg = if (isHighlighted) GoldColor else colors.primary
    val textColor = if (isHighlighted) Color.Black else colors.onPrimary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
fun MaturityRatingBadge(
    rating: String,
    modifier: Modifier = Modifier
) {
    val colors = MovieDetailsTheme.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rating,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun VideoQualityBadge(
    quality: String,
    modifier: Modifier = Modifier
) {
    val colors = MovieDetailsTheme.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = quality,
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
