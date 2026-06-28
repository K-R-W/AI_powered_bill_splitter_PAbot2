package com.example.pa_bot2

import com.example.pa_bot2.model.BillItem
import com.example.pa_bot2.model.Participant
import com.example.pa_bot2.util.calculateSettlements
import com.example.pa_bot2.util.calculateTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillMathTest {

    private val a = Participant(id = "1", name = "Alice")
    private val b = Participant(id = "2", name = "Bob")
    private val c = Participant(id = "3", name = "Carol")

    @Test
    fun equalSplit_dividesEvenly() {
        val items = listOf(
            BillItem(id = "i", name = "Pizza", price = 100.0, assignedParticipantIds = listOf("1", "2"))
        )
        val totals = calculateTotals(items, listOf(a, b))
        assertEquals(50.0, totals[a]!!, 0.001)
        assertEquals(50.0, totals[b]!!, 0.001)
    }

    @Test
    fun customShares_areRespected() {
        val items = listOf(
            BillItem(
                id = "i", name = "Cake", price = 100.0,
                assignedParticipantIds = listOf("1", "2"),
                participantShares = mapOf("1" to 3.0, "2" to 1.0)
            )
        )
        val totals = calculateTotals(items, listOf(a, b))
        assertEquals(75.0, totals[a]!!, 0.001)
        assertEquals(25.0, totals[b]!!, 0.001)
    }

    @Test
    fun proportionalItem_distributesBySubtotal() {
        val items = listOf(
            BillItem(id = "1", name = "Main", price = 80.0, assignedParticipantIds = listOf("1")),
            BillItem(id = "2", name = "Side", price = 20.0, assignedParticipantIds = listOf("2")),
            BillItem(id = "3", name = "Tax", price = 10.0, isProportionalSplit = true)
        )
        val totals = calculateTotals(items, listOf(a, b))
        // Tax split 80:20 -> Alice +8, Bob +2
        assertEquals(88.0, totals[a]!!, 0.001)
        assertEquals(22.0, totals[b]!!, 0.001)
        assertEquals(110.0, totals.values.sum(), 0.001)
    }

    @Test
    fun rounding_alwaysSumsToGrandTotal() {
        // 100 / 3 cannot divide evenly; totals must still reconcile to 100.00
        val items = listOf(
            BillItem(id = "i", name = "Split3", price = 100.0, assignedParticipantIds = listOf("1", "2", "3"))
        )
        val totals = calculateTotals(items, listOf(a, b, c))
        assertEquals(100.0, totals.values.sum(), 0.001)
        totals.values.forEach { assertTrue(it == 33.33 || it == 33.34) }
    }

    @Test
    fun noParticipants_returnsEmpty() {
        val items = listOf(BillItem(id = "i", name = "x", price = 50.0))
        assertTrue(calculateTotals(items, emptyList()).isEmpty())
    }

    @Test
    fun settlements_singlePayerOwedByOthers() {
        val totals = mapOf(a to 50.0, b to 50.0)
        val payerAmounts = mapOf("1" to 100.0) // Alice paid everything
        val settlements = calculateSettlements(totals, payerAmounts)
        assertEquals(1, settlements.size)
        assertEquals(b, settlements[0].from)
        assertEquals(a, settlements[0].to)
        assertEquals(50.0, settlements[0].amount, 0.001)
    }

    @Test
    fun settlements_balancedBill_isEmpty() {
        val totals = mapOf(a to 50.0, b to 50.0)
        val payerAmounts = mapOf("1" to 50.0, "2" to 50.0) // each paid their share
        assertTrue(calculateSettlements(totals, payerAmounts).isEmpty())
    }
}
