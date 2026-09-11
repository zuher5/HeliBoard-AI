// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.IconOrImage
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.screens.gesturedata.END_DATE_EPOCH_MILLIS
import helium314.keyboard.settings.screens.gesturedata.TWO_WEEKS_IN_MILLIS

// ─────────────────────────────────────────────────────────────────────
// Reusable grouped-card components (LeanType style)
// ─────────────────────────────────────────────────────────────────────

/** One outer ElevatedCard per settings group — large rounded corners, subtle elevation. */
@Composable
fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            content = content
        )
    }
}

/** A single row inside a [SettingsGroupCard] — circular icon badge, title/description, chevron. */
@Composable
fun SettingsGroupItem(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    @DrawableRes icon: Int? = null,
    showDivider: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(38.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    IconOrImage(icon, name, 20)
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        NextScreenIcon()
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(start = if (icon != null) 68.dp else 16.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            thickness = 0.8.dp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Main Settings Screen
// ─────────────────────────────────────────────────────────────────────

@Composable
fun MainSettingsScreen(
    onClickAbout: () -> Unit,
    onClickTextCorrection: () -> Unit,
    onClickPreferences: () -> Unit,
    onClickToolbar: () -> Unit,
    onClickGestureTyping: () -> Unit,
    onClickDataGathering: () -> Unit,
    onClickAdvanced: () -> Unit,
    onClickAppearance: () -> Unit,
    onClickLanguage: () -> Unit,
    onClickLayouts: () -> Unit,
    onClickDictionaries: () -> Unit,
    onClickAI: () -> Unit,
    onClickBack: () -> Unit,
) {
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.ime_settings),
        settings = emptyList(),
    ) {
        val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(true)
        val showGesture = JniUtils.sHaveGestureLib
        val showGestureData = showGesture &&
            System.currentTimeMillis() < END_DATE_EPOCH_MILLIS + TWO_WEEKS_IN_MILLIS

        Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) { innerPadding ->
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(vertical = 8.dp)
            ) {
                // ── Card 1 — Main ──────────────────────────────────────────
                SettingsGroupCard {
                    SettingsGroupItem(
                        name = stringResource(R.string.language_and_layouts_title),
                        description = enabledSubtypes.joinToString(", ") { it.displayName() },
                        onClick = onClickLanguage,
                        icon = R.drawable.ic_settings_languages
                    )
                    SettingsGroupItem(
                        name = stringResource(R.string.settings_screen_preferences),
                        onClick = onClickPreferences,
                        icon = R.drawable.ic_settings_preferences
                    )
                    SettingsGroupItem(
                        name = stringResource(R.string.ai_integration),
                        onClick = onClickAI,
                        icon = R.drawable.ic_translate
                    )
                    SettingsGroupItem(
                        name = stringResource(R.string.settings_screen_appearance),
                        onClick = onClickAppearance,
                        icon = R.drawable.ic_settings_appearance
                    )
                    SettingsGroupItem(
                        name = stringResource(R.string.settings_screen_toolbar),
                        onClick = onClickToolbar,
                        icon = R.drawable.ic_settings_toolbar,
                        showDivider = false
                    )
                }

                Spacer(Modifier.height(2.dp))

                // ── Card 2 — Typing ────────────────────────────────────────
                SettingsGroupCard {
                    if (showGesture) {
                        SettingsGroupItem(
                            name = stringResource(R.string.settings_screen_gesture),
                            onClick = onClickGestureTyping,
                            icon = R.drawable.ic_settings_gesture
                        )
                    }
                    if (showGestureData) {
                        SettingsGroupItem(
                            name = stringResource(R.string.gesture_data_screen),
                            onClick = onClickDataGathering,
                            icon = R.drawable.ic_settings_gesture
                        )
                    }
                    SettingsGroupItem(
                        name = stringResource(R.string.settings_screen_correction),
                        onClick = onClickTextCorrection,
                        icon = R.drawable.ic_settings_correction
                    )
                    SettingsGroupItem(
                        name = stringResource(R.string.settings_screen_secondary_layouts),
                        onClick = onClickLayouts,
                        icon = R.drawable.ic_settings_layout
                    )
                    SettingsGroupItem(
                        name = stringResource(R.string.dictionary_settings_category),
                        onClick = onClickDictionaries,
                        icon = R.drawable.ic_dictionary,
                        showDivider = false
                    )
                }

                Spacer(Modifier.height(2.dp))

                // ── Card 3 — Advanced ──────────────────────────────────────
                SettingsGroupCard {
                    SettingsGroupItem(
                        name = stringResource(R.string.settings_screen_advanced),
                        onClick = onClickAdvanced,
                        icon = R.drawable.ic_settings_advanced
                    )
                    SettingsGroupItem(
                        name = stringResource(R.string.settings_screen_about),
                        onClick = onClickAbout,
                        icon = R.drawable.ic_settings_about,
                        showDivider = false
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewScreen() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            MainSettingsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}
