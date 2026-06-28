package com.example.pa_bot2.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AdaptiveMainScreen(
    onScanBill: () -> Unit,
    onAddManual: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()

    BackHandler(navigator.canNavigateBack()) {
        scope.launch {
            navigator.navigateBack()
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                HomeScreen(
                    onScanBill = onScanBill,
                    onAddManual = onAddManual,
                    onOpenSettings = onOpenSettings,
                    onBillClick = { billId ->
                        scope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, billId)
                        }
                    }
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val billId = navigator.currentDestination?.contentKey
                DetailScreen(
                    billId = billId,
                    onBack = {
                        if (navigator.canNavigateBack()) {
                            scope.launch {
                                navigator.navigateBack()
                            }
                        }
                    }
                )
            }
        }
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 1000, heightDp = 600)
@Composable
fun AdaptiveMainScreenPreview() {
    com.example.pa_bot2.ui.theme.PA_bot2Theme {
        AdaptiveMainScreen(onScanBill = {}, onAddManual = {}, onOpenSettings = {})
    }
}
