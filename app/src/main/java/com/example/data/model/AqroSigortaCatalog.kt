package com.example.data.model

data class AqroSigortaBranch(
    val id: String,
    val name: String,
    val address: String,
    val phone: String,
    val regionTag: String,
    val mapQuery: String,
) {
    val dialUri: String
        get() = "tel:${phone.filter { it.isDigit() || it == '+' }}"
}

object AqroSigortaCatalog {
    val branches: List<AqroSigortaBranch> = listOf(
        AqroSigortaBranch(
            id = "absheron",
            name = "Abşeron regional mərkəzi",
            address = "Bakı şəhəri, Nərimanov rayonu, Səməd Vurğun küçəsi, 103",
            phone = "1651",
            regionTag = "Bakı / Abşeron",
            mapQuery = "Bakı şəhəri, Nərimanov rayonu, Səməd Vurğun küçəsi, 103 Aqrar Sığorta Fondu",
        ),
        AqroSigortaBranch(
            id = "celilabad",
            name = "Cəlilabad Filialı",
            address = "Cəlilabad şəhəri Qurtuluş küçəsi 114 A",
            phone = "051 244 56 77",
            regionTag = "Cəlilabad",
            mapQuery = "Cəlilabad şəhəri Qurtuluş küçəsi 114 A",
        ),
        AqroSigortaBranch(
            id = "goranboy",
            name = "Goranboy filialı",
            address = "GORANBOY RAYONU, H.Əliyev p. -19",
            phone = "010 228 13 00",
            regionTag = "Goranboy",
            mapQuery = "Goranboy rayonu Heydər Əliyev prospekti 19",
        ),
        AqroSigortaBranch(
            id = "goycay",
            name = "Göyçay filialı",
            address = "Natavan küçəsi 84",
            phone = "051 270 98 02",
            regionTag = "Göyçay",
            mapQuery = "Göyçay şəhəri Natavan küçəsi 84",
        ),
        AqroSigortaBranch(
            id = "quba_xacmaz",
            name = "Quba-Xaçmaz filialı",
            address = "Heydər Əliyev pr-ti 282A",
            phone = "010 300 36 48",
            regionTag = "Quba-Xaçmaz",
            mapQuery = "Quba-Xaçmaz Heydər Əliyev prospekti 282A Aqrar Sığorta",
        ),
        AqroSigortaBranch(
            id = "sheki",
            name = "Şəki filialı",
            address = "Şəki şəhər Istiqlaliyyət prospekti N149",
            phone = "051 244 56 50",
            regionTag = "Şəki",
            mapQuery = "Şəki şəhəri İstiqlaliyyət prospekti 149",
        ),
        AqroSigortaBranch(
            id = "shemkir",
            name = "Şəmkir Filialı",
            address = "Şəmkir rayonu S.Vurğun küç 202B",
            phone = "010 230 37 17",
            regionTag = "Şəmkir",
            mapQuery = "Şəmkir rayonu Səməd Vurğun küçəsi 202B",
        ),
    )

    fun filterBranches(query: String): List<AqroSigortaBranch> {
        val norm = CropProtectionCatalog.normalizeSearch(query)
        if (norm.isBlank()) return branches
        return branches.filter { branch ->
            CropProtectionCatalog.normalizeSearch(
                "${branch.name} ${branch.address} ${branch.phone} ${branch.regionTag}"
            ).contains(norm)
        }
    }
}
