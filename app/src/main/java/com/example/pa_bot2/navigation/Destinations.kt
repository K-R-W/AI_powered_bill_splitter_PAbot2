package com.example.pa_bot2.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import com.example.pa_bot2.model.Bill

@Serializable
sealed interface Destination : NavKey {
    @Serializable
    data object Onboarding : Destination

    @Serializable
    data object Home : Destination

    @Serializable
    data class Capture(val sharedImageUri: String? = null) : Destination

    @Serializable
    data class Details(val billId: String? = null, val initialBill: Bill? = null) : Destination

    @Serializable
    data object Settings : Destination
}
