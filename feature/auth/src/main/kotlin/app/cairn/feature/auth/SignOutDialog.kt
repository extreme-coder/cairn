package app.cairn.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.cairn.core.designsystem.CairnIcons
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.designsystem.IconSize
import app.cairn.core.designsystem.Spacing

/**
 * Asks before wiping the device, then asks again if that would lose work.
 *
 * The consequence is stated in the same words as the button that carries it out,
 * and the count is quantified rather than described. Both are the voice rules,
 * and both matter more here than anywhere else in the app: this is the one
 * action that can destroy an observation.
 */
@Composable
public fun SignOutDialog(
    state: SignOutUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state == SignOutUiState.Hidden) return
    val discarding = state as? SignOutUiState.WouldDiscard

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        title = {
            Text(
                text = "Sign out",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            if (discarding != null) {
                PendingWarning(discarding.pending)
            } else {
                Text(
                    text = "This device keeps nothing after you sign out. " +
                        "Everything already uploaded stays on the server.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("sign_out_body"),
                )
            }
        },
        confirmButton = {
            // Destructive actions are outlined in the error colour, never filled.
            OutlinedButton(
                onClick = onConfirm,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(Spacing.Hairline, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.testTag("confirm_sign_out"),
            ) {
                Text(
                    text = if (discarding != null) "Delete and sign out" else "Sign out",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_sign_out")) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        },
    )
}

/**
 * The same ochre row the Settings screen uses, because it is the same fact: work
 * on this device that the server has never seen.
 */
@Composable
private fun PendingWarning(pending: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                MaterialTheme.shapes.small,
            )
            .padding(Spacing.Large),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = CairnIcons.Alert,
            // The sentence is the warning, and it is the thing that must be
            // read before the button below it is pressed.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(IconSize),
        )
        Spacer(Modifier.size(Spacing.Medium))
        Text(
            text = "${pending.submissions()} not uploaded yet. " +
                "Signing out deletes ${if (pending == 1) "it" else "them"} from this device.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(top = 2.dp)
                .testTag("pending_warning"),
        )
    }
}

/**
 * English plurals, in code, until `strings.xml` and `<plurals>` arrive with
 * localisation. Written out rather than "1 submission(s)", which is the kind of
 * thing that reads as unfinished software.
 */
internal fun Int.submissions(): String =
    if (this == 1) "1 submission has" else "$this submissions have"

@Preview(name = "Sign out", widthDp = 390)
@Composable
private fun SignOutPreview() {
    CairnTheme {
        SignOutDialog(SignOutUiState.Confirming, onConfirm = {}, onDismiss = {})
    }
}

@Preview(name = "Sign out with queue", widthDp = 390)
@Composable
private fun SignOutWithQueuePreview() {
    CairnTheme {
        SignOutDialog(SignOutUiState.WouldDiscard(3), onConfirm = {}, onDismiss = {})
    }
}
