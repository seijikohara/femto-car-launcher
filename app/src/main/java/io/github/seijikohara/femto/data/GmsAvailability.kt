package io.github.seijikohara.femto.data

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

internal class GmsAvailability(
    private val context: Context,
    private val availability: GoogleApiAvailability = GoogleApiAvailability.getInstance(),
) {
    fun isPresent(): Boolean = availability.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}
