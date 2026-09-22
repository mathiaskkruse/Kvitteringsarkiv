package dk.kvitteringsarkiv.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dk.kvitteringsarkiv.app.data.ReceiptDraft
import dk.kvitteringsarkiv.app.data.ReceiptRecord
import dk.kvitteringsarkiv.app.storage.ReceiptPath
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun KvitteringsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography(),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    receipts: List<ReceiptRecord>,
    query: String,
    onQueryChange: (String) -> Unit,
    onScan: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kvitteringsarkiv", fontWeight = FontWeight.SemiBold) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Indstillinger") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onScan, icon = { Icon(Icons.Default.CameraAlt, null) }, text = { Text("Scan kvittering") })
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                label = { Text("Søg butik, dato eller tekst på kvitteringen") },
            )
            Spacer(Modifier.height(18.dp))
            Text(if (query.isBlank()) "Seneste kvitteringer" else "Søgeresultater", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (receipts.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                    Text("Ingen kvitteringer endnu", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                    items(receipts, key = { it.id }) { receipt -> ReceiptRow(receipt) }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(receipt: ReceiptRecord) {
    ListItem(
        headlineContent = { Text(receipt.supplier, fontWeight = FontWeight.Medium) },
        supportingContent = { Text("${receipt.purchaseDate.dkDate()} · ${receipt.storagePath}", maxLines = 2) },
        trailingContent = { receipt.total?.let { Text(it.dkAmount()) } },
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    initial: ReceiptDraft,
    onBack: () -> Unit,
    onSave: (ReceiptDraft) -> Unit,
    saving: Boolean,
) {
    var supplier by remember(initial) { mutableStateOf(initial.supplier) }
    var dateText by remember(initial) { mutableStateOf(initial.purchaseDate.toString()) }
    var amountText by remember(initial) { mutableStateOf(initial.total?.toPlainString()?.replace('.', ',') ?: "") }

    Scaffold(topBar = { TopAppBar(title = { Text("Kontrollér kvittering") }) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Scanningen er klar. Ret felterne hvis OCR'en har læst noget forkert.")
            OutlinedTextField(supplier, { supplier = it }, label = { Text("Butik / leverandør") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(dateText, { dateText = it }, label = { Text("Dato (ÅÅÅÅ-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(amountText, { amountText = it }, label = { Text("Beløb") }, modifier = Modifier.fillMaxWidth())

            val previewDraft = runCatching {
                initial.copy(
                    supplier = supplier.trim().ifBlank { "Ukendt leverandør" },
                    purchaseDate = LocalDate.parse(dateText),
                    total = amountText.trim().takeIf { it.isNotBlank() }?.replace(".", "")?.replace(',', '.')?.let(::BigDecimal),
                )
            }.getOrNull()

            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Gemmes som", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(previewDraft?.let(ReceiptPath::displayPath) ?: "Ret dato/beløb for at se placeringen")
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), enabled = !saving) { Text("Annuller") }
                Button(
                    onClick = { previewDraft?.let(onSave) },
                    modifier = Modifier.weight(1f),
                    enabled = previewDraft != null && !saving,
                ) { Text(if (saving) "Gemmer…" else "Gem") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentFolder: String?,
    onChooseFolder: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Lagring") }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Vælg hvor appen skal gemme kvitteringer. Version 0.1 har mappe-lagring aktiv; cloud-providerne er gjort klar til OAuth-integration.")
            ProviderCard("Valgt mappe", currentFolder ?: "Ingen mappe valgt", Icons.Default.Folder, onChooseFolder, true)
            ProviderCard("Google Drive", "Connector klar · OAuth mangler", Icons.Default.Cloud, {}, false)
            ProviderCard("OneDrive", "Connector klar · OAuth mangler", Icons.Default.Cloud, {}, false)
            ProviderCard("Dropbox", "Connector klar · OAuth mangler", Icons.Default.Cloud, {}, false)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Tilbage") }
        }
    }
}

@Composable
private fun ProviderCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, enabled: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
