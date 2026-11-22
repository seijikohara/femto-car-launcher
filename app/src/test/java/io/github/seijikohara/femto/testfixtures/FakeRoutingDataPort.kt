package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.routing.InstalledRoutingTile
import io.github.seijikohara.femto.ui.routingdata.RoutingDataPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update

/**
 * In-memory [RoutingDataPort]: the tiles start as [initial] (null holds the list back, as a store still reading its
 * directory does), and each delete removes from them at once, as the store's list follows its files.
 */
internal class FakeRoutingDataPort(
    initial: List<InstalledRoutingTile>?,
) : RoutingDataPort {
    private val state = MutableStateFlow(initial)

    override val tiles: Flow<List<InstalledRoutingTile>> = state.filterNotNull()

    /** The names deleted one by one, in call order. */
    val deleted = mutableListOf<String>()

    var deletedAll = false
        private set

    override suspend fun delete(name: String) {
        deleted += name
        state.update { tiles -> tiles?.filterNot { it.name == name } }
    }

    override suspend fun deleteAll() {
        deletedAll = true
        state.value = emptyList()
    }
}
