package com.checkpointalerts.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.checkpointalerts.app.data.AlertScope
import com.checkpointalerts.app.data.TnCounties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: UiState,
    onBack: () -> Unit,
    onScopeChange: (AlertScope) -> Unit,
    onCountyChange: (String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRefresh: () -> Unit
) {
    var showCountyPicker by remember { mutableStateOf(false) }

    if (showCountyPicker) {
        CountyPickerDialog(
            selected = uiState.homeCounty,
            onSelect = {
                onCountyChange(it)
                showCountyPicker = false
            },
            onDismiss = { showCountyPicker = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Text(
                    "State",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Tennessee", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "More states coming soon — each state gets its own source.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text(
                    "Alert scope",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        ScopeRadioRow(
                            selected = uiState.scope == AlertScope.WHOLE_STATE,
                            onClick = { onScopeChange(AlertScope.WHOLE_STATE) },
                            title = "Whole state",
                            subtitle = "Alerts for any checkpoint in Tennessee"
                        )
                        ScopeRadioRow(
                            selected = uiState.scope == AlertScope.NEARBY,
                            onClick = { onScopeChange(AlertScope.NEARBY) },
                            title = "My county + nearby counties",
                            subtitle = "Alerts for checkpoints in your county and the counties touching it"
                        )
                        if (uiState.scope == AlertScope.NEARBY) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                OutlinedButton(onClick = { showCountyPicker = true }) {
                                    Text(
                                        if (uiState.homeCounty.isBlank()) {
                                            "Choose your county"
                                        } else {
                                            "${uiState.homeCounty} County"
                                        }
                                    )
                                }
                                if (uiState.homeCounty.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    val covered = TnCounties.scopeCounties(uiState.homeCounty)
                                        .sorted()
                                    Text(
                                        "Covered counties: " + covered.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Pick your county to turn on nearby alerts. " +
                                            "Until then, the whole state is shown.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        if (uiState.notificationGranted) {
                            Text(
                                "Notifications are on. You'll be alerted on the day " +
                                    "a checkpoint happens in your area.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                "Notifications are off. Without them, the app can " +
                                    "only show checkpoints when you open it.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onRequestNotificationPermission) {
                                Text("Turn on notifications")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "About this data",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Checkpoints are listed only as publicly announced by " +
                                "the Tennessee Highway Patrol and republished by " +
                                "public listing sites. Announcements can change, " +
                                "and not every checkpoint may appear here.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This app does not track your location.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        val lastUpdated = if (uiState.lastUpdatedMillis > 0L) {
                            val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.US)
                            "Last checked ${fmt.format(Date(uiState.lastUpdatedMillis))}"
                        } else {
                            "Never checked yet"
                        }
                        Text(lastUpdated, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onRefresh) {
                            Text("Check now")
                        }
                        if (uiState.refreshError) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "The last check failed — showing last saved checkpoints.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Checkpoint Alerts v1.0 — Tennessee only for now",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ScopeRadioRow(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountyPickerDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) {
            TnCounties.ALL
        } else {
            TnCounties.ALL.filter { it.contains(query, ignoreCase = true) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose your county") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search counties") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it }) { county ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(county) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "$county County",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (county == selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
