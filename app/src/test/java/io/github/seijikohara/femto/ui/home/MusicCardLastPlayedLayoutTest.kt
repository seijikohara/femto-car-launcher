package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.DashboardGeometry
import io.github.seijikohara.femto.testfixtures.FAKE_MUSIC_APP_NAME
import io.github.seijikohara.femto.testfixtures.fakeLastPlayedState
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MUSIC_ART_TAG
import io.github.seijikohara.femto.ui.home.components.MUSIC_CARD_TAG
import io.github.seijikohara.femto.ui.home.components.MUSIC_META_TAG
import io.github.seijikohara.femto.ui.home.components.MapConfig
import io.github.seijikohara.femto.ui.home.components.PanelVisibility
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The music card offering the last played track, idle and resuming, keeps the Playing card's layout on the dashboard
 * at every geometry and display size the dashboard goldens cover ([DashboardGeometries], plus the SMALL and LARGE
 * cases of DashboardScreenshotTest): the track coming and going never moves the card or the cards above it, and the
 * art tile and the meta block sit where the Playing card has them, with the app's name alone in the eyebrow and
 * "Last played" in the progress row's place. Each geometry is laid out at its own size inside
 * one window larger than all of them, as in RouteChoiceLayoutTest, and the state swaps in one composition, as on the
 * live dashboard.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w2000dp-h1920dp-mdpi")
class MusicCardLastPlayedLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private var geometry by mutableStateOf(DashboardGeometries.all.first())
    private var uiScale by mutableStateOf(UiScale.MEDIUM)
    private var musicState by mutableStateOf(DashboardFixtures.state.musicState)
    private var showAlbum by mutableStateOf(true)
    private var showArt by mutableStateOf(true)

    @Test
    fun `the last played card keeps the playing card's bounds at every dashboard geometry`() {
        setDashboard()
        assertEquals(emptyList(), failuresAcrossCases(listOf(MUSIC_CARD_TAG)))
    }

    @Test
    fun `the last played card puts its art and meta where the playing card does at every dashboard geometry`() {
        setDashboard()
        assertEquals(emptyList(), failuresAcrossCases(listOf(MUSIC_ART_TAG, MUSIC_META_TAG)))
    }

    @Test
    fun `the last played card puts its play button where the playing card's play pause sits at every geometry`() {
        setDashboard()
        val failures =
            cases.flatMap { (candidate, scale) ->
                geometry = candidate
                uiScale = scale
                musicState = DashboardFixtures.state.musicState
                val playPause = boundsOf(hasContentDescription(PLAY_PAUSE))
                listOf(fakeLastPlayedState(), fakeLastPlayedState(resuming = true)).mapNotNull { state ->
                    musicState = state
                    val resume = boundsOf(hasContentDescription(RESUME, substring = true))
                    "${candidate.id} $scale: resuming=${state.resuming} resume $resume, play / pause $playPause"
                        .takeIf { resume != playPause || playPause == null }
                }
            }
        assertEquals(emptyList(), failures)
    }

    @Test
    fun `the last played card puts its art and meta where the playing card does with the art or album hidden`() {
        setDashboard()
        geometry = DashboardGeometries.all.single { it.qualifiers == DashboardGeometries.HEAD_UNIT_853X512 }
        val failures =
            listOf(false to true, true to false, false to false).flatMap { (art, album) ->
                showArt = art
                showAlbum = album
                layoutFailures("${geometry.id} showArt=$art showAlbum=$album", listOf(MUSIC_ART_TAG, MUSIC_META_TAG))
            }
        assertEquals(emptyList(), failures)
    }

    @Test
    fun `the last played card's reserved album line and progress row stay out of the semantics tree`() {
        setDashboard()
        geometry = DashboardGeometries.all.single { it.qualifiers == DashboardGeometries.HEAD_UNIT_853X512 }
        // Anti-vacuous guards: on the Playing card the same matchers find the em dash of a track without an album and
        // the progress row, so an empty match on the last played card means the nodes are absent, not unmatched.
        musicState = MusicCardState.Playing(fakeNowPlaying(album = null))
        assertTrue(cardNodeCount(hasText(EM_DASH)) > 0, "the playing card shows no em dash for a missing album")
        assertTrue(cardNodeCount(hasProgress) > 0, "the playing card exposes no progress semantics")

        val leaks =
            listOf(fakeLastPlayedState(), fakeLastPlayedState(resuming = true)).flatMap { state ->
                musicState = state
                listOf("em dash" to hasText(EM_DASH), "0:00" to hasText(ZERO_TIME), "progress" to hasProgress)
                    .mapNotNull { (name, matcher) ->
                        cardNodeCount(matcher)
                            .takeIf { it > 0 }
                            ?.let { "resuming=${state.resuming}: $it $name node(s)" }
                    }
            }
        assertEquals(emptyList(), leaks)
    }

    @Test
    fun `the last played card shows the last played caption and the playing card does not`() {
        setDashboard()
        geometry = DashboardGeometries.all.single { it.qualifiers == DashboardGeometries.HEAD_UNIT_853X512 }
        musicState = DashboardFixtures.state.musicState
        val playing = cardNodeCount(hasText(LAST_PLAYED))
        val lastPlayed =
            listOf(fakeLastPlayedState(), fakeLastPlayedState(resuming = true)).map { state ->
                musicState = state
                cardNodeCount(hasText(LAST_PLAYED))
            }
        assertEquals(listOf(0, 1, 1), listOf(playing) + lastPlayed)
    }

    @Test
    fun `the last played card's eyebrow is the app's name alone`() {
        setDashboard()
        geometry = DashboardGeometries.all.single { it.qualifiers == DashboardGeometries.HEAD_UNIT_853X512 }
        musicState = fakeLastPlayedState()
        val texts = cardTexts()
        // Anti-vacuous guard: the texts read are the card's own.
        assertTrue(fakeLastPlayedState().track.title in texts, "the card's texts lack the title: $texts")
        assertEquals(
            listOf(FAKE_MUSIC_APP_NAME.uppercase()),
            texts.filter { it.contains(FAKE_MUSIC_APP_NAME, ignoreCase = true) },
        )
    }

    @Test
    fun `the last played card exposes the artist in the merged tree`() {
        setDashboard()
        geometry = DashboardGeometries.all.single { it.qualifiers == DashboardGeometries.HEAD_UNIT_853X512 }
        musicState = fakeLastPlayedState()
        val artist = checkNotNull(fakeLastPlayedState().track.artist)
        // Anti-vacuous guard: the same matcher finds the title, so the card's merged nodes are the ones searched.
        assertEquals(1, cardNodeCount(hasText(fakeLastPlayedState().track.title)), "the card shows no title node")
        assertEquals(1, cardNodeCount(hasText(artist)), "the card exposes no node with the artist")
    }

    private fun setDashboard() =
        rule.setContent {
            Box(modifier = Modifier.size(geometry.widthDp.dp, geometry.heightDp.dp)) {
                FemtoTheme(uiScale = uiScale) {
                    DashboardScaffold(
                        uiState = DashboardFixtures.state.copy(musicState = musicState),
                        is24Hour = true,
                        showClockSeconds = true,
                        speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                        temperatureUnit = TemperatureUnit.CELSIUS,
                        mapConfig = MapConfig(),
                        panels = PanelVisibility(),
                        glassConfig = GlassConfig(),
                        onAction = {},
                        modifier = Modifier.fillMaxSize(),
                        musicShowAlbum = showAlbum,
                        musicShowArt = showArt,
                        clock = DashboardFixtures.fixedClock,
                        mapSurface = { _, _ -> Box(Modifier.fillMaxSize()) },
                    )
                }
            }
        }

    private fun failuresAcrossCases(tags: List<String>): List<String> =
        cases.flatMap { (candidate, scale) ->
            geometry = candidate
            uiScale = scale
            layoutFailures("${candidate.id} $scale", tags)
        }

    private fun layoutFailures(
        case: String,
        tags: List<String>,
    ): List<String> {
        val playing = boundsWith(DashboardFixtures.state.musicState, tags)
        // Anti-vacuous guard: a node that never rendered would equal itself everywhere. The art tile is absent by design
        // while the art is hidden.
        val rendered =
            tags.filterNot { it == MUSIC_ART_TAG && !showArt }.mapNotNull { tag ->
                "$case: the playing card's $tag did not render (${playing[tag]})"
                    .takeIf { playing[tag].let { bounds -> bounds == null || bounds.height <= 0.dp } }
            }
        return rendered +
            listOf(fakeLastPlayedState(), fakeLastPlayedState(resuming = true)).flatMap { state ->
                val lastPlayed = boundsWith(state, tags)
                tags.mapNotNull { tag ->
                    "$case: resuming=${state.resuming} $tag ${lastPlayed[tag]}, playing ${playing[tag]}"
                        .takeIf { lastPlayed[tag] != playing[tag] }
                }
            }
    }

    // The bounds of each tagged node with the card in [state]; null for a tag with no node.
    private fun boundsWith(
        state: MusicCardState,
        tags: List<String>,
    ): Map<String, DpRect?> {
        musicState = state
        rule.waitForIdle()
        return tags.associateWith { tag ->
            rule
                .onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .takeIf { it.isNotEmpty() }
                ?.let { rule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot() }
        }
    }

    // Every text inside the music card, in the merged tree a screen reader reads.
    private fun cardTexts(): List<String> {
        rule.waitForIdle()
        return rule
            .onAllNodes(hasAnyAncestor(hasTestTag(MUSIC_CARD_TAG)))
            .fetchSemanticsNodes()
            .flatMap { node ->
                node.config
                    .getOrNull(SemanticsProperties.Text)
                    .orEmpty()
                    .map { it.text }
            }
    }

    // The bounds of the single node inside the music card matching [matcher]; null when there is none.
    private fun boundsOf(matcher: SemanticsMatcher): DpRect? {
        rule.waitForIdle()
        val inCard = matcher and hasAnyAncestor(hasTestTag(MUSIC_CARD_TAG))
        return rule
            .onAllNodes(inCard, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .takeIf { it.isNotEmpty() }
            ?.let { rule.onNode(inCard, useUnmergedTree = true).getUnclippedBoundsInRoot() }
    }

    // How many nodes inside the music card match [matcher], with the card in its current state. The merged tree is the
    // one accessibility services announce: cleared semantics are gone from it, while the unmerged tree still lists the
    // nodes beneath a clearAndSetSemantics.
    private fun cardNodeCount(matcher: SemanticsMatcher): Int {
        rule.waitForIdle()
        return rule
            .onAllNodes(matcher and hasAnyAncestor(hasTestTag(MUSIC_CARD_TAG)))
            .fetchSemanticsNodes()
            .size
    }

    private companion object {
        const val EM_DASH = "—"

        // The caption the last played card shows in the progress row's space.
        const val LAST_PLAYED = "Last played"

        // The content descriptions of the transport row's play / pause and of the resume button.
        const val PLAY_PAUSE = "Play / pause"
        const val RESUME = "Resume "

        // The progress row's elapsed and total time for a track with no position.
        const val ZERO_TIME = "0:00"

        val hasProgress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

        // Every geometry at the default display size, and the two display-size
        // opt-ins the dashboard goldens render.
        val cases: List<Pair<DashboardGeometry, UiScale>> =
            DashboardGeometries.all.map { it to UiScale.MEDIUM } +
                listOf(
                    DashboardGeometries.all.single { it.qualifiers == DashboardGeometries.PHONE_LANDSCAPE_915X412 } to
                        UiScale.SMALL,
                    DashboardGeometries.all.single { it.qualifiers == DashboardGeometries.HEAD_UNIT_853X512 } to
                        UiScale.LARGE,
                )
    }
}
