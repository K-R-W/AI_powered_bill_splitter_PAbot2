package com.example.pa_bot2.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pa_bot2.model.Bill
import com.example.pa_bot2.model.BillRepository
import com.example.pa_bot2.model.SettingsRepository
import com.example.pa_bot2.ui.theme.PA_bot2Theme
import com.example.pa_bot2.ui.theme.ParticipantColors
import com.example.pa_bot2.util.formatMoney
import com.example.pa_bot2.util.shareBillImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScanBill: () -> Unit,
    onAddManual: () -> Unit,
    onOpenSettings: () -> Unit,
    onBillClick: (String) -> Unit
) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val isAiEnabled by settingsRepository.isAiEnabledFlow.collectAsState(initial = false)
    
    val bills by BillRepository.bills.collectAsState()
    var showAddOptions by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    
    var selectedBillIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMultiDeleteConfirm by remember { mutableStateOf(false) }
    val inSelectionMode = selectedBillIds.isNotEmpty()
    val scope = rememberCoroutineScope()

    // Exit selection mode on back press instead of leaving the screen.
    BackHandler(enabled = inSelectionMode) { selectedBillIds = emptySet() }

    Scaffold(
        topBar = {
            if (inSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedBillIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedBillIds = emptySet() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedBillIds = if (selectedBillIds.size == bills.size) {
                                emptySet()
                            } else {
                                bills.map { it.id }.toSet()
                            }
                        }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(onClick = { showMultiDeleteConfirm = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete selected")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("PA_bot2", fontWeight = FontWeight.Bold)
                            if (bills.isNotEmpty()) {
                                Text(
                                    "${bills.size} ${if (bills.size == 1) "bill" else "bills"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!inSelectionMode) {
                FloatingActionButton(onClick = { showAddOptions = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add Bill")
                }
            }
        }
    ) { padding ->
        if (bills.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "No bills yet",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Scan a receipt or add one manually to start splitting.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                FilledTonalButton(onClick = { showAddOptions = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add your first bill")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(bills) { bill ->
                    BillItemRow(
                        bill = bill,
                        selectionMode = inSelectionMode,
                        selected = selectedBillIds.contains(bill.id),
                        onClick = {
                            if (inSelectionMode) {
                                selectedBillIds = if (selectedBillIds.contains(bill.id)) {
                                    selectedBillIds - bill.id
                                } else {
                                    selectedBillIds + bill.id
                                }
                            } else {
                                onBillClick(bill.id)
                            }
                        },
                        onShareClick = { shareBillImage(context, bill) },
                        onLongClick = { selectedBillIds = selectedBillIds + bill.id }
                    )
                }
            }
        }

        if (showAddOptions) {
            ModalBottomSheet(
                onDismissRequest = { showAddOptions = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "Scan Receipt",
                                color = if (isAiEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                            )
                        },
                        supportingContent = {
                            Text(
                                text = if (isAiEnabled) "Capture or pick a photo — AI extracts items & prices"
                                       else "Connect your Google account in Settings to enable",
                                color = if (isAiEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = if (isAiEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isAiEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.clickable(enabled = isAiEnabled) {
                            showAddOptions = false
                            onScanBill()
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Manual Entry") },
                        supportingContent = { Text("Add items and participants yourself") },
                        leadingContent = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showAddOptions = false
                            onAddManual()
                        }
                    )
                }
            }
        }
        
        if (showMultiDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showMultiDeleteConfirm = false },
                title = { Text("Delete Bills") },
                text = {
                    val count = selectedBillIds.size
                    Text("Are you sure you want to delete $count ${if (count == 1) "bill" else "bills"}? This action cannot be undone.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                selectedBillIds.forEach { BillRepository.deleteBill(it) }
                                selectedBillIds = emptySet()
                                showMultiDeleteConfirm = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMultiDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BillItemRow(
    bill: Bill,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onShareClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
    val total = bill.items.sumOf { it.price }
    // Deterministic accent colour per bill, so cards are distinguishable at a glance.
    val accent = ParticipantColors[bill.id.hashCode().absoluteValue % ParticipantColors.size]

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = if (selected) {
            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.elevatedCardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading: selection checkbox or colored avatar
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = bill.title.trim().take(1).uppercase().ifEmpty { "#" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bill.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dateFormat.format(Date(bill.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${bill.items.size} ${if (bill.items.size == 1) "item" else "items"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (bill.participants.isNotEmpty()) {
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            Icons.Rounded.Groups,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${bill.participants.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                if (!selectionMode) {
                    IconButton(
                        onClick = onShareClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = formatMoney(total, bill.currencyCode),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    PA_bot2Theme {
        HomeScreen(onScanBill = {}, onAddManual = {}, onOpenSettings = {}, onBillClick = {})
    }
}
