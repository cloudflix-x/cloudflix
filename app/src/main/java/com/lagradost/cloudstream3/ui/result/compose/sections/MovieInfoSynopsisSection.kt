package com.lagradost.cloudstream3.ui.result.compose.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.compose.components.BadgeSize
import com.lagradost.cloudstream3.ui.result.compose.components.ContentPillBadge
import com.lagradost.cloudstream3.ui.result.compose.components.MaturityRatingBadge
import com.lagradost.cloudstream3.ui.result.compose.components.MovieDetailsTokens
import com.lagradost.cloudstream3.ui.result.compose.components.Top10RankLabel
import com.lagradost.cloudstream3.ui.result.compose.components.VideoQualityBadge
import com.lagradost.cloudstream3.ui.result.compose.theme.MovieDetailsTheme
import com.lagradost.cloudstream3.ui.result.compose.theme.getRatingScoreColor

@Composable
fun MovieInfoSynopsisSection(
    matchScore: String?,
    releaseYear: String?,
    seasonsCount: String?,
    quality: String?,
    maturityRating: String?,
    advisories: String?,
    top10RankText: String?,
    synopsis: String,
    castList: List<String>,
    genres: List<String>,
    moodTags: List<String> = emptyList(),
    contentDescriptors: List<String> = emptyList(),
    directors: List<String> = emptyList(),
    creators: List<String> = emptyList(),
    writers: List<String> = emptyList(),
    top10Rank: Int? = null,
    contentBadge: String? = null,
    isMovie: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = MovieDetailsTheme.colors
    val typography = MovieDetailsTheme.typography
    val dimens = MovieDetailsTheme.dimens
    val hasRightColumn = castList.isNotEmpty() || genres.isNotEmpty() || moodTags.isNotEmpty() ||
            creators.isNotEmpty() || directors.isNotEmpty() || writers.isNotEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing2Xl, vertical = dimens.spacingL),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing2Xl)
    ) {
        Column(
            modifier = Modifier
                .weight(if (hasRightColumn) 0.65f else 1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingM)
        ) {
            val hasLine1 = !matchScore.isNullOrBlank() || !releaseYear.isNullOrBlank() ||
                    !seasonsCount.isNullOrBlank() || !quality.isNullOrBlank()

            if (hasLine1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingM)
                ) {
                    if (!matchScore.isNullOrBlank()) {
                        Text(
                            text = matchScore,
                            color = getRatingScoreColor(matchScore),
                            style = typography.mediumBody,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!releaseYear.isNullOrBlank()) {
                        Text(
                            text = releaseYear,
                            color = colors.textPrimary,
                            style = typography.regularBody
                        )
                    }

                    if (!seasonsCount.isNullOrBlank()) {
                        Text(
                            text = seasonsCount,
                            color = colors.textPrimary,
                            style = typography.regularBody
                        )
                    }

                    if (!quality.isNullOrBlank()) {
                        VideoQualityBadge(quality = quality)
                    }
                }
            }

            if (!contentBadge.isNullOrBlank()) {
                ContentPillBadge(
                    text = contentBadge,
                    isHighlighted = (contentBadge == "Must Watch")
                )
            }

            val advisoryText = advisories?.takeIf { it.isNotBlank() }
                ?: contentDescriptors.takeIf { it.isNotEmpty() }?.joinToString(", ")
            if (!maturityRating.isNullOrBlank() || !advisoryText.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingS)
                ) {
                    if (!maturityRating.isNullOrBlank()) {
                        MaturityRatingBadge(rating = maturityRating)
                    }
                    if (!advisoryText.isNullOrBlank()) {
                        Text(
                            text = advisoryText,
                            color = colors.textMuted,
                            style = typography.regularCaption1
                        )
                    }
                }
            }

            if (top10Rank != null || !top10RankText.isNullOrBlank()) {
                Top10RankLabel(
                    rank = top10Rank ?: 1,
                    isMovie = isMovie,
                    customRankText = top10RankText,
                    size = BadgeSize.Small,
                    modifier = Modifier.padding(vertical = dimens.spacingXs)
                )
            }

            if (synopsis.isNotBlank()) {
                val synopsisInteractionSource = remember { MutableInteractionSource() }
                val isSynopsisFocused by synopsisInteractionSource.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MovieDetailsTokens.ShapeCardSmall)
                        .background(if (isSynopsisFocused) colors.surfaceElevated.copy(alpha = 0.5f) else Color.Transparent)
                        .then(
                            if (isSynopsisFocused) {
                                Modifier.border(
                                    BorderStroke(dimens.borderFocus, colors.primary),
                                    MovieDetailsTokens.ShapeCardSmall
                                )
                            } else Modifier
                        )
                        .focusable(interactionSource = synopsisInteractionSource)
                        .padding(dimens.spacingS)
                ) {
                    Text(
                        text = synopsis,
                        style = typography.regularBody,
                        color = if (isSynopsisFocused) colors.textPrimary else colors.textSecondary,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        if (hasRightColumn) {
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingM)
            ) {
                if (castList.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(id = R.string.cast_label),
                            style = typography.regularCaption2,
                            color = colors.textSecondary
                        )
                        Text(
                            text = castList.joinToString(", "),
                            style = typography.regularCaption1,
                            color = colors.textPrimary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (genres.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(id = R.string.genres_label),
                            style = typography.regularCaption2,
                            color = colors.textSecondary
                        )
                        Text(
                            text = genres.joinToString(", "),
                            style = typography.regularCaption1,
                            color = colors.textPrimary
                        )
                    }
                }

                if (creators.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(id = if (creators.size > 1) R.string.creators_label else R.string.creator_label),
                            style = typography.regularCaption2,
                            color = colors.textSecondary
                        )
                        Text(
                            text = creators.joinToString(", "),
                            style = typography.regularCaption1,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (directors.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(id = if (directors.size > 1) R.string.directors_label else R.string.director_label),
                            style = typography.regularCaption2,
                            color = colors.textSecondary
                        )
                        Text(
                            text = directors.joinToString(", "),
                            style = typography.regularCaption1,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (writers.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(id = R.string.writers_label),
                            style = typography.regularCaption2,
                            color = colors.textSecondary
                        )
                        Text(
                            text = writers.joinToString(", "),
                            style = typography.regularCaption1,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (moodTags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(id = R.string.mood_tags_label),
                            style = typography.regularCaption2,
                            color = colors.textSecondary
                        )
                        Text(
                            text = moodTags.joinToString(", "),
                            style = typography.regularCaption1,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}
