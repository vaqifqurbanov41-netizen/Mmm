package com.example.data.model

import com.example.i18n.AppLanguage

data class LocalizedText(
    val az: String,
    val en: String,
    val ru: String,
) {
    fun get(language: AppLanguage): String = when (language) {
        AppLanguage.AZ -> az
        AppLanguage.EN -> en
        AppLanguage.RU -> ru
    }
}

data class AgriRegion(
    val id: String,
    val name: LocalizedText,
    val districts: LocalizedText,
    val climateNote: LocalizedText,
)

data class SeedCrop(
    val id: String,
    val name: LocalizedText,
)

data class SeedVariety(
    val id: String,
    val cropId: String,
    val regionIds: List<String>,
    val name: String,
    val badge: LocalizedText,
    val summary: LocalizedText,
    val origin: LocalizedText,
    val sowingWindow: LocalizedText,
    val seedingRate: LocalizedText,
    val vegetationPeriod: LocalizedText,
    val yieldPotential: LocalizedText,
    val resistance: LocalizedText,
    val soilAndIrrigation: LocalizedText,
    val agronomyTip: LocalizedText,
)

object SeedCatalog {
    val regions: List<AgriRegion> = listOf(
        AgriRegion(
            id = "aran",
            name = LocalizedText("Mərkəzi Aran", "Central Aran", "Центральный Аран"),
            districts = LocalizedText(
                "Ağcabədi, Bərdə, Yevlax, Sabirabad, İmişli, Kürdəmir, Beyləqan",
                "Aghjabadi, Barda, Yevlakh, Sabirabad, Imishli, Kurdamir, Beylagan",
                "Агджабеди, Барда, Евлах, Сабирабад, Имишли, Кюрдамир, Бейлаган",
            ),
            climateNote = LocalizedText(
                "Quru subtropik, isti yay — suvarılan və şoranlığa dözümlü sortlar üstünlük təşkil edir.",
                "Dry subtropical, warm summer — irrigated and salt/drought-tolerant varieties thrive.",
                "Сухой субтропический климат — подходят орошаемые и соле-/засухоустойчивые сорта.",
            ),
        ),
        AgriRegion(
            id = "shirvan_mugan",
            name = LocalizedText("Şirvan – Muğan", "Shirvan – Mughan", "Ширван – Мугань"),
            districts = LocalizedText(
                "Şirvan, Salyan, Neftçala, Biləsuvar, Saatlı, Hacıqabul",
                "Shirvan, Salyan, Neftchala, Bilasuvar, Saatli, Hajigabul",
                "Ширван, Сальян, Нефтчала, Билясувар, Саатлы, Гаджигабул",
            ),
            climateNote = LocalizedText(
                "İsti düzənlik və az yağıntı — duzlaşmaya davamlı pambıq, arpa və payızlıq buğda sortları tövsiyə olunur.",
                "Warm plains with low rainfall — salt-tolerant cotton, barley, and winter wheat perform best.",
                "Тёплая равнина с малыми осадками — оптимальны солеустойчивые сорта хлопчатника, ячменя и пшеницы.",
            ),
        ),
        AgriRegion(
            id = "ganja_gazakh",
            name = LocalizedText("Gəncə – Qazax", "Ganja – Gazakh", "Гянджа – Казах"),
            districts = LocalizedText(
                "Gəncə, Şəmkir, Tovuz, Qazax, Göygöl, Samux, Goranboy",
                "Ganja, Shamkir, Tovuz, Gazakh, Goygol, Samukh, Goranboy",
                "Гянджа, Шамкир, Товуз, Казах, Гёйгёль, Самух, Геранбой",
            ),
            climateNote = LocalizedText(
                "Münbit dağətəyi və düzənlik — kartof, üzüm, tərəvəz, buğda və pambıq üçün yüksək məhsuldar zona.",
                "Fertile foothills & plains — high-yield zone for potatoes, grapes, tomatoes, wheat, and cotton.",
                "Плодородные предгорья и равнины — зона высокой урожайности картофеля, винограда, томатов и пшеницы.",
            ),
        ),
        AgriRegion(
            id = "quba_khachmaz",
            name = LocalizedText("Quba – Xaçmaz", "Guba – Khachmaz", "Губа – Хачмаз"),
            districts = LocalizedText(
                "Quba, Xaçmaz, Qusar, Şabran, Siyəzən",
                "Guba, Khachmaz, Gusar, Shabran, Siyazan",
                "Губа, Хачмаз, Гусар, Шабран, Сиязань",
            ),
            climateNote = LocalizedText(
                "Mülayim rütubətli və sərin dağətəyi — meyvəçilik, şaxtaya davamlı buğda, kartof və tərəvəz zonası.",
                "Temperate humid foothills — ideal for orchards, frost-hardy winter wheat, potatoes, and vegetables.",
                "Умеренно-влажные предгорья — зона садоводства, зимостойкой пшеницы, картофеля и овощей.",
            ),
        ),
        AgriRegion(
            id = "shaki_zagatala",
            name = LocalizedText("Şəki – Zaqatala", "Shaki – Zagatala", "Шеки – Закаталы"),
            districts = LocalizedText(
                "Şəki, Qax, Zaqatala, Balakən, Oğuz, Qəbələ",
                "Shaki, Gakh, Zagatala, Balakan, Oghuz, Gabala",
                "Шеки, Гах, Закаталы, Балакен, Огуз, Габала",
            ),
            climateNote = LocalizedText(
                "Yağıntısı bol dağətəyi — qarğıdalı, fındıq, payızlıq taxıllar və dəmyə/yarımsuvarılan əkinlər.",
                "High-rainfall foothill valleys — optimal for maize, hazelnut orchards, winter grains, and potatoes.",
                "Влажные предгорья — идеальны для кукурузы, фундука, озимых зерновых и картофеля.",
            ),
        ),
        AgriRegion(
            id = "lankaran_astara",
            name = LocalizedText("Lənkəran – Astara", "Lankaran – Astara", "Ленкорань – Астара"),
            districts = LocalizedText(
                "Lənkəran, Astara, Masallı, Lerik, Cəlilabad",
                "Lankaran, Astara, Masalli, Lerik, Jalilabad",
                "Ленкорань, Астара, Масаллы, Лерик, Джалилабад",
            ),
            climateNote = LocalizedText(
                "Rütubətli subtropik — çəltik, sitrus bağları, erkən kartof, pomidor və Cəlilabadda dəmyə taxılçılıq.",
                "Humid subtropical & foothill — rice paddies, citrus, early potatoes, tomatoes, and Jalilabad cereals.",
                "Влажные субтропики и предгорья — рис, цитрусовые, ранний картофель, томаты и зерновые.",
            ),
        ),
    )

