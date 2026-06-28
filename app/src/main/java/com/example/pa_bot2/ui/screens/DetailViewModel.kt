package com.example.pa_bot2.ui.screens

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.pa_bot2.model.Bill
import com.example.pa_bot2.model.BillItem
import com.example.pa_bot2.model.BillRepository
import com.example.pa_bot2.model.Participant
import com.example.pa_bot2.ui.theme.ParticipantColors
import com.example.pa_bot2.util.calculateSettlements
import com.example.pa_bot2.util.calculateTotals

/**
 * Owns the editable state for a single bill so it survives configuration changes and
 * process death. The screen reads [draftBill]/[hasChanges] and the derived values, and
 * mutates only through the methods here.
 */
class DetailViewModel(billId: String?, initialBill: Bill?) : ViewModel() {

    private val existing = (initialBill?.id ?: billId)?.let { id ->
        BillRepository.bills.value.find { it.id == id }
    }

    var draftBill by mutableStateOf(
        run {
            val base = initialBill ?: existing ?: Bill(title = "New Bill")
            // Seed every participant into payerAmounts so "remaining" is correct up front.
            val payers = base.payerAmounts.toMutableMap()
            base.participants.forEach { payers.putIfAbsent(it.id, 0.0) }
            base.copy(payerAmounts = payers)
        }
    )
        private set

    var hasChanges by mutableStateOf(initialBill != null && existing == null)
        private set

    /** True once the bill exists in the repository (so delete/export make sense). */
    val isExistingBill: Boolean
        get() = BillRepository.bills.value.any { it.id == draftBill.id }

    private inline fun update(transform: (Bill) -> Bill) {
        draftBill = transform(draftBill)
        hasChanges = true
    }

    // --- Title / items ---------------------------------------------------------
    fun setTitle(title: String) = update { it.copy(title = title) }

    fun addItem(item: BillItem) = update { it.copy(items = it.items + item) }

    fun updateItem(item: BillItem) =
        update { b -> b.copy(items = b.items.map { if (it.id == item.id) item else it }) }

    fun deleteItem(item: BillItem) =
        update { b -> b.copy(items = b.items.filter { it.id != item.id }) }

    fun updateShares(itemId: String, shares: Map<String, Double>) =
        update { b -> b.copy(items = b.items.map { if (it.id == itemId) it.copy(participantShares = shares) else it }) }

    fun toggleAssignment(itemId: String, token: String) = update { b ->
        b.copy(items = b.items.map { item ->
            if (item.id != itemId) return@map item
            val ids = item.assignedParticipantIds.toMutableList()
            when (token) {
                "*" -> { ids.clear(); ids.addAll(b.participants.map { it.id }) }
                "" -> ids.clear()
                else -> if (ids.contains(token)) ids.remove(token) else ids.add(token)
            }
            item.copy(assignedParticipantIds = ids)
        })
    }

    fun assignEveryoneToAll() = update { b ->
        b.copy(items = b.items.map {
            if (it.isProportionalSplit) it
            else it.copy(assignedParticipantIds = b.participants.map { p -> p.id }, participantShares = emptyMap())
        })
    }

    fun unassignAll() = update { b ->
        b.copy(items = b.items.map {
            if (it.isProportionalSplit) it
            else it.copy(assignedParticipantIds = emptyList(), participantShares = emptyMap())
        })
    }

    // --- Participants ----------------------------------------------------------
    /** Returns false if the name is blank or a duplicate. */
    fun addParticipant(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        if (draftBill.participants.any { it.name.equals(trimmed, ignoreCase = true) }) return false
        val used = draftBill.participants.map { it.colorIndex }.toSet()
        val idx = (0 until ParticipantColors.size).firstOrNull { it !in used }
            ?: (draftBill.participants.size % ParticipantColors.size)
        val participant = Participant(name = trimmed, colorIndex = idx)
        update {
            it.copy(
                participants = it.participants + participant,
                payerAmounts = it.payerAmounts + (participant.id to 0.0)
            )
        }
        return true
    }

    fun removeParticipant(id: String) = update { b ->
        b.copy(
            participants = b.participants.filter { it.id != id },
            items = b.items.map { item ->
                item.copy(
                    assignedParticipantIds = item.assignedParticipantIds.filter { it != id },
                    participantShares = item.participantShares.filterKeys { it != id }
                )
            },
            payerAmounts = b.payerAmounts.filterKeys { it != id }
        )
    }

    // --- Payments --------------------------------------------------------------
    fun setPayer(id: String, amount: Double) =
        update { it.copy(payerAmounts = it.payerAmounts.toMutableMap().apply { put(id, amount) }) }

    fun removePayer(id: String) =
        update { it.copy(payerAmounts = it.payerAmounts.toMutableMap().apply { remove(id) }) }

    // --- Splitwise group -------------------------------------------------------
    fun linkGroup(groupId: String, participants: List<Participant>, payerAmounts: Map<String, Double>) = update {
        it.copy(
            splitwiseGroupId = groupId,
            participants = participants,
            items = it.items.map { i -> i.copy(assignedParticipantIds = emptyList(), participantShares = emptyMap()) },
            payerAmounts = payerAmounts
        )
    }

    fun unlinkGroup() = update {
        it.copy(
            splitwiseGroupId = null,
            participants = emptyList(),
            items = it.items.map { i -> i.copy(assignedParticipantIds = emptyList(), participantShares = emptyMap()) }
        )
    }

    // --- Persistence -----------------------------------------------------------
    suspend fun save() {
        if (isExistingBill) BillRepository.updateBill(draftBill) else BillRepository.addBill(draftBill)
        hasChanges = false
    }

    suspend fun delete() = BillRepository.deleteBill(draftBill.id)

    // --- Derived (recompute only when draftBill changes) -----------------------
    val participantTotals by derivedStateOf { calculateTotals(draftBill.items, draftBill.participants) }
    val settlements by derivedStateOf { calculateSettlements(participantTotals, draftBill.payerAmounts) }
    val grandTotal by derivedStateOf { draftBill.items.sumOf { it.price } }
    val totalPaid by derivedStateOf { draftBill.participants.sumOf { draftBill.payerAmounts[it.id] ?: 0.0 } }
    val unassignedTotal by derivedStateOf {
        draftBill.items.filter { !it.isProportionalSplit && it.assignedParticipantIds.isEmpty() }.sumOf { it.price }
    }
    val standardSubtotals by derivedStateOf {
        val totals = mutableMapOf<String, Double>()
        draftBill.participants.forEach { totals[it.id] = 0.0 }
        draftBill.items.filter { !it.isProportionalSplit }.forEach { item ->
            val assigned = item.assignedParticipantIds
            val totalShares = assigned.sumOf { id -> item.participantShares[id] ?: 1.0 }
            if (totalShares > 0) {
                assigned.forEach { id ->
                    val share = item.participantShares[id] ?: 1.0
                    totals[id] = (totals[id] ?: 0.0) + item.price * (share / totalShares)
                }
            }
        }
        totals
    }
}
