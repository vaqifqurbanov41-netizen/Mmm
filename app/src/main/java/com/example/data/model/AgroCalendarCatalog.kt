package com.example.data.model

import com.example.i18n.AppLanguage

data class AgroCategoryDef(
    val id: String,
    val nameAz: String,
    val nameEn: String,
    val nameRu: String,
) {
    fun localizedName(lang: AppLanguage): String = when (lang) {
        AppLanguage.AZ -> nameAz
        AppLanguage.EN -> nameEn
        AppLanguage.RU -> nameRu
    }
}

data class AgroMonthPlan(
    val monthNumber: Int, // 1..12
    val tasksAz: List<String>,
    val tasksEn: List<String>,
    val tasksRu: List<String>,
) {
    fun localizedTasks(lang: AppLanguage): List<String> = when (lang) {
        AppLanguage.AZ -> tasksAz
        AppLanguage.EN -> tasksEn
        AppLanguage.RU -> tasksRu
    }
}

data class AgroCropCalendar(
    val id: String,
    val categoryId: String,
    val nameAz: String,
    val nameEn: String,
    val nameRu: String,
    val months: List<AgroMonthPlan>,
) {
    fun localizedName(lang: AppLanguage): String = when (lang) {
        AppLanguage.AZ -> nameAz
        AppLanguage.EN -> nameEn
        AppLanguage.RU -> nameRu
    }
}

object AgroCalendarCatalog {

    val categories: List<AgroCategoryDef> = listOf(
        AgroCategoryDef("grain", "Dənli", "Grains", "Зерновые"),
        AgroCategoryDef("industrial", "Texniki", "Industrial", "Технические"),
        AgroCategoryDef("vegetable", "Tərəvəz", "Vegetables", "Овощи"),
        AgroCategoryDef("forage", "Yem", "Forage", "Кормовые"),
        AgroCategoryDef("orchard", "Meyvə və Üzüm", "Orchard & Vine", "Сад и виноград"),
    )

    fun cropsForCategory(categoryId: String): List<AgroCropCalendar> =
        crops.filter { it.categoryId == categoryId }

