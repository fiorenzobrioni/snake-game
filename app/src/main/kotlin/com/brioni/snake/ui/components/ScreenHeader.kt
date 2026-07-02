package com.brioni.snake.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brioni.snake.R

/**
 * The shared top bar of the secondary screens (Settings, Records, ...): a glassy
 * back button pinned at the start — the standard Android top-left back
 * affordance, replacing the old bottom "Menu" buttons — with the screen title
 * optically centred over the full width. Kept outside the screen's scrolling
 * content so the exit is always one thumb-reach away.
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        MenuIconButton(
            onClick = onBack,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                // Keep long titles clear of the back button (and its mirror space).
                .padding(horizontal = 56.dp)
                .fillMaxWidth(),
        )
    }
}
