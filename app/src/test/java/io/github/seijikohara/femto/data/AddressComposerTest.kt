package io.github.seijikohara.femto.data

import io.github.seijikohara.femto.data.NominatimApi.NominatimAddress
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AddressComposerTest {
    @Test
    fun `composes east-asian line large-to-small when country is japan`() {
        val address =
            NominatimAddress(
                neighbourhood = "新宿三丁目",
                quarter = "新宿",
                city = "新宿区",
                isoLvl4 = "JP-13",
                countryCode = "jp",
            )

        val result = assertNotNull(AddressComposer.composeAddress(address))

        assertEquals("東京都新宿区新宿三丁目", result.line)
    }

    @Test
    fun `sets locality to ward and region to prefecture for japan`() {
        val address =
            NominatimAddress(
                neighbourhood = "新宿三丁目",
                quarter = "新宿",
                city = "新宿区",
                isoLvl4 = "JP-13",
                countryCode = "jp",
            )

        val result = assertNotNull(AddressComposer.composeAddress(address))

        assertEquals("新宿区", result.locality)
        assertEquals("東京都", result.region)
    }

    @Test
    fun `orders prefecture municipality and sub-ward large-to-small for yokohama`() {
        val address =
            NominatimAddress(
                suburb = "西区",
                city = "横浜市",
                province = "神奈川県",
                countryCode = "jp",
            )

        val result = assertNotNull(AddressComposer.composeAddress(address))

        assertEquals("神奈川県横浜市西区", result.line)
    }

    @Test
    fun `sets locality to city for yokohama`() {
        val address =
            NominatimAddress(
                suburb = "西区",
                city = "横浜市",
                province = "神奈川県",
                countryCode = "jp",
            )

        val result = assertNotNull(AddressComposer.composeAddress(address))

        assertEquals("横浜市", result.locality)
    }

    @Test
    fun `composes western line small-to-large comma-joined for mountain view`() {
        val address =
            NominatimAddress(
                houseNumber = "1600",
                road = "Amphitheatre Parkway",
                city = "Mountain View",
                isoLvl4 = "US-CA",
                countryCode = "us",
            )

        val result = assertNotNull(AddressComposer.composeAddress(address))

        assertEquals("1600 Amphitheatre Parkway, Mountain View, CA", result.line)
    }

    @Test
    fun `derives western state from iso suffix for mountain view`() {
        val address =
            NominatimAddress(
                houseNumber = "1600",
                road = "Amphitheatre Parkway",
                city = "Mountain View",
                isoLvl4 = "US-CA",
                countryCode = "us",
            )

        val result = assertNotNull(AddressComposer.composeAddress(address))

        assertEquals("Mountain View", result.locality)
        assertEquals("CA", result.region)
    }

    @Test
    fun `emits an overlapping value once when suburb equals neighbourhood`() {
        val address =
            NominatimAddress(
                neighbourhood = "本町",
                suburb = "本町",
                city = "中央区",
                province = "大阪府",
                countryCode = "jp",
            )

        val result = assertNotNull(AddressComposer.composeAddress(address))

        assertEquals("大阪府中央区本町", result.line)
    }

    @Test
    fun `excludes the road poi from a japanese address line`() {
        val address =
            NominatimAddress(
                road = "JR新宿駅;1階;15番線",
                neighbourhood = "新宿三丁目",
                quarter = "新宿",
                city = "新宿区",
                isoLvl4 = "JP-13",
                countryCode = "jp",
            )

        val result = assertNotNull(AddressComposer.composeAddress(address))

        assertEquals("東京都新宿区新宿三丁目", result.line)
    }

    @Test
    fun `keeps the house number as the banchi in a japanese address line`() {
        val address =
            NominatimAddress(
                houseNumber = "1-3",
                neighbourhood = "西新宿一丁目",
                city = "新宿区",
                isoLvl4 = "JP-13",
                countryCode = "jp",
            )

        val result = assertNotNull(AddressComposer.composeAddress(address))

        assertEquals("東京都新宿区西新宿一丁目1-3", result.line)
    }

    @Test
    fun `composes non-japanese east-asian line large-to-small with no separator for seoul`() {
        val address =
            NominatimAddress(
                suburb = "Myeong-dong",
                city = "Jung-gu",
                province = "Seoul",
                countryCode = "kr",
            )

        val result = assertNotNull(AddressComposer.composeAddress(address))

        assertEquals("SeoulJung-guMyeong-dong", result.line)
        assertEquals("Jung-gu", result.locality)
        assertEquals("Seoul", result.region)
    }

    @Test
    fun `derives western region from the state fallback when no iso suffix is present`() {
        val address =
            NominatimAddress(
                houseNumber = "221B",
                road = "Baker Street",
                city = "London",
                state = "England",
                countryCode = "gb",
            )

        val result = assertNotNull(AddressComposer.composeAddress(address))

        assertEquals("221B Baker Street, London, England", result.line)
        assertEquals("London", result.locality)
        assertEquals("England", result.region)
    }

    @Test
    fun `falls back to the western branch when the country is null`() {
        val address =
            NominatimAddress(
                city = "Toronto",
                province = "Ontario",
                countryCode = null,
            )

        val result = assertNotNull(AddressComposer.composeAddress(address))

        assertEquals("Toronto, Ontario", result.line)
        assertEquals("Toronto", result.locality)
        assertEquals("Ontario", result.region)
    }

    @Test
    fun `returns null when neither municipality nor prefecture resolves`() {
        val address =
            NominatimAddress(
                road = "Some Road",
                countryCode = "jp",
            )

        assertEquals(null, AddressComposer.composeAddress(address))
    }
}
