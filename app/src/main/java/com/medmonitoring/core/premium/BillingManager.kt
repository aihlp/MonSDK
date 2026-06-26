package com.medmonitoring.core.premium

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class BillingUiState(
    val connected: Boolean = false,
    val loading: Boolean = false,
    val message: String? = null
)

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext context: Context,
    private val premiumRepository: PremiumRepository
) {
    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()
    private val productDetails = mutableMapOf<String, ProductDetails>()

    private val client = BillingClient.newBuilder(context)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.orEmpty().forEach(::grantPurchase)
            } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                _state.value = _state.value.copy(message = result.debugMessage)
            }
        }
        .build()

    fun connect(config: PremiumConfig) {
        if (client.isReady) {
            loadProducts(config)
            restorePurchases()
            return
        }
        _state.value = _state.value.copy(loading = true)
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val connected = result.responseCode == BillingClient.BillingResponseCode.OK
                _state.value = BillingUiState(connected = connected, message = result.debugMessage.takeIf { !connected })
                if (connected) {
                    loadProducts(config)
                    restorePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = BillingUiState(message = "Google Play Billing disconnected")
            }
        })
    }

    fun launchPurchase(activity: Activity, productId: String) {
        val details = productDetails[productId]
        val offerToken = details?.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (details == null || offerToken == null) {
            _state.value = _state.value.copy(message = "Product is not available in Google Play")
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
        )
    }

    fun restorePurchases() {
        if (!client.isReady) return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach(::grantPurchase)
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun loadProducts(config: PremiumConfig) {
        val products = listOf(config.monthlyProductId, config.yearlyProductId).map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(products).build()
        ) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails.clear()
                details.productDetailsList.forEach { productDetails[it.productId] = it }
            } else {
                _state.value = _state.value.copy(message = result.debugMessage)
            }
        }
    }

    private fun grantPurchase(purchase: com.android.billingclient.api.Purchase) {
        if (purchase.purchaseState != com.android.billingclient.api.Purchase.PurchaseState.PURCHASED) return
        val productId = purchase.products.firstOrNull() ?: return
        premiumRepository.updateSubscription(productId, purchase.purchaseToken)
        if (!purchase.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) {}
        }
    }
}
