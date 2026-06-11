package io.github.seijikohara.femto.data.fonts

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoogleFontsApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses the catalog sorted by popularity`() =
        runTest {
            server.enqueue(MockResponse().setBody(CATALOG_BODY))

            val catalog = newApi().catalog()

            assertEquals(listOf("Roboto", "Lobster"), catalog?.map { it.family })
        }

    @Test
    fun `strips the xssi prefix from a guarded response`() =
        runTest {
            server.enqueue(MockResponse().setBody(")]}'\n$CATALOG_BODY"))

            val catalog = newApi().catalog()

            assertEquals(2, catalog?.size)
        }

    @Test
    fun `returns null on a malformed catalog body`() =
        runTest {
            server.enqueue(MockResponse().setBody("{ not json"))

            assertNull(newApi().catalog())
        }

    @Test
    fun `returns null on a catalog http error`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))

            assertNull(newApi().catalog())
        }

    @Test
    fun `plan prefers the upright variable font over static weights`() =
        runTest {
            server.enqueue(MockResponse().setBody(MANIFEST_BODY))

            val plan = newApi().plan("Roboto")

            assertTrue(plan is FontDownloadPlan.Variable)
            assertEquals("https://fonts.example/vf.ttf", plan.url)
        }

    @Test
    fun `plan falls back to static weights without a variable font`() =
        runTest {
            server.enqueue(MockResponse().setBody(STATIC_MANIFEST_BODY))

            val plan = newApi().plan("Roboto")

            assertTrue(plan is FontDownloadPlan.Static)
            assertEquals(setOf(400, 700), plan.urlByWeight.keys)
        }

    @Test
    fun `plan returns null on a malformed manifest body`() =
        runTest {
            server.enqueue(MockResponse().setBody("<html>not json</html>"))

            assertNull(newApi().plan("Roboto"))
        }

    private fun newApi(): GoogleFontsApi = GoogleFontsApi(client, metadataBaseUrl = server.url("/").toString())

    private companion object {
        const val CATALOG_BODY = """
            {
              "familyMetadataList": [
                {"family": "Lobster", "category": "Display", "subsets": ["latin"], "popularity": 42},
                {"family": "Roboto", "category": "Sans Serif", "subsets": ["latin"], "popularity": 1}
              ]
            }
        """

        // The italic variable font precedes the upright one so the plan's
        // italic exclusion is exercised, not just the VariableFont match.
        const val MANIFEST_BODY = """
            {
              "manifest": {
                "fileRefs": [
                  {"filename": "Roboto-Italic-VariableFont_wght.ttf", "url": "https://fonts.example/italic-vf.ttf"},
                  {"filename": "Roboto-VariableFont_wght.ttf", "url": "https://fonts.example/vf.ttf"},
                  {"filename": "Roboto-Regular.ttf", "url": "https://fonts.example/regular.ttf"},
                  {"filename": "Roboto-Bold.ttf", "url": "https://fonts.example/bold.ttf"}
                ]
              }
            }
        """

        const val STATIC_MANIFEST_BODY = """
            {
              "manifest": {
                "fileRefs": [
                  {"filename": "Roboto-Regular.ttf", "url": "https://fonts.example/regular.ttf"},
                  {"filename": "Roboto-Bold.ttf", "url": "https://fonts.example/bold.ttf"},
                  {"filename": "Roboto-BoldItalic.ttf", "url": "https://fonts.example/bold-italic.ttf"}
                ]
              }
            }
        """
    }
}