    val crops: List<SeedCrop> = listOf(
        SeedCrop("wheat", LocalizedText("Buğda", "Wheat", "Пшеница")),
        SeedCrop("barley", LocalizedText("Arpa", "Barley", "Ячмень")),
        SeedCrop("cotton", LocalizedText("Pambıq", "Cotton", "Хлопчатник")),
        SeedCrop("corn", LocalizedText("Qarğıdalı", "Maize / Corn", "Кукуруза")),
        SeedCrop("potato", LocalizedText("Kartof", "Potato", "Картофель")),
        SeedCrop("tomato", LocalizedText("Pomidor", "Tomato", "Томат")),
        SeedCrop("alfalfa", LocalizedText("Yonca", "Alfalfa", "Люцерна")),
        SeedCrop("rice", LocalizedText("Çəltik (Düyü)", "Rice", "Рис")),
        SeedCrop("orchard", LocalizedText("Bağçılıq (Üzüm / Alma / Fındıq / Sitrus)", "Orchard (Grape / Apple / Hazelnut / Citrus)", "Сады (Виноград / Яблоня / Фундук / Цитрус)")),
    )

    val varieties: List<SeedVariety> = listOf(
        SeedVariety(
            id = "gobustan",
            cropId = "wheat",
            regionIds = listOf("aran", "shirvan_mugan", "ganja_gazakh", "shaki_zagatala", "lankaran_astara"),
            name = "Qobustan",
            badge = LocalizedText("Yüksək məhsuldar • Quraqlığa davamlı", "High Yield • Drought Tolerant", "Высокий урожай • Засухоустойчивый"),
            summary = LocalizedText(
                "Azərbaycanın suvarılan və yarımtəmin olunmuş dəmyə sahələri üçün ən geniş yayılmış yumşaq buğda sortu.",
                "Widely adapted bread wheat cultivar for irrigated and semi-arid regions of Azerbaijan.",
                "Широко районированный сорт мягкой озимой пшеницы для орошаемых и полузасушливых зон.",
            ),
            origin = LocalizedText(
                "Əkinçilik Elmi-Tədqiqat İnstitutu (Azərbaycan)",
                "Research Institute of Crop Husbandry (Azerbaijan)",
                "НИИ Земледелия (Азербайджан)",
            ),
            sowingWindow = LocalizedText(
                "15 oktyabr – 15 noyabr",
                "Oct 15 – Nov 15",
                "15 октября – 15 ноября",
            ),
            seedingRate = LocalizedText(
                "200 – 220 kq/ha (təqribən 4.5 – 5.0 milyon dən/ha)",
                "200 – 220 kg/ha (~4.5 – 5.0M seeds/ha)",
                "200 – 220 кг/га (~4.5 – 5.0 млн зёрен/га)",
            ),
            vegetationPeriod = LocalizedText(
                "210 – 225 gün (ortatezyetişən)",
                "210 – 225 days (medium-early)",
                "210 – 225 дней (среднеранний)",
            ),
            yieldPotential = LocalizedText(
                "55 – 70 sentner/ha (suvarma şəraitində 75 s/ha-dək)",
                "5.5 – 7.0 t/ha (up to 7.5 t/ha under full irrigation)",
                "55 – 70 ц/га (при орошении до 75 ц/га)",
            ),
            resistance = LocalizedText(
                "Sarı və qonur pasa orta-yüksək davamlılıq, yatmaya və quraqlığa davamlı.",
                "Good tolerance to yellow/leaf rust, lodging, and late-spring heat.",
                "Устойчив к жёлтой и бурой ржавчине, полеганию и засухе.",
            ),
            soilAndIrrigation = LocalizedText(
                "Boz-çəmən və şabalıdı torpaqlar; 2–3 vegetasiya suvarması yüksək dən dolğunluğu təmin edir.",
                "Grey-meadow and chestnut soils; 2–3 seasonal irrigations ensure plump grain fill.",
                "Серо-луговые и каштановые почвы; 2–3 вегетационных полива дают высокий налив зерна.",
            ),
            agronomyTip = LocalizedText(
                "Səpin zamanı hektara 60–80 kq P₂O₅, yaz kollanmasında isə 70–90 kq aktiv azot (karbamid/şora) tətbiq edin.",
                "Apply 60–80 kg/ha P₂O₅ at sowing and top-dress 70–90 kg/ha N in early spring tillering.",
                "При посеве внесите 60–80 кг/га P₂O₅, а весной в кущение — 70–90 кг/га д.в. азота.",
            ),
        ),
        SeedVariety(
            id = "bereketli_95",
            cropId = "wheat",
            regionIds = listOf("aran", "shirvan_mugan", "ganja_gazakh"),
            name = "Bərəkətli-95",
            badge = LocalizedText("Bərk buğda • Yüksək kleykovina", "Durum Wheat • High Gluten", "Твёрдая пшеница • Высокая клейковина"),
            summary = LocalizedText(
                "Aran və Gəncə-Qazax suvarılan sahələrində yüksək keyfiyyətli makaronluq bərk buğda sortu.",
                "Premium durum wheat variety suited to irrigated Aran and Ganja-Gazakh plains.",
                "Высококачественный сорт твёрдой пшеницы для орошаемых равнин Арана и Гянджа-Казаха.",
            ),
            origin = LocalizedText(
                "Əkinçilik ETİ & ICARDA birgə seleksiyası",
                "Research Institute of Crop Husbandry & ICARDA",
                "НИИ Земледелия и ICARDA",
            ),
            sowingWindow = LocalizedText(
                "20 oktyabr – 20 noyabr",
                "Oct 20 – Nov 20",
                "20 октября – 20 ноября",
            ),
            seedingRate = LocalizedText(
                "210 – 230 kq/ha",
                "210 – 230 kg/ha",
                "210 – 230 кг/га",
            ),
            vegetationPeriod = LocalizedText(
                "215 – 230 gün",
                "215 – 230 days",
                "215 – 230 дней",
            ),
            yieldPotential = LocalizedText(
                "55 – 68 sentner/ha (dən şüşəvariliyi 85–90%)",
                "5.5 – 6.8 t/ha (85–90% vitreousness)",
                "55 – 68 ц/га (стекловидность зерна 85–90%)",
            ),
            resistance = LocalizedText(
                "Yatmaya, quraqlığa və əsas göbələk xəstəliklərinə davamlıdır.",
                "Strong stem against lodging; tolerant to heat and common rusts.",
                "Устойчив к полеганию, жаре и основным видам ржавчины.",
            ),
            soilAndIrrigation = LocalizedText(
                "Münbit suvarılan sahələrdə 2–3 suvarma (kollanma, boruya çıxma, dən dolması) tələb edir.",
                "Requires fertile irrigated soil with 2–3 well-timed irrigations.",
                "Требует плодородных почв и 2–3 своевременных поливов (кущение, выход в трубку, налив).",
            ),
            agronomyTip = LocalizedText(
                "Bərk buğdada şüşəvariliyi artırmaq üçün boruya çıxma fazasında əlavə azot yemləməsi effektlidir.",
                "Split nitrogen with a booting top-dress improves grain protein and vitreousness.",
                "Дробная азотная подкормка в фазу трубкования повышает стекловидность и белок.",
            ),
        ),
        SeedVariety(
            id = "murov_2",
            cropId = "wheat",
            regionIds = listOf("quba_khachmaz", "shaki_zagatala", "lankaran_astara", "ganja_gazakh"),
            name = "Murov-2",
            badge = LocalizedText("Şaxtaya və pasa davamlı", "Frost & Rust Resistant", "Морозо- и ржавчиноустойчивый"),
            summary = LocalizedText(
                "Dağətəyi və rütubətli bölgələrdə qış şaxtasına və sarı pasa yüksək davamlılığı ilə seçilir.",
                "Adapted to foothill & humid zones with strong winter hardiness and yellow rust resistance.",
                "Предгорный сорт, отличающийся высокой зимостойкостью и защитой от жёлтой ржавчины.",
            ),
            origin = LocalizedText(
                "Əkinçilik Elmi-Tədqiqat İnstitutu",
                "Research Institute of Crop Husbandry",
                "НИИ Земледелия (Азербайджан)",
            ),
            sowingWindow = LocalizedText(
                "5 oktyabr – 5 noyabr (dağətəyində daha erkən)",
                "Oct 5 – Nov 5 (earlier in uplands)",
                "5 октября – 5 ноября (в предгорьях раньше)",
            ),
            seedingRate = LocalizedText(
                "210 – 240 kq/ha",
                "210 – 240 kg/ha",
                "210 – 240 кг/га",
            ),
            vegetationPeriod = LocalizedText(
                "225 – 240 gün",
                "225 – 240 days",
                "225 – 240 дней",
            ),
            yieldPotential = LocalizedText(
                "52 – 65 sentner/ha",
                "5.2 – 6.5 t/ha",
                "52 – 65 ц/га",
            ),
            resistance = LocalizedText(
                "Sarı pasa, unlu şehə və şaxtaya (-18°C qar örtüyü altında) yüksək davamlı.",
                "High resistance to yellow rust, powdery mildew, and winter frost.",
                "Высокая устойчивость к жёлтой ржавчине, мучнистой росе и зимним заморозкам.",
            ),
            soilAndIrrigation = LocalizedText(
                "Dəmyə və yarımsuvarılan meşə-dağ torpaqlarında stabil məhsul verir.",
                "Performs steadily on rainfed and foothill forest-chestnut soils.",
                "Стабильно плодоносит на богарных и предгорных лесных/каштановых почвах.",
            ),
            agronomyTip = LocalizedText(
                "Rütubətli bölgələrdə toxumları səpindən öncə fungisidlə dərmanlamaq çıxışı 12–15% yaxşılaşdırır.",
                "Seed treatment with a registered fungicide prior to sowing protects emergence in wet soils.",
                "Протравливание семян перед посевом надёжно защищает всходы во влажной почве.",
            ),
        ),
        SeedVariety(
            id = "celilabad_19",
            cropId = "barley",
            regionIds = listOf("aran", "shirvan_mugan", "lankaran_astara", "shaki_zagatala"),
            name = "Cəlilabad-19",
            badge = LocalizedText("Dəmyə və quraqlıq çempionu", "Drought & Salinity Hardy", "Засухо- и солеустойчивый"),
            summary = LocalizedText(
                "Az yağıntılı və şoranlığa meyilli ərazilərdə belə yüksək yem və dən məhsulu verən payızlıq arpa.",
                "Six-row winter barley well suited for dryland Jalilabad, Mughan, and Aran areas.",
                "Озимый ячмень, дающий стабильный урожай на богарных и засушливых землях.",
            ),
            origin = LocalizedText(
                "Əkinçilik ETİ – Cəlilabad Zona Təcrübə Stansiyası",
                "Crop Husbandry Institute – Jalilabad Station",
                "Джалилабадская опытная станция НИИ Земледелия",
            ),
            sowingWindow = LocalizedText(
                "10 oktyabr – 10 noyabr",
                "Oct 10 – Nov 10",
                "10 октября – 10 ноября",
            ),
            seedingRate = LocalizedText(
                "170 – 190 kq/ha",
                "170 – 190 kg/ha",
                "170 – 190 кг/га",
            ),
            vegetationPeriod = LocalizedText(
                "190 – 205 gün (tez yetişir)",
                "190 – 205 days (early maturing)",
                "190 – 205 дней (раннеспелый)",
            ),
            yieldPotential = LocalizedText(
                "42 – 55 sentner/ha",
                "4.2 – 5.5 t/ha",
                "42 – 55 ц/га",
            ),
            resistance = LocalizedText(
                "Quraqlığa, isti küləklərə və zolaqlı ləkəliyə yüksək davamlı.",
                "Very hardy against spring drought, hot winds, and barley stripe.",
                "Высокая устойчивость к весенней засухе, суховеям и полосатой пятнистости.",
            ),
            soilAndIrrigation = LocalizedText(
                "Suvarılmayan dəmyə və yüngül şorlaşmış torpaqlar üçün idealdır.",
                "Ideal for rainfed fields and lightly saline plains.",
                "Идеален для богарных полей и слабозасолённых почв.",
            ),
            agronomyTip = LocalizedText(
                "Buğdadan 10–15 gün tez yetişdiyi üçün yay istilərindən əvvəl dən tökməsini tamamlayır.",
                "Maturing 10–15 days earlier than wheat helps escape peak June heat.",
                "Созревает на 10–15 дней раньше пшеницы, избегая летней жары.",
            ),
        ),
        SeedVariety(
            id = "deyanetli",
            cropId = "barley",
            regionIds = listOf("aran", "shirvan_mugan", "ganja_gazakh", "shaki_zagatala"),
            name = "Dəyanətli",
            badge = LocalizedText("Yatmaya davamlı • İri dənli", "Lodging Resistant", "Устойчив к полеганию"),
            summary = LocalizedText(
                "Yüksək kollanma qabiliyyətinə malik, mexaniki yığıma tam əlverişli payızlıq arpa sortu.",
                "High-tillering winter barley with sturdy straw ideal for combine harvesting.",
                "Высококустистый озимый ячмень с прочной соломиной для комбайновой уборки.",
            ),
            origin = LocalizedText("Əkinçilik Elmi-Tədqiqat İnstitutu", "Research Institute of Crop Husbandry", "НИИ Земледелия (Азербайджан)"),
            sowingWindow = LocalizedText("15 oktyabr – 15 noyabr", "Oct 15 – Nov 15", "15 октября – 15 ноября"),
            seedingRate = LocalizedText("180 – 200 kq/ha", "180 – 200 kg/ha", "180 – 200 кг/га"),
            vegetationPeriod = LocalizedText("195 – 210 gün", "195 – 210 days", "195 – 210 дней"),
            yieldPotential = LocalizedText("48 – 60 sentner/ha", "4.8 – 6.0 t/ha", "48 – 60 ц/га"),
            resistance = LocalizedText("Yatmaya, şaxtaya və tozlu sürməyə davamlı.", "Good lodging, cold, and loose smut resistance.", "Устойчив к полеганию, морозу и пыльной головне."),
            soilAndIrrigation = LocalizedText("Orta münbit torpaqlar; 1–2 suvarma ilə maksimum nəticə verir.", "Medium-fertile soils; 1–2 irrigations boost yield.", "Среднеплодородные почвы; 1–2 полива повышают отдачу."),
            agronomyTip = LocalizedText("Səpin dərinliyi 4–5 sm saxlanmalıdır; erkən yaz alaq otlarına qarşı vaxtında mübarizə aparın.", "Sow at 4–5 cm depth and control broadleaf weeds early in spring.", "Соблюдайте глубину посева 4–5 см и вовремя удаляйте сорняки весной."),
        ),
        SeedVariety(
            id = "gence_110",
            cropId = "cotton",
            regionIds = listOf("aran", "shirvan_mugan", "ganja_gazakh"),
            name = "Gəncə-110",
            badge = LocalizedText("Yüksək lif çıxımı • 38–40%", "High Lint % (38–40%)", "Высокий выход волокна"),
            summary = LocalizedText(
                "Aran və Şirvan zonasında yüksək lif keyfiyyəti və vaxtında açılan qozaları ilə seçilən pambıq sortu.",
                "Leading Azerbaijani upland cotton cultivar prized for high lint percentage and uniform boll opening.",
                "Ведущий азербайджанский сорт хлопчатника с высоким выходом и качеством волокна.",
            ),
            origin = LocalizedText(
                "Bitki Mühafizə və Texniki Bitkilər ETİ (Gəncə)",
                "Plant Protection & Industrial Crops Institute (Ganja)",
                "НИИ Защиты растений и технических культур (Гянджа)",
            ),
            sowingWindow = LocalizedText("5 aprel – 25 aprel (torpaq 14–15°C olduqda)", "Apr 5 – Apr 25 (soil >= 14–15°C)", "5 – 25 апреля (почва >= 14–15°C)"),
            seedingRate = LocalizedText("25 – 35 kq/ha (tüksüzləşdirilmiş dəqiq səpin)", "25 – 35 kg/ha (delinted precision seeding)", "25 – 35 кг/га (оголённые семена)"),
            vegetationPeriod = LocalizedText("118 – 125 gün (tezyetişən)", "118 – 125 days (early)", "118 – 125 дней (раннеспелый)"),
            yieldPotential = LocalizedText("40 – 50 sentner/ha (xam pambıq)", "4.0 – 5.0 t/ha seed cotton", "40 – 50 ц/га хлопка-сырца"),
            resistance = LocalizedText("Vilt xəstəliyinə (Verticillium) və şoran torpaqlara yüksək dözümlülük.", "Strong tolerance to Verticillium wilt and mild soil salinity.", "Устойчив к вилту (Verticillium) и умеренному засолению."),
            soilAndIrrigation = LocalizedText("Qönçələmə–çiçəkləmə dövründə 3–4 dəfə müntəzəm suvarma vacibdir.", "3–4 scheduled irrigations from squaring through boll fill.", "Требует 3–4 поливов в период бутонизации и цветения."),
            agronomyTip = LocalizedText("Kompakt kol quruluşuna görə kombaynla pambıq yığımı üçün çox əlverişlidir.", "Compact plant architecture makes it well adapted to mechanical picker harvesting.", "Компактный куст идеально подходит для машинной уборки урожая."),
        ),
        SeedVariety(
            id = "gence_160",
            cropId = "cotton",
            regionIds = listOf("aran", "shirvan_mugan", "ganja_gazakh"),
            name = "Gəncə-160",
            badge = LocalizedText("İri qozalı • İsti düzənlik", "Large Bolls • Heat Hardy", "Крупная коробочка"),
            summary = LocalizedText(
                "Yüksək temperatura dözümlü, ağır qozalı və IV tip keyfiyyətli lif verən yerli pambıq sortu.",
                "Heat-tolerant Azerbaijani cotton with heavy bolls (6.5–7.0 g) and strong fiber.",
                "Жаростойкий сорт с крупной коробочкой (6.5–7 г) и прочным волокном.",
            ),
            origin = LocalizedText("Azərbaycan Dövlət Aqrar Universiteti / BMTB ETİ", "ADAU / Industrial Crops Institute", "АГАУ / НИИ технических культур"),
            sowingWindow = LocalizedText("10 aprel – 30 aprel", "Apr 10 – Apr 30", "10 – 30 апреля"),
            seedingRate = LocalizedText("28 – 35 kq/ha", "28 – 35 kg/ha", "28 – 35 кг/га"),
            vegetationPeriod = LocalizedText("122 – 128 gün", "122 – 128 days", "122 – 128 дней"),
            yieldPotential = LocalizedText("42 – 54 sentner/ha", "4.2 – 5.4 t/ha", "42 – 54 ц/га"),
            resistance = LocalizedText("İstiyə, hörümçək gənəsinə və hommoza orta-yüksək davamlı.", "Resilient to summer heat stress and bacterial blight.", "Устойчив к летнему зною и гоммозу."),
            soilAndIrrigation = LocalizedText("Dərin şumlanmış yüngül gillicəli torpaqlar; suvarma normasına nəzarət tövsiyə olunur.", "Deep-plowed loam soils with balanced irrigation.", "Глубоко вспаханные суглинистые почвы с умеренным поливом."),
            agronomyTip = LocalizedText("Çiçəkləmə əvvəli kalium və bor yarpaq gübrəsi qozaların tökülməsini azaldır.", "Foliar boron & potassium before flowering reduces square and young boll shedding.", "Внекорневая подкормка бором и калием снижает опадение бутонов."),
        ),
        SeedVariety(
            id = "agqizil",
            cropId = "cotton",
            regionIds = listOf("aran", "shirvan_mugan"),
            name = "Ağqızıl (Bəyaz Altun)",
            badge = LocalizedText("Şoranlığa davamlı • Sürətli çıxış", "Salt Tolerant • Fast Emergence", "Солеустойчивый"),
            summary = LocalizedText(
                "Şirvan-Salyan və Muğanın az şorlaşmış torpaqlarında sürətli cücərmə və zəngin bar verən sort.",
                "Performs reliably on mildly saline Shirvan-Salyan and Mughan plain soils.",
                "Надёжно плодоносит на слабозасолённых почвах Ширвана и Мугани.",
            ),
            origin = LocalizedText("Regionallaşdırılmış selektiv sort", "Regionally adapted cotton cultivar", "Районированный селекционный сорт"),
            sowingWindow = LocalizedText("5 aprel – 25 aprel", "Apr 5 – Apr 25", "5 – 25 апреля"),
            seedingRate = LocalizedText("30 – 38 kq/ha", "30 – 38 kg/ha", "30 – 38 кг/га"),
            vegetationPeriod = LocalizedText("115 – 122 gün", "115 – 122 days", "115 – 122 дня"),
            yieldPotential = LocalizedText("40 – 48 sentner/ha", "4.0 – 4.8 t/ha", "40 – 48 ц/га"),
            resistance = LocalizedText("Duz stresinə və kök çürüməsinə qarşı dözümlü.", "Tolerates salt stress and seedling damping-off.", "Выдерживает солевой стресс и корневую гниль всходов."),
            soilAndIrrigation = LocalizedText("Qış arat suyu duzların yuyulması üçün mütləqdir.", "Pre-plant winter leaching irrigation improves establishment.", "Зимний промывной полив значительно улучшает всходы."),
            agronomyTip = LocalizedText("15–20 sm cücərmədən sonra seyrəltmə və cərgəarası yumşaltma aparın.", "Cultivate inter-rows promptly once seedlings reach 15–20 cm.", "Проводите междурядную культивацию при высоте всходов 15–20 см."),
        ),
        SeedVariety(
            id = "zaqatala_68",
            cropId = "corn",
            regionIds = listOf("shaki_zagatala", "quba_khachmaz", "aran", "ganja_gazakh"),
            name = "Zaqatala-68",
            badge = LocalizedText("Dən və silos • Yerli lider", "Grain & Silage Leader", "Зерно и силос • Лидер"),
            summary = LocalizedText(
                "Şəki-Zaqatala və suvarılan düzənliklərdə yüksək dən və yaşıl kütlə verən güclü qarğıdalı sortu.",
                "Classic Azerbaijani maize variety delivering high grain and lush silage yields.",
                "Классический сорт кукурузы для высокого урожая зерна и зелёной массы на силос.",
            ),
            origin = LocalizedText("Əkinçilik ETİ – Zaqatala Zona Təcrübə Stansiyası", "Crop Husbandry Institute – Zagatala Station", "Закатальская опытная станция НИИ Земледелия"),
            sowingWindow = LocalizedText("15 aprel – 10 may", "Apr 15 – May 10", "15 апреля – 10 мая"),
            seedingRate = LocalizedText("20 – 25 kq/ha (55–65 min bitki/ha)", "20 – 25 kg/ha (55–65k plants/ha)", "20 – 25 кг/га (55–65 тыс. раст./га)"),
            vegetationPeriod = LocalizedText("125 – 135 gün", "125 – 135 days", "125 – 135 дней"),
            yieldPotential = LocalizedText("80 – 105 sentner/ha dən (500 s/ha silos)", "8.0 – 10.5 t/ha grain (50 t/ha silage)", "80 – 105 ц/га зерна (500 ц/га силос)"),
            resistance = LocalizedText("Yatmaya və qovuqcuqlu sürməyə qarşı yaxşı dözümlülük.", "Sturdy stalks; tolerant to common corn smut.", "Крепкий стебель; устойчив к пузырчатой головне."),
            soilAndIrrigation = LocalizedText("Rütubətli Şəki-Zaqatala dəmyəsində və Aranda 3–4 suvarma ilə yüksək məhsul verir.", "Thrives rainfed in Zagatala valleys or with 3–4 irrigations in Aran.", "Хорошо растёт на богаре в Закаталах и при 3–4 поливах в Аране."),
            agronomyTip = LocalizedText("6–8 yarpaq fazasında sink və azotla yemləmə qıçaların tam dolmasını təmin edir.", "Side-dress nitrogen and zinc at the V6–V8 leaf stage for full cob fill.", "Подкормка азотом и цинком в фазе 6–8 листьев улучшает озернённость початков."),
        ),
        SeedVariety(
            id = "emiri_600",
            cropId = "potato",
            regionIds = listOf("ganja_gazakh", "quba_khachmaz", "shaki_zagatala", "lankaran_astara"),
            name = "Əmiri-600",
            badge = LocalizedText("Dadlı • Uzun saxlanma", "Great Taste • Long Storage", "Отличный вкус • Лёжкий"),
            summary = LocalizedText(
                "Tovuz–Şəmkir–Gədəbəy və Quba–Xaçmaz zonalarında ən sevilən yerli məhsuldar kartof sortu.",
                "Prized local potato cultivar in Shamkir-Tovuz and Guba-Khachmaz with long shelf life.",
                "Популярный местный сорт картофеля для западных и северных районов с длительной лёжкостью.",
            ),
            origin = LocalizedText("Tərəvəzçilik Elmi-Tədqiqat İnstitutu (Azərbaycan)", "Vegetable Research Institute (Azerbaijan)", "НИИ Овощеводства (Азербайджан)"),
            sowingWindow = LocalizedText("Martın sonu – Aprel (Cənubda Fevral)", "Late Mar – Apr (Feb in South)", "Конец марта – апрель (на юге с февраля)"),
            seedingRate = LocalizedText("2.8 – 3.3 ton/ha toxumluq yumru (50–70 q)", "2.8 – 3.3 t/ha seed tubers", "2.8 – 3.3 т/га семенных клубней"),
            vegetationPeriod = LocalizedText("100 – 115 gün (orta-yetişən)", "100 – 115 days (mid-season)", "100 – 115 дней (среднеспелый)"),
            yieldPotential = LocalizedText("320 – 420 sentner/ha", "32 – 42 t/ha", "320 – 420 ц/га"),
            resistance = LocalizedText("Fitoftora və quru çürüməyə orta-yüksək müqavimət.", "Moderate-to-high tolerance to late blight and dry rot.", "Хорошая устойчивость к фитофторозу и сухой гнили."),
            soilAndIrrigation = LocalizedText("Yumşaq, drenajlı gillicəli torpaq və müntəzəm şırımla/damcı suvarma.", "Loose loamy soil with steady drip or furrow irrigation.", "Рыхлые суглинки, регулярный капельный или бороздковый полив."),
            agronomyTip = LocalizedText("Çiçəkləmədən əvvəl cərgələri dibdoldurma (okuchivanie) yaşıl yumruların qarşısını alır.", "Hill rows prior to flowering to prevent tuber greening and increase stolons.", "Окучивание до цветения предотвращает позеленение клубней."),
        ),
        SeedVariety(
            id = "marfona_agria",
            cropId = "potato",
            regionIds = listOf("lankaran_astara", "ganja_gazakh", "quba_khachmaz"),
            name = "Marfona / Agria",
            badge = LocalizedText("Erkən bazar • İri yumru", "Early Market • Large Tubers", "Ранний • Крупные клубни"),
            summary = LocalizedText(
                "Cəlilabad–Lənkəran erkən yaz əkinləri və Şəmkir açıq sahələri üçün yüksək satış keyfiyyətli sortlar.",
                "High-marketability early/mid cultivars ideal for Jalilabad early spring and Shamkir.",
                "Высокотоварные сорта для раннего весеннего рынка Джалилабада и Шамкира.",
            ),
            origin = LocalizedText("Niderland seleksiyası (Azərbaycanda rayonlaşdırılmış)", "Dutch origin (adapted locally)", "Нидерландская селекция (районированы)"),
            sowingWindow = LocalizedText("Fevral – Mart (Cənub), Aprel (Qərb/Şimal)", "Feb – Mar (South), Apr (West/North)", "Февраль – март (Юг), апрель (Запад/Север)"),
            seedingRate = LocalizedText("3.0 – 3.5 ton/ha", "3.0 – 3.5 t/ha", "3.0 – 3.5 т/га"),
            vegetationPeriod = LocalizedText("85 – 105 gün", "85 – 105 days", "85 – 105 дней"),
            yieldPotential = LocalizedText("350 – 480 sentner/ha", "35 – 48 t/ha", "350 – 480 ц/га"),
            resistance = LocalizedText("Yumru göbələklərinə və mexaniki zədələnməyə davamlı.", "Resistant to tuber bruising and common scab.", "Устойчивы к механическим повреждениям и парше."),
            soilAndIrrigation = LocalizedText("Kaliumla zənginləşdirilmiş yumşaq torpaq; çiçəkləmə dövründə rütubət sabit saxlanmalıdır.", "Potassium-rich light soil; keep moisture steady during tuber bulking.", "Рыхлые почвы с достаточным калием; стабильная влажность при клубнеобразовании."),
            agronomyTip = LocalizedText("Kalium sulfat tətbiqi quru maddəni və yumru dadını artırır; xlorlu kaliumdan az istifadə edin.", "Use potassium sulfate over KCl to boost starch quality.", "Предпочитайте сульфат калия для повышения содержания крахмала."),
        ),
        SeedVariety(
            id = "zefer_tomato",
            cropId = "tomato",
            regionIds = listOf("aran", "shirvan_mugan", "ganja_gazakh", "quba_khachmaz", "lankaran_astara"),
            name = "Zəfər / Lənkəran-1",
            badge = LocalizedText("Yerli • Ətirli və məhsuldar", "Local • Rich Flavor", "Местный • Ароматный"),
            summary = LocalizedText(
                "Azərbaycanın iqliminə tam uyğunlaşmış, həm təzə istehlak, həm də emal üçün yararlı yerli pomidor sortları.",
                "Local Azerbaijani tomato lines combining strong heat adaptation with classic rich taste.",
                "Местные сорта томата с отличным вкусом и высокой жаростойкостью.",
            ),
            origin = LocalizedText("Tərəvəzçilik Elmi-Tədqiqat İnstitutu", "Vegetable Research Institute (Azerbaijan)", "НИИ Овощеводства (Азербайджан)"),
            sowingWindow = LocalizedText("Mart (şitilxana), Aprel–May (açıq sahəyə köçürmə)", "Mar (nursery), Apr–May (transplant)", "Март (рассада), апрель–май (открытый грунт)"),
            seedingRate = LocalizedText("0.3 – 0.4 kq/ha toxum (28–33 min şitil/ha)", "0.3 – 0.4 kg/ha seed (28–33k seedlings/ha)", "0.3 – 0.4 кг/га семян (28–33 тыс. рассады/га)"),
            vegetationPeriod = LocalizedText("105 – 115 gün (şitildən)", "105 – 115 days", "105 – 115 дней"),
            yieldPotential = LocalizedText("450 – 600 sentner/ha", "45 – 60 t/ha", "450 – 600 ц/га"),
            resistance = LocalizedText("Günəş yanığına, fuzarioza və çatlamaya davamlı.", "Tolerant to sunscald, Fusarium, and fruit cracking.", "Устойчив к солнечным ожогам, фузариозу и растрескиванию."),
            soilAndIrrigation = LocalizedText("Damcı suvarma ilə kalsium verilməsi təpə çürüməsinin qarşısını alır.", "Drip irrigation with calcium prevents blossom-end rot.", "Капельный полив с кальцием защищает от вершинной гнили."),
            agronomyTip = LocalizedText("Açıq sahədə 70×40 sm sxemi ilə əkin hava dövranını yaxşılaşdırır.", "Space plants at 70×40 cm to improve airflow and cut blight risk.", "Схема посадки 70×40 см снижает влажность и риск фитофтороза."),
        ),
        SeedVariety(
            id = "aran_yonca",
            cropId = "alfalfa",
            regionIds = listOf("aran", "shirvan_mugan", "ganja_gazakh"),
            name = "Aran / Abşeron Yoncası",
            badge = LocalizedText("4–5 biçim/il • Torpağı zənginləşdirir", "4–5 Cuts/Yr • Nitrogen Fixer", "4–5 укосов/год • Обогащает почву"),
            summary = LocalizedText(
                "Heyvandarlıq üçün yüksək zülallı ot verən və torpağı azotla zənginləşdirən növbəli əkin sortu.",
                "High-protein forage legume ideal for crop rotation after cotton or cereals.",
                "Высокобелковый кормовой сорт, идеально восстанавливающий плодородие в севообороте.",
            ),
            origin = LocalizedText("Azərbaycan Heyvandarlıq və Əkinçilik ETİ", "Azerbaijan Forage & Crop Husbandry Institute", "НИИ Земледелия и Животноводства"),
            sowingWindow = LocalizedText("Mart–Aprel və ya Sentyabr", "Mar–Apr or Sep", "Март–апрель или сентябрь"),
            seedingRate = LocalizedText("16 – 20 kq/ha", "16 – 20 kg/ha", "16 – 20 кг/га"),
            vegetationPeriod = LocalizedText("Çoxillik (3–4 il istifadə; ildə 4–5 biçim)", "Perennial (3–4 yrs; 4–5 cuts/yr)", "Многолетняя (3–4 года; 4–5 укосов/год)"),
            yieldPotential = LocalizedText("120 – 160 sentner/ha quru ot", "12 – 16 t/ha dry hay", "120 – 160 ц/га сухого сена"),
            resistance = LocalizedText("Quraqlığa, tez bərpa olunmağa və yüngül şoranlığa davamlı.", "Fast regrowth after cutting; hardy against heat and salinity.", "Быстро отрастает после укоса; выносит лёгкое засоление."),
            soilAndIrrigation = LocalizedText("Hər biçimdən sonra 1 dəfə suvarma növbəti biçimi 35% artırır.", "One irrigation after each cut accelerates regrowth by ~35%.", "Полив после каждого укоса ускоряет отрастание до 35%."),
            agronomyTip = LocalizedText("Qönçələmə başlanğıcında biçim aparmaq zülal və yarpaq faizini maksimum saxlayır.", "Cut at early bud stage for peak protein and leaf retention.", "Скашивайте в начале бутонизации для максимального содержания белка."),
        ),
        SeedVariety(
            id = "hashimi_rice",
            cropId = "rice",
            regionIds = listOf("lankaran_astara", "shaki_zagatala"),
            name = "Haşimi / Sədri-Mazandaran",
            badge = LocalizedText("Ətirli elit düyü • Cənub", "Aromatic Elite Rice", "Ароматный элитный рис"),
            summary = LocalizedText(
                "Lənkəran–Astara və Masallının subtropik ləklərində yüksək bazar qiymətli, ətirli uzun düyü sortu.",
                "Premium aromatic long-grain rice thriving in Lankaran-Astara and Masalli paddies.",
                "Элитный ароматный длиннозёрный сорт риса для субтропиков Ленкорани и Астары.",
            ),
            origin = LocalizedText("Cənub subtropik bölgələri üçün uyğunlaşdırılmış", "Adapted regional aromatic cultivar", "Адаптированный ароматный сорт"),
            sowingWindow = LocalizedText("Aprelin sonu – May (ləklərə şitil köçürmə: May)", "Late Apr – May (transplant in May)", "Конец апреля – май (высадка рассады в мае)"),
            seedingRate = LocalizedText("45 – 55 kq/ha (şitil üsulu ilə)", "45 – 55 kg/ha (transplanted)", "45 – 55 кг/га (рассадный способ)"),
            vegetationPeriod = LocalizedText("120 – 130 gün", "120 – 130 days", "120 – 130 дней"),
            yieldPotential = LocalizedText("42 – 55 sentner/ha çəltik", "4.2 – 5.5 t/ha paddy rice", "42 – 55 ц/га риса-сырца"),
            resistance = LocalizedText("Pirkularioz göbələk xəstəliyinə orta-yüksək müqavimət.", "Moderate-good resistance to rice blast.", "Хорошая устойчивость к пирикуляриозу."),
            soilAndIrrigation = LocalizedText("Gilli su saxlayan ləklər; daimi 5–10 sm su səviyyəsi qorunmalıdır.", "Clay paddy fields with 5–10 cm controlled standing water.", "Глинистые чеки с поддержанием уровня воды 5–10 см."),
            agronomyTip = LocalizedText("Süd yetişmə dövründə suyu tədricən azaltmaq dənin çatlamasının qarşısını alır.", "Gradually drain paddies at dough stage to reduce grain fissuring.", "Постепенный сброс воды в фазу молочной спелости улучшает цельность зерна."),
        ),
        SeedVariety(
            id = "quba_alma_orchard",
            cropId = "orchard",
            regionIds = listOf("quba_khachmaz"),
            name = "Qızıl Əhmədi / Fuji (Alma)",
            badge = LocalizedText("Quba bağı brendi • Uzun saxlanma", "Guba Heritage & Export Apple", "Губинский бренд • Долго хранится"),
            summary = LocalizedText(
                "Quba–Xaçmaz bağlarında ətirli yerli Qızıl Əhmədi və intensiv bağlar üçün Fuji alma calaqaltıları.",
                "Iconic Guba Gizil Ahmadi and high-density Fuji apple varieties for northern orchards.",
                "Легендарный губинский сорт Гызыл Ахмеди и интенсивный сорт Фуджи для северных садов.",
            ),
            origin = LocalizedText("Meyvəçilik və Çayçılıq ETİ (Quba)", "Fruit & Tea Research Institute (Guba)", "НИИ Плодоводства и Чаеводства (Губа)"),
            sowingWindow = LocalizedText("Noyabr və ya Fevral–Mart (ting əkini)", "Nov or Feb–Mar (sapling planting)", "Ноябрь или февраль–март (посадка саженцев)"),
            seedingRate = LocalizedText("İntensiv M9/MM106 calaqaltıda 1000–2500 ting/ha", "1000–2500 saplings/ha (dwarf/semi-dwarf)", "1000–2500 саженцев/га (на карликовых подвоях)"),
            vegetationPeriod = LocalizedText("Çoxillik bağ • Yığım: Sentyabrın sonu – Oktyabr", "Perennial orchard • Harvest late Sep – Oct", "Многолетний сад • Уборка: конец сен. – окт."),
            yieldPotential = LocalizedText("350 – 550 sentner/ha (tam məhsul dövründə)", "35 – 55 t/ha at full bearing", "350 – 550 ц/га в полном плодоношении"),
            resistance = LocalizedText("Dəmgilə (Venturia) qarşı vaxtında profilaktika tələb edir; şaxtaya davamlıdır.", "Cold-hardy; schedule spring scab prevention.", "Морозостойкий; требует весенней профилактики от парши."),
            soilAndIrrigation = LocalizedText("Yaxşı drenajlı dağətəyi torpaqlar, damcı suvarma və dayaq sistemləri.", "Well-drained foothills with drip irrigation and trellising.", "Дренированные предгорные почвы, капельный полив и шпалера."),
            agronomyTip = LocalizedText("Soyuducu anbarda Qızıl Əhmədi aprel ayınadək ətrini itirmədən saxlanılır.", "Cold-stored Gizil Ahmadi holds aroma and crispness until April.", "В холодильнике Гызыл Ахмеди сохраняет вкус и аромат до апреля."),
        ),
        SeedVariety(
            id = "ata_baba_findiq",
            cropId = "orchard",
            regionIds = listOf("shaki_zagatala"),
            name = "Ata-baba (Fındıq)",
            badge = LocalizedText("İxrac standartı • Yağlılıq ~68%", "Export Standard • High Oil", "Экспортный стандарт"),
            summary = LocalizedText(
                "Zaqatala–Balakən–Qax–Qəbələ zonasının dünya bazarında tanınan ən yüksək keyfiyyətli fındıq sortu.",
                "World-renowned Azerbaijani hazelnut cultivar native to Zagatala, Balakan, Gakh, and Gabala.",
                "Знаменитый экспортный азербайджанский сорт фундука для Шеки-Закатальской зоны.",
            ),
            origin = LocalizedText("Azərbaycan xalq seleksiyası (Zaqatala–Balakən)", "Azerbaijan native cultivar (Zagatala–Balakan)", "Народная селекция (Закаталы–Балакен)"),
            sowingWindow = LocalizedText("Payız (Noyabr) və ya Erkən Yaz (Mart)", "Nov or early Mar", "Ноябрь или ранний март"),
            seedingRate = LocalizedText("400 – 500 kol/ha (5×4 m və ya 5×5 m sxem)", "400 – 500 bushes/ha (5×4 m)", "400 – 500 кустов/га (схема 5×4 м)"),
            vegetationPeriod = LocalizedText("Çoxillik • Yığım: Avqustun sonu – Sentyabr", "Perennial • Harvest late Aug – Sep", "Многолетний • Уборка: конец авг. – сен."),
            yieldPotential = LocalizedText("22 – 32 sentner/ha quru qabıqlı fındıq", "2.2 – 3.2 t/ha dried in-shell nuts", "22 – 32 ц/га сухого ореха"),
            resistance = LocalizedText("Şaxtaya və yerli iqlimə yüksək uyğunlaşma; tozlanma üçün 10% Yağlı fındıq əkin.", "Winter hardy; interplant 10% Yagli hazelnut pollinators.", "Хорошо переносит зиму; подсаживайте 10% опылителей (Яглы фундук)."),
            soilAndIrrigation = LocalizedText("Yarı-kölgə və rütubətli meşə-çəmən torpaqları; yayda 1–2 əlavə suvarma ləpə dolğunluğunu artırır.", "Moist forest-meadow soils; 1–2 summer irrigations improve kernel fill.", "Влажные лесо-луговые почвы; 1–2 летних полива улучшают налив ядра."),
            agronomyTip = LocalizedText("Hər kolda 4–6 əsas gövdə saxlayıb artıq pöhrələri budamaq məhsuldarlığı 25% artırır.", "Maintain 4–6 main stems per bush and prune suckers for +25% yield.", "Оставляйте 4–6 главных стволов и удаляйте лишнюю поросль."),
        ),
        SeedVariety(
            id = "satsuma_meyer",
            cropId = "orchard",
            regionIds = listOf("lankaran_astara"),
            name = "Satsuma (Naringi) / Meyyer Limonu",
            badge = LocalizedText("Cənub subtropik • Tez bar verir", "Subtropical • Early Bearing", "Субтропики • Скороплодный"),
            summary = LocalizedText(
                "Astara–Lənkəran sitrus bağlarında yüksək məhsuldarlıq və çərdəksiz/nazik qabıqlı meyvə verən sortlar.",
                "Top seedless mandarin and thin-skinned Meyer lemon cultivars for Astara and Lankaran.",
                "Лучшие сорта мандарина Сатсума и лимона Мейера для Астары и Ленкорани.",
            ),
            origin = LocalizedText("Lənkəran – Astara subtropik bağçılıq təcrübəsi", "Lankaran–Astara Subtropical Station", "Ленкорано-Астаринская зона"),
            sowingWindow = LocalizedText("Martın sonu – Aprel (yaz ting əkini)", "Late Mar – Apr", "Конец марта – апрель"),
            seedingRate = LocalizedText("600 – 800 ağac/ha (4×3 m və ya 4×3.5 m)", "600 – 800 trees/ha (4×3 m)", "600 – 800 деревьев/га (4×3 м)"),
            vegetationPeriod = LocalizedText("Çoxillik • Yığım: Oktyabr – Dekabr", "Perennial • Harvest Oct – Dec", "Многолетний • Уборка: октябрь – декабрь"),
            yieldPotential = LocalizedText("250 – 380 sentner/ha", "25 – 38 t/ha", "250 – 380 ц/га"),
            resistance = LocalizedText("Trifoliata calaqaltısında -7..-9°C qısamüddətli soyuğa dözür.", "On Poncirus trifoliata rootstock endures brief -7°C chills.", "На подвое трифолиата выдерживает кратковременные морозы до -7°C."),
            soilAndIrrigation = LocalizedText("Yüngül turş (pH 5.5–6.5), su buraxan sarı torpaqlar.", "Slightly acidic (pH 5.5–6.5), well-drained yellow soils.", "Слабокислые (pH 5.5–6.5) хорошо дренированные желтозёмы."),
            agronomyTip = LocalizedText("Yazda maqnezium və dəmir xelatı yarpaqların saralmasının qarşısını alır.", "Apply iron chelate and magnesium in spring to keep foliage deep green.", "Весной вносите хелат железа и магний против хлороза листьев."),
        ),
        SeedVariety(
            id = "madrasa_tebrizi_grape",
            cropId = "orchard",
            regionIds = listOf("ganja_gazakh", "aran", "shirvan_mugan"),
            name = "Təbrizi / Mədrəsə (Üzüm)",
            badge = LocalizedText("Süfrə və şərablıq elit", "Table & Wine Classic", "Столовый и винный элит"),
            summary = LocalizedText(
                "Şəmkir, Samux, Göygöl və Şirvan bağları üçün iri giləli süfrə (Təbrizi) və texniki (Mədrəsə) üzüm sortları.",
                "Premier table (Tabrizi) and native wine (Madrasa) grape varieties for western and Shirvan vineyards.",
                "Классические сорта столового (Тебризи) и винного (Мадраса) винограда.",
            ),
            origin = LocalizedText("Azərbaycan Üzümçülük və Şərabçılıq ETİ", "Azerbaijan Viticulture & Winemaking Institute", "НИИ Виноградарства и Виноделия (Азербайджан)"),
            sowingWindow = LocalizedText("Noyabr və ya Fevral–Mart", "Nov or Feb–Mar", "Ноябрь или февраль–март"),
            seedingRate = LocalizedText("2200 – 2600 tənək/ha (3×1.5 m)", "2200 – 2600 vines/ha (3×1.5 m)", "2200 – 2600 кустов/га (3×1.5 м)"),
            vegetationPeriod = LocalizedText("Çoxillik • Yığım: Avqust – Sentyabr", "Perennial • Harvest Aug – Sep", "Многолетний • Уборка: август – сентябрь"),
            yieldPotential = LocalizedText("160 – 220 sentner/ha (şəkərlilik 20–23%)", "16 – 22 t/ha (20–23% Brix)", "160 – 220 ц/га (сахаристость 20–23%)"),
            resistance = LocalizedText("Quraqlığa davamlıdır; oidiuma qarşı vaxtında kükürdlü çiləmə aparılmalıdır.", "Drought-resilient; follow regular powdery mildew sprays.", "Засухоустойчив; проводите профилактику от оидиума."),
            soilAndIrrigation = LocalizedText("Çınqıllı-karbonatlı dağətəyi torpaqlar şəkər toplanmasını artırır.", "Gravelly-calcareous soils enhance berry aroma and sugar.", "Щебнисто-карбонатные почвы повышают сахаристость ягод."),
            agronomyTip = LocalizedText("Yazda yaşıl zoğların seyrəldilməsi salxımların günəşlənməsini və rənglənməsini gücləndirir.", "Shoot thinning and leaf removal around clusters improves berry color.", "Зелёная обломка и прореживание улучшают проветривание гроздей."),
        ),
    )

    fun cropsForRegion(regionId: String): List<SeedCrop> {
        val validCropIds = varieties
            .filter { regionId in it.regionIds }
            .map { it.cropId }
            .toSet()
        return crops.filter { it.id in validCropIds }
    }

    fun varietiesFor(regionId: String, cropId: String): List<SeedVariety> =
        varieties.filter { it.cropId == cropId && regionId in it.regionIds }
}
