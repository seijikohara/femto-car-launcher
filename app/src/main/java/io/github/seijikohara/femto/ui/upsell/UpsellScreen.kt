package io.github.seijikohara.femto.ui.upsell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.billing.SubscriptionOffer
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

/**
 * Paywall surface listing Mapbox subscription offers. When offers are
 * unavailable or the billing connection is down the screen shows a
 * friendly error and a Retry button instead of leaving the user stuck.
 */
@Composable
internal fun UpsellScreen(
    uiState: UpsellUiState,
    onAction: (UpsellAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier =
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    Text(
        text = stringResource(R.string.upsell_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
        text = stringResource(R.string.upsell_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (uiState.offers.isEmpty() || !uiState.connected) {
        UnavailableContent(onAction = onAction)
    } else {
        uiState.offers.forEach { offer -> OfferCard(offer = offer, onAction = onAction) }
    }
}

@Composable
private fun OfferCard(
    offer: SubscriptionOffer,
    onAction: (UpsellAction) -> Unit,
) = Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = billingPeriodLabel(offer.billingPeriod),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = offer.formattedPrice,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { onAction(UpsellAction.Subscribe(offer.offerToken)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = FemtoDimens.MinTouchTarget),
        ) {
            Text(
                text = stringResource(
                    if (offer.isTrial) R.string.upsell_try_free else R.string.upsell_subscribe,
                ),
            )
        }
    }
}

@Composable
private fun UnavailableContent(onAction: (UpsellAction) -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.upsell_unavailable),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { onAction(UpsellAction.Retry) },
            modifier = Modifier.fillMaxWidth().heightIn(min = FemtoDimens.MinTouchTarget),
        ) {
            Text(text = stringResource(R.string.upsell_retry))
        }
    }

/** Map ISO 8601 duration codes to human-readable period labels. */
@Composable
private fun billingPeriodLabel(billingPeriod: String): String =
    when (billingPeriod) {
        "P1M" -> stringResource(R.string.upsell_period_month)
        "P1Y" -> stringResource(R.string.upsell_period_year)
        else -> billingPeriod
    }

@PreviewLightDark
@Composable
private fun UpsellScreenOffersPreview() {
    FemtoTheme {
        UpsellScreen(
            uiState = UpsellUiState(
                offers = listOf(
                    SubscriptionOffer("monthly", "tok-monthly", "$3.99/mo", "P1M", isTrial = false),
                    SubscriptionOffer("annual", "tok-annual", "$29.99/yr", "P1Y", isTrial = true),
                ),
                connected = true,
                mapboxUnlocked = false,
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun UpsellScreenUnavailablePreview() {
    FemtoTheme {
        UpsellScreen(
            uiState = UpsellUiState.Initial,
            onAction = {},
        )
    }
}
