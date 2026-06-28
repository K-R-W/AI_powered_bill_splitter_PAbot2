package com.example.pa_bot2.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import com.example.pa_bot2.model.Bill
import com.example.pa_bot2.model.BillItem
import com.example.pa_bot2.model.BillRepository
import com.example.pa_bot2.model.Participant
import com.example.pa_bot2.model.SettingsRepository
import com.example.pa_bot2.model.SplitwiseGroup
import com.example.pa_bot2.api.SplitwiseApi
import com.example.pa_bot2.ui.theme.PA_bot2Theme
import com.example.pa_bot2.ui.theme.ParticipantColors
import com.example.pa_bot2.util.formatMoney
import com.example.pa_bot2.util.currencySymbol
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    billId: String?,
    initialBill: Bill? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val splitwiseToken by settingsRepository.splitwiseAccessTokenFlow.collectAsState(initial = null)

    val repositoryBills by BillRepository.bills.collectAsState()

    val vm: DetailViewModel = viewModel(key = billId ?: initialBill?.id) {
        DetailViewModel(billId, initialBill)
    }
    val draftBill = vm.draftBill
    val hasChanges = vm.hasChanges
    val existingBill = repositoryBills.find { it.id == draftBill.id }

    val splitwiseApi = remember(splitwiseToken) {
        if (splitwiseToken != null) {
            Retrofit.Builder()
                .baseUrl(SplitwiseApi.BASE_URL)
                .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(SplitwiseApi::class.java)
        } else null
    }

    var splitwiseGroups by remember { mutableStateOf<List<SplitwiseGroup>>(emptyList()) }
    var isFetchingGroups by remember { mutableStateOf(false) }

    LaunchedEffect(splitwiseApi) {
        if (splitwiseApi != null) {
            isFetchingGroups = true
            try {
                val response = splitwiseApi.getGroups("Bearer $splitwiseToken")
                splitwiseGroups = response.groups
            } catch (e: Exception) {
            } finally {
                isFetchingGroups = false
            }
        }
    }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showAddParticipantDialog by remember { mutableStateOf(false) }
    var newParticipantName by remember { mutableStateOf("") }
    var participantError by remember { mutableStateOf<String?>(null) }

    var itemToEdit by remember { mutableStateOf<BillItem?>(null) }
    var itemForShares by remember { mutableStateOf<BillItem?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showEditTitleDialog by remember { mutableStateOf(false) }
    var showGroupSelector by remember { mutableStateOf(false) }
    var showSplitwiseExportDialog by remember { mutableStateOf(false) }
    var isExportingToSplitwise by remember { mutableStateOf(false) }
    var splitwiseExportError by remember { mutableStateOf<String?>(null) }
    var newTitle by remember { mutableStateOf("") }
    var showReceiptImage by remember { mutableStateOf(false) }

    // Local text buffers for payer amount inputs so typing doesn't jump.
    var payerAmountTexts by remember {
        mutableStateOf(draftBill.payerAmounts.mapValues { if (it.value == 0.0) "" else it.value.toString() })
    }

    val scope = rememberCoroutineScope()
    val currency = draftBill.currencyCode

    val participantTotals = vm.participantTotals
    val settlements = vm.settlements
    val grandTotal = vm.grandTotal
    val totalPaid = vm.totalPaid
    val remaining = grandTotal - totalPaid
    val standardSubtotals = vm.standardSubtotals
    val unassignedTotal = vm.unassignedTotal

    val canExportToSplitwise = draftBill.splitwiseGroupId != null && unassignedTotal <= 0 && draftBill.items.isNotEmpty()

    val handleBack = {
        if (hasChanges) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = hasChanges, onBack = handleBack)

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { 
                            newTitle = draftBill.title
                            showEditTitleDialog = true 
                        }
                    ) {
                        Text(text = draftBill.title)
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Edit, 
                            contentDescription = null, 
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (draftBill.imageUri != null) {
                        AppTooltip("View original bill photo") {
                            IconButton(onClick = { showReceiptImage = true }) {
                                Icon(Icons.Rounded.Receipt, contentDescription = "View Receipt")
                            }
                        }
                    }
                    if (splitwiseToken != null) {
                        AppTooltip(if (draftBill.splitwiseGroupId == null) "Link to Splitwise group" else "Change Splitwise group") {
                            IconButton(onClick = { showGroupSelector = true }) {
                                Icon(
                                    if (draftBill.splitwiseGroupId == null) Icons.Rounded.CloudSync else Icons.Rounded.SyncAlt,
                                    contentDescription = "Splitwise group"
                                )
                            }
                        }
                    }
                    if (existingBill != null) {
                        AppTooltip("Delete this bill permanently") {
                            IconButton(onClick = { showDeleteConfirmation = true }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete Bill")
                            }
                        }
                    }
                    if (hasChanges) {
                        AppTooltip("Save changes to database") {
                            Button(
                                // Saving stays on this screen so the user can immediately
                                // link/export to Splitwise without re-entering.
                                onClick = { scope.launch { vm.save() } },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (splitwiseToken != null && draftBill.splitwiseGroupId != null) {
                    AppTooltip("Export this bill to your Splitwise group") {
                        ExtendedFloatingActionButton(
                            onClick = { if (canExportToSplitwise && !hasChanges) showSplitwiseExportDialog = true },
                            icon = { 
                                Icon(
                                    Icons.Rounded.Send, 
                                    contentDescription = null
                                ) 
                            },
                            text = { 
                                Text(if (hasChanges) "Save to Export" else "Send to Splitwise") 
                            },
                            containerColor = if (canExportToSplitwise && !hasChanges) 
                                MaterialTheme.colorScheme.tertiaryContainer 
                            else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (canExportToSplitwise && !hasChanges) 
                                MaterialTheme.colorScheme.onTertiaryContainer 
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                AppTooltip("Add a new item to the bill") {
                    FloatingActionButton(
                        onClick = { showAddItemDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add Item")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Splitwise Group Indicator
            if (draftBill.splitwiseGroupId != null) {
                item {
                    val groupName = splitwiseGroups.find { it.id.toString() == draftBill.splitwiseGroupId }?.name ?: "Splitwise Group"
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Link, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Linked to $groupName",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Participants Section
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Participants", 
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        // When linked to a Splitwise group, participants come from the
                        // group's members, so manual adding is disabled.
                        if (draftBill.splitwiseGroupId == null) {
                            AppTooltip("Add a new participant") {
                                TextButton(onClick = { showAddParticipantDialog = true }) {
                                    Icon(Icons.Rounded.PersonAdd, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add")
                                }
                            }
                        }
                    }
                    
                    if (draftBill.participants.isEmpty()) {
                        Text(
                            "No participants added yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            draftBill.participants.forEach { participant ->
                                val participantColor = ParticipantColors.getOrElse(participant.colorIndex) { ParticipantColors[0] }
                                InputChip(
                                    selected = false,
                                    onClick = {
                                        vm.removeParticipant(participant.id)
                                        payerAmountTexts = payerAmountTexts.filterKeys { it != participant.id }
                                    },
                                    label = { Text(participant.name) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(participantColor)
                                        )
                                    },
                                    colors = InputChipDefaults.inputChipColors(
                                        labelColor = participantColor,
                                    ),
                                    border = InputChipDefaults.inputChipBorder(
                                        borderColor = participantColor.copy(alpha = 0.5f),
                                        enabled = true,
                                        selected = false,
                                        borderWidth = 1.dp
                                    ),
                                    trailingIcon = {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(16.dp),
                                            tint = participantColor.copy(alpha = 0.7f)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Items Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Items & Assignment", 
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (draftBill.participants.isNotEmpty() && draftBill.items.isNotEmpty()) {
                        Row {
                            AppTooltip("Unassign everyone from all items") {
                                IconButton(
                                    onClick = { vm.unassignAll() }
                                ) {
                                    Icon(
                                        Icons.Rounded.PersonOff,
                                        null, 
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            AppTooltip("Assign all participants to every bill item") {
                                IconButton(
                                    onClick = { vm.assignEveryoneToAll() }
                                ) {
                                    Icon(
                                        Icons.Rounded.Groups,
                                        null, 
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (draftBill.items.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.ReceiptLong, 
                            null, 
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No items yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Tap + to add items or scan a bill.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(draftBill.items, key = { it.id }) { item ->
                BillItemCard(
                    item = item,
                    participants = draftBill.participants,
                    standardSubtotals = standardSubtotals,
                    currency = currency,
                    onToggleParticipant = { participantId -> vm.toggleAssignment(item.id, participantId) },
                    onEditItem = { itemToEdit = it },
                    onDeleteItem = { deletedItem -> vm.deleteItem(deletedItem) },
                    onEditShares = { itemForShares = it }
                )
            }

            // Paid By Section — shown for every bill so non-Splitwise splits can settle up too.
            if (draftBill.participants.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Paid By",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Total Bill: ${formatMoney(grandTotal, currency)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            when {
                                remaining > 0.01 -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "Remaining: ${formatMoney(remaining, currency)}",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                remaining < -0.01 -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "Extra: ${formatMoney(-remaining, currency)}",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                else -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.CheckCircle, 
                                            null, 
                                            tint = MaterialTheme.colorScheme.primary, 
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Balanced", 
                                            style = MaterialTheme.typography.labelSmall, 
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        
                        draftBill.participants.forEach { participant ->
                            val isPayer = draftBill.payerAmounts.containsKey(participant.id)
                            val amount = draftBill.payerAmounts[participant.id] ?: 0.0
                            val participantColor = ParticipantColors.getOrElse(participant.colorIndex) { ParticipantColors[0] }
                            
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = if (isPayer) participantColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                border = if (isPayer) BorderStroke(1.dp, participantColor.copy(alpha = 0.3f)) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = CircleShape,
                                        color = participantColor.copy(alpha = 0.2f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                participant.name.take(1).uppercase(),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = participantColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    Spacer(Modifier.width(12.dp))
                                    
                                    Text(
                                        participant.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                        color = if (isPayer) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (isPayer) {
                                        OutlinedTextField(
                                            value = payerAmountTexts[participant.id] ?: amount.toString(),
                                            onValueChange = { newValue ->
                                                if (newValue.isEmpty() || (newValue.toDoubleOrNull()?.let { it >= 0 } == true) || newValue == ".") {
                                                    // Limit to 8 digits before decimal
                                                    val parts = newValue.split(".")
                                                    if (parts[0].length <= 8) {
                                                        payerAmountTexts = payerAmountTexts + (participant.id to newValue)
                                                        vm.setPayer(participant.id, newValue.toDoubleOrNull() ?: 0.0)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.width(145.dp),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            prefix = { Text(currencySymbol(currency), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 4.dp)) },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedBorderColor = participantColor.copy(alpha = 0.3f),
                                                focusedBorderColor = participantColor,
                                                unfocusedContainerColor = participantColor.copy(alpha = 0.05f),
                                                focusedContainerColor = participantColor.copy(alpha = 0.1f)
                                            ),
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = {
                                                        vm.removePayer(participant.id)
                                                        payerAmountTexts = payerAmountTexts - participant.id
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    } else {
                                        TextButton(
                                            onClick = {
                                                val toPay = maxOf(0.0, remaining)
                                                vm.setPayer(participant.id, toPay)
                                                val textValue = if (toPay % 1.0 == 0.0) toPay.toInt().toString() else toPay.toString()
                                                payerAmountTexts = payerAmountTexts + (participant.id to textValue)
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Paid")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Summary Section
            item {
                Spacer(Modifier.height(32.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Analytics, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Split Summary", 
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(Modifier.height(16.dp))

                        if (unassignedTotal > 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Unassigned Base", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                                        Text("Assign all items to get an accurate split", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f))
                                    }
                                    Text(
                                        formatMoney(unassignedTotal, currency),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        participantTotals.toList().forEach { (participant, amount) ->
                            val participantColor = ParticipantColors.getOrElse(participant.colorIndex) { ParticipantColors[0] }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(32.dp),
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = participantColor.copy(alpha = 0.2f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                participant.name.take(1).uppercase(),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = participantColor
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(participant.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                }
                                Text(
                                    formatMoney(amount, currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = participantColor
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(modifier = Modifier.alpha(0.1f))
                        Spacer(Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Grand Total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                formatMoney(grandTotal, currency),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Settle Up — who pays whom, derived from owed vs paid.
                        if (settlements.isNotEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.SwapHoriz, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Settle Up",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            settlements.forEach { s ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text(s.from.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Icon(
                                            Icons.AutoMirrored.Rounded.ArrowForward,
                                            null,
                                            modifier = Modifier.size(16.dp).padding(horizontal = 4.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(s.to.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    }
                                    Text(
                                        formatMoney(s.amount, currency),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(100.dp))
            }
        }
    }

    if (showGroupSelector) {
        AlertDialog(
            onDismissRequest = { showGroupSelector = false },
            title = { Text("Select Splitwise Group") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isFetchingGroups) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            if (draftBill.splitwiseGroupId != null) {
                                item {
                                    ListItem(
                                        headlineContent = { Text("Unlink Group", color = MaterialTheme.colorScheme.error) },
                                        leadingContent = { Icon(Icons.Rounded.LinkOff, null, tint = MaterialTheme.colorScheme.error) },
                                        modifier = Modifier.clickable {
                                            vm.unlinkGroup()
                                            payerAmountTexts = emptyMap()
                                            showGroupSelector = false
                                        }
                                    )
                                    HorizontalDivider()
                                }
                            }

                            items(splitwiseGroups) { group ->
                                ListItem(
                                    headlineContent = { Text(group.name) },
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            // Fetch members for the selected group
                                            val groupDetails = splitwiseApi?.getGroup("Bearer $splitwiseToken", group.id)
                                            val members = groupDetails?.group?.members ?: emptyList()
                                            
                                            val assignedIndices = draftBill.participants.map { it.colorIndex }.toMutableSet()
                                            val newParticipants = members.mapIndexed { index, member ->
                                                val nextIndex = (0 until ParticipantColors.size).firstOrNull { it !in assignedIndices } 
                                                    ?: (index % ParticipantColors.size)
                                                assignedIndices.add(nextIndex)
                                                Participant(
                                                    name = "${member.first_name} ${member.last_name ?: ""}".trim(),
                                                    colorIndex = nextIndex,
                                                    splitwiseId = member.id
                                                )
                                            }
                                            
                                            val newPayers = newParticipants.associate { it.id to 0.0 }

                                            vm.linkGroup(group.id.toString(), newParticipants, newPayers)
                                            payerAmountTexts = newPayers.mapValues { "" }
                                            showGroupSelector = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGroupSelector = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSplitwiseExportDialog) {
        AlertDialog(
            onDismissRequest = { if (!isExportingToSplitwise) showSplitwiseExportDialog = false },
            title = { Text("Export to Splitwise") },
            text = {
                Column {
                    Text("This will create a new expense in the linked Splitwise group.")
                    if (isExportingToSplitwise) {
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    if (splitwiseExportError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(splitwiseExportError!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isExportingToSplitwise = true
                            splitwiseExportError = null
                            try {
                                val totalCost = draftBill.items.sumOf { it.price }
                                val currentUserResponse = splitwiseApi?.getCurrentUser("Bearer $splitwiseToken")
                                val currentUserId = currentUserResponse?.user?.id
                                
                                if (currentUserId == null) {
                                    splitwiseExportError = "Could not identify your Splitwise user ID."
                                    return@launch
                                }

                                val finalParams = mutableMapOf<String, String>()
                                finalParams["cost"] = String.format(Locale.US, "%.2f", totalCost)
                                finalParams["description"] = draftBill.title
                                finalParams["group_id"] = draftBill.splitwiseGroupId!!
                                finalParams["currency_code"] = draftBill.currencyCode
                                
                                // Map to collect all unique Splitwise user IDs involved (payers or owers)
                                val allSplitwiseUserIds = mutableSetOf<Long>()
                                participantTotals.keys.forEach { it.splitwiseId?.let { id -> allSplitwiseUserIds.add(id) } }
                                draftBill.payerAmounts.keys.forEach { pid -> 
                                    draftBill.participants.find { it.id == pid }?.splitwiseId?.let { id -> allSplitwiseUserIds.add(id) }
                                }
                                
                                // If no payers selected, assume current user paid everything
                                val effectivePayerAmounts = if (draftBill.payerAmounts.isEmpty()) {
                                    allSplitwiseUserIds.add(currentUserId)
                                    mapOf(currentUserId to totalCost)
                                } else {
                                    val mapped = mutableMapOf<Long, Double>()
                                    draftBill.payerAmounts.forEach { (pid, amount) ->
                                        draftBill.participants.find { it.id == pid }?.splitwiseId?.let { id ->
                                            mapped[id] = mapped.getOrDefault(id, 0.0) + amount
                                        }
                                    }
                                    mapped
                                }

                                // Map participant totals (owed shares) to Splitwise IDs
                                val effectiveOwedShares = mutableMapOf<Long, Double>()
                                participantTotals.forEach { (p, amount) ->
                                    p.splitwiseId?.let { id ->
                                        effectiveOwedShares[id] = effectiveOwedShares.getOrDefault(id, 0.0) + amount
                                    }
                                }

                                var userIndex = 0
                                allSplitwiseUserIds.forEach { uid ->
                                    val paid = effectivePayerAmounts[uid] ?: 0.0
                                    val owed = effectiveOwedShares[uid] ?: 0.0
                                    
                                    if (paid > 0 || owed > 0) {
                                        finalParams["users__${userIndex}__user_id"] = uid.toString()
                                        finalParams["users__${userIndex}__paid_share"] = String.format(Locale.US, "%.2f", paid)
                                        finalParams["users__${userIndex}__owed_share"] = String.format(Locale.US, "%.2f", owed)
                                        userIndex++
                                    }
                                }
                                
                                if (userIndex == 0) {
                                    splitwiseExportError = "No valid Splitwise participants found to export."
                                    return@launch
                                }
                                
                                val response = splitwiseApi?.createExpense("Bearer $splitwiseToken", finalParams)
                                if (response?.errors?.isNotEmpty() == true) {
                                    splitwiseExportError = response.errors.values.flatten().joinToString()
                                } else {
                                    showSplitwiseExportDialog = false
                                }
                            } catch (e: Exception) {
                                splitwiseExportError = e.localizedMessage
                            } finally {
                                isExportingToSplitwise = false
                            }
                        }
                    },
                    enabled = !isExportingToSplitwise
                ) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSplitwiseExportDialog = false },
                    enabled = !isExportingToSplitwise
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Bill") },
            text = { Text("Are you sure you want to delete this bill permanently?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            showDeleteConfirmation = false
                            vm.delete()
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddParticipantDialog) {
        AlertDialog(
            onDismissRequest = { 
                showAddParticipantDialog = false
                participantError = null
            },
            title = { Text("Add Participant") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newParticipantName,
                        onValueChange = { 
                            newParticipantName = it
                            participantError = null
                        },
                        label = { Text("Name") },
                        singleLine = true,
                        isError = participantError != null,
                        supportingText = {
                            if (participantError != null) {
                                Text(participantError!!, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedName = newParticipantName.trim()
                        if (trimmedName.isNotBlank()) {
                            if (vm.addParticipant(trimmedName)) {
                                newParticipantName = ""
                                participantError = null
                                showAddParticipantDialog = false
                            } else {
                                participantError = "This participant already exists"
                            }
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddParticipantDialog = false
                    participantError = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditTitleDialog) {
        AlertDialog(
            onDismissRequest = { showEditTitleDialog = false },
            title = { Text("Edit Bill Title") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Title") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            vm.setTitle(newTitle)
                            showEditTitleDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTitleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    itemToEdit?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { itemToEdit = null },
            onConfirm = { updatedItem ->
                vm.updateItem(updatedItem)
                itemToEdit = null
            }
        )
    }

    itemForShares?.let { selectedItem ->
        val currentItem = draftBill.items.find { it.id == selectedItem.id } ?: selectedItem
        SharesBottomSheet(
            item = currentItem,
            participants = draftBill.participants.filter { currentItem.assignedParticipantIds.contains(it.id) },
            onDismiss = { itemForShares = null },
            onUpdateShares = { updatedShares ->
                vm.updateShares(currentItem.id, updatedShares)
            }
        )
    }

    if (showAddItemDialog) {
        EditItemDialog(
            item = BillItem(name = "", price = 0.0),
            onDismiss = { showAddItemDialog = false },
            onConfirm = { newItem ->
                vm.addItem(newItem)
                showAddItemDialog = false
            }
        )
    }

    if (showReceiptImage && draftBill.imageUri != null) {
        ReceiptImageDialog(
            imageUri = draftBill.imageUri!!,
            onDismiss = { showReceiptImage = false }
        )
    }
}

@Composable
fun ReceiptImageDialog(
    imageUri: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 10f)
        // Only allow panning while zoomed in; reset offset at 1x.
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Original Receipt")
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Receipt Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                            .transformable(state = transformableState)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (scale > 1f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.5f
                                        }
                                    }
                                )
                            }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pinch or double-tap to zoom",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun EditItemDialog(
    item: BillItem,
    onDismiss: () -> Unit,
    onConfirm: (BillItem) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var priceText by remember { mutableStateOf(if (item.price == 0.0) "" else item.price.toString()) }
    var isProportional by remember { mutableStateOf(item.isProportionalSplit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item.name.isEmpty()) "Add Item" else "Edit Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Price") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Eqt Split", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Ideal for tax/service charges. Split based on each person's share of the total.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isProportional,
                        onCheckedChange = { isProportional = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceText.toDoubleOrNull() ?: 0.0
                    onConfirm(item.copy(name = name, price = price, isProportionalSplit = isProportional))
                },
                enabled = name.isNotBlank() && priceText.toDoubleOrNull() != null
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharesBottomSheet(
    item: BillItem,
    participants: List<Participant>,
    onDismiss: () -> Unit,
    onUpdateShares: (Map<String, Double>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Map of participant ID to their share as a string for text entry
    var shareTexts by remember(item.id) { 
        mutableStateOf(
            participants.associate { p -> 
                val share = item.participantShares[p.id] ?: 1.0
                p.id to (if (share % 1.0 == 0.0) share.toInt().toString() else share.toString())
            }
        ) 
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Customize Shares",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(participants) { participant ->
                    val participantColor = ParticipantColors.getOrElse(participant.colorIndex) { ParticipantColors[0] }
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = participantColor.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, participantColor.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = participantColor.copy(alpha = 0.2f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        participant.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = participantColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Spacer(Modifier.width(12.dp))
                            
                            Text(
                                participant.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = shareTexts[participant.id] ?: "1",
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || (newValue.toDoubleOrNull()?.let { it >= 0 } == true) || newValue == ".") {
                                        shareTexts = shareTexts + (participant.id to newValue)
                                        val doubleValue = newValue.toDoubleOrNull() ?: 1.0
                                        val newShares = item.participantShares.toMutableMap()
                                        newShares[participant.id] = doubleValue
                                        onUpdateShares(newShares)
                                    }
                                },
                                modifier = Modifier.width(120.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = participantColor.copy(alpha = 0.3f),
                                    focusedBorderColor = participantColor,
                                    unfocusedContainerColor = participantColor.copy(alpha = 0.05f),
                                    focusedContainerColor = participantColor.copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                suffix = { Text("share", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Done")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BillItemCard(
    item: BillItem,
    participants: List<Participant>,
    standardSubtotals: Map<String, Double>,
    currency: String,
    onToggleParticipant: (String) -> Unit,
    onEditItem: (BillItem) -> Unit,
    onDeleteItem: (BillItem) -> Unit,
    onEditShares: (BillItem) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (item.isProportionalSplit)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isProportionalSplit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (item.isProportionalSplit) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.AutoAwesome, 
                                null, 
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Proportional Split",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        formatMoney(item.price, currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row {
                    AppTooltip("Edit item details") {
                        IconButton(onClick = { onEditItem(item) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                        }
                    }
                    AppTooltip("Remove this item") {
                        IconButton(onClick = { onDeleteItem(item) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            
            if (!item.isProportionalSplit) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(modifier = Modifier.alpha(0.1f))
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Split with", 
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.assignedParticipantIds.isNotEmpty()) {
                            AppTooltip("Customize shares for each person") {
                                TextButton(
                                    onClick = { onEditShares(item) },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Rounded.PieChart, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Shares", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if (participants.isNotEmpty()) {
                            val allSelected = item.assignedParticipantIds.size == participants.size
                            AppTooltip(if (allSelected) "Deselect everyone" else "Select everyone") {
                                TextButton(
                                    onClick = {
                                        if (allSelected) onToggleParticipant("") else onToggleParticipant("*")
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        if (allSelected) "Deselect All" else "Select All",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    participants.forEach { participant ->
                        val isSelected = item.assignedParticipantIds.contains(participant.id)
                        val share = item.participantShares[participant.id] ?: 1.0
                        val participantColor = ParticipantColors.getOrElse(participant.colorIndex) { ParticipantColors[0] }
                        
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggleParticipant(participant.id) },
                            label = { 
                                Text(
                                    text = if (isSelected && share != 1.0) 
                                        "${participant.name} (${if (share % 1.0 == 0.0) share.toInt() else share})" 
                                    else participant.name,
                                    style = MaterialTheme.typography.labelSmall
                                ) 
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = participantColor.copy(alpha = 0.15f),
                                selectedLabelColor = participantColor,
                                selectedLeadingIconColor = participantColor,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(12.dp)) }
                            } else null,
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (isSelected) participantColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                enabled = true,
                                selected = isSelected,
                                borderWidth = 1.dp
                            )
                        )
                    }
                    if (participants.isEmpty()) {
                        Text(
                            "Add participants first",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Colored Split Bar
            Spacer(Modifier.height(16.dp))
            val weights = if (item.isProportionalSplit) {
                standardSubtotals
            } else {
                item.assignedParticipantIds.associateWith { item.participantShares[it] ?: 1.0 }
            }
            
            ItemSplitBar(weights = weights, participants = participants)
        }
    }
}

@Composable
fun ItemSplitBar(
    weights: Map<String, Double>,
    participants: List<Participant>,
    modifier: Modifier = Modifier
) {
    val totalWeight = weights.values.sum()
    if (totalWeight <= 0) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
    ) {
        val weightList = weights.toList().filter { it.second > 0 }
        weightList.forEachIndexed { index, (id, weight) ->
            val participant = participants.find { it.id == id }
            if (participant != null) {
                val color = ParticipantColors.getOrElse(participant.colorIndex) { ParticipantColors[0] }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(weight.toFloat())
                        .background(color)
                )
                // Black separator line between segments
                if (index < weightList.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(Color.Black)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppTooltip(
    text: String,
    content: @Composable () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.combinedClickable(
            onClick = { /* The child handles the click */ },
            onLongClick = { showDialog = true }
        )
    ) {
        content()
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Information")
                }
            },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DetailScreenPreview() {
    val sampleParticipants = listOf(
        Participant(id = "1", name = "Alice", colorIndex = 0),
        Participant(id = "2", name = "Bob", colorIndex = 1),
        Participant(id = "3", name = "Charlie", colorIndex = 2)
    )

    val sampleItems = listOf(
        BillItem(
            id = "i1",
            name = "Large Margherita Pizza",
            price = 500.0,
            assignedParticipantIds = listOf("1", "2")
        ),
        BillItem(
            id = "i2",
            name = "Soda",
            price = 60.0,
            assignedParticipantIds = listOf("1", "2", "3")
        ),
        BillItem(
            id = "i3",
            name = "Veg Burger",
            price = 150.0,
            assignedParticipantIds = listOf("3")
        ),
        BillItem(
            id = "i4",
            name = "GST & Service Charge",
            price = 71.0,
            isProportionalSplit = true
        )
    )

    val sampleBill = Bill(
        id = "b1",
        title = "Dinner at Luigi's",
        items = sampleItems,
        participants = sampleParticipants,
        splitwiseGroupId = "12345",
        payerAmounts = mapOf("1" to 500.0, "2" to 281.0)
    )

    PA_bot2Theme {
        DetailScreen(
            billId = "b1",
            initialBill = sampleBill,
            onBack = {}
        )
    }
}