    fun monthName(monthNumber: Int, lang: AppLanguage): String = when (lang) {
        AppLanguage.AZ -> listOf(
            "Yanvar", "Fevral", "Mart", "Aprel", "May", "İyun",
            "İyul", "Avqust", "Sentyabr", "Oktyabr", "Noyabr", "Dekabr",
        )
        AppLanguage.EN -> listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )
        AppLanguage.RU -> listOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
        )
    }.getOrElse((monthNumber - 1).coerceIn(0, 11)) { "" }

    val crops: List<AgroCropCalendar> = listOf(
        AgroCropCalendar(
            id = "wheat",
            categoryId = "grain",
            nameAz = "Taxıl (Payızlıq buğda)",
            nameEn = "Winter Wheat",
            nameRu = "Озимая пшеница",
            months = listOf(
                AgroMonthPlan(
                    1,
                    listOf(
                        "Taxıl zərərvericiləri və siçanabənzər gəmiricilərə qarşı mübarizə tədbirləri aparılmalıdır.",
                        "Bitkilərin kollama vəziyyəti və şaxtaya qarşı davamlılığı qiymətləndirilməlidir.",
                    ),
                    listOf(
                        "Inspect fields for cereal pests and rodent damage and apply control measures.",
                        "Evaluate tillering density and winter frost survival.",
                    ),
                    listOf(
                        "Проведите борьбу с вредителями зерновых и мышевидными грызунами.",
                        "Оцените фазу кущения растений и их зимостойкость.",
                    ),
                ),
                AgroMonthPlan(
                    2,
                    listOf(
                        "Payızlıq taxıl bitkiləri üçün vegetasiya suvarmaları (quraq ərazilərdə arat/suvarma) aparılmalıdır.",
                        "Siçanabənzər gəmiricilərə və digər ziyanvericilərə qarşı mübarizə tədbirləri davam etdirilməlidir.",
                    ),
                    listOf(
                        "Carry out early vegetation irrigation in dry lowland zones.",
                        "Continue monitoring and control of field rodents and overwintering pests.",
                    ),
                    listOf(
                        "При засушливой погоде проведите вегетационный полив озимых посевов.",
                        "Продолжайте борьбу с мышевидными грызунами и другими вредителями.",
                    ),
                ),
                AgroMonthPlan(
                    3,
                    listOf(
                        "Yemləmə gübrəsi verilməlidir (ammonium şorası 250–300 kq/ha və ya karbamid normaya uyğun).",
                        "Vegetasiya suvarmaları aparılmalıdır.",
                        "Alaq otlarına qarşı herbisidlə mübarizə tədbirləri yerinə yetirilməlidir.",
                    ),
                    listOf(
                        "Apply nitrogen top-dressing (ammonium nitrate 250–300 kg/ha).",
                        "Conduct vegetation irrigation according to soil moisture.",
                        "Apply selective herbicides against broadleaf and grassy weeds.",
                    ),
                    listOf(
                        "Внесите азотную подкормку (аммиачная селитра 250–300 кг/га).",
                        "Проведите вегетационные поливы по состоянию влажности почвы.",
                        "Выполните гербицидную обработку против сорняков.",
                    ),
                ),
                AgroMonthPlan(
                    4,
                    listOf(
                        "Alaq otlarına, zərərvericilərə (taxıl böcəyi, mənənə) və pas xəstəliklərinə qarşı mübarizə aparılmalıdır.",
                        "Dağlıq və dağətəyi zonalarda yemləmə gübrəsi verilməlidir.",
                    ),
                    listOf(
                        "Control late weeds, cereal bugs, aphids, and leaf rust diseases.",
                        "Apply spring top-dressing fertilizer in foothill and mountain zones.",
                    ),
                    listOf(
                        "Продолжайте защиту от сорняков, вредителей (клоп-черепашка, тля) и ржавчины.",
                        "В горных и предгорных зонах проведите весеннюю подкормку.",
                    ),
                ),
                AgroMonthPlan(
                    5,
                    listOf(
                        "Məhsul yığılacaq taxıl sahələrinin kənarları biçilməli və yanğından mühafizə məqsədilə şumlanmalıdır.",
                        "Biçində istifadə olunacaq texnikalar (kombayn, qoşqu) tam hazırlıq vəziyyətinə gətirilməlidir.",
                        "Toxumluq sahələrdə növ və sort alağı aparılmalıdır.",
                        "Saxlanc anbarları dezinfeksiya olunmalıdır.",
                    ),
                    listOf(
                        "Mow field borders and plow fire-protection strips around ripening wheat fields.",
                        "Service combine harvesters and grain transport machinery ahead of harvest.",
                        "Perform varietal roguing in certified seed-production plots.",
                        "Clean and disinfect grain storage warehouses.",
                    ),
                    listOf(
                        "Скосите края полей и опашите противопожарные полосы.",
                        "Полностью подготовьте зерноуборочные комбайны и транспорт к страде.",
                        "На семенных участках проведите сортовую и видовую прополку.",
                        "Проведите дезинфекцию зернохранилищ.",
                    ),
                ),
                AgroMonthPlan(
                    6,
                    listOf(
                        "Dənin tam yetişmə fazasında (13–14% nəmlikdə) itkisiz taxıl biçini aparılmalıdır.",
                        "Küləş sahədən vaxtında yığılmalı və preslənməlidir.",
                    ),
                    listOf(
                        "Harvest wheat at 13–14% grain moisture to minimize shattering losses.",
                        "Bale and clear straw promptly after combining.",
                    ),
                    listOf(
                        "Проведите уборку урожая при влажности зерна 13–14% без потерь.",
                        "Своевременно соберите и затюкуйте солому с полей.",
                    ),
                ),
                AgroMonthPlan(
                    7,
                    listOf(
                        "Kövşənin 10–12 sm dərinlikdə üzlənməsi (alaq toxumlarının cücərməsi üçün) aparılmalıdır.",
                        "Anbardakı taxılın temperatur və zərərverici nəzarəti aparılmalıdır.",
                    ),
                    listOf(
                        "Perform shallow stubble disking (10–12 cm) to preserve moisture and trigger weed germination.",
                        "Monitor stored grain temperature, moisture, and weevils.",
                    ),
                    listOf(
                        "Проведите лущение стерни на глубину 10–12 см для сохранения влаги.",
                        "Контролируйте температуру и отсутствие вредителей в хранящемся зерне.",
                    ),
                ),
                AgroMonthPlan(
                    8,
                    listOf(
                        "Əsas şum (25–30 sm dərinlikdə) və şum altına fosfor-kalium gübrəsi (superfosfat, kalium) verilməlidir.",
                        "Səpin materialı təmizlənməli və sertifikatlı sort toxumlar hazırlanmalıdır.",
                    ),
                    listOf(
                        "Conduct deep primary plowing (25–30 cm) incorporating basal phosphorus and potassium fertilizers.",
                        "Clean, grade, and prepare certified wheat seed lots.",
                    ),
                    listOf(
                        "Проведите основную вспашку (25–30 см) с внесением фосфорно-калийных удобрений.",
                        "Очистите и подготовьте сертифицированный семенной материал.",
                    ),
                ),
                AgroMonthPlan(
                    9,
                    listOf(
                        "Dağlıq və dağətəyi rayonlarda payızlıq səpinə başlanmalıdır; səpindən əvvəl toxum dərmanlanmalıdır.",
                        "Suvarılan aran rayonlarında torpaq mala/kultivatorla hamarlanmalı və arat suyu verilməlidir.",
                    ),
                    listOf(
                        "Treat seed against smut/root rot and start winter sowing in mountain/foothill areas.",
                        "Level seedbeds and apply pre-sowing irrigation in lowland irrigated zones.",
                    ),
                    listOf(
                        "Протравите семена и начните озимый сев в горных районах.",
                        "В орошаемых равнинах выровняйте почву и проведите предвсходовый арат.",
                    ),
                ),
                AgroMonthPlan(
                    10,
                    listOf(
                        "Aran və orta rayonlarda optimal payızlıq buğda səpini (4–5 sm dərinlikdə, 180–220 kq/ha) aparılmalıdır.",
                        "Səpinlə birlikdə start fosfor gübrəsi (ammofos) tətbiq edilməlidir.",
                    ),
                    listOf(
                        "Complete optimal sowing across lowland zones (4–5 cm depth, 180–220 kg/ha).",
                        "Apply starter phosphorus fertilizer alongside seeding.",
                    ),
                    listOf(
                        "Проведите оптимальный озимый сев на равнинах (глубина 4–5 см, 180–220 кг/га).",
                        "Внесите стартовое фосфорное удобрение при посеве.",
                    ),
                ),
                AgroMonthPlan(
                    11,
                    listOf(
                        "Çıxışların sıxlığı yoxlanılmalı, yağış suyunun yığılmaması üçün sahə arxları təmizlənməlidir.",
                        "Payız alaq otları çoxdursa erkən nəzarət tədbirləri planlaşdırılmalıdır.",
                    ),
                    listOf(
                        "Check emergence uniformity and clear drainage furrows before winter rains.",
                        "Scout early autumn weeds and seedling pests.",
                    ),
                    listOf(
                        "Проверьте равномерность всходов и очистите дренажные канавки перед дождями.",
                        "Проведите фитосанитарный осмотр всходов.",
                    ),
                ),
                AgroMonthPlan(
                    12,
                    listOf(
                        "Siçanabənzər gəmiricilərin yuvalarına qarşı qış mübarizəsi aparılmalıdır.",
                        "Növbəti mövsüm üçün gübrə, herbisid və texnika ehtiyacları planlaşdırılmalıdır.",
                    ),
                    listOf(
                        "Control field rodents before snow or deep cold sets in.",
                        "Plan next spring fertilizer and crop-protection procurement.",
                    ),
                    listOf(
                        "Проведите зимний контроль численности грызунов.",
                        "Спланируйте закупку удобрений и СЗР на весенний сезон.",
                    ),
                ),
            ),
        ),
        AgroCropCalendar(
            id = "barley",
            categoryId = "grain",
            nameAz = "Arpa",
            nameEn = "Barley",
            nameRu = "Ячмень",
            months = listOf(
                AgroMonthPlan(1, listOf("Qışlayan əkinlərdə siçanabənzər gəmiricilərə və gölməçələnməyə nəzarət edilməlidir."), listOf("Check drainage and rodent activity in winter barley."), listOf("Контролируйте дренаж и грызунов на посевах озимого ячменя.")),
                AgroMonthPlan(2, listOf("Erkən yaz arpa səpini üçün sahələr hamarlanmalı, payızlıq arpada kollama yoxlanmalıdır."), listOf("Prepare seedbeds for spring barley and inspect winter tillering."), listOf("Подготовьте поля к ранневесеннему севу и осмотрите озимые.")),
                AgroMonthPlan(3, listOf("Payızlıq arpaya yemləmə azot gübrəsi (150–200 kq/ha ammonium şorası) və alaq mübarizəsi aparılmalıdır."), listOf("Apply 150–200 kg/ha nitrogen top-dressing and early weed control."), listOf("Внесите подкормку (150–200 кг/га селитры) и проведите борьбу с сорняками.")),
                AgroMonthPlan(4, listOf("Sünbülləmə qabağı suvarma və unlu şeh / tor ləkəliliyə qarşı müşahidə aparılmalıdır."), listOf("Apply pre-heading irrigation and scout powdery mildew / net blotch."), listOf("Проведите полив перед колошением и осмотрите на мучнистую росу.")),
                AgroMonthPlan(5, listOf("Tezyetişən arpa sortlarında biçin qabağı yanğın zolaqları açılmalı və kombaynlar sazlanmalıdır."), listOf("Prepare firebreaks and harvesters ahead of early barley harvest."), listOf("Подготовьте противопожарные полосы и технику к ранней уборке.")),
                AgroMonthPlan(6, listOf("Arpa buğdadan 10–14 gün tez yetişdiyi üçün vaxtında biçilib anbara daşınmalıdır."), listOf("Harvest barley 10–14 days ahead of wheat at 13–14% moisture."), listOf("Уберите ячмень своевременно (на 10–14 дней раньше пшеницы).")),
                AgroMonthPlan(7, listOf("Kövşən üzlənməsi aparılmalı və boşalmış sahə növbəti bitki (təkrar qarğıdalı/yem) üçün hazırlanmalıdır."), listOf("Disk stubble or prepare irrigated plots for second-crop forage/corn."), listOf("Проведите лущение стерни или подготовку к пожнивному посеву.")),
                AgroMonthPlan(8, listOf("Əsas şum və fosfor-kalium gübrələnməsi həyata keçirilməlidir."), listOf("Perform primary plowing and basal P/K fertilization."), listOf("Выполните основную вспашку с внесением фосфора и калия.")),
                AgroMonthPlan(9, listOf("Toxumluq arpa sortları seçilməli və tozlu sürməyə qarşı dərmanlanmalıdır."), listOf("Clean and treat barley seeds against loose smut."), listOf("Очистите и протравите семена ячменя от пыльной головни.")),
                AgroMonthPlan(10, listOf("Payızlıq arpa səpini (160–190 kq/ha) optimal müddətdə başa çatdırılmalıdır."), listOf("Sow winter barley at 160–190 kg/ha during optimal October window."), listOf("Завершите сев озимого ячменя (160–190 кг/га).")),
                AgroMonthPlan(11, listOf("Çıxış və kök sistemi yoxlanmalı, lazım gələrsə qışqabağı suvarma aparılmalıdır."), listOf("Inspect seedling establishment and drainage."), listOf("Проверьте густоту всходов и водоотводные канавки.")),
                AgroMonthPlan(12, listOf("Gəmiricilərə qarşı mübarizə və gübrə balansının hesablanması aparılmalıdır."), listOf("Monitor winter rodents and plan spring input needs."), listOf("Проведите зимний осмотр посевов и планирование удобрений.")),
            ),
        ),
        AgroCropCalendar(
            id = "corn",
            categoryId = "grain",
            nameAz = "Qarğıdalı",
            nameEn = "Corn (Maize)",
            nameRu = "Кукуруза",
            months = listOf(
                AgroMonthPlan(1, listOf("Anbarda dən qarğıdalının nəmliyi yoxlanılmalı və hibrid toxum sifarişi verilməlidir."), listOf("Check stored grain moisture and order certified hybrid seeds."), listOf("Проверьте влажность зерна на складе и закажите гибридные семена.")),
                AgroMonthPlan(2, listOf("Səpin aqreqatları (pnevmatik toxumsəpən) kalibrlənməlidir."), listOf("Calibrate pneumatic precision planters."), listOf("Откалибруйте пневматические сеялки.")),
                AgroMonthPlan(3, listOf("Şumlanmış sahələrə kultivasiya çəkilməli və start gübrə hazırlanmalıdır."), listOf("Cultivate plowed seedbeds and prepare starter fertilizer."), listOf("Проведите предпосевную культивацию почвы.")),
                AgroMonthPlan(4, listOf("Torpaq 10–12°C qızdıqda əsas qarğıdalı səpini (70 sm cərgəarası) və çıxışqabağı herbisid tətbiq edilməlidir."), listOf("Sow corn once soil reaches 10–12°C (70 cm rows) and apply pre-emergence herbicide."), listOf("Посейте кукурузу при прогреве почвы до 10–12°C с междурядьем 70 см.")),
                AgroMonthPlan(5, listOf("5–6 yarpaq fazasında cərgəarası kultivasiya, dibdoldurma və azot yemləməsi aparılmalıdır."), listOf("Perform inter-row cultivation and nitrogen side-dressing at 5–6 leaf stage."), listOf("В фазе 5–6 листьев проведите междурядную культивацию и азотную подкормку.")),
                AgroMonthPlan(6, listOf("Suvarma (xüsusən süpürgələməyə keçiddə) və qarğıdalı kəpənəyi / pambıq sovkasına qarşı nəzarət aparılmalıdır."), listOf("Irrigate before tasseling and monitor corn borer / earworm pests."), listOf("Проведите полив и контроль кукурузного мотылька и совки.")),
                AgroMonthPlan(7, listOf("Çiçəkləmə və dəndoldurma dövründə müntəzəm suvarma fasiləsiz davam etdirilməlidir."), listOf("Maintain regular irrigation through pollination and grain fill."), listOf("Поддерживайте регулярный полив в период цветения и налива зерна.")),
                AgroMonthPlan(8, listOf("Silosluq qarğıdalının süd-mum yetişkənliyində biçiminə başlanmalıdır."), listOf("Start silage harvest at milk-dough maturity (32–35% dry matter)."), listOf("Начните уборку силосной кукурузы в фазе молочно-восковой спелости.")),
                AgroMonthPlan(9, listOf("Dən qarğıdalının yığımı və qurudulması aparılmalıdır."), listOf("Harvest grain corn and dry below 14% moisture for storage."), listOf("Проведите уборку зерновой кукурузы и сушку до влажности 14%.")),
                AgroMonthPlan(10, listOf("Biçindən qalan gövdələr xırdalanmalı və 28–30 sm dərinlikdə dondurma şumu vurulmalıdır."), listOf("Shred stalks and perform deep autumn plowing (28–30 cm)."), listOf("Измельчите стебли и проведите глубокую зяблевую вспашку.")),
                AgroMonthPlan(11, listOf("Əsas fosfor-kalium gübrələnməsi (şum zamanı) tamamlanmalıdır."), listOf("Complete basal P/K incorporation in autumn-plowed fields."), listOf("Завершите внесение основных фосфорно-калийных удобрений.")),
                AgroMonthPlan(12, listOf("Suvarma kanalları və drenaj xətləri təmizlənməlidir."), listOf("Clean irrigation ditches and winter drainage channels."), listOf("Очистите оросительные каналы и дренажную сеть.")),
            ),
        ),
        AgroCropCalendar(
            id = "cotton",
            categoryId = "industrial",
            nameAz = "Pambıq",
            nameEn = "Cotton",
            nameRu = "Хлопчатник",
            months = listOf(
                AgroMonthPlan(1, listOf("Şorlaşmış Aran sahələrində qış duz yuyulması (yuma suvarma) aparılmalıdır."), listOf("Conduct winter salt-leaching irrigation in saline lowland soils."), listOf("Проведите зимнюю промывку засоленных почв.")),
                AgroMonthPlan(2, listOf("Arat suyu verilməli və səpin texnikası sazlanmalıdır."), listOf("Apply pre-sowing moisture charge irrigation and service planters."), listOf("Проведите предпахотный полив и подготовьте посевную технику.")),
                AgroMonthPlan(3, listOf("Erkən yaz malalanması, alaq əleyhinə torpaq herbisidi və sertifikatlı çiyid tədarükü aparılmalıdır."), listOf("Harrow beds, apply pre-sowing soil herbicide, and prepare certified delinted seed."), listOf("Проведите боронование, внесение почвенных гербицидов и подготовку семян.")),
                AgroMonthPlan(4, listOf("Torpaq 14–15°C olduqda çiyid səpini (90 sm və ya 60 sm cərgəarası, 3–4 sm dərinlik) aparılmalıdır."), listOf("Sow cotton seed once soil temperature reaches 14–15°C."), listOf("Проведите сев хлопчатника при температуре почвы 14–15°C.")),
                AgroMonthPlan(5, listOf("Seyrəltmə, birinci cərgəarası kultivasiya və mənənə/trips əleyhinə mübarizə aparılmalıdır."), listOf("Thin stands, cultivate inter-rows, and monitor aphids/thrips."), listOf("Выполните прореживание, культивацию и защиту от тли и трипсов.")),
                AgroMonthPlan(6, listOf("Qönçələmə dövründə birinci vegetasiya suvarma, yemləmə azotu və pambıq sovkası müşahidəsi aparılmalıdır."), listOf("Start first irrigation, nitrogen top-dressing, and scout bollworm at squaring."), listOf("Проведите первый полив в фазе бутонизации, подкормку и учет совки.")),
                AgroMonthPlan(7, listOf("Kütləvi çiçəkləmə-qozalama zamanı suvarma, kultivasiya və ucvurma (çikanovka) yerinə yetirilməlidir."), listOf("Irrigate, manage bollworm/mites, and perform plant topping."), listOf("Проведите поливы в период цветения, защиту от клеща/совки и чеканку.")),
                AgroMonthPlan(8, listOf("Sonuncu vegetasiya suvarma dayandırılmalı və qozaların açılması sürətləndirilməlidir."), listOf("Cut off late irrigation to promote uniform boll opening."), listOf("Завершите вегетационные поливы для ускорения раскрытия коробочек.")),
                AgroMonthPlan(9, listOf("Qozaların 50–60%-i açıldıqda defoliant tətbiqi və birinci maşınla pambıq yığımı başlanmalıdır."), listOf("Apply defoliant at 50–60% open bolls and begin mechanical harvesting."), listOf("Проведите дефолиацию при раскрытии 50–60% коробочек и начните сбор.")),
                AgroMonthPlan(10, listOf("İkinci yığım tamamlanmalı, qurumuş çöp/kolluq (guzapaya) doğranıb çıxarılmalıdır."), listOf("Finish second picking and shred/clear cotton stalks."), listOf("Завершите второй сбор и уберите стебли гузапаи.")),
                AgroMonthPlan(11, listOf("30–35 sm dərinlikdə dərin dondurma şumu və əsas fosfor-kalium gübrəsi verilməlidir."), listOf("Deep-plow (30–35 cm) with basal phosphorus and potassium."), listOf("Проведите глубокую зяблевую вспашку (30–35 см) с фосфором и калием.")),
                AgroMonthPlan(12, listOf("Sahə planlaması (lazer hamarlama) və qış suvarma kanallarının təmizliyi aparılmalıdır."), listOf("Laser-level fields and clear irrigation canals."), listOf("Выполните лазерное выравнивание полей и очистку арыков.")),
            ),
        ),
        AgroCropCalendar(
            id = "potato",
            categoryId = "vegetable",
            nameAz = "Kartof",
            nameEn = "Potato",
            nameRu = "Картофель",
            months = listOf(
                AgroMonthPlan(1, listOf("Toxumluq kartofun anbarda cücərmə temperaturu (+2..+4°C) nəzarətdə saxlanılmalıdır."), listOf("Maintain seed potato storage at +2..+4°C and remove diseased tubers."), listOf("Поддерживайте температуру хранения семенного картофеля +2..+4°C.")),
                AgroMonthPlan(2, listOf("Aran rayonlarında erkən (örtükaltı/faro) kartof üçün toxumların işıqda cücərdilməsinə başlanmalıdır."), listOf("Chit (pre-sprout) seed tubers in bright rooms for early lowland planting."), listOf("Начните яровизацию семенных клубней для ранней посадки.")),
                AgroMonthPlan(3, listOf("Aran zonalarında yazlıq kartof əkini (70×25–30 sm) və start gübrələmə aparılmalıdır."), listOf("Plant early lowland potatoes and apply starter NPK."), listOf("Проведите посадку раннего картофеля на равнинах (70×25–30 см).")),
                AgroMonthPlan(4, listOf("Dağlıq və dağətəyi rayonlarda (Gədəbəy, Tovuz, Şəmkir, Qusar) əsas kartof əkini aparılmalıdır."), listOf("Plant main-season potatoes in mountain and foothill districts."), listOf("Проведите основную посадку картофеля в горных и предгорных районах.")),
                AgroMonthPlan(5, listOf("Cərgəarası dibdoldurma, Kolorado böcəyinə və fitoftora xəstəliyinə qarşı profilaktik mübarizə başlanmalıdır."), listOf("Hill ridges and control Colorado potato beetle and early blight."), listOf("Окучьте ряды и проведите обработку от колорадского жука и фитофтороза.")),
                AgroMonthPlan(6, listOf("Çiçəkləmə və yumru bağlama dövründə müntəzəm suvarma və fitoftoroz əleyhinə funqisid tətbiqi edilməlidir."), listOf("Irrigate regularly during tuber bulking and protect against late blight."), listOf("Регулярно поливайте в период клубнеобразования и защищайте от фитофторы.")),
                AgroMonthPlan(7, listOf("Erkən kartof yığımı başa çatdırılmalı, dağlıq sahələrdə suvarma davam etdirilməlidir."), listOf("Harvest early lowland crop; maintain irrigation in highland fields."), listOf("Завершите уборку раннего картофеля; продолжайте полив в горах.")),
                AgroMonthPlan(8, listOf("Yığımdan 10–14 gün əvvəl yaşıl gövdələr biçilməli və ya qurudulmalıdır ki, qabıq möhkəmlənsin."), listOf("Desiccate haulms 10–14 days before digging for firm skin set."), listOf("За 10–14 дней до копки скосите ботву для укрепления кожуры.")),
                AgroMonthPlan(9, listOf("Dağlıq bölgələrdə əsas kartof yığımı, çeşidləmə və quru havada anbara yığılması aparılmalıdır."), listOf("Dig, sort, and cure main-season highland potatoes."), listOf("Проведите основную уборку картофеля в горах, сортировку и закладку.")),
                AgroMonthPlan(10, listOf("Məhsul qalığı yığışdırılmalı və əsas payız şumu vurulmalıdır."), listOf("Clear field residues and deep-plow for next season's rotation."), listOf("Уберите растительные остатки и проведите осеннюю вспашку.")),
                AgroMonthPlan(11, listOf("Üzvi gübrə (çürümüş peyin 20–30 t/ha) şum altına verilməlidir."), listOf("Incorporate well-rotted compost/manure during autumn soil preparation."), listOf("Внесите перепревший навоз под зяблевую вспашку.")),
                AgroMonthPlan(12, listOf("Anbar havalandırılması və rütubətinin (85–90%) sabit saxlanması yoxlanmalıdır."), listOf("Monitor storage ventilation and 85–90% relative humidity."), listOf("Контролируйте вентиляцию и влажность (85–90%) в хранилище.")),
            ),
        ),
        AgroCropCalendar(
            id = "tomato",
            categoryId = "vegetable",
            nameAz = "Pomidor",
            nameEn = "Tomato",
            nameRu = "Томат",
            months = listOf(
                AgroMonthPlan(1, listOf("İstixana/şitilliklər dezinfeksiya olunmalı və hibrid toxumlar əkilməlidir."), listOf("Disinfect nursery beds and sow greenhouse tomato seedlings."), listOf("Продезинфицируйте теплицы и посейте семена на рассаду.")),
                AgroMonthPlan(2, listOf("Şitillərin şaxtadan qorunması, işıqlandırma və kassa/kasetdə bəslənməsi aparılmalıdır."), listOf("Manage nursery temperature, lighting, and balanced seedling feeding."), listOf("Контролируйте температуру, освещение и подкормку рассады.")),
                AgroMonthPlan(3, listOf("Açıq sahə üçün cərgələr çəkilməli, damcı suvarma boruları və mulça döşənməlidir."), listOf("Prepare raised beds, drip lines, and plastic mulch for open fields."), listOf("Нарежьте гряды, уложите капельные ленты и мульчирующую плёнку.")),
                AgroMonthPlan(4, listOf("Şaxta təhlükəsi bitdikdən sonra şitillərin açıq sahəyə köçürülməsi başlanmalıdır."), listOf("Transplant hardened tomato seedlings outdoors after frost risk passes."), listOf("Высадите закалённую рассаду в открытый грунт после угрозы заморозков.")),
                AgroMonthPlan(5, listOf("Kultivasiya, kolların dirəyə bağlanması, Tuta absoluta (pomidor güvəsi) və fitoftora nəzarəti aparılmalıdır."), listOf("Stake plants, weed inter-rows, and set Tuta absoluta pheromone traps."), listOf("Подвяжите кусты, установите феромонные ловушки от Tuta absoluta.")),
                AgroMonthPlan(6, listOf("Meyvə bağlama dövründə kalsium + kalium yemləməsi və müntəzəm damcı suvarma aparılmalıdır."), listOf("Feed calcium/potassium via fertigation and maintain steady drip irrigation."), listOf("Внесите кальций и калий с поливом и поддерживайте равномерную влажность.")),
                AgroMonthPlan(7, listOf("Kütləvi pomidor yığımı, günəş yanığından qoruma və hörümçək gənəsinə qarşı müşahidə aparılmalıdır."), listOf("Harvest ripe fruit, monitor spider mites and fruitworm."), listOf("Проводите массовый сбор урожая и защиту от паутинного клеща и совки.")),
                AgroMonthPlan(8, listOf("Sənaye və bazar yığımı davam etdirilməli, gözləmə müddəti qısa preparatlardan istifadə olunmalıdır."), listOf("Continue harvest using only short-PHI biopesticides if needed."), listOf("Продолжайте сбор, соблюдая короткий срок ожидания препаратов.")),
                AgroMonthPlan(9, listOf("Son məhsul yığımı tamamlanmalı, şitil dirəkləri və damcı xətləri yığılmalıdır."), listOf("Finish harvest and retrieve stakes and drip irrigation lines."), listOf("Завершите сбор, уберите колья и капельные линии.")),
                AgroMonthPlan(10, listOf("Xəstə bitki qalıqları sahədən çıxarılıb məhv edilməli və payız şumu vurulmalıdır."), listOf("Remove diseased crop debris and deep-plow field."), listOf("Удалите растительные остатки и проведите глубокую вспашку.")),
                AgroMonthPlan(11, listOf("Torpaq analizi götürülməli və növbəti il üçün əkin dövriyyəsi (taxıl/paxlalı) seçilməlidir."), listOf("Take soil samples and plan 3–4 year crop rotation."), listOf("Отберите пробы почвы и спланируйте севооборот.")),
                AgroMonthPlan(12, listOf("Damcı suvarma filtrləri təmizlənməli və toxum ehtiyatı hazırlanmalıdır."), listOf("Service drip filters and select next season's resistant hybrids."), listOf("Промойте фильтры капельного полива и подберите гибриды.")),
            ),
        ),
        AgroCropCalendar(
            id = "alfalfa",
            categoryId = "forage",
            nameAz = "Yonca",
            nameEn = "Alfalfa (Lucerne)",
            nameRu = "Люцерна",
            months = listOf(
                AgroMonthPlan(1, listOf("Yonca sahələrində qış suvarma kanalları yoxlanmalı və gəmiricilərə qarşı mübarizə aparılmalıdır."), listOf("Inspect drainage channels and control winter rodents in alfalfa."), listOf("Проверьте дренаж и проведите борьбу с грызунами на люцерне.")),
                AgroMonthPlan(2, listOf("Fosfor-kalium gübrəsi (superfosfat) və yaz səpini üçün hamar toxum yatağı hazırlanmalıdır."), listOf("Top-dress established stands with P/K and prepare firm spring seedbeds."), listOf("Внесите фосфорно-калийные удобрения и выровняйте почву под весенний сев.")),
                AgroMonthPlan(3, listOf("Yaz yonca səpini (16–20 kq/ha, 1.5–2 sm dərinlikdə) və yaşıl yonca böcəyinə qarşı nəzarət aparılmalıdır."), listOf("Sow spring alfalfa (16–20 kg/ha, 1.5–2 cm deep) and scout alfalfa weevil."), listOf("Проведите весенний сев люцерны (16–20 кг/га) и контроль листового слоника.")),
                AgroMonthPlan(4, listOf("Birinci biçin öncəsi suvarma və alaq otlarına nəzarət edilməlidir."), listOf("Apply first spring irrigation and control early weeds."), listOf("Проведите весенний полив перед первым укосом.")),
                AgroMonthPlan(5, listOf("10–20% çiçəkləmə fazasında 1-ci biçin (yüksək protein üçün) və biçindən sonra dərhal suvarma aparılmalıdır."), listOf("Cut 1st harvest at 10–20% bloom for peak protein and irrigate afterward."), listOf("Проведите 1-й укос в начале цветения (10–20%) и сразу полейте.")),
                AgroMonthPlan(6, listOf("2-ci biçin yerinə yetirilməli, küsküt alaq otuna qarşı sahə yoxlanmalıdır."), listOf("Complete 2nd cut and remove any dodder (Cuscuta) patches immediately."), listOf("Проведите 2-й укос и удалите очаги повилики.")),
                AgroMonthPlan(7, listOf("3-cü biçin və isti havada gecə/səhər suvarma qrafiki tətbiq edilməlidir."), listOf("Cut 3rd harvest and irrigate during cool morning/evening hours."), listOf("Выполните 3-й укос и поливайте в прохладное время суток.")),
                AgroMonthPlan(8, listOf("4-cü biçin aparılmalı, toxumluq saxlanılacaq sahələrdə isə suvarma dayandırılıb arılar qorunmalıdır."), listOf("Complete 4th hay cut; stop irrigation on seed plots to aid pollination."), listOf("Проведите 4-й укос; на семенниках прекратите полив в период созревания.")),
                AgroMonthPlan(9, listOf("5-ci (son) biçin və payız yonca səpini (Aran zonalarda optimal vaxt) həyata keçirilməlidir."), listOf("Take 5th cut or sow new autumn alfalfa stands in lowland zones."), listOf("Выполните 5-й укос или осенний сев люцерны на равнинах.")),
                AgroMonthPlan(10, listOf("Qışqabağı fosfor gübrəsi və əkinlərin otarılmasının dayandırılması tövsiyə olunur."), listOf("Apply autumn phosphorus and avoid heavy grazing so crowns store reserves."), listOf("Внесите осенний фосфор и прекратите выпас перед зимовкой.")),
                AgroMonthPlan(11, listOf("Son suvarma (nəmlik ehtiyatı) tamamlanmalıdır."), listOf("Complete final winter-moisture irrigation before cold weather."), listOf("Завершите влагозарядковый полив.")),
                AgroMonthPlan(12, listOf("Ot bağlamalarının quru anbarda saxlanması və gəmirici nəzarəti aparılmalıdır."), listOf("Check stored hay bales for dryness and control rodents."), listOf("Проверьте сохранность тюков сена и проведите дератизацию.")),
            ),
        ),
        AgroCropCalendar(
            id = "orchard",
            categoryId = "orchard",
            nameAz = "Meyvə bağları və Üzüm",
            nameEn = "Orchards & Vineyards",
            nameRu = "Плодовые сады и виноград",
            months = listOf(
                AgroMonthPlan(1, listOf("Quru və şaxtasız günlərdə meyvə ağaclarında və tənəklərdə qış budaması aparılmalıdır."), listOf("Conduct dormant winter pruning on frost-free days."), listOf("Проведите зимнюю обрезку в сухие безморозные дни.")),
                AgroMonthPlan(2, listOf("Budama tamamlanmalı, tumurcuqlar açılmazdan əvvəl mis tərkibli (Bordo) və mineral yağ çiləməsi aparılmalıdır."), listOf("Finish pruning and apply dormant copper + horticultural oil spray before budbreak."), listOf("Завершите обрезку и проведите «голубое опрыскивание» медью и минеральным маслом.")),
                AgroMonthPlan(3, listOf("Ağac dibləri bellənməli, birinci azot-fosfor gübrəsi verilməli və qönçə açılışında göbələk mübarizəsi aparılmalıdır."), listOf("Cultivate tree rows, apply spring fertilizer, and guard emerging buds against scab."), listOf("Перекопайте приствольные круги, внесите удобрения и защитите почки от парши.")),
                AgroMonthPlan(4, listOf("Çiçəkləmə dövründə arıları qorumaq üçün insektisid vurulmamalı, suvarma və şaxta qorunması izlənməlidir."), listOf("Never spray insecticides during bloom to protect bees; monitor late frost."), listOf("Во время цветения исключите инсектициды для защиты пчёл.")),
                AgroMonthPlan(5, listOf("Meyvəqurdu, tənək yarpaqbükəni və mildyu/oidium xəstəliklərinə qarşı planlı çiləmə və yaşıl budama aparılmalıdır."), listOf("Apply preventive sprays against codling moth, mildew, and start green pruning."), listOf("Проведите опрыскивание против плодожорки, милдью/оидиума и зелёную обломку.")),
                AgroMonthPlan(6, listOf("Meyvə bağlarında yük tənzimləməsi (artıq meyvə seyrəltməsi) və damcı suvarma gücləndirilməlidir."), listOf("Thin heavy fruit loads and ramp up drip irrigation."), listOf("Проведите нормировку плодов и усильте поливы.")),
                AgroMonthPlan(7, listOf("Yay budaması (bağda işıqlanma üçün), gənə və zərərverici tələlərinə nəzarət aparılmalıdır."), listOf("Perform summer canopy thinning and monitor pheromone/mite traps."), listOf("Выполните летнюю чеканку побегов и контроль феромонных ловушек.")),
                AgroMonthPlan(8, listOf("Erkən sortların (şaftalı, armud, süfrə üzümü) yığımı və isti havada suvarma davam etdirilməlidir."), listOf("Harvest summer fruit/table grapes while maintaining drip moisture."), listOf("Убирайте летние сорта фруктов и столового винограда.")),
                AgroMonthPlan(9, listOf("Payızlıq alma, nar, xurma və texniki üzüm yığımı aparılmalıdır."), listOf("Harvest autumn apples, pomegranates, persimmons, and wine grapes."), listOf("Проведите сбор осенних яблок, граната, хурмы и винограда.")),
                AgroMonthPlan(10, listOf("Məhsul yığımından sonra bağ dibinə fosfor-kalium + üzvi gübrə verilməli və yarpaqlar toplanmalıdır."), listOf("Apply post-harvest P/K and compost and rake fallen diseased leaves."), listOf("Внесите осенние P/K удобрения с перегноем и уберите опавшую листву.")),
                AgroMonthPlan(11, listOf("Xəzan tamamlandıqdan sonra mis sulfatla (Bordo mayesi) payız müalicəvi çiləməsi və qış suyu verilməlidir."), listOf("Apply post-leaf-fall copper spray and winter moisture irrigation."), listOf("После листопада проведите искореняющее опрыскивание медью и влагозарядковый полив.")),
                AgroMonthPlan(12, listOf("Cavan tinglərin gövdəsi gəmiricilərə və şaxtaya qarşı əhənglə ağardılmalı və sarılmalıdır."), listOf("Whitewash trunks and wrap young saplings against frost and hares."), listOf("Побелите штамбы и укройте молодые саженцы от мороза и грызунов.")),
            ),
        ),
    )
}
