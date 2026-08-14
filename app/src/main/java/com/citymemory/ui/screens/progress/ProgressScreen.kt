package com.citymemory.ui.screens.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.citymemory.domain.model.Achievement
import com.citymemory.domain.model.CategoryProgress
import com.citymemory.domain.model.ExplorationProgress
import com.citymemory.ui.components.GlowProgressBar
import com.citymemory.ui.components.LoadingState
import com.citymemory.ui.theme.CityNight
import com.citymemory.ui.theme.CitySurface
import com.citymemory.ui.theme.GlowAmber
import com.citymemory.ui.theme.GlowCore
import com.citymemory.ui.theme.TextSecondary
import com.citymemory.ui.theme.TextTertiary
import com.citymemory.ui.theme.WishCyan
import com.citymemory.ui.theme.hue
import com.citymemory.ui.theme.icon

@Composable
fun ProgressScreen(
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isLoading) {
        LoadingState(modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp),
            ) {
                Text(
                    text = "Progress",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.cityName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }

        item(key = "overall") {
            OverallCard(
                progress = state.progress,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        item(key = "level") {
            LevelCard(
                progress = state.progress,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        item(key = "categories-label") { SectionLabel("BY CATEGORY") }

        items(state.progress.categories, key = { it.category.id }) { category ->
            CategoryRow(category, Modifier.padding(horizontal = 20.dp))
        }

        item(key = "achievements-label") {
            SectionLabel("ACHIEVEMENTS  ·  ${state.unlockedCount}/${state.achievements.size}")
        }

        items(state.achievements, key = { it.id.name }) { achievement ->
            AchievementCard(achievement, Modifier.padding(horizontal = 20.dp))
        }
    }
}

@Composable
private fun OverallCard(progress: ExplorationProgress, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = CitySurface,
    ) {
        Column(
            Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(GlowAmber.copy(alpha = 0.08f), Color.Transparent),
                    ),
                )
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${progress.percent}%",
                    style = MaterialTheme.typography.displayMedium,
                    color = GlowCore,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Explored",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            GlowProgressBar(
                fraction = progress.fraction,
                height = 10.dp,
                contentDescription = "${progress.percent} percent explored",
            )

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${progress.visitedCount} / ${progress.totalCount} Places",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                if (progress.wishlistCount > 0) {
                    Text(
                        text = "${progress.wishlistCount} wishlisted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WishCyan,
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelCard(progress: ExplorationProgress, modifier: Modifier = Modifier) {
    val level = progress.level

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = CitySurface,
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(GlowAmber.copy(alpha = 0.45f), GlowAmber.copy(alpha = 0.08f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${level.level}",
                    style = MaterialTheme.typography.titleLarge,
                    color = GlowCore,
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = "Explorer Level ${level.level}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = level.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = GlowAmber,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = progress.visitsToNextLevel
                        ?.let { "$it more ${if (it == 1) "place" else "places"} to level up" }
                        ?: "Every place explored. The whole city is lit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(category: CategoryProgress, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = category.category.icon,
                contentDescription = null,
                tint = category.category.hue.copy(alpha = 0.9f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = category.category.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${category.visited} / ${category.total}",
                style = MaterialTheme.typography.labelLarge,
                color = if (category.visited > 0) GlowAmber else TextTertiary,
            )
        }
        Spacer(Modifier.height(8.dp))
        GlowProgressBar(
            fraction = category.fraction,
            height = 6.dp,
            accent = category.category.hue,
            contentDescription = "${category.category.displayName}: " +
                "${category.visited} of ${category.total} explored",
        )
    }
}

@Composable
private fun AchievementCard(achievement: Achievement, modifier: Modifier = Modifier) {
    val unlocked = achievement.isUnlocked

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CitySurface,
        border = BorderStroke(
            1.dp,
            if (unlocked) GlowAmber.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.05f),
        ),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (unlocked) {
                            Brush.radialGradient(
                                listOf(GlowAmber.copy(alpha = 0.55f), GlowAmber.copy(alpha = 0.10f)),
                            )
                        } else {
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (unlocked) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = CityNight,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(
                        text = "${achievement.progress}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary,
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (unlocked) GlowCore else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                if (!unlocked) {
                    Spacer(Modifier.height(8.dp))
                    GlowProgressBar(
                        fraction = achievement.fraction,
                        height = 4.dp,
                        contentDescription = "${achievement.progress} of ${achievement.target}",
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextTertiary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}
