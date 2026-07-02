package com.brioni.snake.ui.credits

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.brioni.snake.ui.components.ScreenHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.brioni.snake.R

/**
 * Credits / About screen: app identity, license and per-asset attribution
 * (music, sound effects, fonts, art) reachable from the main menu. Static
 * content; the only dynamic value is the app version, read from the package.
 */
@Composable
fun CreditsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.credits_title),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // App identity block.
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
            )
            if (version.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.credits_version, version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Text(
                text = stringResource(R.string.credits_author),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.credits_copyright),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Text(
                text = stringResource(R.string.credits_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )

            CreditSection(R.string.credits_section_license, R.string.credits_license_body)
            CreditLinkSection(R.string.credits_section_source, R.string.credits_source_body)
            CreditSection(R.string.credits_section_music, R.string.credits_music_body)
            CreditSection(R.string.credits_section_sfx, R.string.credits_sfx_body)
            CreditSection(R.string.credits_section_fonts, R.string.credits_fonts_body)
            CreditSection(R.string.credits_section_art, R.string.credits_art_body)
            CreditSection(R.string.credits_section_built, R.string.credits_built_body)
        }
    }
}

@Composable
private fun CreditSection(titleRes: Int, bodyRes: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** A credit section whose body is a tappable URL opening in the browser. */
@Composable
private fun CreditLinkSection(titleRes: Int, urlRes: Int) {
    val context = LocalContext.current
    val url = stringResource(urlRes)
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                textAlign = TextAlign.Start,
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
            )
        }
    }
}
