package dk.kvitteringsarkiv.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dk.kvitteringsarkiv.app.data.ReceiptDraft
import dk.kvitteringsarkiv.app.data.ReceiptRecord
import dk.kvitteringsarkiv.app.storage.DropboxOAuth
import dk.kvitteringsarkiv.app.storage.DropboxStorageProvider
import dk.kvitteringsarkiv.app.storage.ReceiptPath
import dk.kvitteringsarkiv.app.storage.SafStorageProvider
import java.math.BigDecimal
import java.time.LocalDate

private val SandBackground = Color(0xFFF7F2EC)
private val WarmSurface = Color(0xFFFFFCF8)
private val WarmSurfaceAlt = Color(0xFFF0E7DE)
private val Taupe = Color(0xFF745B52)
private val TaupeDark = Color(0xFF4E3D37)
private val SandOutline = Color(0xFFD8C9BD)
private val Muted = Color(0xFF776E68)
private val Success = Color(0xFF4D7558)

@Composable
fun KvitteringsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Taupe,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEADDD3),
            onPrimaryContainer = TaupeDark,
            secondary = Color(0xFF9A7F70),
            background = SandBackground,
            onBackground = Color(0xFF241F1C),
            surface = WarmSurface,
            onSurface = Color(0xFF241F1C),
            surfaceVariant = WarmSurfaceAlt,
            onSurfaceVariant = Muted,
            outline = SandOutline,
            error = Color(0xFFB34A3C),
        ),
        typography = Typography(),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    receipts: List<ReceiptRecord>,
    query: String,
    activeStorageName: String,
    onQueryChange: (String) -> Unit,
    onScan: () -> Unit,
    onReceiptClick: (ReceiptRecord) -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        containerColor = SandBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SandBackground),
                title = {
                    Column {
                        Text("Kvitteringsarkiv", fontWeight = FontWeight.Bold)
                        Text("Scan, gem og find dine kvitteringer", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Indstillinger") }
                },
            )
        },
        bottomBar = {
            Surface(color = SandBackground) {
                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).height(58.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Icon(Icons.Default.CameraAlt, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Scan kvittering", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Søg i kvitteringer…") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = WarmSurface,
                        unfocusedContainerColor = WarmSurface,
                        focusedBorderColor = Taupe,
                        unfocusedBorderColor = SandOutline,
                    ),
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (query.isBlank()) "Seneste kvitteringer" else "Søgeresultater",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Surface(shape = RoundedCornerShape(50), color = WarmSurfaceAlt) {
                        Text(
                            activeStorageName,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = TaupeDark,
                        )
                    }
                }
            }

            if (receipts.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = WarmSurface),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Default.Description, null, modifier = Modifier.size(42.dp), tint = Taupe)
                            Spacer(Modifier.height(12.dp))
                            Text("Ingen kvitteringer endnu", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Tryk på Scan kvittering for at gemme din første.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                            )
                        }
                    }
                }
            } else {
                items(receipts, key = { it.id }) { receipt ->
                    ReceiptRow(receipt, onClick = { onReceiptClick(receipt) })
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(receipt: ReceiptRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = WarmSurface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(46.dp), shape = RoundedCornerShape(14.dp), color = WarmSurfaceAlt) {
                Box(contentAlignment = Alignment.Center) {
                    Text(receipt.supplier.take(1).uppercase(), fontWeight = FontWeight.Bold, color = TaupeDark)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(receipt.supplier, fontWeight = FontWeight.SemiBold)
                Text(receipt.purchaseDate.dkDate(), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            receipt.total?.let { Text(it.dkAmount(), fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.width(8.dp))
            Text("›", style = MaterialTheme.typography.titleLarge, color = Taupe)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    initial: ReceiptDraft,
    storageName: String,
    onBack: () -> Unit,
    onSave: (ReceiptDraft) -> Unit,
    saving: Boolean,
) {
    var supplier by remember(initial) { mutableStateOf(initial.supplier) }
    var dateText by remember(initial) { mutableStateOf(if (initial.purchaseDateDetected) initial.purchaseDate.toString() else "") }
    var amountText by remember(initial) { mutableStateOf(initial.total?.toPlainString()?.replace('.', ',') ?: "") }

    Scaffold(
        containerColor = SandBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SandBackground),
                title = {
                    Column {
                        Text("Gennemse kvittering", fontWeight = FontWeight.Bold)
                        Text("Tjek oplysningerne før gemning", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(18.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = WarmSurface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = supplier,
                        onValueChange = { supplier = it },
                        label = { Text("Butik / leverandør") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("Dato (ÅÅÅÅ-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        isError = dateText.isBlank(),
                        supportingText = {
                            if (dateText.isBlank()) Text("Datoen kunne ikke aflæses sikkert – udfyld den før du gemmer.")
                        },
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Beløb") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            }

            val previewDraft = runCatching {
                initial.copy(
                    supplier = supplier.trim().ifBlank { "Ukendt leverandør" },
                    purchaseDate = LocalDate.parse(dateText),
                    total = amountText.trim().takeIf { it.isNotBlank() }
                        ?.replace(".", "")?.replace(',', '.')?.let(::BigDecimal),
                    purchaseDateDetected = true,
                )
            }.getOrNull()

            Card(
                colors = CardDefaults.cardColors(containerColor = WarmSurfaceAlt),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(17.dp)) {
                    Text("Gemmes i $storageName", style = MaterialTheme.typography.labelLarge, color = TaupeDark)
                    Spacer(Modifier.height(6.dp))
                    Text(previewDraft?.let(ReceiptPath::displayPath) ?: "Ret dato/beløb for at se placeringen")
                }
            }

            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(54.dp),
                    enabled = !saving,
                    shape = RoundedCornerShape(18.dp),
                ) { Text("Annuller") }

                Button(
                    onClick = { previewDraft?.let(onSave) },
                    modifier = Modifier.weight(1f).height(54.dp),
                    enabled = previewDraft != null && !saving,
                    shape = RoundedCornerShape(18.dp),
                ) { Text(if (saving) "Gemmer…" else "Gem kvittering") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(
    receipt: ReceiptRecord,
    onOpenPdf: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = SandBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SandBackground),
                title = { Text("Kvittering", fontWeight = FontWeight.Bold) },
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(18.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = WarmSurface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(receipt.supplier, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    HorizontalDivider(color = SandOutline)
                    DetailLine("Dato", receipt.purchaseDate.dkDate())
                    DetailLine("Beløb", receipt.total?.dkAmount() ?: "Ikke fundet")
                    DetailLine("Placering", receipt.storagePath)
                }
            }

            Button(
                onClick = onOpenPdf,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(20.dp),
            ) { Text("Åbn PDF", fontWeight = FontWeight.SemiBold) }

            Card(
                colors = CardDefaults.cardColors(containerColor = WarmSurfaceAlt),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Genkendt tekst", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    LazyColumn {
                        item {
                            Text(
                                receipt.ocrText.ifBlank { "Ingen OCR-tekst gemt." },
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("Tilbage") }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    activeProvider: String,
    localFolder: String?,
    dropboxConnected: Boolean,
    dropboxAccount: String?,
    dropboxAppKey: String,
    dropboxRootFolder: String,
    onDropboxAppKeyChange: (String) -> Unit,
    onDropboxRootChange: (String) -> Unit,
    onConnectDropbox: () -> Unit,
    onDisconnectDropbox: () -> Unit,
    onSelectDropbox: () -> Unit,
    onChooseLocalFolder: () -> Unit,
    onSelectLocal: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = SandBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SandBackground),
                title = {
                    Column {
                        Text("Lagring", fontWeight = FontWeight.Bold)
                        Text("Vælg hvor dine kvitteringer gemmes", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                StorageProviderCard(
                    title = "Dropbox",
                    subtitle = if (dropboxConnected) (dropboxAccount ?: "Forbundet") else "Cloud-lagring",
                    icon = { Icon(Icons.Default.Cloud, null, tint = Taupe) },
                    active = activeProvider == DropboxStorageProvider.ID,
                    connected = dropboxConnected,
                    onClick = onSelectDropbox,
                ) {
                    if (!dropboxConnected) {
                        Text(
                            "Forbind Dropbox én gang, så gemmes kvitteringer automatisk i din valgte mappe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = dropboxAppKey,
                            onValueChange = onDropboxAppKeyChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Dropbox App Key") },
                            shape = RoundedCornerShape(14.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Redirect URI: ${DropboxOAuth.REDIRECT_URI}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onConnectDropbox,
                            enabled = dropboxAppKey.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("Forbind Dropbox") }
                    } else {
                        OutlinedTextField(
                            value = dropboxRootFolder,
                            onValueChange = onDropboxRootChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Rodmappe") },
                            supportingText = { Text("Fx Kvitteringer") },
                            shape = RoundedCornerShape(14.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (activeProvider != DropboxStorageProvider.ID) {
                                Button(
                                    onClick = onSelectDropbox,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                ) { Text("Brug Dropbox") }
                            }
                            OutlinedButton(
                                onClick = onDisconnectDropbox,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                            ) { Text("Afbryd") }
                        }
                    }
                }
            }

            item {
                StorageProviderCard(
                    title = "Lokal mappe",
                    subtitle = if (localFolder == null) "Ingen mappe valgt" else "Mappe valgt på telefonen",
                    icon = { Icon(Icons.Default.Folder, null, tint = Taupe) },
                    active = activeProvider == SafStorageProvider.ID,
                    connected = localFolder != null,
                    onClick = onSelectLocal,
                ) {
                    if (localFolder == null) {
                        Button(
                            onClick = onChooseLocalFolder,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("Vælg lokal mappe") }
                    } else {
                        Text(
                            localFolder,
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (activeProvider != SafStorageProvider.ID) {
                                Button(
                                    onClick = onSelectLocal,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                ) { Text("Brug lokal") }
                            }
                            OutlinedButton(
                                onClick = onChooseLocalFolder,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                            ) { Text("Skift mappe") }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = WarmSurfaceAlt),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Success)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Din filstruktur bevares", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Butik → år → måned → PDF, uanset hvilken lagring du vælger.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                            )
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("Tilbage") }
            }
        }
    }
}

@Composable
private fun StorageProviderCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    active: Boolean,
    connected: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WarmSurface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(modifier = Modifier.size(46.dp), shape = RoundedCornerShape(14.dp), color = WarmSurfaceAlt) {
                    Box(contentAlignment = Alignment.Center) { icon() }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                if (active) {
                    Surface(shape = CircleShape, color = Color(0xFFE2EEE5)) {
                        Text(
                            "Aktiv",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Success,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else if (connected) {
                    Text("Forbundet", style = MaterialTheme.typography.labelSmall, color = Success)
                }
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}
