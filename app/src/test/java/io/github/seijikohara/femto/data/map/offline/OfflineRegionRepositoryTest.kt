package io.github.seijikohara.femto.data.map.offline

import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure.CORRUPT
import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure.GONE
import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure.NETWORK
import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure.NO_SPACE
import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure.OVER_LIMIT
import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure.STORAGE
import io.github.seijikohara.femto.data.map.offline.RegionDownloadPhase.FAILED
import io.github.seijikohara.femto.data.map.offline.RegionDownloadPhase.PAUSED
import io.github.seijikohara.femto.data.map.offline.RegionDownloadPhase.RUNNING
import io.github.seijikohara.femto.data.map.offline.RegionDownloadPhase.VERIFYING
import io.github.seijikohara.femto.data.map.offline.RegionOffer.Unavailable.Reason
import io.github.seijikohara.femto.testfixtures.FakeDownloadStop
import io.github.seijikohara.femto.testfixtures.FakeRegionFeed
import io.github.seijikohara.femto.testfixtures.RequestedRegionDownload
import io.github.seijikohara.femto.testfixtures.pendingDownloadRecord
import io.github.seijikohara.femto.testfixtures.region
import io.github.seijikohara.femto.testfixtures.resizeSparse
import io.github.seijikohara.femto.testfixtures.sampleRegionCatalog
import io.github.seijikohara.femto.testfixtures.sampleRegionIndex
import io.github.seijikohara.femto.testfixtures.sampleShortbreadSizes
import io.github.seijikohara.femto.testfixtures.savedRegionRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val NOW = 1_763_500_000_000L
private const val DATA_DATE = 1_763_400_000_000L
private const val SAVED_AT = 1_763_300_000_000L

