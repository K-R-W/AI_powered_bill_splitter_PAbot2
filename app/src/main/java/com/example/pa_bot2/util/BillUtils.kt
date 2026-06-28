package com.example.pa_bot2.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.example.pa_bot2.model.Bill
import com.example.pa_bot2.model.BillItem
import com.example.pa_bot2.model.Participant
import com.example.pa_bot2.ui.theme.ParticipantColors
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A single suggested payment: [from] pays [to] the given [amount]. */
data class Settlement(val from: Participant, val to: Participant, val amount: Double)

/**
 * Reduces "who paid vs who owes" into the minimal set of payments that settle the
 * bill, using a greedy largest-creditor / largest-debtor match.
 *
 * @param participantTotals each participant's owed share (from [calculateTotals]).
 * @param payerAmounts participantId -> amount that person actually paid.
 */
fun calculateSettlements(
    participantTotals: Map<Participant, Double>,
    payerAmounts: Map<String, Double>
): List<Settlement> {
    // net > 0 => they paid more than they owe (creditor); net < 0 => they owe (debtor).
    val creditors = mutableListOf<Pair<Participant, Double>>()
    val debtors = mutableListOf<Pair<Participant, Double>>()
    participantTotals.forEach { (participant, owed) ->
        val net = (payerAmounts[participant.id] ?: 0.0) - owed
        when {
            net > 0.009 -> creditors.add(participant to net)
            net < -0.009 -> debtors.add(participant to -net) // store positive amount owed
        }
    }
    creditors.sortByDescending { it.second }
    debtors.sortByDescending { it.second }

    val settlements = mutableListOf<Settlement>()
    var i = 0
    var j = 0
    while (i < creditors.size && j < debtors.size) {
        val (creditor, credit) = creditors[i]
        val (debtor, debt) = debtors[j]
        val amount = minOf(credit, debt)
        if (amount > 0.009) {
            settlements.add(
                Settlement(from = debtor, to = creditor, amount = Math.round(amount * 100.0) / 100.0)
            )
        }
        creditors[i] = creditor to (credit - amount)
        debtors[j] = debtor to (debt - amount)
        if (creditors[i].second <= 0.009) i++
        if (debtors[j].second <= 0.009) j++
    }
    return settlements
}

fun calculateTotals(items: List<BillItem>, participants: List<Participant>): Map<Participant, Double> {
    if (participants.isEmpty()) return emptyMap()
    
    val totals = mutableMapOf<Participant, Double>()
    participants.forEach { totals[it] = 0.0 }

    // 1. Calculate standard subtotals (items with fixed assignment)
    val standardItems = items.filter { !it.isProportionalSplit }
    for (item in standardItems) {
        val assignedIds = item.assignedParticipantIds
        if (assignedIds.isNotEmpty()) {
            val totalShares = assignedIds.sumOf { id -> item.participantShares[id] ?: 1.0 }
            if (totalShares > 0) {
                for (id in assignedIds) {
                    val participant = participants.find { it.id == id }
                    if (participant != null) {
                        val share = item.participantShares[id] ?: 1.0
                        val shareAmount = item.price * (share / totalShares)
                        totals[participant] = (totals[participant] ?: 0.0) + shareAmount
                    }
                }
            }
        }
    }

    // 2. Calculate proportional items (like tax, service charges)
    val proportionalItems = items.filter { it.isProportionalSplit }
    if (proportionalItems.isNotEmpty()) {
        val totalProportionalAmount = proportionalItems.sumOf { it.price }
        val grandStandardSubtotal = totals.values.sum()

        if (grandStandardSubtotal > 0) {
            for (participant in participants) {
                val share = (totals[participant] ?: 0.0) / grandStandardSubtotal
                val proportionalShare = totalProportionalAmount * share
                totals[participant] = (totals[participant] ?: 0.0) + proportionalShare
            }
        } else if (participants.isNotEmpty()) {
            val equalShare = totalProportionalAmount / participants.size
            for (participant in participants) {
                totals[participant] = (totals[participant] ?: 0.0) + equalShare
            }
        }
    }

    // Rounding and adjusting to match Grand Total exactly
    val grandTotal = items.sumOf { it.price }
    val roundedTotals = totals.mapValues { 
        Math.round(it.value * 100.0) / 100.0 
    }.toMutableMap()
    
    val sumOfRounded = roundedTotals.values.sum()
    val difference = Math.round((grandTotal - sumOfRounded) * 100.0) / 100.0

    // If there's a small discrepancy due to rounding, adjust a random participant
    if (Math.abs(difference) >= 0.01 && Math.abs(difference) <= 0.1) {
        val luckyParticipant = participants.random()
        val currentAmount = roundedTotals[luckyParticipant] ?: 0.0
        roundedTotals[luckyParticipant] = Math.round((currentAmount + difference) * 100.0) / 100.0
    }

    return roundedTotals
}

