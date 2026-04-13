package com.premiumtvplayer.app.ui.billing

import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.api.EntitlementDto
import com.premiumtvplayer.app.data.billing.BillingClientWrapper
import com.premiumtvplayer.app.data.billing.BillingRepository
import com.premiumtvplayer.app.data.billing.BillingSku
import com.premiumtvplayer.app.data.billing.PremiumProduct
import com.premiumtvplayer.app.data.billing.PurchaseResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

    @Before
    fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun mockWrapper(purchaseFlow: kotlinx.coroutines.flow.Flow<PurchaseResult>): BillingClientWrapper {
        val wrapper = mockk<BillingClientWrapper>(relaxed = true)
        every { wrapper.purchaseFlow } returns purchaseFlow
        return wrapper
    }

    @Test
    fun `init loads SKUs and lands on Ready`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<BillingRepository>()
        val skus = listOf(
            BillingSku(PremiumProduct.Single, mockk(relaxed = true)),
            BillingSku(PremiumProduct.Family, mockk(relaxed = true)),
        )
        coEvery { repo.querySkus() } returns skus

        val wrapper = mockWrapper(MutableSharedFlow())
        val vm = PaywallViewModel(repo, wrapper)

        val state = vm.uiState.value
        assertTrue(state is PaywallUiState.Ready)
        assertEquals(2, (state as PaywallUiState.Ready).skus.size)
    }

    @Test
    fun `init surfaces Error when querySkus fails`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<BillingRepository>()
        coEvery { repo.querySkus() } throws IllegalStateException("Play Billing offline")

        val wrapper = mockWrapper(MutableSharedFlow())
        val vm = PaywallViewModel(repo, wrapper)

        val state = vm.uiState.value
        assertTrue(state is PaywallUiState.Error)
        assertTrue((state as PaywallUiState.Error).message.contains("offline") ||
            state.message.contains("Play Billing unavailable"))
    }

    @Test
    fun `successful purchase flow verifies and transitions to PurchaseSucceeded`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<BillingRepository>()
        val skus = listOf(BillingSku(PremiumProduct.Family, mockk(relaxed = true)))
        coEvery { repo.querySkus() } returns skus

        // Fake a Play Billing purchase + our server's entitlement response.
        val entitlement = EntitlementDto(state = "lifetime_family", activatedAt = "2026-04-13T12:00:00.000Z")
        coEvery { repo.acknowledgeAndVerify("tok", "premium_player_family") } returns entitlement

        val flow = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 4)
        val wrapper = mockWrapper(flow)

        val vm = PaywallViewModel(repo, wrapper)
        // Simulate Play Billing callback with the purchase token.
        val purchase = mockk<com.android.billingclient.api.Purchase>()
        every { purchase.purchaseToken } returns "tok"
        every { purchase.products } returns listOf("premium_player_family")
        flow.emit(PurchaseResult.Success(purchase))

        val state = vm.uiState.value
        assertTrue(state is PaywallUiState.PurchaseSucceeded)
        assertEquals("lifetime_family", (state as PaywallUiState.PurchaseSucceeded).entitlement.state)
    }

    @Test
    fun `user cancel clears submitting and keeps Ready`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<BillingRepository>()
        coEvery { repo.querySkus() } returns listOf(BillingSku(PremiumProduct.Family, mockk(relaxed = true)))

        val flow = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 4)
        val wrapper = mockWrapper(flow)
        val vm = PaywallViewModel(repo, wrapper)

        flow.emit(PurchaseResult.UserCanceled)

        val state = vm.uiState.value
        assertTrue(state is PaywallUiState.Ready)
        assertEquals(false, (state as PaywallUiState.Ready).submitting)
        // No error banner on user-cancel — it's a premium detail: we only
        // yell at users when *we* failed, not when they changed their mind.
        assertEquals(null, state.errorMessage)
    }

    @Test
    fun `restore calls backend and transitions on success`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<BillingRepository>()
        coEvery { repo.querySkus() } returns listOf(BillingSku(PremiumProduct.Single, mockk(relaxed = true)))
        coEvery { repo.restore() } returns EntitlementDto(state = "lifetime_single")

        val vm = PaywallViewModel(repo, mockWrapper(MutableSharedFlow()))
        vm.restore()

        val state = vm.uiState.value
        assertTrue(state is PaywallUiState.PurchaseSucceeded)
        assertEquals("lifetime_single", (state as PaywallUiState.PurchaseSucceeded).entitlement.state)
    }

    @Test
    fun `restore with server error surfaces friendly message`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<BillingRepository>()
        coEvery { repo.querySkus() } returns listOf(BillingSku(PremiumProduct.Single, mockk(relaxed = true)))
        coEvery { repo.restore() } throws ApiException.Server(
            httpStatus = 401,
            code = "UNAUTHORIZED",
            message = "Missing Authorization: Bearer token",
        )

        val vm = PaywallViewModel(repo, mockWrapper(MutableSharedFlow()))
        vm.restore()

        val state = vm.uiState.value
        assertTrue(state is PaywallUiState.Ready)
        val ready = state as PaywallUiState.Ready
        assertEquals(false, ready.submitting)
        assertTrue(ready.errorMessage?.contains("session", ignoreCase = true) == true
            || ready.errorMessage?.contains("sign in", ignoreCase = true) == true)
    }
}
