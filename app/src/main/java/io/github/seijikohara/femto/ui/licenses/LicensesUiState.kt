package io.github.seijikohara.femto.ui.licenses

/**
 * One credited component on the licenses screen: a Gradle or web dependency
 * collected by AboutLibraries, reduced to what the screen renders. [licenseText]
 * is the full embedded license body shown in the detail view; it is null when the
 * build could not resolve a body for the component.
 */
internal data class LicenseItem(
    val id: String,
    val name: String,
    val licenseName: String?,
    val licenseText: String?,
    val url: String?,
)

internal data class LicensesUiState(
    val isLoading: Boolean = true,
    val libraries: List<LicenseItem> = emptyList(),
    // The component whose full license body is open; null shows the list.
    val selected: LicenseItem? = null,
) {
    companion object {
        val Initial: LicensesUiState = LicensesUiState()
    }
}

internal sealed interface LicensesAction {
    data class Select(
        val item: LicenseItem,
    ) : LicensesAction

    data object ClearSelection : LicensesAction
}
