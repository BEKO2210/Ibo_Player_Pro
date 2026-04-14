package com.premiumtvplayer.app.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin coroutine-friendly wrapper around Google Play's [BillingClient].
 *
 * V1 scope: query product details for the two one-time products, launch
 * the purchase flow, and stream `Purchase` callbacks so
 * [BillingRepository] can forward them to the backend verifier.
 *
 * Hardening deferred to post-launch:
 *  - reconnect-on-disconnect with exponential backoff
 *  - retry budget for network-level failures
 *  - pending-purchase lifecycle edge cases (PENDING / CONSUME_PENDING)
 */
sealed interface BillingConnection {
    data object Idle : BillingConnection
    data object Connecting : BillingConnection
    data object Ready : BillingConnection
    data class Failed(val message: String) : BillingConnection
}

@Singleton
class BillingClientWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _connection = MutableStateFlow<BillingConnection>(BillingConnection.Idle)
    val connection: StateFlow<BillingConnection> = _connection.asStateFlow()

    /** Purchase results fire into this channel from the global listener. */
    private val purchaseChannel = Channel<PurchaseResult>(capacity = Channel.UNLIMITED)
    val purchaseFlow = purchaseChannel.receiveAsFlow()

    private val listener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchaseChannel.trySend(PurchaseResult.Success(it)) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                purchaseChannel.trySend(PurchaseResult.UserCanceled)
            }
            else -> {
                purchaseChannel.trySend(
                    PurchaseResult.Error(
                        code = billingResult.responseCode,
                        message = billingResult.debugMessage.ifBlank { "Play Billing error" },
                    ),
                )
            }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    /**
     * Connect or return immediately if already ready. Call from the
     * PaywallViewModel's init.
     */
    suspend fun ensureReady(): Boolean {
        if (client.isReady) {
            _connection.value = BillingConnection.Ready
            return true
        }
        _connection.value = BillingConnection.Connecting
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        _connection.value = BillingConnection.Ready
                        if (cont.isActive) cont.resumeWith(Result.success(true))
                    } else {
                        _connection.value = BillingConnection.Failed(
                            result.debugMessage.ifBlank { "setup failed (${result.responseCode})" },
                        )
                        if (cont.isActive) cont.resumeWith(Result.success(false))
                    }
                }

                override fun onBillingServiceDisconnected() {
                    _connection.value = BillingConnection.Idle
                }
            })
        }
    }

    /** Query product details for the two one-time products at once. */
    suspend fun queryProducts(productIds: List<String>): List<ProductDetails> {
        require(ensureReady()) { "Play Billing not ready" }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            throw BillingWrapperException(
                result.billingResult.responseCode,
                result.billingResult.debugMessage.ifBlank { "queryProductDetails failed" },
            )
        }
        return result.productDetailsList.orEmpty()
    }

    /**
     * Launch the Play Billing purchase sheet. The result arrives via
     * [purchaseFlow] (i.e. the listener set on the client at construction).
     */
    fun launchPurchase(activity: Activity, details: ProductDetails) {
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    /** Query the user's currently-owned non-consumed purchases. */
    suspend fun queryExistingPurchases(): List<Purchase> {
        require(ensureReady()) { "Play Billing not ready" }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            throw BillingWrapperException(
                result.billingResult.responseCode,
                result.billingResult.debugMessage.ifBlank { "queryPurchases failed" },
            )
        }
        return result.purchasesList
    }

    fun close() {
        client.endConnection()
    }
}

sealed interface PurchaseResult {
    data class Success(val purchase: Purchase) : PurchaseResult
    data object UserCanceled : PurchaseResult
    data class Error(val code: Int, val message: String) : PurchaseResult
}

class BillingWrapperException(val code: Int, message: String) : Exception(message)
