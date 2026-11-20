package com.checkpointalerts.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.checkpointalerts.app.data.AlertScope
import com.checkpointalerts.app.data.Checkpoint
import com.checkpointalerts.app.data.CheckpointType
import com.checkpointalerts.app.data.TnCounties
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val listDateFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val detailDateFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)

private fun dateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(listDateFmt)
    }
}

private fun lastUpdatedText(millis: Long): String {
    if (millis <= 0L) return "Never updated yet"
    val fmt = SimpleDateFormat("h:mm a", Locale.US)
    return "Last updated ${fmt.format(Date(millis))}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: UiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelect: (Checkpoint) -> Unit,
    onToggleShowAll: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val today = uiState.checkpoints.filter { it.isToday }
    val upcoming = uiState.checkpoints.filter { !it.isToday }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checkpoint Alerts") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.Top
        ) {
            if (!uiState.notificationGranted) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Turn on notifications",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "We'll alert you the day before and the day a checkpoint happens in your area.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = onRequestNotificationPermission) {
                                Text("Turn on")
                            }
                        }
                    }
                }
            }

            item {
                ScopeSummaryCard(
                    scope = uiState.scope,
                    homeCounty = uiState.homeCounty,
                    onOpenSettings = onOpenSettings
                )
            }

            if (today.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (today.size == 1) {
                                    "1 checkpoint today in your area"
                                } else {
                                    "${today.size} checkpoints today in your area"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                items(today, key = { it.id }) { checkpoint ->
                    CheckpointCard(
                        checkpoint = checkpoint,
                        highlight = true,
                        onClick = { onSelect(checkpoint) }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Upcoming",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.scope == AlertScope.NEARBY) {
                        FilterChip(
                            selected = uiState.showAll,
                            onClick = onToggleShowAll,
                            label = { Text("All Tennessee") }
                        )
                    }
                }
            }

            if (today.isEmpty() && upcoming.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No checkpoints announced",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Nothing in your area right now. New announcements " +
                                "appear through the week — check back later.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(upcoming, key = { it.id }) { checkpoint ->
                    CheckpointCard(
                        checkpoint = checkpoint,
                        highlight = false,
                        onClick = { onSelect(checkpoint) }
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        lastUpdatedText(uiState.lastUpdatedMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.refreshError) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Couldn't refresh — showing last saved checkpoints. " +
                                "Tap refresh to try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item {
                MadeWithLoveFooter()
            }
        }
    }
}

@Composable
private fun ScopeSummaryCard(
    scope: AlertScope,
    homeCounty: String,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Alert scope", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(2.dp))
                if (scope == AlertScope.WHOLE_STATE) {
                    Text(
                        "Whole state — every checkpoint in Tennessee",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (homeCounty.isBlank()) {
                    Text(
                        "My county + nearby — pick your county in Settings",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    val covered = TnCounties.scopeCounties(homeCounty).size
                    Text(
                        "$homeCounty County + nearby ($covered counties)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            OutlinedButton(onClick = onOpenSettings) { Text("Change") }
        }
    }
}

@Composable
private fun CheckpointCard(
    checkpoint: Checkpoint,
    highlight: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = if (highlight) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(52.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    checkpoint.date.format(DateTimeFormatter.ofPattern("EEE", Locale.US))
                        .uppercase(Locale.US),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    checkpoint.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    checkpoint.location,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeBadge(checkpoint.type)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${checkpoint.county} County",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    dateLabel(checkpoint.date) + " · " + checkpoint.timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TypeBadge(type: CheckpointType) {
    val container = when (type) {
        CheckpointType.SOBRIETY -> MaterialTheme.colorScheme.errorContainer
        CheckpointType.LICENSE -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when (type) {
        CheckpointType.SOBRIETY -> MaterialTheme.colorScheme.onErrorContainer
        CheckpointType.LICENSE -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(color = container, shape = RoundedCornerShape(8.dp)) {
        Text(
            type.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(checkpoint: Checkpoint, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checkpoint details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                checkpoint.date.format(detailDateFmt),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(checkpoint.type)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${checkpoint.county} County, Tennessee",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Location", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(checkpoint.location, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccessTime, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Time: ${checkpoint.timeText}", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${checkpoint.county} County, Tennessee",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(Modifier.height(24.dp))
            if (checkpoint.sourceUrl.isNotBlank()) {
                OutlinedButton(onClick = { uriHandler.openUri(checkpoint.sourceUrl) }) {
                    Text("View announcement")
                }
                Spacer(Modifier.height(16.dp))
            }
            Text(
                "Announcements can change. Only publicly announced checkpoints " +
                    "are shown here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            MadeWithLoveFooter()
        }
    }
}
