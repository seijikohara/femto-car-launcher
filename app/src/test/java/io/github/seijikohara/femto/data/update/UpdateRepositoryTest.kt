package io.github.seijikohara.femto.data.update

import io.github.seijikohara.femto.data.location.MOTION_VERDICT_TIMEOUT_MS
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.testfixtures.FAKE_FEED_BASE
import io.github.seijikohara.femto.testfixtures.FakeApkBody
import io.github.seijikohara.femto.testfixtures.FakeApkInstaller
import io.github.seijikohara.femto.testfixtures.FakeClock
import io.github.seijikohara.femto.testfixtures.FakeInstallConfirmation
import io.github.seijikohara.femto.testfixtures.FakeUpdateFeed
import io.github.seijikohara.femto.testfixtures.FakeUpdateSettingsStore
import io.github.seijikohara.femto.testfixtures.HeldDispatcher
import io.github.seijikohara.femto.testfixtures.fakeUpdateManifest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val feed = FakeUpdateFeed()
    private val store = FakeUpdateSettingsStore()
    private val installer = FakeApkInstaller()
    private val clock = FakeClock(NOW)
    private val newer = fakeUpdateManifest(NEWER)

    // Parked unless a test says otherwise; [motionSource] swaps in a source
    // that behaves differently from any verdict.
    private val motion = MutableStateFlow(VehicleMotion.PARKED)
    private var motionSource: Flow<VehicleMotion> = motion

    // A getter: the rule creates its folder only once each test starts.
    private val stagingDir: File get() = File(tempFolder.root, "update")

    // --- start --------------------------------------------------------------

    @Test
    fun `a disabled build stays Disabled and never touches the feed`() =
        runTest {
            feed.latestResult = FeedResult.Found(newer)
            val repository = repository(enabled = false)

            repository.checkNow()
            repository.maybeAutoCheck(online = true)
            repository.download()
            repository.install()
            runCurrent()

            assertEquals(UpdateState.Disabled, repository.state.value)
            assertEquals(emptyList(), feed.latestCalls)
            assertEquals(emptyList(), feed.downloads)
        }

    @Test
    fun `starts Idle with the last recorded attempt`() =
        runTest {
            val lastAttempt = NOW - Duration.ofHours(3)
            store.setLastCheckAttemptAt(lastAttempt.toEpochMilli())

            val repository = startedRepository()

            assertEquals(UpdateState.Idle(lastAttempt), repository.state.value)
        }

    // --- checking -----------------------------------------------------------

    @Test
    fun `checkNow passes through Checking to Available when the feed carries a newer build`() =
        runTest {
            feed.latestResult = FeedResult.Found(newer)
            val gate = feed.gateLatest()
            val repository = startedRepository()

            repository.checkNow()
            runCurrent()
            assertEquals(UpdateState.Checking, repository.state.value)

            gate.complete(Unit)
            runCurrent()
            assertEquals(UpdateState.Available(newer), repository.state.value)
        }

    @Test
    fun `a check that finds a newer build records it as the offer`() =
        runTest {
            availableRepository()

            assertEquals(newer, store.current.offer)
        }

    @Test
    fun `checkNow reports UpToDate when the feed carries the running build`() =
        runTest {
            feed.latestResult = FeedResult.Found(fakeUpdateManifest(CURRENT))
            val repository = startedRepository()

            repository.checkNow()
            runCurrent()

            assertEquals(UpdateState.UpToDate, repository.state.value)
        }

    @Test
    fun `checkNow reads the feed of the build's own channel`() =
        runTest {
            val repository = startedRepository(channel = UpdateChannel.NIGHTLY)

            repository.checkNow()
            runCurrent()

            assertEquals(listOf(UpdateChannel.NIGHTLY), feed.latestCalls)
        }

    @Test
    fun `checkNow records the attempt before the request leaves`() =
        runTest {
            feed.gateLatest()
            val repository = startedRepository()

            repository.checkNow()
            runCurrent()

            // The request is still in flight, yet the attempt already counts.
            assertEquals(NOW.toEpochMilli(), store.current.lastCheckAttemptAt)
        }

    @Test
    fun `a missing manifest leaves the updater Idle rather than Failed`() =
        runTest {
            feed.latestResult = FeedResult.NoInformation
            val repository = startedRepository()

            repository.checkNow()
            runCurrent()

            assertEquals(UpdateState.Idle(NOW), repository.state.value)
        }

    @Test
    fun `a manifest for the other channel is no information`() =
        runTest {
            feed.latestResult = FeedResult.Found(fakeUpdateManifest(NEWER, channel = UpdateChannel.NIGHTLY))
            val repository = startedRepository(channel = UpdateChannel.STABLE)

            repository.checkNow()
            runCurrent()

            assertEquals(UpdateState.Idle(NOW), repository.state.value)
        }

    @Test
    fun `a manifest of another schema version is no information`() =
        runTest {
            feed.latestResult =
                FeedResult.Found(fakeUpdateManifest(NEWER, schemaVersion = SUPPORTED_MANIFEST_SCHEMA_VERSION + 1))
            val repository = startedRepository()

            repository.checkNow()
            runCurrent()

            assertEquals(UpdateState.Idle(NOW), repository.state.value)
        }

    @Test
    fun `a manual check that cannot reach the feed fails with the reason`() =
        runTest {
            val repository = startedRepository()

            listOf(UpdateFailure.NETWORK, UpdateFailure.RATE_LIMITED).forEach { reason ->
                feed.latestResult = FeedResult.Unavailable(reason)
                repository.checkNow()
                runCurrent()

                assertEquals(UpdateState.Failed(reason, manifest = null), repository.state.value)
            }
        }

    @Test
    fun `a manual check that fails keeps the offer the user already had`() =
        runTest {
            val repository = availableRepository()
            feed.latestResult = FeedResult.Unavailable(UpdateFailure.NETWORK)

            repository.checkNow()
            runCurrent()

            assertEquals(UpdateState.Failed(UpdateFailure.NETWORK, newer), repository.state.value)
        }

    @Test
    fun `two concurrent checks send one request`() =
        runTest {
            feed.gateLatest()
            val repository = startedRepository()

            repository.checkNow()
            repository.checkNow()
            runCurrent()

            assertEquals(1, feed.latestCalls.size)
        }

    @Test
    fun `a check never interrupts a download`() =
        runTest {
            val repository = availableRepository()
            feed.gateDownload()
            repository.download()
            runCurrent()
            val requests = feed.latestCalls.size

            repository.checkNow()
            runCurrent()

            // The check never reached the feed.
            assertEquals(requests, feed.latestCalls.size)
            assertIs<UpdateState.Downloading>(repository.state.value)
        }

    // --- automatic checks ---------------------------------------------------

    @Test
    fun `an automatic check runs when online and never checked before`() =
        runTest {
            feed.latestResult = FeedResult.Found(newer)
            val repository = startedRepository()

            repository.maybeAutoCheck(online = true)
            runCurrent()

            assertEquals(UpdateState.Available(newer), repository.state.value)
        }

    @Test
    fun `an automatic check waits while offline`() =
        runTest {
            val repository = startedRepository()

            repository.maybeAutoCheck(online = false)
            runCurrent()

            assertEquals(emptyList(), feed.latestCalls)
        }

    @Test
    fun `an automatic check never runs with automatic checks turned off`() =
        runTest {
            store.setAutoCheck(false)
            val repository = startedRepository()

            repository.maybeAutoCheck(online = true)
            runCurrent()

            assertEquals(emptyList(), feed.latestCalls)
        }

    @Test
    fun `an automatic check waits a full day after the last attempt`() =
        runTest {
            store.setLastCheckAttemptAt(NOW.toEpochMilli())
            val repository = startedRepository()

            clock.now = NOW + DAY - Duration.ofMillis(1)
            repository.maybeAutoCheck(online = true)
            runCurrent()
            assertEquals(emptyList(), feed.latestCalls)

            clock.now = NOW + DAY
            repository.maybeAutoCheck(online = true)
            runCurrent()
            assertEquals(1, feed.latestCalls.size)
        }

    @Test
    fun `an automatic check treats a clock behind the last attempt as due`() =
        runTest {
            // The attempt was stamped by a clock that has since been set back
            // (an AI box boots on a wrong clock until NTP corrects it).
            store.setLastCheckAttemptAt((NOW + Duration.ofHours(1)).toEpochMilli())
            val repository = startedRepository()

            repository.maybeAutoCheck(online = true)
            runCurrent()

            assertEquals(1, feed.latestCalls.size)
        }

    @Test
    fun `evaluations during and after a check send no second request`() =
        runTest {
            val gate = feed.gateLatest()
            val repository = startedRepository()

            repository.maybeAutoCheck(online = true)
            repository.maybeAutoCheck(online = true)
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            // The attempt is on record now, so the next tick is not due either.
            repository.maybeAutoCheck(online = true)
            runCurrent()

            assertEquals(1, feed.latestCalls.size)
        }

    @Test
    fun `a failed automatic check stays quiet`() =
        runTest {
            feed.latestResult = FeedResult.Unavailable(UpdateFailure.NETWORK)
            val repository = startedRepository()

            repository.maybeAutoCheck(online = true)
            runCurrent()

            assertEquals(UpdateState.Idle(NOW), repository.state.value)
        }

    @Test
    fun `an automatic check that learns nothing keeps the offer the user saw`() =
        runTest {
            val repository = availableRepository()

            listOf(FeedResult.Unavailable(UpdateFailure.NETWORK), FeedResult.NoInformation).forEach { result ->
                clock.now += DAY
                feed.latestResult = result
                repository.maybeAutoCheck(online = true)
                runCurrent()

                assertEquals(UpdateState.Available(newer), repository.state.value)
            }
            // Both automatic checks really ran; the offer survived them.
            assertEquals(3, feed.latestCalls.size)
        }

    @Test
    fun `automatic checks stay daily when the store loses the attempt`() =
        runTest {
            val forgetful = FakeUpdateSettingsStore(dropsAttemptWrites = true)
            val repository = startedRepository(store = forgetful)

            repository.maybeAutoCheck(online = true)
            runCurrent()
            clock.now += Duration.ofMinutes(1)
            repository.maybeAutoCheck(online = true)
            runCurrent()

            assertEquals(1, feed.latestCalls.size)
        }

    @Test
    fun `an automatic check reads the attempt a concurrent one recorded`() =
        runTest {
            val repository = startedRepository()
            val gate = store.gateReads()

            // Both evaluations would read the same snapshot, without an attempt.
            repository.maybeAutoCheck(online = true)
            repository.maybeAutoCheck(online = true)
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertEquals(1, feed.latestCalls.size)
        }

    @Test
    fun `an automatic check behind a verified download offers a strictly newer build`() =
        runTest {
            val repository = readyRepository()
            val newest = fakeUpdateManifest(NEWER + 1)
            feed.latestResult = FeedResult.Found(newest)
            clock.now += DAY

            repository.maybeAutoCheck(online = true)
            runCurrent()

            assertEquals(UpdateState.Available(newest), repository.state.value)
        }

    @Test
    fun `an automatic check behind a verified download keeps it for the same build`() =
        runTest {
            val repository = readyRepository()
            val ready = repository.state.value
            val requests = feed.latestCalls.size
            clock.now += DAY

            repository.maybeAutoCheck(online = true)
            runCurrent()

            assertEquals(ready, repository.state.value)
            // The check did run; the feed just had nothing newer.
            assertEquals(requests + 1, feed.latestCalls.size)
        }

    @Test
    fun `a quiet check behind a verified download leaves an install started meanwhile alone`() =
        runTest {
            val repository = readyRepository()
            val ready = repository.state.value
            feed.latestResult = FeedResult.Found(fakeUpdateManifest(NEWER + 1))
            val gate = feed.gateLatest()
            clock.now += DAY
            repository.maybeAutoCheck(online = true)
            runCurrent()
            // The check is in flight, and the user still sees the verified download.
            assertEquals(ready, repository.state.value)

            repository.install()
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertEquals(UpdateState.Installing(newer, SESSION), repository.state.value)
        }

    @Test
    fun `an automatic check behind an offer leaves it on screen while it runs`() =
        runTest {
            // The request can take up to the client's timeouts on a weak hotspot.
            val repository = availableRepository()
            feed.gateLatest()
            clock.now += DAY

            repository.maybeAutoCheck(online = true)
            runCurrent()

            assertEquals(UpdateState.Available(newer), repository.state.value)
        }

    @Test
    fun `an automatic check behind a failed download leaves the retry on screen while it runs`() =
        runTest {
            val repository = availableRepository()
            feed.downloadFailure = UpdateFailure.NETWORK
            repository.download()
            runCurrent()
            val failed = repository.state.value
            feed.gateLatest()
            clock.now += DAY

            repository.maybeAutoCheck(online = true)
            runCurrent()

            assertEquals(failed, repository.state.value)
        }

    @Test
    fun `a download tapped while an automatic check runs behind the offer goes ahead`() =
        runTest {
            val repository = availableRepository()
            val gate = feed.gateLatest()
            clock.now += DAY
            repository.maybeAutoCheck(online = true)
            runCurrent()

            repository.download()
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertIs<UpdateState.Ready>(repository.state.value)
        }

    @Test
    fun `an automatic check behind an offer replaces it with a newer build`() =
        runTest {
            val repository = availableRepository()
            val newest = fakeUpdateManifest(NEWER + 1)
            feed.latestResult = FeedResult.Found(newest)
            clock.now += DAY

            repository.maybeAutoCheck(online = true)
            runCurrent()

            assertEquals(UpdateState.Available(newest), repository.state.value)
        }

    // --- downloading --------------------------------------------------------

    @Test
    fun `download passes through Downloading to Ready with the verified file`() =
        runTest {
            val repository = availableRepository()
            val gate = feed.gateDownload()

            repository.download()
            runCurrent()
            assertEquals(UpdateState.Downloading(newer, FakeUpdateFeed.HALF_PROGRESS), repository.state.value)

            gate.complete(Unit)
            runCurrent()
            assertEquals(UpdateState.Ready(newer, feed.downloads.single().target), repository.state.value)
        }

    @Test
    fun `download fetches the APK the manifest names`() =
        runTest {
            val repository = availableRepository()

            repository.download()
            runCurrent()

            assertEquals(newer.apk.url, feed.downloads.single().url)
        }

    @Test
    fun `a size mismatch deletes the download and fails verification`() =
        runTest {
            // The hash is right for the served bytes, so only the size can tell.
            val repository = availableRepository(fakeUpdateManifest(NEWER, size = FakeApkBody.size + 1L))

            repository.download()
            runCurrent()

            assertEquals(UpdateState.Failed(UpdateFailure.VERIFY, manifest = null), repository.state.value)
            assertFalse(
                feed.downloads
                    .single()
                    .target
                    .exists(),
            )
        }

    @Test
    fun `a hash mismatch deletes the download and fails verification`() =
        runTest {
            val repository = availableRepository()
            // The same length with one byte changed, so only the hash can tell.
            feed.downloadBody = FakeApkBody.withFirstByteFlipped()

            repository.download()
            runCurrent()

            assertEquals(UpdateState.Failed(UpdateFailure.VERIFY, manifest = null), repository.state.value)
            assertFalse(
                feed.downloads
                    .single()
                    .target
                    .exists(),
            )
        }

    @Test
    fun `a failed transfer keeps the offer so the download can be retried`() =
        runTest {
            val repository = availableRepository()
            feed.downloadFailure = UpdateFailure.NETWORK
            repository.download()
            runCurrent()
            assertEquals(UpdateState.Failed(UpdateFailure.NETWORK, newer), repository.state.value)

            feed.downloadFailure = null
            repository.download()
            runCurrent()
            assertIs<UpdateState.Ready>(repository.state.value)
        }

    @Test
    fun `a download the device cannot store fails as storage, not as the network`() =
        runTest {
            val repository = availableRepository()
            feed.downloadFailure = UpdateFailure.STORAGE

            repository.download()
            runCurrent()

            assertEquals(UpdateState.Failed(UpdateFailure.STORAGE, newer), repository.state.value)
        }

    @Test
    fun `a download reads the manifest again and fetches the newer build it names`() =
        runTest {
            // The nightly republished since the check: same asset URL, newer build.
            val repository = availableRepository()
            val newest = fakeUpdateManifest(NEWER + 1)
            feed.latestResult = FeedResult.Found(newest)

            repository.download()
            runCurrent()

            assertEquals(newest.apk.url, feed.downloads.single().url)
            assertEquals(UpdateState.Ready(newest, feed.downloads.single().target), repository.state.value)
        }

    @Test
    fun `a newer build found right before a download becomes the offer`() =
        runTest {
            val repository = availableRepository()
            val newest = fakeUpdateManifest(NEWER + 1)
            feed.latestResult = FeedResult.Found(newest)

            repository.download()
            runCurrent()

            assertEquals(newest, store.current.offer)
        }

    @Test
    fun `a download whose re-read shows nothing newer drops the offer`() =
        runTest {
            val repository = availableRepository()
            feed.latestResult = FeedResult.Found(fakeUpdateManifest(CURRENT))

            repository.download()
            runCurrent()

            assertEquals(UpdateState.UpToDate, repository.state.value)
            assertEquals(emptyList(), feed.downloads)
        }

    @Test
    fun `a download whose re-read fails fetches the offered build`() =
        runTest {
            val repository = availableRepository()
            feed.latestResult = FeedResult.Unavailable(UpdateFailure.NETWORK)

            repository.download()
            runCurrent()

            assertEquals(newer.apk.url, feed.downloads.single().url)
        }

    @Test
    fun `a download whose re-read finds no manifest fetches the offered build`() =
        runTest {
            // The seconds in which the nightly job republishes its release.
            val repository = availableRepository()
            feed.latestResult = FeedResult.NoInformation

            repository.download()
            runCurrent()

            assertEquals(newer.apk.url, feed.downloads.single().url)
        }

    @Test
    fun `a download's re-read leaves the daily gate alone`() =
        runTest {
            val repository = availableRepository()
            clock.now += Duration.ofHours(1)

            repository.download()
            runCurrent()

            assertEquals(NOW.toEpochMilli(), store.current.lastCheckAttemptAt)
        }

    @Test
    fun `two concurrent downloads start one transfer`() =
        runTest {
            val repository = availableRepository()
            feed.gateDownload()

            repository.download()
            repository.download()
            runCurrent()

            assertEquals(1, feed.downloads.size)
        }

    @Test
    fun `download does nothing without an offer`() =
        runTest {
            val repository = startedRepository()

            repository.download()
            runCurrent()

            assertEquals(emptyList(), feed.downloads)
        }

    // --- installing ---------------------------------------------------------

    @Test
    fun `install records the pending version before the platform takes the session`() =
        runTest {
            var pendingAtCommit: Int? = null
            val observingInstaller =
                FakeApkInstaller(onCommit = { pendingAtCommit = store.current.pendingInstallVersionCode })
            val repository = readyRepository(installer = observingInstaller)

            repository.install()
            runCurrent()

            assertEquals(NEWER, pendingAtCommit)
        }

    @Test
    fun `install stages the verified file in a session and commits it`() =
        runTest {
            val repository = readyRepository()
            val ready = assertIs<UpdateState.Ready>(repository.state.value)

            repository.install()
            runCurrent()

            assertEquals(listOf(ready.file), installer.staged)
            assertEquals(listOf(SESSION), installer.committed)
            assertEquals(UpdateState.Installing(newer, SESSION), repository.state.value)
        }

    @Test
    fun `two concurrent installs hand the file over once`() =
        runTest {
            val repository = readyRepository()

            repository.install()
            repository.install()
            runCurrent()

            assertEquals(1, installer.staged.size)
        }

    @Test
    fun `install does nothing until a verified file is ready`() =
        runTest {
            val repository = availableRepository()

            repository.install()
            runCurrent()

            assertEquals(emptyList(), installer.staged)
        }

    @Test
    fun `install checks the staged file again and refuses a damaged one`() =
        runTest {
            val repository = readyRepository()
            val file = assertIs<UpdateState.Ready>(repository.state.value).file
            file.writeBytes(FakeApkBody.withFirstByteFlipped())

            repository.install()
            runCurrent()

            assertEquals(UpdateState.Failed(UpdateFailure.VERIFY, newer), repository.state.value)
            assertEquals(emptyList(), installer.staged)
            assertNull(store.current.pendingInstallVersionCode)
            assertFalse(file.exists())
        }

    @Test
    fun `a verdict that arrives before the commit returns settles the install`() =
        runTest {
            // The platform can report on a session while commit() is still on
            // its way back; the session must already be on the state by then.
            lateinit var repository: UpdateRepository
            val reportingInstaller = FakeApkInstaller(onCommit = { sessionId ->
                repository.onInstallCancelled(sessionId)
            })
            repository = readyRepository(installer = reportingInstaller)
            val ready = repository.state.value

            repository.install()
            runCurrent()

            assertEquals(ready, repository.state.value)
            assertNull(store.current.pendingInstallVersionCode)
        }

    @Test
    fun `a session the platform cannot open fails the install and clears the pending record`() =
        runTest {
            installer.stages = false
            val repository = readyRepository()

            repository.install()
            runCurrent()

            assertEquals(UpdateState.Failed(UpdateFailure.OTHER, newer, staged = true), repository.state.value)
            assertEquals(emptyList(), installer.committed)
            assertNull(store.current.pendingInstallVersionCode)
        }

    @Test
    fun `a commit the platform cannot take fails the install and clears the pending record`() =
        runTest {
            installer.commits = false
            val repository = readyRepository()

            repository.install()
            runCurrent()

            assertEquals(UpdateState.Failed(UpdateFailure.OTHER, newer, staged = true), repository.state.value)
            assertNull(store.current.pendingInstallVersionCode)
        }

    @Test
    fun `a platform refusal fails with its reason and keeps the download`() =
        runTest {
            val repository = readyRepository()
            val file = installedBy(repository)

            repository.onInstallFailed(SESSION, UpdateFailure.INSTALL_BLOCKED)
            runCurrent()

            assertEquals(
                UpdateState.Failed(UpdateFailure.INSTALL_BLOCKED, newer, staged = true),
                repository.state.value,
            )
            assertNull(store.current.pendingInstallVersionCode)
            assertTrue(file.exists())
        }

    @Test
    fun `a retry after a refused install reuses the staged file`() =
        runTest {
            val repository = installingRepository()
            repository.onInstallFailed(SESSION, UpdateFailure.INSTALL_BLOCKED)

            repository.download()
            runCurrent()

            assertIs<UpdateState.Ready>(repository.state.value)
            assertEquals(1, feed.downloads.size)
        }

    @Test
    fun `a signature conflict deletes the download and withdraws the offer`() =
        runTest {
            val repository = installingRepository()

            repository.onInstallFailed(SESSION, UpdateFailure.INSTALL_CONFLICT)
            runCurrent()

            // No offer left, so neither a retry nor the dock badge offers the
            // same APK: only a new check can find a build that installs.
            assertEquals(UpdateState.Failed(UpdateFailure.INSTALL_CONFLICT, manifest = null), repository.state.value)
            assertFalse(stagingDir.exists())
            assertNull(store.current.pendingInstallVersionCode)
        }

    @Test
    fun `a signature conflict is not offered again at the next start`() =
        runTest {
            installingRepository().onInstallFailed(SESSION, UpdateFailure.INSTALL_CONFLICT)
            runCurrent()

            val restarted = startedRepository()

            assertEquals(UpdateState.Idle(NOW), restarted.state.value)
        }

    @Test
    fun `a dismissed confirmation offers the same file again`() =
        runTest {
            val repository = readyRepository()
            val ready = repository.state.value
            repository.install()
            runCurrent()

            repository.onInstallCancelled(SESSION)
            runCurrent()

            assertEquals(ready, repository.state.value)
            assertNull(store.current.pendingInstallVersionCode)
        }

    @Test
    fun `a verdict on another session leaves the install alone`() =
        runTest {
            val repository = installingRepository()
            val installing = repository.state.value

            // An earlier attempt's session, abandoned when this one was staged, still reports.
            repository.onInstallFailed(SESSION + 1, UpdateFailure.INSTALL_CONFLICT)
            repository.onInstallCancelled(SESSION + 1)
            runCurrent()

            assertEquals(installing, repository.state.value)
            assertEquals(NEWER, store.current.pendingInstallVersionCode)
            assertTrue(stagingDir.exists())
        }

    // --- confirming ---------------------------------------------------------

    @Test
    fun `the platform's confirmation for this install is shown and kept`() =
        runTest {
            val repository = installingRepository()
            val confirmation = FakeInstallConfirmation()

            repository.onConfirmationRequested(SESSION, confirmation)
            runCurrent()

            assertEquals(1, confirmation.shows)
            assertEquals(UpdateState.Installing(newer, SESSION, confirmation), repository.state.value)
        }

    @Test
    fun `a confirmation for another session is neither shown nor kept`() =
        runTest {
            val repository = installingRepository()
            val installing = repository.state.value
            val confirmation = FakeInstallConfirmation()

            repository.onConfirmationRequested(SESSION + 1, confirmation)
            runCurrent()

            assertEquals(0, confirmation.shows)
            assertEquals(installing, repository.state.value)
        }

    @Test
    fun `a confirmation that arrives while the vehicle moves is kept but not shown`() =
        runTest {
            // A tap at a light staged the update while parked, and the car
            // pulled away before the platform asked.
            val repository = installingRepository()
            motion.value = VehicleMotion.MOVING
            val confirmation = FakeInstallConfirmation()

            repository.onConfirmationRequested(SESSION, confirmation)
            runCurrent()

            assertEquals(0, confirmation.shows)
            assertEquals(UpdateState.Installing(newer, SESSION, confirmation), repository.state.value)
        }

    @Test
    fun `a confirmation that arrives while no fix can tell the motion is shown`() =
        runTest {
            // No location grant or no fix yet: only a vehicle known to move holds it.
            val repository = installingRepository()
            motion.value = VehicleMotion.UNKNOWN
            val confirmation = FakeInstallConfirmation()

            repository.onConfirmationRequested(SESSION, confirmation)
            runCurrent()

            assertEquals(1, confirmation.shows)
        }

    @Test
    fun `a confirmation is shown once the motion source stays silent past the bound`() =
        runTest {
            // A stalled location stack must not hold the confirmation forever.
            motionSource = flow { awaitCancellation() }
            val repository = installingRepository()
            val confirmation = FakeInstallConfirmation()
            repository.onConfirmationRequested(SESSION, confirmation)
            runCurrent()

            advanceTimeBy(MOTION_VERDICT_TIMEOUT_MS)
            runCurrent()

            assertEquals(1, confirmation.shows)
        }

    @Test
    fun `a confirmation held while moving never shows on its own once the vehicle stops`() =
        runTest {
            // Parked also means stopped at the next light: a dialog popping up
            // there could meet the car pulling away again.
            val repository = installingRepository()
            motion.value = VehicleMotion.MOVING
            val confirmation = FakeInstallConfirmation()
            repository.onConfirmationRequested(SESSION, confirmation)
            runCurrent()

            motion.value = VehicleMotion.PARKED
            advanceUntilIdle()

            assertEquals(0, confirmation.shows)
        }

    @Test
    fun `a held confirmation shows at once on the first request to show it again`() =
        runTest {
            val repository = installingRepository()
            motion.value = VehicleMotion.MOVING
            val confirmation = FakeInstallConfirmation()
            repository.onConfirmationRequested(SESSION, confirmation)
            runCurrent()
            assertEquals(0, confirmation.shows)
            motion.value = VehicleMotion.PARKED

            // Well inside the re-show guard, which debounces only a dialog that went up.
            repository.install()
            runCurrent()

            assertEquals(1, confirmation.shows)
        }

    @Test
    fun `an earlier attempt's dialog does not hold off a held confirmation of the next one`() =
        runTest {
            val repository = installingRepository()
            repository.onConfirmationRequested(SESSION, FakeInstallConfirmation())
            runCurrent()
            // Declined: the same file is offered again, and the retry opens a new session.
            repository.onInstallCancelled(SESSION)
            runCurrent()
            repository.install()
            runCurrent()
            motion.value = VehicleMotion.MOVING
            val held = FakeInstallConfirmation()
            repository.onConfirmationRequested(SESSION + 1, held)
            runCurrent()
            assertEquals(0, held.shows)
            motion.value = VehicleMotion.PARKED

            // Still inside the guard of the first attempt's dialog.
            repository.install()
            runCurrent()

            assertEquals(1, held.shows)
        }

    @Test
    fun `a request to show again while the first show is on its way puts up one dialog`() =
        runTest {
            // A tap on "show again" while the platform's dialog is still coming up.
            val repository = installingRepository()
            val confirmation = FakeInstallConfirmation()

            repository.onConfirmationRequested(SESSION, confirmation)
            repository.install()
            runCurrent()

            assertEquals(1, confirmation.shows)
        }

    @Test
    fun `install while the platform waits for the user shows its confirmation again`() =
        runTest {
            // Home over the system's confirmation leaves the session waiting and sends no verdict.
            val repository = installingRepository()
            val confirmation = FakeInstallConfirmation()
            repository.onConfirmationRequested(SESSION, confirmation)
            runCurrent()
            clock.now += RESHOW_GUARD

            repository.install()
            runCurrent()

            assertEquals(2, confirmation.shows)
            assertEquals(UpdateState.Installing(newer, SESSION, confirmation), repository.state.value)
            assertEquals(1, installer.staged.size)
        }

    @Test
    fun `a re-show sooner than the guard after the last show is ignored`() =
        runTest {
            // A tap while the dialog is still starting, or a double tap: two
            // dialogs for one session would destroy it once both are answered.
            val repository = installingRepository()
            val confirmation = FakeInstallConfirmation()
            repository.onConfirmationRequested(SESSION, confirmation)
            runCurrent()
            clock.now += RESHOW_GUARD - Duration.ofMillis(1)

            repository.install()
            runCurrent()

            assertEquals(1, confirmation.shows)
        }

    @Test
    fun `each re-show restarts the guard`() =
        runTest {
            val repository = installingRepository()
            val confirmation = FakeInstallConfirmation()
            repository.onConfirmationRequested(SESSION, confirmation)
            runCurrent()
            clock.now += RESHOW_GUARD
            repository.install()
            runCurrent()

            clock.now += RESHOW_GUARD - Duration.ofMillis(1)
            repository.install()
            runCurrent()

            assertEquals(2, confirmation.shows)
        }

    @Test
    fun `a clock set back since the last show does not hold a re-show off`() =
        runTest {
            val repository = installingRepository()
            val confirmation = FakeInstallConfirmation()
            repository.onConfirmationRequested(SESSION, confirmation)
            runCurrent()
            clock.now -= Duration.ofHours(1)

            repository.install()
            runCurrent()

            assertEquals(2, confirmation.shows)
        }

    @Test
    fun `a confirmation the platform cannot start fails the install as blocked`() =
        runTest {
            // A locked-down ROM with its package installer disabled: the session
            // would otherwise wait for days, and the install with it.
            val repository = installingRepository()

            repository.onConfirmationRequested(SESSION, FakeInstallConfirmation(starts = false))
            runCurrent()

            assertEquals(
                UpdateState.Failed(UpdateFailure.INSTALL_BLOCKED, newer, staged = true),
                repository.state.value,
            )
            assertEquals(listOf(SESSION), installer.abandoned)
            assertNull(store.current.pendingInstallVersionCode)
        }

    @Test
    fun `a re-shown confirmation the platform cannot start fails the install as blocked`() =
        runTest {
            val repository = installingRepository()
            val confirmation = FakeInstallConfirmation()
            repository.onConfirmationRequested(SESSION, confirmation)
            runCurrent()
            confirmation.starts = false
            clock.now += RESHOW_GUARD

            repository.install()
            runCurrent()

            assertEquals(
                UpdateState.Failed(UpdateFailure.INSTALL_BLOCKED, newer, staged = true),
                repository.state.value,
            )
            assertEquals(listOf(SESSION), installer.abandoned)
        }

    @Test
    fun `install while the platform prepares its confirmation changes nothing`() =
        runTest {
            val repository = installingRepository()
            val installing = repository.state.value

            repository.install()
            runCurrent()

            assertEquals(installing, repository.state.value)
            assertEquals(1, installer.staged.size)
        }

    @Test
    fun `install after the platform dropped the session offers the verified file again`() =
        runTest {
            val repository = readyRepository()
            val ready = repository.state.value
            repository.install()
            runCurrent()
            val confirmation = FakeInstallConfirmation()
            repository.onConfirmationRequested(SESSION, confirmation)
            runCurrent()
            // The platform expired the session without a verdict reaching this process.
            installer.pending -= SESSION

            repository.install()
            runCurrent()

            assertEquals(ready, repository.state.value)
            assertNull(store.current.pendingInstallVersionCode)
            assertEquals(1, confirmation.shows)
        }

    @Test
    fun `the file offered again after a dropped session installs in a new session`() =
        runTest {
            val repository = installingRepository()
            installer.pending -= SESSION
            repository.install()
            runCurrent()

            repository.install()
            runCurrent()

            assertEquals(listOf(SESSION, SESSION + 1), installer.committed)
            assertEquals(UpdateState.Installing(newer, SESSION + 1), repository.state.value)
            assertEquals(NEWER, store.current.pendingInstallVersionCode)
        }

    // --- the next start -----------------------------------------------------

    @Test
    fun `the successor of an install announces the version it runs`() =
        runTest {
            installedBy(readyRepository())

            val successor = startedRepository(currentVersionCode = NEWER)

            assertEquals(runningName(NEWER), successor.updatedTo.value)
            assertNull(store.current.pendingInstallVersionCode)
        }

    @Test
    fun `the successor of an install deletes the installed download`() =
        runTest {
            val file = installedBy(readyRepository())

            startedRepository(currentVersionCode = NEWER)

            assertFalse(file.exists())
        }

    @Test
    fun `an update is announced once`() =
        runTest {
            installedBy(readyRepository())
            val successor = startedRepository(currentVersionCode = NEWER)

            successor.acknowledgeUpdatedTo()
            val nextStart = startedRepository(currentVersionCode = NEWER)

            assertNull(successor.updatedTo.value)
            assertNull(nextStart.updatedTo.value)
        }

    @Test
    fun `a pending install the running build has not reached returns to Ready`() =
        runTest {
            val file = installedBy(readyRepository())

            val restarted = startedRepository(currentVersionCode = CURRENT)

            assertEquals(UpdateState.Ready(newer, file), restarted.state.value)
            assertTrue(file.exists())
        }

    @Test
    fun `a verified download survives a restart`() =
        runTest {
            val ready = readyRepository().state.value

            val restarted = startedRepository()

            assertEquals(ready, restarted.state.value)
        }

    @Test
    fun `a start without a pending install announces nothing`() =
        runTest {
            readyRepository()

            val restarted = startedRepository(currentVersionCode = NEWER)

            assertNull(restarted.updatedTo.value)
        }

    @Test
    fun `a download the running build already reached is deleted at start`() =
        runTest {
            val file = assertIs<UpdateState.Ready>(readyRepository().state.value).file

            val restarted = startedRepository(currentVersionCode = NEWER)

            assertEquals(UpdateState.Idle(NOW), restarted.state.value)
            assertFalse(file.exists())
        }

    @Test
    fun `the successor of a newer install also clears the record and the download`() =
        runTest {
            val file = installedBy(readyRepository())

            // Something newer than the pending build landed (e.g. sideloaded).
            val successor = startedRepository(currentVersionCode = NEWER + 1)

            assertNull(store.current.pendingInstallVersionCode)
            assertFalse(file.exists())
            assertEquals(runningName(NEWER + 1), successor.updatedTo.value)
        }

    @Test
    fun `a cold start restores a staged download from its size without hashing it`() =
        runTest {
            val ready = readyRepository().state.value
            // Same size, one byte off: only a hash could tell, and a cold start
            // must not read ~45 MB to find out.
            assertIs<UpdateState.Ready>(ready).file.writeBytes(FakeApkBody.withFirstByteFlipped())

            val restarted = startedRepository()

            assertEquals(ready, restarted.state.value)
        }

    @Test
    fun `a staged download whose size changed is offered for download again at start`() =
        runTest {
            val file = assertIs<UpdateState.Ready>(readyRepository().state.value).file
            file.writeBytes(FakeApkBody.copyOf(FakeApkBody.size - 1))

            val restarted = startedRepository()

            assertEquals(UpdateState.Available(newer), restarted.state.value)
            assertFalse(file.exists())
        }

    @Test
    fun `a pending install whose APK the system trimmed is offered for download again`() =
        runTest {
            val file = installedBy(readyRepository())
            file.delete()

            val restarted = startedRepository()

            assertEquals(UpdateState.Available(newer), restarted.state.value)
        }

    @Test
    fun `a pending install with nothing left staged checks at the next evaluation`() =
        runTest {
            installedBy(readyRepository())
            // The system reclaimed the whole staging directory, and the offer's
            // own record is gone as well (a lost write, or a record from a
            // build that kept none).
            assertTrue(stagingDir.deleteRecursively())
            store.setOffer(null)
            val restarted = startedRepository()
            val requests = feed.latestCalls.size

            // The check that found the offer ran just now, so only the lost
            // offer makes this evaluation due.
            restarted.maybeAutoCheck(online = true)
            runCurrent()

            assertEquals(requests + 1, feed.latestCalls.size)
        }

    @Test
    fun `a check waits for the start-up reconciliation`() =
        runTest {
            val ready = readyRepository().state.value
            val requests = feed.latestCalls.size
            // Not started yet: the check below is queued behind the reconciliation.
            val restarted = repository()

            restarted.checkNow()
            runCurrent()

            // A restored download is not a resting state, so the check never claimed.
            assertEquals(ready, restarted.state.value)
            assertEquals(requests, feed.latestCalls.size)
        }

    @Test
    fun `an offer found before a restart is offered again`() =
        runTest {
            availableRepository()

            // The daily gate keeps the new process from checking again today.
            val restarted = startedRepository()

            assertEquals(UpdateState.Available(newer), restarted.state.value)
        }

    @Test
    fun `a download that failed verification is not offered again after a restart`() =
        runTest {
            // Corrupted in transit, or a nightly that moved on: only a new check can tell.
            val repository = availableRepository()
            feed.downloadBody = FakeApkBody.withFirstByteFlipped()
            repository.download()
            runCurrent()
            assertEquals(UpdateState.Failed(UpdateFailure.VERIFY, manifest = null), repository.state.value)

            val restarted = startedRepository()

            assertEquals(UpdateState.Idle(NOW), restarted.state.value)
        }

    @Test
    fun `an offer a manual check stopped showing is not offered again after a restart`() =
        runTest {
            val repository = availableRepository()
            feed.latestResult = FeedResult.NoInformation
            repository.checkNow()
            runCurrent()
            assertEquals(UpdateState.Idle(NOW), repository.state.value)

            val restarted = startedRepository()

            assertEquals(UpdateState.Idle(NOW), restarted.state.value)
        }

    @Test
    fun `an install the device blocked is still offered after a restart`() =
        runTest {
            // The failure keeps its offer, and so does the next start.
            val repository = installingRepository()
            repository.onConfirmationRequested(SESSION, FakeInstallConfirmation(starts = false))
            runCurrent()
            assertEquals(
                UpdateState.Failed(UpdateFailure.INSTALL_BLOCKED, newer, staged = true),
                repository.state.value,
            )

            val restarted = startedRepository()

            assertEquals(newer, assertIs<UpdateState.Ready>(restarted.state.value).manifest)
        }

    @Test
    fun `a persisted offer the running build has caught up with is dropped at start`() =
        runTest {
            availableRepository()

            val successor = startedRepository(currentVersionCode = NEWER)

            assertEquals(UpdateState.Idle(NOW), successor.state.value)
            assertNull(store.current.offer)
        }

    @Test
    fun `a persisted offer this build cannot use is dropped at start`() =
        runTest {
            store.setOffer(fakeUpdateManifest(NEWER, channel = UpdateChannel.NIGHTLY))

            val restarted = startedRepository()

            assertEquals(UpdateState.Idle(lastAttemptAt = null), restarted.state.value)
            assertNull(store.current.offer)
        }

    @Test
    fun `a verified download wins over the persisted offer at start`() =
        runTest {
            val ready = readyRepository().state.value
            store.setOffer(fakeUpdateManifest(NEWER + 1))

            val restarted = startedRepository()

            assertEquals(ready, restarted.state.value)
        }

    @Test
    fun `a check that finds nothing newer clears the persisted offer`() =
        runTest {
            val repository = availableRepository()
            feed.latestResult = FeedResult.Found(fakeUpdateManifest(CURRENT))

            repository.checkNow()
            runCurrent()

            assertNull(store.current.offer)
        }

    @Test
    fun `a signature conflict clears the persisted offer`() =
        runTest {
            val repository = installingRepository()

            repository.onInstallFailed(SESSION, UpdateFailure.INSTALL_CONFLICT)
            runCurrent()

            assertNull(store.current.offer)
        }

    // --- discard and skip -----------------------------------------------------

    @Test
    fun `discard deletes the verified download and returns to the offer`() =
        runTest {
            val repository = readyRepository()

            repository.discard()
            runCurrent()

            assertEquals(UpdateState.Available(newer), repository.state.value)
            assertFalse(stagingDir.exists())
            assertEquals(newer, store.current.offer)
        }

    @Test
    fun `a discarded download is not restored at the next start`() =
        runTest {
            readyRepository().discard()
            runCurrent()

            val restarted = startedRepository()

            assertEquals(UpdateState.Available(newer), restarted.state.value)
        }

    @Test
    fun `discard deletes the download a refused install kept and clears the pending record`() =
        runTest {
            val repository = installingRepository()
            repository.onInstallFailed(SESSION, UpdateFailure.INSTALL_BLOCKED)
            runCurrent()

            repository.discard()
            runCurrent()

            assertEquals(UpdateState.Available(newer), repository.state.value)
            assertFalse(stagingDir.exists())
            assertNull(store.current.pendingInstallVersionCode)
        }

    @Test
    fun `a download tapped right after a discard waits for the deletion`() =
        runTest {
            val io = HeldDispatcher()
            val repository = readyRepository(ioDispatcher = io)
            // The discard's deletion and the download's look at the staged file
            // are both IO; run them newest first, as a busy IO pool may.
            io.hold = true

            repository.discard()
            repository.download()
            runCurrent()
            io.releaseNewestFirst()
            runCurrent()

            // Without the wait, the download would take the doomed file as
            // already staged, and offer an install of a file that is gone.
            assertTrue(assertIs<UpdateState.Ready>(repository.state.value).file.exists())
            assertEquals(2, feed.downloads.size)
        }

    @Test
    fun `discard leaves a download that is not staged alone`() =
        runTest {
            val repository = availableRepository()
            feed.downloadFailure = UpdateFailure.NETWORK
            repository.download()
            runCurrent()
            val failed = repository.state.value

            repository.discard()
            runCurrent()

            assertEquals(failed, repository.state.value)
        }

    @Test
    fun `skip records the offered build as skipped and keeps the offer`() =
        runTest {
            val repository = availableRepository()

            repository.skip()
            runCurrent()

            assertEquals(NEWER, store.current.skippedVersionCode)
            assertEquals(UpdateState.Available(newer), repository.state.value)
        }

    @Test
    fun `skip also discards a verified download of the build`() =
        runTest {
            val repository = readyRepository()

            repository.skip()
            runCurrent()

            assertEquals(NEWER, store.current.skippedVersionCode)
            assertEquals(UpdateState.Available(newer), repository.state.value)
            assertFalse(stagingDir.exists())
        }

    @Test
    fun `skip does nothing without an offer`() =
        runTest {
            val repository = startedRepository()

            repository.skip()
            runCurrent()

            assertNull(store.current.skippedVersionCode)
        }

    @Test
    fun `a newer build clears the skip`() =
        runTest {
            val repository = availableRepository()
            repository.skip()
            runCurrent()
            feed.latestResult = FeedResult.Found(fakeUpdateManifest(NEWER + 1))

            repository.checkNow()
            runCurrent()

            assertNull(store.current.skippedVersionCode)
        }

    @Test
    fun `a skip of the newer build tapped while its offer is recorded survives`() =
        runTest {
            val repository = availableRepository()
            repository.skip()
            runCurrent()
            val newest = fakeUpdateManifest(NEWER + 1)
            feed.latestResult = FeedResult.Found(newest)
            // The check's record of the newer offer reads the store and then
            // waits, holding the old skip it read.
            val gate = store.gateReads()
            repository.checkNow()
            runCurrent()

            repository.skip()
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertEquals(newest.versionCode, store.current.skippedVersionCode)
        }

    @Test
    fun `a check that finds the skipped build again keeps the skip`() =
        runTest {
            val repository = availableRepository()
            repository.skip()
            runCurrent()

            repository.checkNow()
            runCurrent()

            assertEquals(NEWER, store.current.skippedVersionCode)
        }

    @Test
    fun `the running build reaching a skipped build clears the skip at start`() =
        runTest {
            store.setSkippedVersionCode(NEWER)

            startedRepository(currentVersionCode = NEWER)

            assertNull(store.current.skippedVersionCode)
        }

    @Test
    fun `a skip of a build the running one has not reached survives a restart`() =
        runTest {
            store.setSkippedVersionCode(NEWER)

            startedRepository()

            assertEquals(NEWER, store.current.skippedVersionCode)
        }

    @Test
    fun `a signature conflict records the refused build`() =
        runTest {
            installingRepository().onInstallFailed(SESSION, UpdateFailure.INSTALL_CONFLICT)
            runCurrent()

            assertEquals(NEWER, store.current.refusedVersionCode)
        }

    @Test
    fun `a check after a signature conflict does not offer the refused build again`() =
        runTest {
            val repository = installingRepository()
            repository.onInstallFailed(SESSION, UpdateFailure.INSTALL_CONFLICT)
            runCurrent()

            repository.checkNow()
            runCurrent()

            assertEquals(UpdateState.UpToDate, repository.state.value)
        }

    @Test
    fun `a refused build is not offered again after a restart`() =
        runTest {
            installingRepository().onInstallFailed(SESSION, UpdateFailure.INSTALL_CONFLICT)
            runCurrent()
            val restarted = startedRepository()

            restarted.checkNow()
            runCurrent()

            assertEquals(UpdateState.UpToDate, restarted.state.value)
        }

    @Test
    fun `a build older than the refused one is not offered either`() =
        runTest {
            store.setRefusedVersionCode(NEWER + 1)
            feed.latestResult = FeedResult.Found(newer)
            val repository = startedRepository()

            repository.checkNow()
            runCurrent()

            assertEquals(UpdateState.UpToDate, repository.state.value)
        }

    @Test
    fun `a build newer than the refused one is offered`() =
        runTest {
            store.setRefusedVersionCode(NEWER)
            val newest = fakeUpdateManifest(NEWER + 1)
            feed.latestResult = FeedResult.Found(newest)
            val repository = startedRepository()

            repository.checkNow()
            runCurrent()

            assertEquals(UpdateState.Available(newest), repository.state.value)
        }

    @Test
    fun `a persisted offer of the refused build is dropped at start`() =
        runTest {
            store.setRefusedVersionCode(NEWER)
            store.setOffer(newer)

            val restarted = startedRepository()

            assertEquals(UpdateState.Idle(lastAttemptAt = null), restarted.state.value)
        }

    @Test
    fun `the running build reaching the refused one clears the record at start`() =
        runTest {
            store.setRefusedVersionCode(NEWER)

            startedRepository(currentVersionCode = NEWER)

            assertNull(store.current.refusedVersionCode)
        }

    // --- helpers ------------------------------------------------------------

    private fun TestScope.repository(
        currentVersionCode: Int = CURRENT,
        channel: UpdateChannel = UpdateChannel.STABLE,
        enabled: Boolean = true,
        installer: ApkInstaller = this@UpdateRepositoryTest.installer,
        store: UpdateSettingsStore = this@UpdateRepositoryTest.store,
        ioDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
    ): UpdateRepository =
        UpdateRepository(
            feed = feed,
            store = store,
            installer = installer,
            motion = motionSource,
            channel = channel,
            feedBase = FAKE_FEED_BASE,
            currentVersionCode = currentVersionCode,
            currentVersionName = runningName(currentVersionCode),
            enabled = enabled,
            clock = clock,
            scope = backgroundScope,
            stagingDir = stagingDir,
            ioDispatcher = ioDispatcher,
        )

    // A repository whose start-up reconciliation has run.
    private fun TestScope.startedRepository(
        currentVersionCode: Int = CURRENT,
        channel: UpdateChannel = UpdateChannel.STABLE,
        installer: ApkInstaller = this@UpdateRepositoryTest.installer,
        store: UpdateSettingsStore = this@UpdateRepositoryTest.store,
        ioDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
    ): UpdateRepository =
        repository(currentVersionCode, channel, installer = installer, store = store, ioDispatcher = ioDispatcher)
            .also { runCurrent() }

    // A started repository whose check (at NOW) found [manifest].
    private fun TestScope.availableRepository(
        manifest: UpdateManifest = newer,
        installer: ApkInstaller = this@UpdateRepositoryTest.installer,
        ioDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
    ): UpdateRepository =
        startedRepository(installer = installer, ioDispatcher = ioDispatcher).also { repository ->
            feed.latestResult = FeedResult.Found(manifest)
            repository.checkNow()
            runCurrent()
        }

    // A repository holding a verified download of [newer], reached through the
    // real check and download path.
    private fun TestScope.readyRepository(
        installer: ApkInstaller = this@UpdateRepositoryTest.installer,
        ioDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
    ): UpdateRepository =
        availableRepository(installer = installer, ioDispatcher = ioDispatcher).also { repository ->
            repository.download()
            runCurrent()
        }

    // Hand [repository]'s verified download to the platform; returns the staged file.
    private fun TestScope.installedBy(repository: UpdateRepository): File =
        assertIs<UpdateState.Ready>(repository.state.value).file.also {
            repository.install()
            runCurrent()
        }

    // A repository whose verified download is in the platform's hands as session [SESSION].
    private fun TestScope.installingRepository(): UpdateRepository = readyRepository().also { installedBy(it) }

    private fun ByteArray.withFirstByteFlipped(): ByteArray = copyOf().also { it[0] = (it[0] + 1).toByte() }

    private companion object {
        const val CURRENT = 26092401
        const val NEWER = 26092402

        // The session the fake installer opens first.
        const val SESSION = FakeApkInstaller.FIRST_SESSION_ID
        val RESHOW_GUARD: Duration = Duration.ofMillis(CONFIRMATION_RESHOW_GUARD_MS)
        val NOW: Instant = Instant.parse("2026-09-24T03:00:00Z")
        val DAY: Duration = Duration.ofHours(24)

        // Distinct from the manifest's versionName, so an announcement can only
        // name the running build.
        fun runningName(versionCode: Int): String = "running-$versionCode"
    }
}