/**
 * Renders a shareable split-summary image for [bill] and fires a share sheet.
 * The image shows each participant's share, the grand total, and the settle-up plan.
 */
fun shareBillImage(context: Context, bill: Bill) {
    val bitmap = renderBillBitmap(bill)
    try {
        val file = File(context.cacheDir, "Split_${bill.title.replace(Regex("[^A-Za-z0-9]"), "_")}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, bill.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share split"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun renderBillBitmap(bill: Bill): Bitmap {
    val totals = calculateTotals(bill.items, bill.participants)
    val settlements = calculateSettlements(totals, bill.payerAmounts)
    val grandTotal = bill.items.sumOf { it.price }

    val width = 1080
    val pad = 64f
    val row = 76f

    // Estimate height from the content we'll draw, with a little breathing room.
    var height = 360f                            // header + section label
    height += totals.size * row
    height += 150f                               // divider + grand total
    if (settlements.isNotEmpty()) height += 70f + settlements.size * row
    height += 120f                               // footer

    val bitmap = Bitmap.createBitmap(width, height.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(AndroidColor.parseColor("#1C1B1F"))

    val cTitle = AndroidColor.parseColor("#F5EFF4")
    val cSecondary = AndroidColor.parseColor("#B5AFB8")
    val cAccent = AndroidColor.parseColor("#FFB59D")
    val cDivider = AndroidColor.parseColor("#3A363D")

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cTitle; textSize = 60f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cSecondary; textSize = 34f }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cAccent; textSize = 34f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cTitle; textSize = 42f }
    val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cTitle; textSize = 42f; textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val dividerPaint = Paint().apply { color = cDivider; strokeWidth = 2f }

    var y = pad + 56f
    canvas.drawText(bill.title, pad, y, titlePaint)
    y += 46f
    canvas.drawText(
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(bill.date)),
        pad, y, subPaint
    )
    y += 50f
    canvas.drawLine(pad, y, width - pad, y, dividerPaint)
    y += 56f
    canvas.drawText("WHO OWES WHAT", pad, y, labelPaint)

    totals.forEach { (participant, amount) ->
        y += row
        val color = ParticipantColors.getOrElse(participant.colorIndex) { ParticipantColors[0] }.toArgb()
        dotPaint.color = color
        canvas.drawCircle(pad + 16f, y - 14f, 16f, dotPaint)
        canvas.drawText(participant.name, pad + 56f, y, namePaint)
        canvas.drawText(formatMoney(amount, bill.currencyCode), width - pad, y, amountPaint)
    }

    y += 40f
    canvas.drawLine(pad, y, width - pad, y, dividerPaint)
    y += 64f
    canvas.drawText("Total", pad, y, Paint(namePaint).apply {
        textSize = 48f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    })
    canvas.drawText(formatMoney(grandTotal, bill.currencyCode), width - pad, y,
        Paint(amountPaint).apply { color = cAccent; textSize = 50f })

    if (settlements.isNotEmpty()) {
        y += 70f
        canvas.drawText("SETTLE UP", pad, y, labelPaint)
        settlements.forEach { s ->
            y += row
            canvas.drawText("${s.from.name}  →  ${s.to.name}", pad, y, namePaint)
            canvas.drawText(formatMoney(s.amount, bill.currencyCode), width - pad, y, amountPaint)
        }
    }

    y += 80f
    canvas.drawText("Split with PA_bot2", pad, y, subPaint)

    return bitmap
}
