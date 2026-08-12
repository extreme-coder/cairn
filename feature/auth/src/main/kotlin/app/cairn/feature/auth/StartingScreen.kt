package app.cairn.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.cairn.core.designsystem.CairnMark
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.designsystem.Spacing

/**
 * What is on screen while the stored session is read.
 *
 * Deliberately the top of the Sign in screen and nothing else. Reading the
 * session takes a moment and usually ends with the app signed in, so a
 * spinner would announce work nobody asked about, and a signed-out screen
 * would be a lie that then has to be taken back. This is the same mark in the
 * same place, so if the Sign in screen does follow, only the form appears.
 *
 * No text: there is nothing true to say in the word budget that "Cairn" does
 * not already say.
 */
@Composable
public fun StartingScreen(modifier: Modifier = Modifier) {
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
                .systemBarsPadding()
                .padding(horizontal = Spacing.Gutter),
        ) {
            Spacer(Modifier.height(BrandTop))
            CairnMark()
            Spacer(Modifier.height(Spacing.Gutter))
            Text("Cairn", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Preview(name = "Starting", widthDp = 390, heightDp = 844)
@Composable
private fun StartingPreview() {
    CairnTheme { StartingScreen() }
}
