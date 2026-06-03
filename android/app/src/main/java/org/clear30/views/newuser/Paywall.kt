package org.clear30.views.newuser

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PurchasesError
import org.clear30.data.model.EntitlementType
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading1
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * Paywall — ported from Paywall.swift. iOS rendered a Helium-managed paywall
 * over RevenueCat; on Android we render RevenueCat offerings directly (a Helium
 * Android paywall view can replace this body later — see HeliumPaywallView TODO).
 * Purchasing a package resolves to [EntitlementType.DEFAULT].
 */
@Composable
fun Paywall(
    popup: Boolean = false,
    hard: Boolean = true,
    onCompleted: (EntitlementType?) -> Unit,
) {
    val activity = LocalContext.current as? Activity
    var packages by remember { mutableStateOf<List<Package>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        Purchases.sharedInstance.getOfferingsWith(onError = { loading = false }) { offerings ->
            packages = offerings.current?.availablePackages.orEmpty()
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(Dimens.horizontalPadding)) {
        if (popup && !hard) {
            IconButton("xmark", modifier = Modifier.align(Alignment.End)) { onCompleted(null) }
        }
        Spacer(Modifier.weight(1f))
        Heading1("Unlock Clear30")
        Spacer(Modifier.padding(Dimens.cardSpacing))

        if (loading) {
            CircularProgressIndicator()
        } else {
            packages.forEach { pkg ->
                Clear30Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing / 2).clickable {
                        if (activity != null) purchase(activity, pkg) { onCompleted(EntitlementType.DEFAULT) }
                    },
                    gradient = Clear30Gradients.clear30,
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SmallText(pkg.product.title, color = androidx.compose.ui.graphics.Color.White)
                        Spacer(Modifier.weight(1f))
                        SmallText(pkg.product.price.formatted, color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

private fun purchase(activity: Activity, pkg: Package, onSuccess: () -> Unit) {
    Purchases.sharedInstance.purchase(
        PurchaseParams.Builder(activity, pkg).build(),
        object : PurchaseCallback {
            override fun onCompleted(transaction: StoreTransaction, customerInfo: CustomerInfo) = onSuccess()
            override fun onError(error: PurchasesError, userCancelled: Boolean) { /* surface via AlertHandler later */ }
        },
    )
}
