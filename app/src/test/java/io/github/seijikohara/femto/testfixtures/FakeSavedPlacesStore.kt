package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.SavedPlace
import io.github.seijikohara.femto.data.places.SavedPlacesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [SavedPlacesStore] for view-model tests: every write mutates a
 * [MutableStateFlow] synchronously, appending newest last like the real store.
 */
internal class FakeSavedPlacesStore(
    initial: List<SavedPlace> = emptyList(),
) : SavedPlacesStore {
    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1
    override val places: Flow<List<SavedPlace>> = state

    /** The stored list as of now, for direct assertions. */
    val current: List<SavedPlace> get() = state.value

    override suspend fun add(
        label: String,
        target: PlaceTarget,
    ) = state.update { it + SavedPlace(nextId++, label, target) }

    override suspend fun delete(id: Long) = state.update { places -> places.filterNot { it.id == id } }
}

/** A saved place with test defaults: a query place labelled after its query. */
internal fun fakeSavedPlace(
    id: Long = 1L,
    label: String = "Office",
    target: PlaceTarget = PlaceTarget.Query("1st & Pike, Seattle"),
): SavedPlace = SavedPlace(id = id, label = label, target = target)
