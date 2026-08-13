package app.cairn.feature.collect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.cairn.core.designsystem.CairnCard
import app.cairn.core.designsystem.CairnListRow
import app.cairn.core.designsystem.CairnRowDivider
import app.cairn.core.designsystem.CairnSectionHeading
import app.cairn.core.designsystem.CairnStatus
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.designsystem.Spacing

/**
 * Settings: who is signed in, which server, when this device last synced, and
 * the way out.
 *
 * Deliberately shorter than the design. Language, attachment storage, retention
 * and the licence list are all drawn in `DESIGN.md` and none of them have
 * anything behind them yet — localisation is a later step, attachments are on
 * the cut list, and a licence screen without the `licensee` plugin would be a
 * claim rather than a check. A row that does nothing is worse than an absent
 * one, so they are absent.
 */
@Composable
public fun SettingsScreen(
    state: SettingsUiState,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { SettingsAppBar() },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.Gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.Large),
        ) {
            Identity(state)

            Section("Sync") {
                CairnListRow(
                    primary = "Last synced",
                    trailing = {
                        Text(
                            text = state.lastSynced,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("last_synced"),
                        )
                    },
                )
                CairnRowDivider()
                CairnListRow(
                    primary = "Server",
                    secondary = "Set when this app was installed.",
                    trailing = {
                        Text(
                            text = state.server.ifBlank { "Not configured" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("server"),
                        )
                    },
                )
            }

            Section("About") {
                CairnListRow(
                    primary = "Version",
                    trailing = {
                        Text(
                            text = state.version,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("version"),
                        )
                    },
                )
            }

            Spacer(Modifier.height(Spacing.Small))
            SignOut(state, onSignOut)
            Spacer(Modifier.height(Spacing.XLarge))
        }
    }
}

@Composable
private fun SettingsAppBar() {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(horizontal = Spacing.Gutter, vertical = Spacing.Large)
                .testTag("screen_title"),
        )
        HorizontalDivider(thickness = Spacing.Hairline, color = MaterialTheme.colorScheme.outline)
    }
}

/**
 * Two initials in a tonal circle. There are no photographs anywhere in this app,
 * so this is what a person looks like.
 */
@Composable
private fun Identity(state: SettingsUiState) {
    CairnCard(Modifier.testTag("identity")) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(AvatarSize)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.initials,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("initials"),
                )
            }
            Spacer(Modifier.size(Spacing.Large))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                Text(
                    text = state.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(500)),
                    modifier = Modifier.testTag("signed_in_as"),
                )
                if (state.stale) {
                    // A stale session is not an error and must not read as one:
                    // capture still works, and the collector needs to know that
                    // rather than to be told something is wrong.
                    CairnStatus(
                        word = "Signed in, waiting for the server",
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.testTag("stale"),
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(heading: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        CairnSectionHeading(heading)
        CairnCard(Modifier.testTag("section_${heading.lowercase()}")) { content() }
    }
}

/**
 * Outlined in the error colour, never filled red — a destructive action should
 * be findable without being the loudest thing on the screen.
 *
 * The count above it is what the repository will refuse on, stated before the
 * tap rather than after it.
 */
@Composable
private fun SignOut(state: SettingsUiState, onSignOut: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        if (state.pendingCount > 0) {
            Text(
                text = "${state.pendingCount} ${plural(state.pendingCount, "submission")} " +
                    "${if (state.pendingCount == 1) "has" else "have"} not uploaded yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.testTag("pending_warning"),
            )
        }
        OutlinedButton(
            onClick = onSignOut,
            shape = MaterialTheme.shapes.small,
            border = androidx.compose.foundation.BorderStroke(
                Spacing.Hairline,
                SolidColor(MaterialTheme.colorScheme.error),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.CaptureControl)
                .testTag("sign_out"),
        ) {
            Text(
                text = "Sign out",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(500)),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private val AvatarSize = 48.dp

@Preview(name = "Settings", widthDp = 390, heightDp = 844)
@Composable
private fun SettingsPreview() {
    CairnTheme {
        SettingsScreen(state = previewSettings(), onSignOut = {})
    }
}

@Preview(name = "Settings stale", widthDp = 390, heightDp = 844)
@Composable
private fun SettingsStalePreview() {
    CairnTheme {
        SettingsScreen(
            state = previewSettings().copy(stale = true, pendingCount = 3, lastSynced = "2 hours ago"),
            onSignOut = {},
        )
    }
}