// Background work: `advanceUntilIdle` stops once only the repository's scope has work left, so every test drives
// the repository with `runCurrent`.
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineRegionRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val catalog = sampleRegionCatalog()
    private val monaco = catalog.region("monaco")
    private val berlin = catalog.region("berlin")
    private val kanto = catalog.region("kanto")
    private val monacoFile = RemoteFile(sizeBytes = 1_728_512L, etag = "\"monaco-1\"", lastModifiedEpochMs = DATA_DATE)
    private val berlinFile = RemoteFile(sizeBytes = 49_217_536L, etag = "\"berlin-1\"", lastModifiedEpochMs = DATA_DATE)
    private val kantoFile = RemoteFile(sizeBytes = 862_351_360L, etag = "\"kanto-1\"", lastModifiedEpochMs = DATA_DATE)

    private val feed = FakeRegionFeed()
    private var limit = OfflineStorageLimit.NONE
    private var freeBytes = 100_000_000_000L

    // Getters: the rule creates its folder only once each test starts.
    private val root: File get() = File(folder.root, "offline_map")
    private val indexCache: File get() = File(folder.root, "cache/offline_map/index-v1.json")

    private fun files(root: File = this.root) = OfflineRegionFiles(root, usableBytes = { freeBytes })

    private fun TestScope.repository(
        root: File = this@OfflineRegionRepositoryTest.root,
        feed: FakeRegionFeed = this@OfflineRegionRepositoryTest.feed,
        check: RegionFileCheck = RegionFileCheck { file, expectedBytes -> file.length() == expectedBytes },
    ): OfflineRegionRepository =
        OfflineRegionRepository(
            files = files(root),
            feed = feed,
            check = check,
            limit = { limit },
            sizes = { sampleShortbreadSizes() },
            indexCache = indexCache,
            scope = backgroundScope,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            clock = { NOW },
        )

    // Answers the probe of [region] with [file]; the offer must be Ready.
    private suspend fun OfflineRegionRepository.readyOffer(
        region: CatalogRegion,
        file: RemoteFile,
        feed: FakeRegionFeed = this@OfflineRegionRepositoryTest.feed,
    ): RegionOffer.Ready {
        feed.probeAnswers[region.fileUrl] = ProbeResult.Found(file)
        return assertIs<RegionOffer.Ready>(offer(region))
    }

    // A saved region whose data file has exactly [sizeBytes]: the kept bytes are read from the disk.
    private fun seedSaved(
        region: CatalogRegion,
        sizeBytes: Long,
    ) {
        val files = files()
        files.partFile(region.id).resizeSparse(sizeBytes)
        val file = RemoteFile(sizeBytes, etag = null, lastModifiedEpochMs = DATA_DATE)
        assertTrue(files.commit(savedRegionRecord(region, file, SAVED_AT)))
    }

    // A download of Monaco that stopped with [partBytes] in its part.
    private suspend fun TestScope.pausedRepository(partBytes: Long = 1_000L): OfflineRegionRepository =
        repository().also { repository ->
            feed.stop = FakeDownloadStop(partBytes, TransferResult.Interrupted)
            repository.start(repository.readyOffer(monaco, monacoFile))
            runCurrent()
            assertEquals(
                PAUSED,
                repository.state.value.download
                    ?.phase,
            )
            feed.stop = null
        }

    private fun storedFiles(root: File = this.root): List<File> = root.walkTopDown().filter { it.isFile }.toList()

    private fun OfflineRegionRepository.phaseAndFailure() = state.value.download?.let { it.phase to it.failure }

    // --- construction and loading -------------------------------------------

    @Test
    fun `constructing the repository reads nothing`() =
        runTest {
            val repository = repository()
            runCurrent()

            assertFalse(root.exists())
            assertEquals(OfflineRegionsState(), repository.state.value)
            assertEquals(emptyList(), feed.indexEtags)
        }

    @Test
    fun `loading lists the saved regions by name with the storage figures`() =
        runTest {
            seedSaved(monaco, 1_000L)
            seedSaved(berlin, 2_000L)
            val repository = repository()

            repository.load()

            val state = repository.state.value
            assertEquals(listOf("Berlin", "Monaco"), state.saved.map { it.name })
            assertEquals(storedFiles().sumOf { it.length() }, state.usedBytes)
            assertEquals(freeBytes, state.freeBytes)
            assertTrue(state.loaded)
        }

    @Test
    fun `a pending download comes back paused after a restart`() =
        runTest {
            val gate = feed.gateDownload()
            val first = repository()
            first.start(first.readyOffer(monaco, monacoFile))
            runCurrent()

            val second = repository()
            second.load()

            assertEquals(
                RegionDownload("monaco", "Monaco", monacoFile.sizeBytes / 2, monacoFile.sizeBytes, PAUSED),
                second.state.value.download,
            )
            gate.cancel()
        }

    @Test
    fun `a restored download resumes with its record`() =
        runTest {
            assertTrue(files().writePending(pendingDownloadRecord(monaco, monacoFile)))
            files().partFile(monaco.id).resizeSparse(1_000L)
            val repository = repository()

            repository.resume()
            runCurrent()

            assertEquals(
                RequestedRegionDownload(monaco.fileUrl, files().partFile(monaco.id), monacoFile, heldBytes = 1_000L),
                feed.downloads.single(),
            )
        }

    @Test
    fun `a pending record of another version is not restored and a start writes its own`() =
        runTest {
            assertTrue(files().writePending(pendingDownloadRecord(monaco, monacoFile)))
            File(root, "pending.json").let { it.writeText(it.readText().replace("\"version\":1", "\"version\":2")) }
            val repository = repository()

            repository.load()
            assertNull(repository.state.value.download)

            repository.start(repository.readyOffer(berlin, berlinFile))
            runCurrent()
            assertEquals(
                listOf("berlin"),
                repository.state.value.saved
                    .map { it.id },
            )
        }

    @Test
    fun `a restart after the rename finishes the save`() =
        runTest {
            assertTrue(files().writePending(pendingDownloadRecord(monaco, monacoFile)))
            files().dataFile(monaco.id).resizeSparse(monacoFile.sizeBytes)
            val repository = repository()

            repository.load()

            assertEquals(
                listOf("monaco"),
                repository.state.value.saved
                    .map { it.id },
            )
            assertNull(repository.state.value.download)
        }

    @Test
    fun `a restart after the record was written shows the region saved and no download`() =
        runTest {
            files().partFile(monaco.id).resizeSparse(monacoFile.sizeBytes)
            assertTrue(files().commit(savedRegionRecord(monaco, monacoFile, SAVED_AT)))
            // The commit stopped before its last step: the pending record of the same file is still on disk.
            assertTrue(files().writePending(pendingDownloadRecord(monaco, monacoFile)))
            val repository = repository()

            repository.load()

            assertEquals(
                listOf("monaco"),
                repository.state.value.saved
                    .map { it.id },
            )
            assertNull(repository.state.value.download)
        }

    // --- the download -------------------------------------------------------

    @Test
    fun `a saved region is listed after its download completes`() =
        runTest {
            val repository = repository()

            repository.start(repository.readyOffer(monaco, monacoFile))
            runCurrent()

            assertEquals(
                listOf(SavedRegion("monaco", "Monaco", monacoFile.sizeBytes, DATA_DATE, NOW)),
                repository.state.value.saved,
            )
            assertNull(repository.state.value.download)
        }

    @Test
    fun `a saved region keeps its record on disk`() =
        runTest {
            val repository = repository()

            repository.start(repository.readyOffer(monaco, monacoFile))
            runCurrent()

            assertEquals(listOf(savedRegionRecord(monaco, monacoFile, NOW)), files().savedRegions())
            assertNull(files().pendingOrNull())
        }

    @Test
    fun `progress moves the download and the used bytes`() =
        runTest {
            seedSaved(berlin, 200_000_000L)
            val repository = repository()
            val offer = repository.readyOffer(monaco, monacoFile)
            val gate = feed.gateDownload()

            repository.start(offer)
            runCurrent()

            val halfway = monacoFile.sizeBytes / 2
            assertEquals(
                RegionDownload("monaco", "Monaco", halfway, monacoFile.sizeBytes, RUNNING),
                repository.state.value.download,
            )
            assertEquals(200_000_000L + halfway, repository.state.value.usedBytes)
            gate.complete(Unit)
            runCurrent()
        }

    @Test
    fun `a second start while one runs does nothing`() =
        runTest {
            val repository = repository()
            val first = repository.readyOffer(monaco, monacoFile)
            val second = repository.readyOffer(berlin, berlinFile)
            val gate = feed.gateDownload()

            repository.start(first)
            repository.start(second)
            runCurrent()

            assertEquals(
                "monaco",
                repository.state.value.download
                    ?.regionId,
            )
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf(monaco.fileUrl), feed.downloads.map { it.url })
        }

    @Test
    fun `a start for a region saved since its offer does nothing`() =
        runTest {
            val repository = repository()
            val offer = repository.readyOffer(monaco, monacoFile)
            repository.start(offer)
            runCurrent()

            repository.start(offer)
            runCurrent()

            assertEquals(1, feed.downloads.size)
            assertNull(repository.state.value.download)
        }

    @Test
    fun `a start that cannot write its record fails as storage and leaves nothing on disk`() =
        runTest {
            val repository = repository()
            val offer = repository.readyOffer(monaco, monacoFile)
            // A directory where the record goes: the record is written beside it and cannot be renamed into place.
            File(root, "pending.json").mkdirs()

            repository.start(offer)
            runCurrent()

            assertEquals(FAILED to STORAGE, repository.phaseAndFailure())
            assertEquals(emptyList(), feed.downloads)
            assertEquals(emptyList(), storedFiles())
        }

    @Test
    fun `an interruption pauses and keeps the bytes`() =
        runTest {
            feed.stop = FakeDownloadStop(partBytes = 1_000L, result = TransferResult.Interrupted)
            val repository = repository()

            repository.start(repository.readyOffer(monaco, monacoFile))
            runCurrent()

            assertEquals(
                RegionDownload("monaco", "Monaco", 1_000L, monacoFile.sizeBytes, PAUSED, NETWORK),
                repository.state.value.download,
            )
            assertEquals(1_000L, files().partFile(monaco.id).length())
            assertEquals(pendingDownloadRecord(monaco, monacoFile), files().pendingOrNull())
        }

    @Test
    fun `an unavailable answer that deleted the part pauses with nothing received`() =
        runTest {
            val repository = pausedRepository()
            feed.stop = FakeDownloadStop(partBytes = null, result = TransferResult.Unavailable)

            repository.resume()
            runCurrent()

            assertEquals(
                RegionDownload("monaco", "Monaco", 0L, monacoFile.sizeBytes, PAUSED, NETWORK),
                repository.state.value.download,
            )
        }

    @Test
    fun `a part that cannot be written pauses as storage`() =
        runTest {
            feed.stop = FakeDownloadStop(partBytes = 2_000L, result = TransferResult.StorageFailed)
            val repository = repository()

            repository.start(repository.readyOffer(monaco, monacoFile))
            runCurrent()

            assertEquals(
                RegionDownload("monaco", "Monaco", 2_000L, monacoFile.sizeBytes, PAUSED, STORAGE),
                repository.state.value.download,
            )
        }

    @Test
    fun `resume continues from the part with the same record`() =
        runTest {
            val repository = pausedRepository()

            repository.resume()
            runCurrent()

            val (first, second) = feed.downloads
            assertEquals(first.copy(heldBytes = 1_000L), second)
            assertEquals(
                listOf("monaco"),
                repository.state.value.saved
                    .map { it.id },
            )
        }

    @Test
    fun `resume without room stays paused and says why`() =
        runTest {
            val repository = pausedRepository()
            // One byte short of the rest of the file once the margin is kept back.
            freeBytes = FREE_SPACE_MARGIN_BYTES + monacoFile.sizeBytes - 1_000L - 1

            repository.resume()
            runCurrent()

            assertEquals(
                RegionDownload("monaco", "Monaco", 1_000L, monacoFile.sizeBytes, PAUSED, NO_SPACE),
                repository.state.value.download,
            )
            assertEquals(1, feed.downloads.size)
        }

    @Test
    fun `lowering the limit does not stop a paused download`() =
        runTest {
            seedSaved(berlin, 900_000_000L)
            val repository = repository()
            feed.stop = FakeDownloadStop(partBytes = 1_000L, result = TransferResult.Interrupted)
            repository.start(repository.readyOffer(kanto, kantoFile))
            runCurrent()
            feed.stop = null
            limit = OfflineStorageLimit.GB_1

            repository.resume()
            runCurrent()

            assertEquals(
                listOf("berlin", "kanto"),
                repository.state.value.saved
                    .map { it.id }
                    .sorted(),
            )
        }

    @Test
    fun `a missing file fails as gone`() =
        runTest {
            feed.stop = FakeDownloadStop(partBytes = null, result = TransferResult.Gone)
            val repository = repository()

            repository.start(repository.readyOffer(monaco, monacoFile))
            runCurrent()

            assertEquals(FAILED to GONE, repository.phaseAndFailure())
            assertNull(files().pendingOrNull())
            assertEquals(emptyList(), storedFiles())
        }

    // --- a file that changed on the server ----------------------------------

    @Test
    fun `a changed file that still fits restarts and saves the new size and etag`() =
        runTest {
            val changed = RemoteFile(2_000_000L, "\"monaco-2\"", DATA_DATE + 86_400_000L)
            feed.sentFile = changed
            val gate = feed.gateDownload()
            val repository = repository()

            repository.start(repository.readyOffer(monaco, monacoFile))
            runCurrent()

            assertEquals(pendingDownloadRecord(monaco, changed), files().pendingOrNull())
            assertEquals(
                changed.sizeBytes,
                repository.state.value.download
                    ?.totalBytes,
            )
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf(savedRegionRecord(monaco, changed, NOW)), files().savedRegions())
        }

    @Test
    fun `a changed file that no longer fits stops and leaves nothing on disk`() =
        runTest {
            limit = OfflineStorageLimit.GB_1
            feed.sentFile = RemoteFile(1_500_000_000L, "\"monaco-2\"", DATA_DATE)
            val repository = repository()

            repository.start(repository.readyOffer(monaco, monacoFile))
            runCurrent()

            assertEquals(FAILED to OVER_LIMIT, repository.phaseAndFailure())
            assertEquals(emptyList(), storedFiles())
        }

    @Test
    fun `a changed file without room fails as no space`() =
        runTest {
            val repository = repository()
            val offer = repository.readyOffer(monaco, monacoFile)
            val changed = RemoteFile(200_000_000L, "\"monaco-2\"", DATA_DATE)
            feed.sentFile = changed
            freeBytes = FREE_SPACE_MARGIN_BYTES + changed.sizeBytes - 1

            repository.start(offer)
            runCurrent()

            assertEquals(FAILED to NO_SPACE, repository.phaseAndFailure())
            assertEquals(emptyList(), storedFiles())
        }

    @Test
    fun `a restart counts the old part as free space`() =
        runTest {
            val repository = pausedRepository(partBytes = 1_000_000L)
            val changed = RemoteFile(2_000_000L, "\"monaco-2\"", DATA_DATE)
            feed.sentFile = changed
            // Room for the new file only once the old part, about to be truncated, counts as free.
            freeBytes = FREE_SPACE_MARGIN_BYTES + changed.sizeBytes - 1_000_000L

            repository.resume()
            runCurrent()

            assertEquals(
                listOf(changed.sizeBytes),
                repository.state.value.saved
                    .map { it.sizeBytes },
            )
        }

    @Test
    fun `a process that dies after an accepted restart resumes the new file from its start`() =
        runTest {
            val first = pausedRepository(partBytes = 1_000L)
            val changed = RemoteFile(2_000_000L, "\"monaco-2\"", DATA_DATE)
            feed.sentFile = changed
            // The process dies here: the restart is accepted and the part not yet opened.
            val death = feed.gateRestart()
            first.resume()
            runCurrent()

            val afterDeath = FakeRegionFeed()
            val second = repository(feed = afterDeath)
            second.resume()
            runCurrent()

            // A part of the old file beside the record of the new one would be resumed as a range of the new file.
            assertEquals(
                RequestedRegionDownload(monaco.fileUrl, files().partFile(monaco.id), changed, heldBytes = 0L),
                afterDeath.downloads.single(),
            )
            death.cancel()
        }

    @Test
    fun `a restart whose record cannot be written fails as storage`() =
        runTest {
            val repository = pausedRepository()
            feed.sentFile = RemoteFile(2_000_000L, "\"monaco-2\"", DATA_DATE)
            // A directory in place of the record: the record of the new file cannot replace it.
            File(root, "pending.json").apply { delete() }.mkdirs()

            repository.resume()
            runCurrent()

            assertEquals(FAILED to STORAGE, repository.phaseAndFailure())
            assertEquals(emptyList(), storedFiles())
        }

    // --- the check and the commit -------------------------------------------

    @Test
    fun `a file that fails the check is not saved and leaves nothing on disk`() =
        runTest {
            val repository = repository(check = RegionFileCheck { _, _ -> false })

            repository.start(repository.readyOffer(monaco, monacoFile))
            runCurrent()

            assertEquals(FAILED to CORRUPT, repository.phaseAndFailure())
            assertEquals(emptyList(), repository.state.value.saved)
            assertEquals(emptyList(), storedFiles())
        }

    @Test
    fun `the check reads the whole part against the size the server sent`() =
        runTest {
            val checked = mutableListOf<Pair<File, Long>>()
            val changed = RemoteFile(2_000_000L, "\"monaco-2\"", DATA_DATE)
            feed.sentFile = changed
            val repository =
                repository(
                    check = RegionFileCheck {
                        file,
                        expectedBytes,
                        ->
                        true.also { checked += file to expectedBytes }
                    },
                )

            repository.start(repository.readyOffer(monaco, monacoFile))
            runCurrent()

            assertEquals(listOf(files().partFile(monaco.id) to changed.sizeBytes), checked)
        }

    @Test
    fun `a save that cannot be committed fails as storage and leaves nothing on disk`() =
        runTest {
            val gate = feed.gateDownload()
            val repository = repository()
            repository.start(repository.readyOffer(monaco, monacoFile))
            runCurrent()
            // A directory where the data file goes: the finished part cannot be renamed into place.
            files().dataFile(monaco.id).mkdirs()

            gate.complete(Unit)
            runCurrent()

            assertEquals(FAILED to STORAGE, repository.phaseAndFailure())
            assertEquals(emptyList(), repository.state.value.saved)
            assertEquals(emptyList(), storedFiles())
        }

    // --- cancel and delete --------------------------------------------------

    @Test
    fun `cancel removes the part and the pending record in every phase`() =
        runTest {
            RegionDownloadPhase.entries.forEach { phase ->
                val root = File(folder.root, phase.name)
                val feed = FakeRegionFeed()
                val gate = feed.gateDownload().also { if (phase != RUNNING) it.complete(Unit) }
                feed.stop =
                    when (phase) {
                        PAUSED -> FakeDownloadStop(partBytes = 1_000L, result = TransferResult.Interrupted)
                        FAILED -> FakeDownloadStop(partBytes = null, result = TransferResult.Gone)
                        else -> null
                    }
                val phasesAtCheck = mutableListOf<RegionDownloadPhase?>()
                lateinit var repository: OfflineRegionRepository
                // In VERIFYING the cancel arrives while the file is checked.
                val cancellingCheck =
                    RegionFileCheck { _, _ ->
                        phasesAtCheck += repository.state.value.download
                            ?.phase
                        repository.cancel()
                        true
                    }
                repository = repository(root, feed, check = cancellingCheck)

                repository.start(repository.readyOffer(monaco, monacoFile, feed))
                runCurrent()
                if (phase != VERIFYING) {
                    assertEquals(
                        phase,
                        repository.state.value.download
                            ?.phase,
                        phase.name,
                    )
                    repository.cancel()
                    runCurrent()
                }

                assertEquals(if (phase == VERIFYING) listOf(VERIFYING) else emptyList(), phasesAtCheck, phase.name)
                assertNull(repository.state.value.download, phase.name)
                assertFalse(files(root).partFile(monaco.id).exists(), phase.name)
                assertNull(files(root).pendingOrNull(), phase.name)
                assertEquals(emptyList(), files(root).savedRegions(), phase.name)
                gate.complete(Unit)
            }
        }

    @Test
    fun `delete removes the region and frees its bytes`() =
        runTest {
            seedSaved(monaco, monacoFile.sizeBytes)
            val repository = repository()
            repository.load()
            assertTrue(repository.state.value.usedBytes >= monacoFile.sizeBytes)
            freeBytes += monacoFile.sizeBytes

            repository.delete(monaco.id)

            assertEquals(emptyList(), repository.state.value.saved)
            assertEquals(0L, repository.state.value.usedBytes)
            assertEquals(freeBytes, repository.state.value.freeBytes)
            assertFalse(files().dataFile(monaco.id).exists())
        }

    @Test
    fun `deleting a region that is not saved changes nothing`() =
        runTest {
            seedSaved(monaco, monacoFile.sizeBytes)
            val repository = repository()
            repository.load()
            val before = repository.state.value
            // A refresh would show the new figure.
            freeBytes = 1L

            repository.delete(berlin.id)

            assertEquals(before, repository.state.value)
            assertTrue(files().dataFile(monaco.id).isFile)
        }

    // --- offers -------------------------------------------------------------

    @Test
    fun `an offer that fits is ready with the storage figures`() =
        runTest {
            seedSaved(berlin, 1_000L)
            feed.probeAnswers[monaco.fileUrl] = ProbeResult.Found(monacoFile)

            assertEquals(
                RegionOffer.Ready(monaco, monacoFile, 1_000L + monacoFile.sizeBytes, limitBytes = null, freeBytes),
                repository().offer(monaco),
            )
        }

    @Test
    fun `an offer is busy while a download is paused`() =
        runTest {
            val repository = pausedRepository()
            feed.probeAnswers[berlin.fileUrl] = ProbeResult.Found(berlinFile)

            assertEquals(RegionOffer.Unavailable(berlin, Reason.BUSY), repository.offer(berlin))
            assertEquals(listOf(monaco.fileUrl), feed.probedUrls)
        }

    @Test
    fun `an offer for a saved region says so without a probe`() =
        runTest {
            seedSaved(monaco, monacoFile.sizeBytes)
            feed.probeAnswers[monaco.fileUrl] = ProbeResult.Found(monacoFile)

            assertEquals(RegionOffer.Unavailable(monaco, Reason.ALREADY_SAVED), repository().offer(monaco))
            assertEquals(emptyList(), feed.probedUrls)
        }

    @Test
    fun `an offer over the limit names the excess`() =
        runTest {
            limit = OfflineStorageLimit.GB_1
            seedSaved(berlin, 200_000_000L)
            feed.probeAnswers[kanto.fileUrl] = ProbeResult.Found(kantoFile)

            assertEquals(
                RegionOffer.OverLimit(kanto, 862_351_360L, overByBytes = 62_351_360L, limitBytes = 1_000_000_000L),
                repository().offer(kanto),
            )
        }

    @Test
    fun `an offer that exactly fills the limit is ready`() =
        runTest {
            limit = OfflineStorageLimit.GB_1
            seedSaved(berlin, 137_648_640L)
            feed.probeAnswers[kanto.fileUrl] = ProbeResult.Found(kantoFile)

            assertEquals(
                RegionOffer.Ready(kanto, kantoFile, 1_000_000_000L, limitBytes = 1_000_000_000L, freeBytes),
                repository().offer(kanto),
            )
        }

    @Test
    fun `an offer larger than the free space is refused`() =
        runTest {
            // The margin is kept back, which leaves one byte too few.
            freeBytes = FREE_SPACE_MARGIN_BYTES + kantoFile.sizeBytes - 1
            feed.probeAnswers[kanto.fileUrl] = ProbeResult.Found(kantoFile)

            assertEquals(RegionOffer.NoSpace(kanto, kantoFile.sizeBytes, freeBytes), repository().offer(kanto))
        }

    @Test
    fun `a file without a size is refused even without a limit`() =
        runTest {
            feed.probeAnswers[kanto.fileUrl] = ProbeResult.SizeUnknown

            assertEquals(RegionOffer.Unavailable(kanto, Reason.SIZE_UNKNOWN), repository().offer(kanto))
        }

    @Test
    fun `a probe that finds no file or no answer says why`() =
        runTest {
            val repository = repository()
            feed.probeAnswers[kanto.fileUrl] = ProbeResult.Gone
            feed.probeAnswers[berlin.fileUrl] = ProbeResult.Unavailable

            assertEquals(RegionOffer.Unavailable(kanto, Reason.GONE), repository.offer(kanto))
            assertEquals(RegionOffer.Unavailable(berlin, Reason.NETWORK), repository.offer(berlin))
        }

    @Test
    fun `sizes above two gibibytes survive an offer and a paused state`() =
        runTest {
            val huge = RemoteFile(58_444_771_328L, "\"kanto-huge\"", DATA_DATE)
            feed.probeAnswers[kanto.fileUrl] = ProbeResult.Found(huge)
            val repository = repository()

            limit = OfflineStorageLimit.GB_50
            assertEquals(
                RegionOffer.OverLimit(
                    kanto,
                    58_444_771_328L,
                    overByBytes = 8_444_771_328L,
                    limitBytes = 50_000_000_000L,
                ),
                repository.offer(kanto),
            )
            limit = OfflineStorageLimit.NONE
            val ready = assertIs<RegionOffer.Ready>(repository.offer(kanto))
            assertEquals(58_444_771_328L, ready.usedAfterBytes)
            feed.stop = FakeDownloadStop(partBytes = 3_000_000_000L, result = TransferResult.Interrupted)

            repository.start(ready)
            runCurrent()

            assertEquals(
                RegionDownload("kanto", kanto.name, 3_000_000_000L, 58_444_771_328L, PAUSED, NETWORK),
                repository.state.value.download,
            )
        }

    // --- the catalogue ------------------------------------------------------

    @Test
    fun `the catalogue falls back to the cached index when the network fails`() =
        runTest {
            val repository = repository()
            assertIs<RegionCatalogResult.Loaded>(repository.catalog(longitude = null, latitude = null))
            feed.indexAvailable = false

            val result = assertIs<RegionCatalogResult.Loaded>(repository.catalog(longitude = null, latitude = null))

            assertNotNull(result.catalog.regionOrNull("kanto"))
            assertEquals(2, feed.indexEtags.size)
        }

    @Test
    fun `the catalogue is unavailable without a cache and without a network`() =
        runTest {
            feed.indexAvailable = false

            assertEquals(RegionCatalogResult.Unavailable, repository().catalog(longitude = null, latitude = null))
        }

    @Test
    fun `the cached index is revalidated with its etag only while the file exists`() =
        runTest {
            val repository = repository()
            repository.catalog(longitude = null, latitude = null)
            repository.catalog(longitude = null, latitude = null)
            indexCache.delete()

            assertIs<RegionCatalogResult.Loaded>(repository.catalog(longitude = null, latitude = null))
            assertEquals(listOf(null, FakeRegionFeed.INDEX_ETAG, null), feed.indexEtags)
        }

    @Test
    fun `an index without an etag drops the one stored before`() =
        runTest {
            val repository = repository()
            repository.catalog(longitude = null, latitude = null)
            feed.indexEtag = null
            repository.catalog(longitude = null, latitude = null)

            repository.catalog(longitude = null, latitude = null)

            assertEquals(listOf(null, FakeRegionFeed.INDEX_ETAG, null), feed.indexEtags)
        }

    @Test
    fun `a cached index that does not parse is fetched again without its etag`() =
        runTest {
            feed.indexBody = "not an index".toByteArray()
            val repository = repository()
            assertEquals(RegionCatalogResult.Unavailable, repository.catalog(longitude = null, latitude = null))
            feed.indexBody = sampleRegionIndex()

            assertIs<RegionCatalogResult.Loaded>(repository.catalog(longitude = null, latitude = null))
            assertEquals(listOf<String?>(null, null), feed.indexEtags)
        }

    @Test
    fun `the suggestion is the smallest region with a file around the position`() =
        runTest {
            val result = repository().catalog(longitude = 139.76, latitude = 35.68)

            assertEquals("kanto", assertIs<RegionCatalogResult.Loaded>(result).suggestedId)
        }

    @Test
    fun `the suggestion skips a region that is already saved`() =
        runTest {
            seedSaved(kanto, 1_000L)

            val result = repository().catalog(longitude = 139.76, latitude = 35.68)

            assertNull(assertIs<RegionCatalogResult.Loaded>(result).suggestedId)
        }

    @Test
    fun `the suggestion skips a region under a saved region`() =
        runTest {
            // Kantō lies in Japan: the saved file of Japan covers it, and the picker does not open Japan either.
            seedSaved(catalog.region("japan"), 1_000L)

            val result = repository().catalog(longitude = 139.76, latitude = 35.68)

            assertNull(assertIs<RegionCatalogResult.Loaded>(result).suggestedId)
        }

    @Test
    fun `a position that is not known gives no suggestion`() =
        runTest {
            val result = repository().catalog(longitude = null, latitude = null)

            assertNull(assertIs<RegionCatalogResult.Loaded>(result).suggestedId)
        }
}
