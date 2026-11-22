package com.cybnate.agent.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cybnate.agent.agent.AgentRuntime

/** AI Coding Workspace: streaming chat, live agent activity, tool logs, stop/resume. */
@Composable
fun WorkspaceScreen(runtime: AgentRuntime, onRun: (String) -> Unit) {
    val state by runtime.state.collectAsState()
    var prompt by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Workspace")
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(state.log) { line -> Text(line) }
            if (state.streamingText.isNotBlank()) item { Text(state.streamingText) }
            state.error?.let { e -> item { Text("Error: $e") } }
        }
        OutlinedTextField(
            value = prompt, onValueChange = { prompt = it },
            label = { Text("Describe a task…") }, modifier = Modifier.fillMaxWidth(),
        )
        Row {
            Button(onClick = { onRun(prompt); prompt = "" }, enabled = !state.running) { Text("Run agent") }
            OutlinedButton(onClick = { runtime.stop() }, enabled = state.running) { Text("Stop") }
        }
    }
}

/** Provider Settings: add/switch providers, masked keys, test connection. Keys never shown in full. */
@Composable
fun ProviderSettingsScreen(
    providers: List<String>,
    maskedKey: (String) -> String,
    onSaveKey: (String, String) -> Unit,
    onTest: (String) -> Unit,
) {
    var selected by remember { mutableStateOf(providers.firstOrNull() ?: "") }
    var keyInput by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("AI Providers")
        providers.forEach { p ->
            OutlinedButton(onClick = { selected = p }) { Text("$p — key: ${maskedKey(p)}") }
        }
        OutlinedTextField(
            value = keyInput, onValueChange = { keyInput = it },
            label = { Text("API key for $selected") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            Button(onClick = { onSaveKey(selected, keyInput); keyInput = "" }) { Text("Save key") }
            OutlinedButton(onClick = { onTest(selected) }) { Text("Test connection") }
        }
        Text("Keys are encrypted with Android Keystore and sent only to the provider you configured.")
    }
}

/** Project Explorer placeholder wired to ToolRegistry.list_files output. */
@Composable
fun ExplorerScreen(files: List<String>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Project Explorer")
        LazyColumn {
            items(files) { f -> OutlinedButton(onClick = { onOpen(f) }) { Text(f) } }
        }
    }
}
