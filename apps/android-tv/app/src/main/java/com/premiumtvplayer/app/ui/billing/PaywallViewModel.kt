package com.premiumtvplayer.app.ui.billing

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.premiumtvplayer.app.data.api.ApiErrorCopy
import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.api.EntitlementDto
import com.premiumtvplayer.app.data.billing.BillingClientWrapper
import com.premiumtvplayer.app.data.billing.BillingRepository
import com.premiumtvplayer.app.data.billing.BillingSku
import com.premiumtvplayer.app.data.billing.PurchaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PaywallUiState {
    data object Loading : PaywallUiState

    data class Ready(
        val skus: List<BillingSku>,
        val submitting: Boolean = false,
        val errorMessage: String? = null,
    ) : PaywallUiState

    data class PurchaseSucceeded(val entitlement: EntitlementDto) : PaywallUiState

    data class Error(val message: String) : PaywallUiState
}

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billing: BillingRepository,
    private val wrapper: BillingClientWrapper,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PaywallUiState>(PaywallUiState.Loading)
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    init {
        loadSkus()
        viewModelScope.launch {
            wrapper.purchaseFlow.collect { result ->
                handlePurchaseResult(result)
            }
        }
    }

    fun loadSkus() {
        _uiState.value = PaywallUiState.Loading
        viewModelScope.launch {
            try {
                val skus = billing.querySkus()
                _uiState.value = PaywallUiState.Ready(skus)
            } catch (t: Throwable) {
                _uiState.value = PaywallUiState.Error(mapError(t, "Play Billing unavailable."))
            }
        }
    }

    fun launchPurchase(activity: Activity, sku: BillingSku) {
        val ready = _uiState.value as? PaywallUiState.Ready ?: return
        _uiState.value = ready.copy(submitting = true, errorMessage = null)
        billing.launchPurchase(activity, sku)
    }

    fun restore() {
        val ready = _uiState.value as? PaywallUiState.Ready ?: return
        if (ready.submitting) return
        _uiState.value = ready.copy(submitting = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val ent = billing.restore()
                _uiState.value = PaywallUiState.PurchaseSucceeded(ent)
            } catch (t: Throwable) {
                _uiState.value = ready.copy(
                    submitting = false,
                    errorMessage = mapError(t, "Restore failed. Try again."),
                )
            }
        }
    }

    private fun handlePurchaseResult(result: PurchaseResult) {
        when (result) {
            is PurchaseResult.Success -> {
                val token = result.purchase.purchaseToken
                val productId = result.purchase.products.firstOrNull() ?: run {
                    setReadyError("Purchase is missing a product id.")
                    return
                }
                viewModelScope.launch {
                    try {
                        val entitlement = billing.acknowledgeAndVerify(token, productId)
                        _uiState.value = PaywallUiState.PurchaseSucceeded(entitlement)
                    } catch (t: Throwable) {
                        setReadyError(mapError(t, "Purchase verification failed."))
                    }
                }
            }
            PurchaseResult.UserCanceled -> setReadyError(null) // clear submitting silently
            is PurchaseResult.Error -> setReadyError("Play Billing error (${result.code}): ${result.message}")
        }
    }

    private fun setReadyError(message: String?) {
        val ready = _uiState.value as? PaywallUiState.Ready ?: return
        _uiState.value = ready.copy(submitting = false, errorMessage = message)
    }

    private fun mapError(t: Throwable, fallback: String): String =
        (t as? ApiException.Server)?.let { ApiErrorCopy.forCode(it.code, it.message) }
            ?: t.message ?: fallback
}
