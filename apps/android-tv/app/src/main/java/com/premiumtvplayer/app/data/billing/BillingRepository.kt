package com.premiumtvplayer.app.data.billing

import android.app.Activity
import com.android.billingclient.api.ProductDetails
import com.premiumtvplayer.app.data.api.ApiErrorMapper
import com.premiumtvplayer.app.data.api.BillingVerifyRequest
import com.premiumtvplayer.app.data.api.EntitlementDto
import com.premiumtvplayer.app.data.api.PremiumPlayerApi
import javax.inject.Inject
import javax.inject.Singleton

/** Canonical catalog entry + its live Play Billing details. */
data class BillingSku(
    val product: PremiumProduct,
    val details: ProductDetails,
) {
    val formattedPrice: String
        get() = details.oneTimePurchaseOfferDetails?.formattedPrice ?: "—"
}

@Singleton
class BillingRepository @Inject constructor(
    private val api: PremiumPlayerApi,
    private val playBilling: BillingClientWrapper,
) {
    /**
     * Query Play Billing for both products + pair them with our catalog.
     * Returns an ordered list so callers can render them in
     * [PremiumProduct.entries] order (Single first, Family second).
     */
    suspend fun querySkus(): List<BillingSku> {
        val ids = PremiumProduct.entries.map { it.productId }
        val details = playBilling.queryProducts(ids)
        return PremiumProduct.entries.mapNotNull { product ->
            val d = details.firstOrNull { it.productId == product.productId } ?: return@mapNotNull null
            BillingSku(product, d)
        }
    }

    /** Kick off the Play Billing purchase sheet for [product]. */
    fun launchPurchase(activity: Activity, sku: BillingSku) {
        playBilling.launchPurchase(activity, sku.details)
    }

    /**
     * Verify the purchase server-side (Run 9 `BillingService.verifyAndApply`)
     * and return the resulting entitlement. This is the single authoritative
     * path — clients should never flip entitlement state locally without
     * a round-trip through here.
     */
    suspend fun acknowledgeAndVerify(
        purchaseToken: String,
        productId: String,
    ): EntitlementDto = wrap {
        api.verifyPurchase(BillingVerifyRequest(purchaseToken, productId)).entitlement
    }

    /**
     * Restore: ask the backend to re-verify every known-good purchase
     * for the caller. Returns the resulting entitlement.
     */
    suspend fun restore(): EntitlementDto = wrap {
        api.restorePurchases().entitlement
    }

    private suspend inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        throw ApiErrorMapper.map(t)
    }
}
