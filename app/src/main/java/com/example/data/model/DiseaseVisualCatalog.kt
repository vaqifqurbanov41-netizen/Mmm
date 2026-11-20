package com.example.data.model

import com.example.i18n.AppLanguage

import java.net.URLEncoder

enum class VisualPatternType {
    DARK_NECROTIC_BLIGHT,
    CONCENTRIC_TARGET_SPOTS,
    POWDERY_WHITE_COATING,
    RUST_ORANGE_PUSTULES,
    YELLOW_CHLOROSIS_MOSAIC,
    ANGULAR_WATER_SOAKED,
    SCAB_CANKER_GALL,
    CHEWING_HOLES_MINES,
    WEB_STIPPLING_SUCKING,
    PARASITIC_DODDER_THREADS,
}

data class VisualFeatureWeights(
    val brownDark: Float = 0f,
    val yellowChlorosis: Float = 0f,
    val whitePowdery: Float = 0f,
    val rustOrange: Float = 0f,
    val spotContrast: Float = 0f,
)

data class DiseaseVisualProfile(
    val threatId: String,
    val patternType: VisualPatternType,
    val plantPart: LocalizedText,
    val visualCue: LocalizedText,
    val weights: VisualFeatureWeights,
    val referenceImageUrl: String,
    val imageAttribution: String = "Wikimedia Commons",
)

object DiseaseVisualCatalog {

    fun resizedUrl(baseUrl: String, width: Int): String =
        baseUrl.replace(Regex("width=\\d+"), "width=$width")

    private fun commonsImage(fileTitle: String, width: Int = 640): String {
        val encoded = URLEncoder.encode(fileTitle.trim().replace(' ', '_'), "UTF-8")
            .replace("+", "%20")
        return "https://commons.wikimedia.org/wiki/Special:FilePath/$encoded?width=$width"
    }

    private fun partLeafBoth() = LocalizedText("Yarpaq (üst və alt səth)", "Foliage (upper & lower leaf)", "Листья (верх и низ)")
    private fun partLeafLower() = LocalizedText("Yarpaq alt səthi və gövdə", "Underside of leaves & stems", "Нижняя сторона листьев и стебли")
    private fun partFruit() = LocalizedText("Meyvə / Salxım / Qoz", "Fruit / Cluster / Nut husk", "Плоды / Кисти / Орехи")
    private fun partTuberStem() = LocalizedText("Yarpaq, gövdə və yumru", "Foliage, stems & tubers", "Ботва, стебли и клубни")
    private fun partSpike() = LocalizedText("Sünbül və bayraq yarpaq", "Wheat head / spike & flag leaf", "Колос и флаговый лист")
    private fun partShoots() = LocalizedText("Təpə zoğları və yarpaq", "Young shoots, buds & leaves", "Молодые побеги, почки и листья")

