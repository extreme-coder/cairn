package app.cairn.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.cairn.core.designsystem.CairnMark
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.designsystem.Spacing

/**
 * The Sign in screen.
 *
 * Stateless: everything it draws is in [state] and everything it does is a
 * callback, so the same composable renders in a preview, in a screenshot test
 * and in the app.
 */
@Composable
public fun SignInScreen(
    state: SignInUiState,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onToggleReveal: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = BrandWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = Spacing.Gutter),
        ) {
            Spacer(Modifier.height(BrandTop))
            CairnMark()
            Spacer(Modifier.height(Spacing.Gutter))
            Text("Cairn", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(Spacing.Small))
            Text(
                text = "Collect research observations offline.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(Spacing.XXLarge))

            Field(label = "Server") {
                ServerAddress(state.server)
            }
            Text(
                text = "Set when this app was installed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.Small),
            )

            Spacer(Modifier.height(Spacing.XLarge))
            Field(label = "Email address") {
                CairnTextField(
                    value = state.email,
                    onValueChange = onEmail,
                    enabled = !state.submitting,
                    placeholder = "name@example.org",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    testTag = "email",
                )
            }

            Spacer(Modifier.height(Spacing.XLarge))
            Field(label = "Password") {
                CairnTextField(
                    value = state.password,
                    onValueChange = onPassword,
                    enabled = !state.submitting,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    onImeAction = onSignIn,
                    masked = !state.revealPassword,
                    trailing = {
                        // A word, not an eye. The app has no icon set, and a word
                        // is also the larger target for someone wearing gloves.
                        TextButton(onClick = onToggleReveal, modifier = Modifier.testTag("reveal")) {
                            Text(
                                text = if (state.revealPassword) "Hide" else "Show",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    },
                    testTag = "password",
                )
            }

            state.problem?.let { problem ->
                Spacer(Modifier.height(Spacing.Large))
                Problem(problem)
            }

            Spacer(Modifier.height(Spacing.XLarge))
            Button(
                onClick = onSignIn,
                enabled = state.canSubmit,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.CaptureControl)
                    .testTag("sign_in"),
            ) {
                Text(
                    text = if (state.submitting) "Signing in" else "Sign in",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(500)),
                )
            }

            Spacer(Modifier.height(Spacing.Large))
            Text(
                text = "Submissions save on this device and upload when you reconnect.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(Spacing.XXLarge))
        }
    }
}

/** The label is always visible above its control, never a placeholder. */
@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

/**
 * Shown, not edited — see [SignInUiState]. Rendered as a field rather than as a
 * line of prose because which server this is matters enough to sit where the
 * eye already is.
 */
@Composable
private fun ServerAddress(server: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.CaptureControl)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.Large),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = server.ifBlank { "Not configured" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("server"),
        )
    }
}

@Composable
private fun CairnTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    testTag: String,
    placeholder: String? = null,
    masked: Boolean = false,
    onImeAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.labelMedium,
        placeholder = placeholder?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingIcon = trailing,
        visualTransformation = if (masked) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = { onImeAction?.invoke() },
        ),
        shape = MaterialTheme.shapes.small,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.CaptureControl)
            .testTag(testTag),
    )
}

/** Same shape as a field error on the capture screen: a dot, then the sentence. */
@Composable
private fun Problem(problem: SignInProblem) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp)) {
            Box(
                Modifier
                    .size(Spacing.Small)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
            )
        }
        Spacer(Modifier.size(Spacing.Small))
        Text(
            text = problem.message(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("problem"),
        )
    }
}

/**
 * Both surfaces that show the mark use these, so the mark does not jump when
 * the Sign in screen replaces the starting one.
 */
internal val BrandTop = 40.dp
internal val BrandWidth = 480.dp

@Preview(name = "Sign in", widthDp = 390, heightDp = 844)
@Composable
private fun SignInPreview() {
    CairnTheme {
        SignInScreen(
            state = SignInUiState(server = "cairn.psych.ubc.ca"),
            onEmail = {},
            onPassword = {},
            onToggleReveal = {},
            onSignIn = {},
        )
    }
}

@Preview(name = "Sign in refused", widthDp = 390, heightDp = 844)
@Composable
private fun SignInRefusedPreview() {
    CairnTheme {
        SignInScreen(
            state = SignInUiState(
                server = "cairn.psych.ubc.ca",
                email = "adaku@cairn.test",
                password = "wrong",
                problem = SignInProblem.REFUSED,
            ),
            onEmail = {},
            onPassword = {},
            onToggleReveal = {},
            onSignIn = {},
        )
    }
}
