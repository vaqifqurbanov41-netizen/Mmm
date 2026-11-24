package com.example

import com.example.data.ai.OfflineAgronomistAdvisor
import com.example.data.model.AqroSigortaCatalog
import com.example.data.model.FarmField
import com.example.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AqroSigortaAndAdvisorUnitTest {

    @Test
    fun `aqroSigortaCatalogContainsAllSevenRegionalBranchesWithValidDialUris`() {
        assertEquals(7, AqroSigortaCatalog.branches.size)

        val byId = AqroSigortaCatalog.branches.associateBy { it.id }
        assertEquals("Abşeron regional mərkəzi", byId["absheron"]?.name)
        assertEquals("1651", byId["absheron"]?.phone)
        assertEquals("tel:1651", byId["absheron"]?.dialUri)

        assertEquals("Cəlilabad Filialı", byId["celilabad"]?.name)
        assertEquals("051 244 56 77", byId["celilabad"]?.phone)
        assertEquals("tel:0512445677", byId["celilabad"]?.dialUri)

        assertEquals("Goranboy filialı", byId["goranboy"]?.name)
        assertEquals("010 228 13 00", byId["goranboy"]?.phone)

        assertEquals("Göyçay filialı", byId["goycay"]?.name)
        assertEquals("051 270 98 02", byId["goycay"]?.phone)

        assertEquals("Quba-Xaçmaz filialı", byId["quba_xacmaz"]?.name)
        assertEquals("010 300 36 48", byId["quba_xacmaz"]?.phone)

        assertEquals("Şəki filialı", byId["sheki"]?.name)
        assertEquals("051 244 56 50", byId["sheki"]?.phone)

        assertEquals("Şəmkir Filialı", byId["shemkir"]?.name)
        assertEquals("010 230 37 17", byId["shemkir"]?.phone)
    }

    @Test
    fun `aqroSigortaFilterMatchesAzerbaijaniDiacriticsAndPhoneNumbers`() {
        val shekiMatches = AqroSigortaCatalog.filterBranches("Şəki")
        assertEquals(1, shekiMatches.size)
        assertEquals("sheki", shekiMatches.first().id)

        val normalizedSheki = AqroSigortaCatalog.filterBranches("seki")
        assertEquals(1, normalizedSheki.size)
        assertEquals("sheki", normalizedSheki.first().id)

        val hotlineMatch = AqroSigortaCatalog.filterBranches("1651")
        assertEquals(1, hotlineMatch.size)
        assertEquals("absheron", hotlineMatch.first().id)

        assertEquals(7, AqroSigortaCatalog.filterBranches("").size)
    }

    @Test
    fun `offlineAdvisorHandlesDiseasesFertilizerIrrigationInsuranceAndFarmContext`() {
        val tomatoResponse = OfflineAgronomistAdvisor.answerOffline("Pomidor fitoftoroz", AppLanguage.AZ)
        assertTrue(tomatoResponse.contains("Mankozeb", ignoreCase = true))

        val fertilizerResponse = OfflineAgronomistAdvisor.answerOffline("Buğda azot və fosfor gübrəsi", AppLanguage.AZ)
        assertTrue(fertilizerResponse.contains("Karbamid", ignoreCase = true))
        assertTrue(fertilizerResponse.contains("NPK", ignoreCase = true))

        val irrigationResponse = OfflineAgronomistAdvisor.answerOffline("Damcılı suvarma nə vaxt aparılmalıdır?", AppLanguage.AZ)
        assertTrue(irrigationResponse.contains("Damcılı Suvarma", ignoreCase = true))

        val insuranceResponse = OfflineAgronomistAdvisor.answerOffline("Aqrosığorta filialları haradadır?", AppLanguage.AZ)
        assertTrue(insuranceResponse.contains("1651"))
        assertTrue(insuranceResponse.contains("Goranboy"))
        assertTrue(insuranceResponse.contains("Şəmkir"))

        val enSoil = OfflineAgronomistAdvisor.answerOffline("How to manage saline soil?", AppLanguage.EN)
        assertTrue(enSoil.contains("leaching", ignoreCase = true))

        val contextSummary = OfflineAgronomistAdvisor.buildFarmContextSummary(
            fields = listOf(FarmField(id = "1", name = "Cənub sahəsi", crop = "Buğda", sizeHa = 4.5)),
            region = "Gəncə",
            tempC = 22.4,
            hazardLabels = listOf("Şaxta (-1°C)"),
        )
        assertTrue(contextSummary.contains("Cənub sahəsi"))
        assertTrue(contextSummary.contains("4.50 ha"))
        assertTrue(contextSummary.contains("22.4°C"))
    }
}