    val profilesByThreatId: Map<String, DiseaseVisualProfile> = listOf(
        // POMIDOR / TOMATO
        DiseaseVisualProfile(
            threatId = "tomato_late_blight",
            patternType = VisualPatternType.DARK_NECROTIC_BLIGHT,
            plantPart = partLeafLower(),
            visualCue = LocalizedText(
                "Kənarlardan mərkəzə doğru sürətlə yayılan sulu qəhvəyi-qara ləkələr və rütubətdə alt səthdə ağ-boz halə.",
                "Rapidly expanding water-soaked dark brown blotches with pale white mold ring underneath.",
                "Быстро растущие мокнущие тёмно-бурые пятна со светлым налётом снизу.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.65f, whitePowdery = 0.15f, spotContrast = 0.2f),
            referenceImageUrl = commonsImage("Late_blight_of_tomato_caused_by_Phytophthora_infestans_(5815188381).jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "tomato_alternaria",
            patternType = VisualPatternType.CONCENTRIC_TARGET_SPOTS,
            plantPart = partLeafBoth(),
            visualCue = LocalizedText(
                "Aşağı yaşlı yarpaqlarda hədəf taxtasına bənzər iç-içə halqalı tünd-qəhvəyi ləkələr və ətrafında sarı haşiyə.",
                "Sharp bull's-eye concentric dark rings surrounded by a thin yellow chlorotic halo.",
                "Чёткие концентрические бурые кольца («мишень») с жёлтым ореолом.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.50f, yellowChlorosis = 0.25f, spotContrast = 0.25f),
            referenceImageUrl = commonsImage("Alternaria_solani_-_leaf_lesions.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "tomato_powdery_mildew",
            patternType = VisualPatternType.POWDERY_WHITE_COATING,
            plantPart = partLeafBoth(),
            visualCue = LocalizedText(
                "Yarpağın üst səthində un səpilmiş kimi ağ tozvari ləkələrin birləşməsi.",
                "Talc-white dusty colonies spreading over the upper leaf epidermis.",
                "Белый мучнистый налёт на верхней стороне листа.",
            ),
            weights = VisualFeatureWeights(whitePowdery = 0.70f, yellowChlorosis = 0.20f, spotContrast = 0.10f),
            referenceImageUrl = commonsImage("5573619-PPT-powdery_mildew_(Leveillula_taurica).jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "tomato_whitefly_aphid",
            patternType = VisualPatternType.WEB_STIPPLING_SUCKING,
            plantPart = partLeafLower(),
            visualCue = LocalizedText(
                "Yarpaqların sarımtıl büzüşməsi, yapışqan şirə parıltısı və üstündə qara hisli örtük.",
                "Curled yellowed foliage with sticky honeydew sheen and dark sooty mold specks.",
                "Скрученные пожелтевшие листья с липкой падью и чёрным сажистым грибком.",
            ),
            weights = VisualFeatureWeights(yellowChlorosis = 0.45f, whitePowdery = 0.25f, spotContrast = 0.30f),
            referenceImageUrl = commonsImage("Bemisia_tabaci_from_USDA_1.jpg"),
            imageAttribution = "Wikimedia Commons • USDA",
        ),
        DiseaseVisualProfile(
            threatId = "tomato_tuta",
            patternType = VisualPatternType.CHEWING_HOLES_MINES,
            plantPart = partLeafBoth(),
            visualCue = LocalizedText(
                "Yarpaq dərisinin arasında şəffaf-ağ kağızabənzər geniş lağımlar (minalar) və içində qara tırtıl ifrazatı.",
                "Translucent papery blotch mines between leaf layers containing dark frass pellets.",
                "Прозрачные широкие мины в мякоти листа с чёрными точками экскрементов.",
            ),
            weights = VisualFeatureWeights(whitePowdery = 0.35f, yellowChlorosis = 0.30f, spotContrast = 0.35f),
            referenceImageUrl = commonsImage("Tuta_absoluta_Tomato_loss.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "tomato_bacterial_speck",
            patternType = VisualPatternType.SCAB_CANKER_GALL,
            plantPart = partFruit(),
            visualCue = LocalizedText(
                "Sarı haşiyəli xırda iynə ucu boyda qara nöqtələr və meyvədə qabarıq qabıqcıq xalları.",
                "Tiny pinpoint black specks ringed with bright yellow halos and raised fruit scabs.",
                "Мелкие чёрные точки с жёлтой каймой на листьях и коростинки на плодах.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.40f, yellowChlorosis = 0.25f, spotContrast = 0.35f),
            referenceImageUrl = commonsImage("Smart-tomato-speck.JPG"),
        ),

        // XIYAR / CUCUMBER
        DiseaseVisualProfile(
            threatId = "cucumber_downy_mildew",
            patternType = VisualPatternType.ANGULAR_WATER_SOAKED,
            plantPart = partLeafLower(),
            visualCue = LocalizedText(
                "Yarpaq damarları ilə hüdudlanan dördbucaqlı sarı-qəhvəyi mozaika ləkələri, alt səthdə tünd-bənövşəyi toz.",
                "Sharp angular yellow-tan spots strictly bounded by leaf veins with dark fuzz underneath.",
                "Угловатые жёлто-бурые пятна строго между жилками и тёмно-серый налёт снизу.",
            ),
            weights = VisualFeatureWeights(yellowChlorosis = 0.50f, brownDark = 0.30f, spotContrast = 0.20f),
            referenceImageUrl = commonsImage("Komkommer_valse_meeldauw_(Pseudoperonospora_cubensis_on_cucumber)_(1).jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "cucumber_spider_mite",
            patternType = VisualPatternType.WEB_STIPPLING_SUCKING,
            plantPart = partLeafLower(),
            visualCue = LocalizedText(
                "Səthdə minlərlə xırda sarı-ağ iynə nöqtəsi (mərmərləşmə) və yarpaq altında incə ipək tor.",
                "Dense fine yellow-silver stippling across leaf surface and delicate webbing underneath.",
                "Мелкая жёлто-серебристая крапчатость листа и тонкая паутина снизу.",
            ),
            weights = VisualFeatureWeights(yellowChlorosis = 0.45f, whitePowdery = 0.25f, spotContrast = 0.30f),
            referenceImageUrl = commonsImage("Tetranychus_urticae_with_silk_threads.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "cucumber_powdery_mildew",
            patternType = VisualPatternType.POWDERY_WHITE_COATING,
            plantPart = partLeafBoth(),
            visualCue = LocalizedText(
                "Yarpaq üstündə dairəvi ağ təbaşirvari ləkələrin bütün səthi örtməsi və sonra saralıb quruması.",
                "Circular chalk-white powdery patches merging across the leaf blade.",
                "Округлые белые мучнистые пятна, сливающиеся на всей поверхности листа.",
            ),
            weights = VisualFeatureWeights(whitePowdery = 0.72f, yellowChlorosis = 0.18f, spotContrast = 0.10f),
            referenceImageUrl = commonsImage("Powderymildewwatermelon.jpg"),
        ),

        // BUĞDA / WHEAT
        DiseaseVisualProfile(
            threatId = "wheat_rust_septoria",
            patternType = VisualPatternType.RUST_ORANGE_PUSTULES,
            plantPart = partSpike(),
            visualCue = LocalizedText(
                "Qəhvəyi-narıncı dağınıq pustullar (qonur pas) və ya uzunsov sarı-qonur ləkə içində xırda qara nöqtələr (septorioz).",
                "Reddish-orange scattered spore pustules or elongated tan lesions speckled with black pycnidia.",
                "Буро-оранжевые разбросанные пустулы или бурые пятна с чёрными точками пикнид.",
            ),
            weights = VisualFeatureWeights(rustOrange = 0.55f, brownDark = 0.25f, spotContrast = 0.20f),
            referenceImageUrl = commonsImage("Wheat_leaf_rust_on_wheat.jpg"),
            imageAttribution = "Wikimedia Commons • USDA ARS",
        ),
        DiseaseVisualProfile(
            threatId = "wheat_sunn_pest",
            patternType = VisualPatternType.CHEWING_HOLES_MINES,
            plantPart = partSpike(),
            visualCue = LocalizedText(
                "Sünbüllərin vaxtından əvvəl ağarması ('ağ sünbül'), bayraq yarpaqda sorulmuş ləkə izləri və cılız dən.",
                "Bleached white empty wheat spikes ('white heads') above feeding punctures and shriveled grain.",
                "Белоколосость, следы уколов на стебле/колосе и щуплое деформированное зерно.",
            ),
            weights = VisualFeatureWeights(yellowChlorosis = 0.45f, whitePowdery = 0.35f, spotContrast = 0.20f),
            referenceImageUrl = commonsImage("Eurygaster_integriceps.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "wheat_yellow_rust_stem",
            patternType = VisualPatternType.RUST_ORANGE_PUSTULES,
            plantPart = partLeafBoth(),
            visualCue = LocalizedText(
                "Damarlar boyu tikiş maşını xətti kimi düzülən parlaq limon-sarı və ya pas-qəhvəyi pustul zolaqları.",
                "Bright yellow-orange spore pustules aligned in parallel stripes along leaf veins.",
                "Яркие лимонно-жёлтые или ржавые пустулы, вытянутые ровными полосами вдоль жилок.",
            ),
            weights = VisualFeatureWeights(rustOrange = 0.55f, yellowChlorosis = 0.30f, spotContrast = 0.15f),
            referenceImageUrl = commonsImage("Stripe_rust_on_wheat.jpg"),
            imageAttribution = "Wikimedia Commons • USDA ARS",
        ),
        DiseaseVisualProfile(
            threatId = "wheat_powdery_mildew",
            patternType = VisualPatternType.POWDERY_WHITE_COATING,
            plantPart = partLeafLower(),
            visualCue = LocalizedText(
                "Alt qınlarda və yarpaq üstündə ağ-boz pambığabənzər yastıqcıqlar, sonradan qara xırda nöqtələr.",
                "Cottony white-gray mycelial cushions dotted later with dark pinhead cleistothecia.",
                "Бело-серые ватообразные подушечки на влагалищах листьев с чёрными точками позже.",
            ),
            weights = VisualFeatureWeights(whitePowdery = 0.65f, brownDark = 0.15f, spotContrast = 0.20f),
            referenceImageUrl = commonsImage("Blumeria_graminis_on_winter_wheat.JPG"),
        ),
        DiseaseVisualProfile(
            threatId = "wheat_fusarium_head_blight",
            patternType = VisualPatternType.RUST_ORANGE_PUSTULES,
            plantPart = partSpike(),
            visualCue = LocalizedText(
                "Yaşıl sünbüldə bir neçə sünbülcüyün ağarması və qabıq kənarında çəhrayı-qızılı kif zolağı.",
                "Bleached tan spikelets in an otherwise green head with salmon-pink mold along glume edges.",
                "Обесцвеченные колоски на зелёном колосе и розовато-оранжевый налёт у чешуек.",
            ),
            weights = VisualFeatureWeights(rustOrange = 0.40f, whitePowdery = 0.35f, yellowChlorosis = 0.25f),
            referenceImageUrl = commonsImage("Wheat_scab.jpg"),
            imageAttribution = "Wikimedia Commons • USDA ARS",
        ),
        DiseaseVisualProfile(
            threatId = "wheat_bunt_smut",
            patternType = VisualPatternType.DARK_NECROTIC_BLIGHT,
            plantPart = partSpike(),
            visualCue = LocalizedText(
                "Sünbülün içi və ya tamamı his kimi qara toz kütləsinə çevrilməsi, sünbül oxunun çılpaq qalması.",
                "Wheat heads completely transformed into black powdery soot masses replacing kernels.",
                "Превращение колоса и зёрен в чёрную сажистую споровую массу.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.70f, spotContrast = 0.30f),
            referenceImageUrl = commonsImage("Ustilago_tritici.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "wheat_tan_spot",
            patternType = VisualPatternType.CONCENTRIC_TARGET_SPOTS,
            plantPart = partLeafBoth(),
            visualCue = LocalizedText(
                "Yarpaqda mərkəzi tünd qəhvəyi nöqtəli, ətrafı parlaq sarı haləli linzavarı (gözcükşəkilli) ləkələr.",
                "Tan diamond/lens-shaped lesions with a dark central pinpoint and bright yellow halo.",
                "Линзовидные светло-бурые пятна с тёмным центром и ярко-жёлтой каймой.",
            ),
            weights = VisualFeatureWeights(yellowChlorosis = 0.45f, brownDark = 0.35f, spotContrast = 0.20f),
            referenceImageUrl = commonsImage("Triticale_tan_spot.JPG"),
        ),

        // ARPA / BARLEY
        DiseaseVisualProfile(
            threatId = "barley_smut_spot",
            patternType = VisualPatternType.CONCENTRIC_TARGET_SPOTS,
            plantPart = partSpike(),
            visualCue = LocalizedText(
                "Yarpaqlarda çarpaz qəhvəyi tor zolaqları və ya sünbüldə qara tozlu sürmə kütləsi.",
                "Dark brown net-like leaf streaks or black dusty smut replacing barley heads.",
                "Сетчатые тёмно-бурые полосы на листьях или чёрная пылящая головня колоса.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.60f, spotContrast = 0.25f, yellowChlorosis = 0.15f),
            referenceImageUrl = commonsImage("Pyrenophora_graminea_at_Hordeum_vulgare.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "barley_aphid",
            patternType = VisualPatternType.YELLOW_CHLOROSIS_MOSAIC,
            plantPart = partShoots(),
            visualCue = LocalizedText(
                "Üst yarpaqların və sünbül altının saralması, sıx yaşıl-qara mənənə topaları.",
                "Chlorotic yellowing of upper shoots with dense clusters of green/dark aphids.",
                "Пожелтение верхних листьев и плотные колонии тли на колосьях.",
            ),
            weights = VisualFeatureWeights(yellowChlorosis = 0.60f, spotContrast = 0.25f, brownDark = 0.15f),
            referenceImageUrl = commonsImage("Rhopalosiphum_padi.jpg"),
        ),

        // PAMBIQ / COTTON
        DiseaseVisualProfile(
            threatId = "cotton_bollworm",
            patternType = VisualPatternType.CHEWING_HOLES_MINES,
            plantPart = partFruit(),
            visualCue = LocalizedText(
                "Qönçə və qozalarda yumru dəliklər, çiçək tacının quruyub düşməsi və qara tırtıl tullantısı.",
                "Circular entry boreholes in cotton squares/bolls with dark larval frass outside.",
                "Круглые отверстия в бутонах и коробочках с тёмными экскрементами гусениц.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.50f, spotContrast = 0.35f, yellowChlorosis = 0.15f),
            referenceImageUrl = commonsImage("Helicoverpa_armigera_larva.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "cotton_aphid_mite",
            patternType = VisualPatternType.WEB_STIPPLING_SUCKING,
            plantPart = partLeafLower(),
            visualCue = LocalizedText(
                "Yarpaqlarda tunc-qırmızımtıl ləkələnmə (gənə) və qıvrılıb şirələnmə (mənənə).",
                "Reddish-bronze blotching across leaves from mites and cupped glossy foliage from aphids.",
                "Красновато-бронзовые пятна (клещ) и скручивание с липкой падью (тля).",
            ),
            weights = VisualFeatureWeights(rustOrange = 0.40f, yellowChlorosis = 0.35f, spotContrast = 0.25f),
            referenceImageUrl = commonsImage("Cotton_Aphids.jpg"),
        ),

        // KARTOF / POTATO
        DiseaseVisualProfile(
            threatId = "potato_colorado",
            patternType = VisualPatternType.CHEWING_HOLES_MINES,
            plantPart = partLeafBoth(),
            visualCue = LocalizedText(
                "Yarpaq ayasının kənarlardan tamamilə gəmirilib yeyilməsi, çəhrayı-narıncı sürfə və zolaqlı böcəklər.",
                "Ragged chewed foliage reduced to bare veins alongside orange larvae or striped beetles.",
                "Объеденная до жилок ботва с оранжевыми личинками и полосатыми жуками.",
            ),
            weights = VisualFeatureWeights(rustOrange = 0.45f, spotContrast = 0.35f, brownDark = 0.20f),
            referenceImageUrl = commonsImage("Leptinotarsa-decemlineata-beetle.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "potato_late_blight",
            patternType = VisualPatternType.DARK_NECROTIC_BLIGHT,
            plantPart = partTuberStem(),
            visualCue = LocalizedText(
                "Yarpaq kənarında sarı-yaşıl sərhədli geniş tünd-qara ləkə, gövdədə qara zolaq və altda ağ halə.",
                "Large dark-brown marginal blight patches bordered by pale green tissue and white mold underneath.",
                "Крупные буро-чёрные пятна по краям листа, почернение стеблей и белый налёт снизу.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.65f, whitePowdery = 0.15f, spotContrast = 0.20f),
            referenceImageUrl = commonsImage("Phytophthora_infestans_potato.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "potato_early_blight",
            patternType = VisualPatternType.CONCENTRIC_TARGET_SPOTS,
            plantPart = partLeafBoth(),
            visualCue = LocalizedText(
                "Damarlarla məhdudlaşan, içində aydın hədəf halqaları olan quru qəhvəyi-qara dairəvi ləkələr.",
                "Dry brown angular-round lesions etched with distinct concentric 'target-board' rings.",
                "Сухие буро-чёрные пятна с чёткими концентрическими кольцами («мишенями»).",
            ),
            weights = VisualFeatureWeights(brownDark = 0.50f, yellowChlorosis = 0.25f, spotContrast = 0.25f),
            referenceImageUrl = commonsImage("Alternaria_solani_IMG_1661.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "potato_rhizoctonia",
            patternType = VisualPatternType.SCAB_CANKER_GALL,
            plantPart = partTuberStem(),
            visualCue = LocalizedText(
                "Yumruda yuyanda getməyən qara torpaq ləkəsinə bənzər qabıqlar və gövdə kökündə ağ örtük.",
                "Black crusty sclerotia clinging like 'stuck dirt' to tubers plus white stem-base mycelium.",
                "Чёрные несмываемые коростинки на клубнях и «белая ножка» у основания стебля.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.60f, whitePowdery = 0.20f, spotContrast = 0.20f),
            referenceImageUrl = commonsImage("Black_scurf_of_potato_rizoktonioza_ziemniaka.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "potato_blackleg_soft_rot",
            patternType = VisualPatternType.DARK_NECROTIC_BLIGHT,
            plantPart = partTuberStem(),
            visualCue = LocalizedText(
                "Gövdə dibinin mürəkkəb-qara rəngdə yaş çürüməsi və təpə yarpaqların boru kimi yuxarı saralması.",
                "Inky-black wet decay of lower stems causing stiff upward rolling and yellowing of top leaves.",
                "Чернильно-чёрная мокрая гниль основания стебля и скручивание жёлтой верхушки.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.55f, yellowChlorosis = 0.35f, spotContrast = 0.10f),
            referenceImageUrl = commonsImage("Blackleg_of_Potato_Stem_Wilt.png"),
        ),
        DiseaseVisualProfile(
            threatId = "potato_common_scab",
            patternType = VisualPatternType.SCAB_CANKER_GALL,
            plantPart = partTuberStem(),
            visualCue = LocalizedText(
                "Kartof yumrusunun qabığında quru, nahamar, qabarıq və ya batıq ulduzvari qəhvəyi qabıqcıqlar.",
                "Dry corky brown star-shaped or pitted scabs roughening the potato skin surface.",
                "Сухие пробковые бурые язвы и выпуклые шероховатые корки на кожуре клубней.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.55f, spotContrast = 0.35f, yellowChlorosis = 0.10f),
            referenceImageUrl = commonsImage("Streptomyces_Scabies.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "potato_aphid_wireworm",
            patternType = VisualPatternType.YELLOW_CHLOROSIS_MOSAIC,
            plantPart = partTuberStem(),
            visualCue = LocalizedText(
                "Yarpaqlarda ləkəli sarı-açıq yaşıl mozaika, yarpaq qıvrılması (virus) və yumruda dəlik kanalları.",
                "Mottled yellow-green leaf mosaic, upward leaf roll, or clean round pinholes through tubers.",
                "Мозаичная жёлто-зелёная пятнистость, скручивание листьев и узкие ходы в клубнях.",
            ),
            weights = VisualFeatureWeights(yellowChlorosis = 0.60f, spotContrast = 0.25f, brownDark = 0.15f),
            referenceImageUrl = commonsImage("Myzus_persicae.jpg"),
        ),

        // ÜZÜM / GRAPE
        DiseaseVisualProfile(
            threatId = "grape_downy_mildew",
            patternType = VisualPatternType.ANGULAR_WATER_SOAKED,
            plantPart = partLeafLower(),
            visualCue = LocalizedText(
                "Yarpaq üstündə şəffaf sarımtıl 'yağ ləkəsi', eyni yerin alt səthində isə ağ-duzvari kif yastığı.",
                "Translucent yellow 'oil spots' on upper leaf surface paired with dense white downy mold underneath.",
                "Светло-жёлтые «маслянистые пятна» сверху и плотный белый пушок точно под ними снизу.",
            ),
            weights = VisualFeatureWeights(yellowChlorosis = 0.40f, whitePowdery = 0.40f, brownDark = 0.20f),
            referenceImageUrl = commonsImage("Plasmopara_viticola_305150900.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "grape_oidium",
            patternType = VisualPatternType.POWDERY_WHITE_COATING,
            plantPart = partFruit(),
            visualCue = LocalizedText(
                "Gilə və yarpaqlarda boz-küllü toz örtüyü, yetişmədə gilələrin partlayıb çəyirdəyinin görünməsi.",
                "Ash-gray floury coating on leaves and berries causing split skins showing exposed seeds.",
                "Серо-пепельный пылящий налёт и растрескивание ягод до семян.",
            ),
            weights = VisualFeatureWeights(whitePowdery = 0.70f, brownDark = 0.15f, spotContrast = 0.15f),
            referenceImageUrl = commonsImage("Erysiphe_necator_on_fruits.jpg"),
        ),

        // ALMA / APPLE
        DiseaseVisualProfile(
            threatId = "apple_scab_monilia",
            patternType = VisualPatternType.SCAB_CANKER_GALL,
            plantPart = partFruit(),
            visualCue = LocalizedText(
                "Yarpaq və almada məxməri zeytunu-qara dəmgil ləkələri və ya konsentrik boz spor yastıqlı meyvə çürüməsi.",
                "Velvety olive-black scab spots with cracking fruit or concentric tan spore rings of brown rot.",
                "Бархатистые оливково-чёрные пятна парши или бурая гниль с серыми кругами спор.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.65f, spotContrast = 0.25f, yellowChlorosis = 0.10f),
            referenceImageUrl = commonsImage("Venturia_inaequalis_01.JPG"),
        ),
        DiseaseVisualProfile(
            threatId = "apple_codling_aphid",
            patternType = VisualPatternType.CHEWING_HOLES_MINES,
            plantPart = partFruit(),
            visualCue = LocalizedText(
                "Alma meyvəsində qəhvəyi qurd girişi dəliyi və cavan zoğlarda qıvrılmış yaşıl yarpaq dəstələri.",
                "Plugged brown worm entry holes in fruit and tightly curled shoot-tip leaves from aphids.",
                "Червоточины в плодах с бурой трухой и скрученные листья на концах побегов.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.45f, yellowChlorosis = 0.30f, spotContrast = 0.25f),
            referenceImageUrl = commonsImage("Cydia.pomonella.7162.jpg"),
        ),

        // YONCA / ALFALFA
        DiseaseVisualProfile(
            threatId = "alfalfa_weevil",
            patternType = VisualPatternType.CHEWING_HOLES_MINES,
            plantPart = partShoots(),
            visualCue = LocalizedText(
                "Təpə yarpaqcıqlarının yaşıl toxumasının gəmirilib ağ şəbəkə halına salınması (tarla uzaqdan bozarmış görünür).",
                "Shredded skeletonized upper leaflets turning the canopy frosted white-gray.",
                "Объеденные скелетированные верхние листочки с белёсым оттенком поля.",
            ),
            weights = VisualFeatureWeights(whitePowdery = 0.45f, yellowChlorosis = 0.25f, spotContrast = 0.30f),
            referenceImageUrl = commonsImage("Hypera_postica_04.JPG"),
        ),
        DiseaseVisualProfile(
            threatId = "alfalfa_leaf_spot",
            patternType = VisualPatternType.CONCENTRIC_TARGET_SPOTS,
            plantPart = partLeafBoth(),
            visualCue = LocalizedText(
                "Hər yonca yarpaqcığında xırda (1–3 mm) tünd qəhvəyi-qara dairəvi nöqtələr və ətrafının sürətlə saralması.",
                "Peppered circular dark-brown pinhead lesions (1–3 mm) across leaflets causing rapid yellow drop.",
                "Мелкие (1–3 мм) округлые тёмно-бурые точки по всему листочку и пожелтение.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.45f, yellowChlorosis = 0.35f, spotContrast = 0.20f),
            referenceImageUrl = commonsImage("Pseudopeziza_medicaginis_83219857.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "alfalfa_downy_mildew",
            patternType = VisualPatternType.ANGULAR_WATER_SOAKED,
            plantPart = partShoots(),
            visualCue = LocalizedText(
                "Üst yarpaqların açıq limon-sarı rəngdə aşağı burulması və alt səthdə boz-bənövşəyi məxməri örtük.",
                "Pale greenish-yellow curled upper leaflets topped with violet-gray downy growth underneath.",
                "Бледно-жёлтые закрученные вниз листочки с серо-фиолетовым налётом снизу.",
            ),
            weights = VisualFeatureWeights(yellowChlorosis = 0.55f, whitePowdery = 0.30f, spotContrast = 0.15f),
            referenceImageUrl = commonsImage("Downy_mildew_(Peronospora_sparsa)_5487855.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "alfalfa_anthracnose_root",
            patternType = VisualPatternType.SCAB_CANKER_GALL,
            plantPart = partShoots(),
            visualCue = LocalizedText(
                "Gövdədə tünd haşiyəli açıq rombşəkilli yaralar, təpə zoğların çoban dəyənəyi kimi əyilib quruması.",
                "Diamond-shaped bleached stem cankers with black borders and 'shepherd's crook' wilted tips.",
                "Светлые ромбовидные язвы с тёмной каймой на стеблях и поникшая верхушка («крючок»).",
            ),
            weights = VisualFeatureWeights(brownDark = 0.45f, yellowChlorosis = 0.30f, whitePowdery = 0.15f, spotContrast = 0.10f),
            referenceImageUrl = commonsImage("Anthracnose_Colletotrichum_graminicola_(Ces.)_G.W._Wilson_1235209.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "alfalfa_rust",
            patternType = VisualPatternType.RUST_ORANGE_PUSTULES,
            plantPart = partLeafBoth(),
            visualCue = LocalizedText(
                "Yarpaq və gövdədə toxunanda barmağa bulaşan darçını-narıncı qabarıq pas tozcuqları.",
                "Raised cinnamon-rusty brown dusty pustules bursting through both leaf surfaces and stems.",
                "Выпуклые корично-рыжие пылящие пустулы на листьях и стеблях люцерны.",
            ),
            weights = VisualFeatureWeights(rustOrange = 0.65f, brownDark = 0.20f, yellowChlorosis = 0.15f),
            referenceImageUrl = commonsImage("Uromyces_viciae-fabae.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "alfalfa_dodder_aphid",
            patternType = VisualPatternType.PARASITIC_DODDER_THREADS,
            plantPart = partShoots(),
            visualCue = LocalizedText(
                "Yonca kollarına sarılan yarpaqsız parlaq narıncı-sarı parazit sapları (kuskuta) və ya şirəli saralma.",
                "Tangled leafless bright orange-yellow parasitic threads smothering patches (or sticky aphid curl).",
                "Спутанные ярко-оранжевые безлистные нити паразита (или жёлтое скручивание от тли).",
            ),
            weights = VisualFeatureWeights(rustOrange = 0.60f, yellowChlorosis = 0.30f, spotContrast = 0.10f),
            referenceImageUrl = commonsImage("Cuscuta_europaea_bgiu.jpg"),
        ),

        // FINDIQ / HAZELNUT
        DiseaseVisualProfile(
            threatId = "hazelnut_powdery_mildew",
            patternType = VisualPatternType.POWDERY_WHITE_COATING,
            plantPart = partLeafBoth(),
            visualCue = LocalizedText(
                "Fındıq yarpaqlarında və yaşıl qərzəkdə ağ-boz tozlu örtük, yarpaq qıvrılması və qəhvəyiləşmə.",
                "White-gray dusty mycelium patches across hazelnut leaves and husks followed by edge browning.",
                "Бело-серый мучнистый налёт на листьях и плюске фундука.",
            ),
            weights = VisualFeatureWeights(whitePowdery = 0.70f, yellowChlorosis = 0.15f, brownDark = 0.15f),
            referenceImageUrl = commonsImage("Phyllactinia_guttata_a1_(4).jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "hazelnut_bacterial_blight",
            patternType = VisualPatternType.DARK_NECROTIC_BLIGHT,
            plantPart = partShoots(),
            visualCue = LocalizedText(
                "Gənc yarpaqlarda qırmızımtıl-qəhvəyi küncşəkilli ləkələr, budaq uclarının qara yanması.",
                "Water-soaked reddish-brown angular leaf spots paired with blackened dead shoot tips and buds.",
                "Бурые угловатые мокнущие пятна на листьях и чёрный ожог верхушечных побегов.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.65f, rustOrange = 0.15f, spotContrast = 0.20f),
            referenceImageUrl = commonsImage("Bacterial_leaf_spot_symptoms.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "hazelnut_brown_rot",
            patternType = VisualPatternType.CONCENTRIC_TARGET_SPOTS,
            plantPart = partFruit(),
            visualCue = LocalizedText(
                "Fındıq qərzəyində qəhvəyi çürümə və üzərində halqa şəklində düzülən boz-sarı kif qabarcıqları.",
                "Spreading brown husk/nut rot studded with concentric buff-gray spore tufts.",
                "Бурая гниль скорлупы и плюски с концентрическими серо-жёлтыми подушечками спор.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.55f, yellowChlorosis = 0.20f, whitePowdery = 0.15f, spotContrast = 0.10f),
            referenceImageUrl = commonsImage("Monilinia_fructigena_01.JPG"),
        ),
        DiseaseVisualProfile(
            threatId = "hazelnut_weevil_bud_mite",
            patternType = VisualPatternType.SCAB_CANKER_GALL,
            plantPart = partShoots(),
            visualCue = LocalizedText(
                "Budaqlarda kələm başı kimi şişmiş açılmayan 'böyük tumurcuqlar' və qabıqda yuvarlaq dəlik.",
                "Swollen cabbage-like 'big buds' that fail to open, or neat circular beetle exit holes on shells.",
                "Вздутые кочаноподобные «большие почки» весной или круглые отверстия на скорлупе.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.45f, yellowChlorosis = 0.25f, spotContrast = 0.30f),
            referenceImageUrl = commonsImage("Phytoptus_avellanae_-_close_up_of_big_bud.JPG"),
        ),

        // QARĞIDALI / CORN
        DiseaseVisualProfile(
            threatId = "corn_borer",
            patternType = VisualPatternType.CHEWING_HOLES_MINES,
            plantPart = partShoots(),
            visualCue = LocalizedText(
                "Yarpaq boyunca düz cərgə halında yumru deşiklər ('güllə deşiyi') və gövdə qırılması yerində uncuq.",
                "Straight rows of shot-holes across unrolled leaves and sawdust-like frass at stalk holes.",
                "Ровные ряды круглых отверстий на листьях и буровая мука у входа в стебель.",
            ),
            weights = VisualFeatureWeights(spotContrast = 0.45f, brownDark = 0.35f, yellowChlorosis = 0.20f),
            referenceImageUrl = commonsImage("Ostrinia_nubilalis01.jpg"),
        ),
        DiseaseVisualProfile(
            threatId = "corn_leaf_blight_smut",
            patternType = VisualPatternType.DARK_NECROTIC_BLIGHT,
            plantPart = partLeafBoth(),
            visualCue = LocalizedText(
                "Yarpaqda uzun siqaret formalı boz-qəhvəyi ləkələr (helmintosporioz) və qıçada gümüşü-qara şiş.",
                "Long cigar-shaped gray-tan leaf lesions or large silvery galls bursting into black smut spores.",
                "Удлинённые сигарообразные серо-бурые пятна или крупные серебристо-чёрные вздутия.",
            ),
            weights = VisualFeatureWeights(brownDark = 0.60f, whitePowdery = 0.20f, spotContrast = 0.20f),
            referenceImageUrl = commonsImage("Northern_corn_leaf_blight.JPG"),
        ),
    ).associateBy { it.threatId }

    fun profileFor(threatId: String): DiseaseVisualProfile? = profilesByThreatId[threatId]

    fun patternLabel(pattern: VisualPatternType, language: AppLanguage): String = when (pattern) {
        VisualPatternType.DARK_NECROTIC_BLIGHT -> when (language) {
            AppLanguage.AZ -> "Tünd nekroz / Yanğın ləkəsi"
            AppLanguage.EN -> "Dark Necrotic Blight"
            AppLanguage.RU -> "Тёмный некроз / Фитофтора"
        }
        VisualPatternType.CONCENTRIC_TARGET_SPOTS -> when (language) {
            AppLanguage.AZ -> "Konsentrik halqalı / Dairəvi ləkə"
            AppLanguage.EN -> "Concentric / Target Spots"
            AppLanguage.RU -> "Концентрическая / Пятнистость"
        }
        VisualPatternType.POWDERY_WHITE_COATING -> when (language) {
            AppLanguage.AZ -> "Ağ-boz unlu toz örtüyü"
            AppLanguage.EN -> "White Powdery Coating"
            AppLanguage.RU -> "Белый мучнистый налёт"
        }
        VisualPatternType.RUST_ORANGE_PUSTULES -> when (language) {
            AppLanguage.AZ -> "Narıncı-darçını pas pustulları"
            AppLanguage.EN -> "Orange-Rust Pustules"
            AppLanguage.RU -> "Ржавчинно-оранжевые пустулы"
        }
        VisualPatternType.YELLOW_CHLOROSIS_MOSAIC -> when (language) {
            AppLanguage.AZ -> "Saralma / Xloroz / Mozaika"
            AppLanguage.EN -> "Yellow Chlorosis / Mosaic"
            AppLanguage.RU -> "Хлороз / Пожелтение / Мозаика"
        }
        VisualPatternType.ANGULAR_WATER_SOAKED -> when (language) {
            AppLanguage.AZ -> "Damararası küncşəkilli ləkə"
            AppLanguage.EN -> "Vein-Bounded Angular Spots"
            AppLanguage.RU -> "Угловатые пятна между жилками"
        }
        VisualPatternType.SCAB_CANKER_GALL -> when (language) {
            AppLanguage.AZ -> "Qabıq / Xora / Şişkinlik"
            AppLanguage.EN -> "Scab / Canker / Swollen Bud"
            AppLanguage.RU -> "Парша / Язва / Вздутие"
        }
        VisualPatternType.CHEWING_HOLES_MINES -> when (language) {
            AppLanguage.AZ -> "Gəmirilmə / Deşik / Yarpaq minası"
            AppLanguage.EN -> "Chewing Holes / Leaf Mines"
            AppLanguage.RU -> "Погрызы / Отверстия / Мины"
        }
        VisualPatternType.WEB_STIPPLING_SUCKING -> when (language) {
            AppLanguage.AZ -> "Xırda nöqtələnmə / Tor / Şirə"
            AppLanguage.EN -> "Fine Stippling / Webbing"
            AppLanguage.RU -> "Крапчатость / Паутинка / Падь"
        }
        VisualPatternType.PARASITIC_DODDER_THREADS -> when (language) {
            AppLanguage.AZ -> "Narıncı parazit sapları"
            AppLanguage.EN -> "Orange Parasitic Threads"
            AppLanguage.RU -> "Оранжевые нити паразита"
        }
    }
}
