package com.example.data.model

import com.example.i18n.AppLanguage

enum class ThreatKind {
    DISEASE,
    PEST,
}

data class ProtectionCropDef(
    val id: String,
    val name: LocalizedText,
)

data class CropThreatEntry(
    val id: String,
    val cropId: String,
    val kind: ThreatKind,
    val name: LocalizedText,
    val symptoms: LocalizedText,
    val activeIngredient: LocalizedText,
    val searchIngredientKey: String,
    val guidance: LocalizedText,
)

data class PreparationEntry(
    val id: String,
    val tradeName: String,
    val activeIngredient: LocalizedText,
    val maxRate: Double,
    val unit: String,
    val quarantineDays: Int,
    val safetyClass: LocalizedText,
    val target: LocalizedText,
    val searchKeywords: String,
)

object CropProtectionCatalog {

    val crops: List<ProtectionCropDef> = listOf(
        ProtectionCropDef("tomato", LocalizedText("Pomidor", "Tomato", "Томат")),
        ProtectionCropDef("cucumber", LocalizedText("Xiyar", "Cucumber", "Огурец")),
        ProtectionCropDef("wheat", LocalizedText("Buğda", "Wheat", "Пшеница")),
        ProtectionCropDef("barley", LocalizedText("Arpa", "Barley", "Ячмень")),
        ProtectionCropDef("cotton", LocalizedText("Pambıq", "Cotton", "Хлопок")),
        ProtectionCropDef("potato", LocalizedText("Kartof", "Potato", "Картофель")),
        ProtectionCropDef("grape", LocalizedText("Üzüm", "Grape", "Виноград")),
        ProtectionCropDef("apple", LocalizedText("Alma", "Apple", "Яблоня")),
        ProtectionCropDef("alfalfa", LocalizedText("Yonca", "Alfalfa", "Люцерна")),
        ProtectionCropDef("corn", LocalizedText("Qarğıdalı", "Corn", "Кукуруза")),
    )

    val threats: List<CropThreatEntry> = listOf(
        // POMİDOR / TOMATO
        CropThreatEntry(
            id = "tomato_late_blight",
            cropId = "tomato",
            kind = ThreatKind.DISEASE,
            name = LocalizedText("Fitoftoroz (Gecikmiş yanğın)", "Late Blight (Phytophthora)", "Фитофтороз"),
            symptoms = LocalizedText(
                "Yarpaqlarda və meyvələrdə tünd-qəhvəyi suvarılmış ləkələr, rütubətli havada alt səthdə ağ kif örtüyü.",
                "Dark brown water-soaked lesions on leaves and fruits with white mold underneath in humid weather.",
                "Тёмно-бурые мокнущие пятна на листьях и плодах, белый налёт с нижней стороны во влажную погоду.",
            ),
            activeIngredient = LocalizedText(
                "Mankozeb + Metalaksil-M (və ya Dimetomorf / Mis hidroksid)",
                "Mancozeb + Metalaxyl-M (or Dimethomorph / Copper hydroxide)",
                "Манкоцеб + Металаксил-М (или Диметоморф / Гидроксид меди)",
            ),
            searchIngredientKey = "Mankozeb",
            guidance = LocalizedText(
                "Yağış və şehdən sonra profilaktik və ya ilk ləkələrdə tətbiq edin; gözləmə müddəti 14–21 gün.",
                "Apply preventively or at first lesions after rain/dew; observe 14–21 day pre-harvest interval.",
                "Применяйте профилактически или при первых пятнах после дождя; срок ожидания 14–21 день.",
            ),
        ),
        CropThreatEntry(
            id = "tomato_alternaria",
            cropId = "tomato",
            kind = ThreatKind.DISEASE,
            name = LocalizedText("Alternarioz (Quru ləkəlik)", "Early Blight (Alternaria)", "Альтернариоз (Сухая пятнистость)"),
            symptoms = LocalizedText(
                "Aşağı yarpaqlarda konsentrik halqalı qəhvəyi-qara ləkələr, tədricən yarpaqların quruması.",
                "Concentric brown-black target spots starting on older lower leaves, causing premature defoliation.",
                "Концентрические тёмно-бурые пятна на нижних листьях, постепенное усыхание листвы.",
            ),
            activeIngredient = LocalizedText(
                "Difenokonazol (və ya Azoksistrobin)",
                "Difenoconazole (or Azoxystrobin)",
                "Дифеноконазол (или Азоксистробин)",
            ),
            searchIngredientKey = "Difenokonazol",
            guidance = LocalizedText(
                "Alt xəstə yarpaqları təmizləyin, damcılı suvarma tətbiq edin və müxtəlif qrup funqisidlərlə növbələşdirin.",
                "Remove lower infected leaves, use drip irrigation, and rotate fungicide FRAC groups.",
                "Удалите нижние больные листья, используйте капельный полив и чередуйте группы фунгицидов.",
            ),
        ),
        CropThreatEntry(
            id = "tomato_powdery_mildew",
            cropId = "tomato",
            kind = ThreatKind.DISEASE,
            name = LocalizedText("Unlu şeh", "Powdery Mildew", "Мучнистая роса"),
            symptoms = LocalizedText(
                "Yarpaqların üst və alt səthində ağ tozvari örtük, saralma və qıvrılma.",
                "White powdery coating on leaves leading to yellowing and curling.",
                "Белый мучнистый налёт на листьях, вызывающий пожелтение и скручивание.",
            ),
            activeIngredient = LocalizedText(
                "Azoksistrobin (və ya Kükürd / Penkonazol)",
                "Azoxystrobin (or Sulfur / Penconazole)",
                "Азоксистробин (или Сера / Пенконазол)",
            ),
            searchIngredientKey = "Azoksistrobin",
            guidance = LocalizedText(
                "İstixanada havalandırmanı artırın, isti saatlarda kükürd tətbiqindən çəkinin.",
                "Improve greenhouse ventilation and avoid sulfur application during peak midday heat.",
                "Усильте проветривание теплиц и не применяйте серу в полуденную жару.",
            ),
        ),
        CropThreatEntry(
            id = "tomato_whitefly_aphid",
            cropId = "tomato",
            kind = ThreatKind.PEST,
            name = LocalizedText("Ağqanad və Mənənə", "Whitefly & Aphids", "Белокрылка и Тля"),
            symptoms = LocalizedText(
                "Yarpaq altında xırda ağ həşəratlar və ya koloniyalar, yapışqan şirə və his göbələyi.",
                "Small whiteflies or aphid clusters under leaves, sticky honeydew, and sooty mold.",
                "Мелкие белые насекомые или колонии под листьями, липкая падь и сажистый гриб.",
            ),
            activeIngredient = LocalizedText(
                "Asetamiprid (və ya Tiametoksam / İmidakloprid)",
                "Acetamiprid (or Thiamethoxam / Imidacloprid)",
                "Ацетамиприд (или Тиаметоксам / Имидаклоприд)",
            ),
            searchIngredientKey = "Asetamiprid",
            guidance = LocalizedText(
                "Sarı yapışqan tələlərlə monitorinq aparın; arıların uçuşu olmayan axşam saatlarında çiləyin.",
                "Monitor with yellow sticky traps and spray in the evening outside bee foraging hours.",
                "Используйте жёлтые клеевые ловушки; опрыскивайте вечером вне лёта пчёл.",
            ),
        ),
        CropThreatEntry(
            id = "tomato_tuta",
            cropId = "tomato",
            kind = ThreatKind.PEST,
            name = LocalizedText("Pomidor güvəsi (Tuta absoluta)", "Tomato Leafminer (Tuta absoluta)", "Томатная моль"),
            symptoms = LocalizedText(
                "Yarpaq daxilində şəffaf minalar (lağımlar) və yaşıl meyvələrdə deşiklər.",
                "Translucent blotch mines inside leaves and bore holes in green fruits.",
                "Прозрачные мины внутри листьев и входные отверстия в зелёных плодах.",
            ),
            activeIngredient = LocalizedText(
                "Emamektin benzoat (və ya Xlorantraniliprol / Spinosad)",
                "Emamectin benzoate (or Chlorantraniliprole / Spinosad)",
                "Эмамектин бензоат (или Хлорантранилипрол / Спиносад)",
            ),
            searchIngredientKey = "Emamektin",
            guidance = LocalizedText(
                "Feromon tələləri qurun, zədələnmiş meyvələri sahədən çıxarın və ana maddələri növbələşdirin.",
                "Set pheromone traps, remove infested fruits, and rotate active ingredients.",
                "Установите феромонные ловушки, удаляйте повреждённые плоды и чередуйте ДВ.",
            ),
        ),
        // XİYAR / CUCUMBER
        CropThreatEntry(
            id = "cucumber_downy_mildew",
            cropId = "cucumber",
            kind = ThreatKind.DISEASE,
            name = LocalizedText("Yalançı unlu şeh (Peronosporoz)", "Downy Mildew", "Ложная мучнистая роса (Пероноспороз)"),
            symptoms = LocalizedText(
                "Yarpaq damarları arasında sarımtıl küncşəkilli ləkələr, alt tərəfdə bənövşəyi-boz örtük.",
                "Yellow angular spots bounded by leaf veins with purplish-gray fuzz beneath.",
                "Жёлтые угловатые пятна между жилками листа с серо-фиолетовым налётом снизу.",
            ),
            activeIngredient = LocalizedText(
                "Mankozeb + Metalaksil-M (və ya Mis hidroksid / Propamokarb)",
                "Mancozeb + Metalaxyl-M (or Copper hydroxide / Propamocarb)",
                "Манкоцеб + Металаксил-М (или Гидроксид меди / Пропамокарб)",
            ),
            searchIngredientKey = "Mankozeb",
            guidance = LocalizedText(
                "Gecə rütubətini azaldın, suvarmanı səhər saatlarına salın.",
                "Reduce night humidity and schedule irrigation in the early morning.",
                "Снизьте ночную влажность и проводите полив в утренние часы.",
            ),
        ),
        CropThreatEntry(
            id = "cucumber_spider_mite",
            cropId = "cucumber",
            kind = ThreatKind.PEST,
            name = LocalizedText("Hörümçək gənəsi və Trips", "Spider Mites & Thrips", "Паутинный клещ и Трипсы"),
            symptoms = LocalizedText(
                "Yarpaqlarda xırda mərmərləşmiş nöqtələr, incə tor və gümüşü ləkələr.",
                "Fine stippling, delicate webbing under leaves, and silvery streaks from thrips.",
                "Мелкая мраморная крапчатость, тонкая паутинка под листьями и серебристые штрихи.",
            ),
            activeIngredient = LocalizedText(
                "Abamektin (və ya Spirodiklofen / Spinosad)",
                "Abamectin (or Spirodiclofen / Spinosad)",
                "Абамектин (или Спиродиклофен / Спиносад)",
            ),
            searchIngredientKey = "Abamektin",
            guidance = LocalizedText(
                "Çiləməni yarpaqların alt səthinə yönəldin; akarisidləri təkrar istifadə etdikdə qrupu dəyişin.",
                "Direct spray toward the undersides of leaves and rotate acaricide modes of action.",
                "Направляйте распыл на нижнюю сторону листьев и чередуйте классы акарицидов.",
            ),
        ),
        // BUĞDA / WHEAT
        CropThreatEntry(
            id = "wheat_rust_septoria",
            cropId = "wheat",
            kind = ThreatKind.DISEASE,
            name = LocalizedText("Sarı/Qonur pas və Septorioz", "Wheat Rust & Septoria Blotch", "Ржавчина и Септориоз пшеницы"),
            symptoms = LocalizedText(
                "Yarpaq və gövdədə sarı-narıncı pustullar və ya xlorotik haşiyəli qəhvəyi ləkələr.",
                "Yellow-orange spore pustules along leaf blades or tan lesions with tiny black pycnidia.",
                "Жёлто-оранжевые пустулы вдоль листа или бурые пятна с чёрными пикнидами.",
            ),
            activeIngredient = LocalizedText(
                "Propikonazol (və ya Azoksistrobin + Siprokonazol / Tebukonazol)",
                "Propiconazole (or Azoxystrobin + Cyproconazole / Tebuconazole)",
                "Пропиконазол (или Азоксистробин + Ципроконазол / Тебуконазол)",
            ),
            searchIngredientKey = "Propikonazol",
            guidance = LocalizedText(
                "Bayraq yarpağın qorunması məhsuldarlıq üçün həlledicidir; kollanma–sünbülləmə dövründə tətbiq edin.",
                "Protecting the flag leaf is critical; spray during booting to heading stage.",
                "Защита флагового листа критична для урожая; обрабатывайте в фазу трубкования–колошения.",
            ),
        ),
        CropThreatEntry(
            id = "wheat_sunn_pest",
            cropId = "wheat",
            kind = ThreatKind.PEST,
            name = LocalizedText("Ziyankar bağacıq (Taxtabiti) və Taxıl böcəyi", "Sunn Pest & Cereal Aphids", "Клоп-черепашка и Злаковая тля"),
            symptoms = LocalizedText(
                "Sünbüllərin ağarması, dənin büzüşməsi və çörəkbişirmə keyfiyyətinin kəskin düşməsi.",
                "White empty heads, shriveled kernels, and degraded gluten quality.",
                "Побеление колосьев, щуплое зерно и резкое снижение клейковины.",
            ),
            activeIngredient = LocalizedText(
                "Lambda-sihalotrin (və ya Asetamiprid / Deltametrin)",
                "Lambda-cyhalothrin (or Acetamiprid / Deltamethrin)",
                "Лямбда-цигалотрин (или Ацетамиприд / Дельтаметрин)",
            ),
            searchIngredientKey = "Lambda-sihalotrin",
            guidance = LocalizedText(
                "Sürfələrin kütləvi çıxışı dövründə iqtisadi zərər həddi (2+ sürfə/m²) aşılarsa işlədin.",
                "Treat when economic threshold (2+ nymphs/m²) is exceeded during larval emergence.",
                "Обрабатывайте при превышении ЭПВ (2+ личинки/м²) в период массового отрождения.",
            ),
        ),
        // ARPA / BARLEY
        CropThreatEntry(
            id = "barley_smut_spot",
            cropId = "barley",
            kind = ThreatKind.DISEASE,
            name = LocalizedText("Sürmə və Torlu ləkəlik", "Barley Smut & Net Blotch", "Головня и Сетчатая пятнистость ячменя"),
            symptoms = LocalizedText(
                "Sünbülün qara toz kütləsinə çevrilməsi və yarpaqlarda torşəkilli qəhvəyi zolaqlar.",
                "Black spore masses replacing grain heads and net-like brown leaf streaks.",
                "Превращение колоса в чёрную споровую массу и сетчатые бурые полосы на листьях.",
            ),
            activeIngredient = LocalizedText(
                "Tebukonazol (toxum dərmanlaması) / Azoksistrobin + Siprokonazol",
                "Tebuconazole (seed treatment) / Azoxystrobin + Cyproconazole",
                "Тебуконазол (протравливание) / Азоксистробин + Ципроконазол",
            ),
            searchIngredientKey = "Azoksistrobin",
            guidance = LocalizedText(
                "Səpindən əvvəl sertifikatlı toxumu dərmanlayın, vegetasiyada ləkəlik artarsa strobilurin+triazol tətbiq edin.",
                "Treat seeds before sowing; apply strobilurin+triazole if foliar net blotch expands.",
                "Протравите семена до посева; по вегетации при развитии пятнистости примените триазол+стробилурин.",
            ),
        ),
        CropThreatEntry(
            id = "barley_aphid",
            cropId = "barley",
            kind = ThreatKind.PEST,
            name = LocalizedText("Arpa mənənəsi və Zərərli taxtabiti", "Barley Aphids & Stink Bugs", "Ячменная тля и Клопы"),
            symptoms = LocalizedText(
                "Cavan zoğlarda və sünbüldə sıx mənənə koloniyaları, saralma və boy geriliyi.",
                "Dense aphid colonies on shoots/heads causing yellowing and stunted growth.",
                "Плотные колонии тли на побегах и колосьях, пожелтение и отставание в росте.",
            ),
            activeIngredient = LocalizedText(
                "Deltametrin (və ya Asetamiprid)",
                "Deltamethrin (or Acetamiprid)",
                "Дельтаметрин (или Ацетамиприд)",
            ),
            searchIngredientKey = "Deltametrin",
            guidance = LocalizedText(
                "Faydalı böcəkləri (parabüzənləri) qorumaq üçün yalnız hədd aşılanda tətbiq edin.",
                "Spray only when pest density exceeds threshold to preserve beneficial ladybugs.",
                "Проводите обработку только при превышении ЭПВ для сохранения божьих коровок.",
            ),
        ),
        // PAMBIQ / COTTON
        CropThreatEntry(
            id = "cotton_bollworm",
            cropId = "cotton",
            kind = ThreatKind.PEST,
            name = LocalizedText("Pambıq sovkası (Qoz qurdu)", "Cotton Bollworm (Helicoverpa)", "Хлопковая совка"),
            symptoms = LocalizedText(
                "Qönçə, çiçək və qoza içərisində gəmirilmiş deşiklər və tırtıl zədəsi.",
                "Bored holes inside squares, blossoms, and green bolls with larval frass.",
                "Выеденные отверстия в бутонах, цветках и коробочках с экскрементами гусениц.",
            ),
            activeIngredient = LocalizedText(
                "Emamektin benzoat (və ya Xlorantraniliprol / Lambda-sihalotrin)",
                "Emamectin benzoate (or Chlorantraniliprole / Lambda-cyhalothrin)",
                "Эмамектин бензоат (или Хлорантранилипрол / Лямбда-цигалотрин)",
            ),
            searchIngredientKey = "Emamektin",
            guidance = LocalizedText(
                "Kiçik yaşlı tırtıllara qarşı erkən tətbiq ən yüksək effektivlik verir.",
                "Target early-instar larvae before they burrow deep inside cotton bolls.",
                "Обработка против гусениц младших возрастов до внедрения в коробочку наиболее эффективна.",
            ),
        ),
        CropThreatEntry(
            id = "cotton_aphid_mite",
            cropId = "cotton",
            kind = ThreatKind.PEST,
            name = LocalizedText("Pambıq mənənəsi, Trips və Hörümçək gənəsi", "Cotton Aphid, Thrips & Spider Mite", "Хлопковая тля, Трипс и Паутинный клещ"),
            symptoms = LocalizedText(
                "Yarpaqların qıvrılması, qırmızımtıl ləkələr, yarpaq tökülməsi və şirə ifrazı.",
                "Leaf curling, bronzing/reddening spots, premature leaf drop, and sticky honeydew.",
                "Скручивание листьев, красноватые пятна, опадение листвы и липкая падь.",
            ),
            activeIngredient = LocalizedText(
                "Asetamiprid / Abamektin / Spirodiklofen",
                "Acetamiprid / Abamectin / Spirodiclofen",
                "Ацетамиприд / Абамектин / Спиродиклофен",
            ),
            searchIngredientKey = "Abamektin",
            guidance = LocalizedText(
                "Mənənəyə qarşı Asetamiprid, gənə müşahidə olunarsa mütləq akarisid (Abamektin/Spirodiklofen) seçin.",
                "Use Acetamiprid for aphids/thrips, and dedicated acaricide (Abamectin/Spirodiclofen) for mites.",
                "Против тли используйте Ацетамиприд, при наличии клещей — акарицид (Абамектин/Спиродиклофен).",
            ),
        ),
        // KARTOF / POTATO
        CropThreatEntry(
            id = "potato_colorado",
            cropId = "potato",
            kind = ThreatKind.PEST,
            name = LocalizedText("Kolorado böcəyi", "Colorado Potato Beetle", "Колорадский жук"),
            symptoms = LocalizedText(
                "Zolaqlı böcəklər və narıncı-qırmızı sürfələr tərəfindən yarpaqların tam yeyilməsi.",
                "Striped adults and orange-red larvae defoliating potato foliage rapidly.",
                "Полосатые жуки и оранжево-красные личинки быстро объедают ботву.",
            ),
            activeIngredient = LocalizedText(
                "Tiametoksam (və ya İmidakloprid / Xlorantraniliprol)",
                "Thiamethoxam (or Imidacloprid / Chlorantraniliprole)",
                "Тиаметоксам (или Имидаклоприд / Хлорантранилипрол)",
            ),
            searchIngredientKey = "Tiametoksam",
            guidance = LocalizedText(
                "Davamlılıq yaranmaması üçün hər mövsümdə fərqli sinif ana maddə növbələşdirin.",
                "Rotate chemical classes each generation to prevent beetle resistance.",
                "Чередуйте химические классы действующих веществ для предотвращения резистентности.",
            ),
        ),
        CropThreatEntry(
            id = "potato_late_blight",
            cropId = "potato",
            kind = ThreatKind.DISEASE,
            name = LocalizedText("Kartof fitoftorozu", "Potato Late Blight", "Фитофтороз картофеля"),
            symptoms = LocalizedText(
                "Yarpaq kənarlarında qara-qəhvəyi ləkələr, gövdənin qaralması və yumrularda paslı çürümə.",
                "Dark brown blotches on leaf margins, stem blackening, and rusty tuber rot.",
                "Буро-чёрные пятна по краям листьев, почернение стеблей и сухая гниль клубней.",
            ),
            activeIngredient = LocalizedText(
                "Mankozeb + Metalaksil-M (və ya Mis sulfat əsaslı Bordo)",
                "Mancozeb + Metalaxyl-M (or Copper sulfate Bordeaux)",
                "Манкоцеб + Металаксил-М (или Бордоская смесь)",
            ),
            searchIngredientKey = "Mankozeb",
            guidance = LocalizedText(
                "Cərgə aralarının qapandığı dövrdə profilaktik çiləməyə başlayın.",
                "Begin preventive sprays at row closure before humid spells.",
                "Начинайте профилактические обработки при смыкании рядков.",
            ),
        ),
        // ÜZÜM / GRAPE
        CropThreatEntry(
            id = "grape_downy_mildew",
            cropId = "grape",
            kind = ThreatKind.DISEASE,
            name = LocalizedText("Mildiu (Yalançı unlu şeh)", "Grape Downy Mildew", "Милдью винограда"),
            symptoms = LocalizedText(
                "Yarpaqlarda 'yağlı ləkələr', alt səthdə ağ kif, salxımların qəhvəyiləşib quruması.",
                "Oily yellow leaf spots with white underside mildew and brown shriveled berries.",
                "Маслянистые пятна на листьях с белым налётом снизу, усыхание соцветий и ягод.",
            ),
            activeIngredient = LocalizedText(
                "Mis hidroksid / Bordo (və ya Metalaksil-M + Mankozeb)",
                "Copper hydroxide / Bordeaux (or Metalaxyl-M + Mancozeb)",
                "Гидроксид меди / Бордоская смесь (или Металаксил-М + Манкоцеб)",
            ),
            searchIngredientKey = "Bordo",
            guidance = LocalizedText(
                "Çiçəkləmədən əvvəl və dərhal sonra sistem təsirli, gilə böyüməsində mis tərkibli ana maddə seçin.",
                "Use systemic ingredients around bloom and copper-based protectants during berry growth.",
                "До и после цветения применяйте системные ДВ, при росте ягод — медьсодержащие.",
            ),
        ),
        CropThreatEntry(
            id = "grape_oidium",
            cropId = "grape",
            kind = ThreatKind.DISEASE,
            name = LocalizedText("Oidium (Həqiqi unlu şeh)", "Grape Powdery Mildew (Oidium)", "Оидиум винограда"),
            symptoms = LocalizedText(
                "Yarpaq və gilələrdə boz-ağ toz örtüyü, gilələrin çatlaması və balıq iyi.",
                "Ash-gray powdery coating on leaves/berries causing berry splitting.",
                "Серо-белый налёт на листьях и ягодах, растрескивание ягод.",
            ),
            activeIngredient = LocalizedText(
                "Penkonazol (və ya Kükürd / Difenokonazol)",
                "Penconazole (or Sulfur / Difenoconazole)",
                "Пенконазол (или Сера / Дифеноконазол)",
            ),
            searchIngredientKey = "Penkonazol",
            guidance = LocalizedText(
                "İsti və quru havada sürətlə yayılır; noxud böyüklüyü dövründə qoruyucu çiləmə vacibdir.",
                "Spreads rapidly in warm dry weather; critical protection at pea-sized berry stage.",
                "Быстро развивается в сухую тёплую погоду; важна защита в фазе горошины.",
            ),
        ),
        // ALMA / APPLE
        CropThreatEntry(
            id = "apple_scab_monilia",
            cropId = "apple",
            kind = ThreatKind.DISEASE,
            name = LocalizedText("Dəmgil (Qabıq ləkəliyi) və Monilioz", "Apple Scab & Monilia Blossom Blight", "Парша и Монилиоз яблони"),
            symptoms = LocalizedText(
                "Yarpaq və meyvələrdə zeytunu-qara qabıqlı ləkələr, meyvələrin çatlaması və çürüməsi.",
                "Olive-black scabby spots on leaves and fruit, fruit cracking and brown rot.",
                "Оливково-чёрные пятна парши на листьях и плодах, растрескивание и плодовая гниль.",
            ),
            activeIngredient = LocalizedText(
                "Siprodinil (və ya Difenokonazol / Mis hidroksid)",
                "Cyprodinil (or Difenoconazole / Copper hydroxide)",
                "Ципродинил (или Дифеноконазол / Гидроксид меди)",
            ),
            searchIngredientKey = "Siprodinil",
            guidance = LocalizedText(
                "Erkən yaz tumurcuq açılışında mislə, çəhrayı qönçə və meyvə bağlama dövründə Siprodinil/Difenokonazolla qoruyun.",
                "Apply copper at bud break and Cyprodinil/Difenoconazole at pink-bud to petal fall.",
                "По зелёному конусу — медь, в фазе розового бутона — Ципродинил/Дифеноконазол.",
            ),
        ),
        CropThreatEntry(
            id = "apple_codling_aphid",
            cropId = "apple",
            kind = ThreatKind.PEST,
            name = LocalizedText("Alma qurdu və Yaşıl alma mənənəsi", "Codling Moth & Green Apple Aphid", "Яблонная плодожорка и Тля"),
            symptoms = LocalizedText(
                "Meyvə içində qurd yolları, cavan zoğların qıvrılması və böyümənin dayanması.",
                "Worm tunnels inside apples, curled young shoots, and stunted terminal growth.",
                "Червоточины в плодах, скручивание молодых побегов и остановка роста.",
            ),
            activeIngredient = LocalizedText(
                "Asetamiprid (və ya İmidakloprid / Lambda-sihalotrin)",
                "Acetamiprid (or Imidacloprid / Lambda-cyhalothrin)",
                "Ацетамиприд (или Имидаклоприд / Лямбда-цигалотрин)",
            ),
            searchIngredientKey = "Asetamiprid",
            guidance = LocalizedText(
                "Çiçəkləmə dövründə arıları qorumaq üçün heç vaxt insektisid çiləməyin.",
                "Never spray insecticides during bloom to protect pollinating bees.",
                "Никогда не применяйте инсектициды во время цветения ради защиты пчёл.",
            ),
        ),
        // YONCA / ALFALFA
        CropThreatEntry(
            id = "alfalfa_weevil",
            cropId = "alfalfa",
            kind = ThreatKind.PEST,
            name = LocalizedText("Yonca böcəyi (Fitonomus) və Mənənə", "Alfalfa Weevil & Aphids", "Люцерновый долгоносик (Фитономус)"),
            symptoms = LocalizedText(
                "Üst yarpaqların şəbəkə kimi gəmirilməsi, tarlanın boz-ağ rəng alması.",
                "Skeletonized upper leaves giving the alfalfa stand a frosted grayish appearance.",
                "Скелетирование верхних листьев, поле приобретает серовато-белёсый оттенок.",
            ),
            activeIngredient = LocalizedText(
                "Lambda-sihalotrin (və ya Deltametrin)",
                "Lambda-cyhalothrin (or Deltamethrin)",
                "Лямбда-цигалотрин (или Дельтаметрин)",
            ),
            searchIngredientKey = "Lambda-sihalotrin",
            guidance = LocalizedText(
                "Çalınmaya az qalıbsa kimyəvi çiləmə əvəzinə erkən biçim daha təhlükəsizdir; çiləmədən sonra karantinə əməl edin.",
                "If close to harvest, early cutting is safer than spraying; strictly observe waiting periods.",
                "Перед укосом безопаснее раннее скашивание; строго соблюдайте срок ожидания до скармливания.",
            ),
        ),
        // QARĞIDALI / CORN
        CropThreatEntry(
            id = "corn_borer",
            cropId = "corn",
            kind = ThreatKind.PEST,
            name = LocalizedText("Qarğıdalı gövdə kəpənəyi və Sovka", "European Corn Borer & Armyworm", "Кукурузный стеблевой мотылёк и Совка"),
            symptoms = LocalizedText(
                "Yarpaqlarda sıra deşikləri, gövdənin sınması və qıçanın uc hissəsinin zədələnməsi.",
                "Shot-hole rows in leaves, stalk breakage, and damaged ear tips.",
                "Ряды отверстий на листьях, поломка стеблей и повреждение початков.",
            ),
            activeIngredient = LocalizedText(
                "Emamektin benzoat (və ya Lambda-sihalotrin)",
                "Emamectin benzoate (or Lambda-cyhalothrin)",
                "Эмамектин бензоат (или Лямбда-цигалотрин)",
            ),
            searchIngredientKey = "Emamektin",
            guidance = LocalizedText(
                "Süpürgə çıxarma başlanğıcında tırtıllar gövdəyə girməzdən öncə tətbiq edin.",
                "Apply at early tasseling before larvae bore into the stalk.",
                "Обрабатывайте в начале вымётывания метёлки до внедрения гусениц в стебель.",
            ),
        ),
    )

    val preparations: List<PreparationEntry> = listOf(
        PreparationEntry(
            id = "ridomil_gold",
            tradeName = "Ridomil Gold MZ 68 WG",
            activeIngredient = LocalizedText("Mankozeb (64%) + Metalaksil-M (4%)", "Mancozeb (64%) + Metalaxyl-M (4%)", "Манкоцеб (64%) + Металаксил-М (4%)"),
            maxRate = 2.5,
            unit = "kg/ha",
            quarantineDays = 21,
            safetyClass = LocalizedText("II Sinif", "Class II", "II Класс"),
            target = LocalizedText("Fitoftoroz, mildiu, peronosporoz", "Late blight, downy mildew", "Фитофтороз, милдью, пероноспороз"),
            searchKeywords = "mankozeb metalaksil mancozeb metalaxyl fitoftoroz mildiu pomidor kartof uzum xiyar",
        ),
        PreparationEntry(
            id = "score_250",
            tradeName = "Score 250 EC",
            activeIngredient = LocalizedText("Difenokonazol (250 q/L)", "Difenoconazole (250 g/L)", "Дифеноконазол (250 г/л)"),
            maxRate = 0.4,
            unit = "L/ha",
            quarantineDays = 21,
            safetyClass = LocalizedText("III Sinif", "Class III", "III Класс"),
            target = LocalizedText("Alternarioz, dəmgil, unlu şeh", "Alternaria, scab, powdery mildew", "Альтернариоз, парша, мучнистая роса"),
            searchKeywords = "difenokonazol difenoconazole alternarioz demgil qabig score",
        ),
        PreparationEntry(
            id = "amistar_extra",
            tradeName = "Amistar Extra 280 SC",
            activeIngredient = LocalizedText("Azoksistrobin (200 q/L) + Siprokonazol (80 q/L)", "Azoxystrobin (200 g/L) + Cyproconazole (80 g/L)", "Азоксистробин + Ципроконазол"),
            maxRate = 0.75,
            unit = "L/ha",
            quarantineDays = 25,
            safetyClass = LocalizedText("III Sinif", "Class III", "III Класс"),
            target = LocalizedText("Pas, septorioz, unlu şeh, torlu ləkəlik", "Rust, septoria, powdery mildew, net blotch", "Ржавчина, септориоз, мучнистая роса"),
            searchKeywords = "azoksistrobin siprokonazol azoxystrobin bugda arpa pomidor pas unlu seh",
        ),
        PreparationEntry(
            id = "tilt_250",
            tradeName = "Tilt 250 EC",
            activeIngredient = LocalizedText("Propikonazol (250 q/L)", "Propiconazole (250 g/L)", "Пропиконазол (250 г/л)"),
            maxRate = 0.5,
            unit = "L/ha",
            quarantineDays = 30,
            safetyClass = LocalizedText("III Sinif", "Class III", "III Класс"),
            target = LocalizedText("Dənli bitkilərdə pas və septorioz", "Cereal rust and septoria", "Ржавчина и септориоз зерновых"),
            searchKeywords = "propikonazol propiconazole bugda arpa pas septorioz tilt",
        ),
        PreparationEntry(
            id = "topas_100",
            tradeName = "Topas 100 EC",
            activeIngredient = LocalizedText("Penkonazol (100 q/L)", "Penconazole (100 g/L)", "Пенконазол (100 г/л)"),
            maxRate = 0.5,
            unit = "L/ha",
            quarantineDays = 20,
            safetyClass = LocalizedText("III Sinif", "Class III", "III Класс"),
            target = LocalizedText("Oidium, həqiqi unlu şeh", "Oidium, powdery mildew", "Оидиум, мучнистая роса"),
            searchKeywords = "penkonazol penconazole oidium unlu seh uzum pomidor topas",
        ),
        PreparationEntry(
            id = "chorus_75",
            tradeName = "Chorus 75 WG",
            activeIngredient = LocalizedText("Siprodinil (750 q/kq)", "Cyprodinil (750 g/kg)", "Ципродинил (750 г/кг)"),
            maxRate = 0.3,
            unit = "kg/ha",
            quarantineDays = 28,
            safetyClass = LocalizedText("III Sinif", "Class III", "III Класс"),
            target = LocalizedText("Meyvə bağlarında dəmgil, monilioz", "Apple scab, moniliosis", "Парша, монилиоз плодовых"),
            searchKeywords = "siprodinil cyprodinil demgil monilioz alma chorus",
        ),
        PreparationEntry(
            id = "bordo_1",
            tradeName = "Bordo məhlulu 1% / Champion",
            activeIngredient = LocalizedText("Mis sulfat / Mis hidroksid", "Copper sulfate / Copper hydroxide", "Сульфат меди / Гидроксид меди"),
            maxRate = 6.0,
            unit = "kg/ha",
            quarantineDays = 15,
            safetyClass = LocalizedText("III Sinif", "Class III", "III Класс"),
            target = LocalizedText("Mildiu, fitoftoroz, bakterial ləkəlik", "Downy mildew, blight, bacterial spot", "Милдью, фитофтороз, бактериоз"),
            searchKeywords = "mis sulfat mis hidroksid copper bordo champion mildiu uzum alma",
        ),
        PreparationEntry(
            id = "mospilan_20",
            tradeName = "Mospilan 20 SP",
            activeIngredient = LocalizedText("Asetamiprid (200 q/kq)", "Acetamiprid (200 g/kg)", "Ацетамиприд (200 г/кг)"),
            maxRate = 0.25,
            unit = "kg/ha",
            quarantineDays = 14,
            safetyClass = LocalizedText("III Sinif", "Class III", "III Класс"),
            target = LocalizedText("Ağqanad, mənənə, taxtabiti, alma qurdu", "Whitefly, aphids, stink bugs, codling moth", "Белокрылка, тля, клопы, плодожорка"),
            searchKeywords = "asetamiprid acetamiprid agqanad menene taxtabiti alma mospilan",
        ),
        PreparationEntry(
            id = "aktara_25",
            tradeName = "Aktara 25 WG",
            activeIngredient = LocalizedText("Tiametoksam (250 q/kq)", "Thiamethoxam (250 g/kg)", "Тиаметоксам (250 г/кг)"),
            maxRate = 0.3,
            unit = "kg/ha",
            quarantineDays = 14,
            safetyClass = LocalizedText("III Sinif", "Class III", "III Класс"),
            target = LocalizedText("Kolorado böcəyi, mənənə, ağqanad", "Colorado beetle, aphids, whitefly", "Колорадский жук, тля, белокрылка"),
            searchKeywords = "tiametoksam thiamethoxam kolorado kartof menene agqanad aktara",
        ),
        PreparationEntry(
            id = "confidor_70",
            tradeName = "Confidor Extra 70 WG",
            activeIngredient = LocalizedText("İmidakloprid (700 q/kq)", "Imidacloprid (700 g/kg)", "Имидаклоприд (700 г/кг)"),
            maxRate = 0.15,
            unit = "kg/ha",
            quarantineDays = 20,
            safetyClass = LocalizedText("II Sinif", "Class II", "II Класс"),
            target = LocalizedText("Sorucu zərərvericilər, kolorado böcəyi, mənənə", "Sucking pests, Colorado beetle, aphids", "Сосущие вредители, колорадский жук, тля"),
            searchKeywords = "imidakloprid imidacloprid kolorado menene confidor",
        ),
        PreparationEntry(
            id = "proclaim_5",
            tradeName = "Proclaim 05 SG",
            activeIngredient = LocalizedText("Emamektin benzoat (50 q/kq)", "Emamectin benzoate (50 g/kg)", "Эмамектин бензоат (50 г/кг)"),
            maxRate = 0.4,
            unit = "kg/ha",
            quarantineDays = 14,
            safetyClass = LocalizedText("III Sinif", "Class III", "III Класс"),
            target = LocalizedText("Pomidor güvəsi (Tuta), pambıq sovkası, gövdə kəpənəyi", "Tuta absoluta, cotton bollworm, corn borer", "Томатная моль, хлопковая совка, мотылёк"),
            searchKeywords = "emamektin emamectin benzoat tuta guve sovka qoz qurdu qargidali",
        ),
        PreparationEntry(
            id = "karate_zeon",
            tradeName = "Karate Zeon 5 CS",
            activeIngredient = LocalizedText("Lambda-sihalotrin (50 q/L)", "Lambda-cyhalothrin (50 g/L)", "Лямбда-цигалотрин (50 г/л)"),
            maxRate = 0.5,
            unit = "L/ha",
            quarantineDays = 20,
            safetyClass = LocalizedText("II Sinif", "Class II", "II Класс"),
            target = LocalizedText("Ziyankar bağacıq, sovka, yonca böcəyi", "Sunn pest, bollworm, alfalfa weevil", "Клоп-черепашка, совка, фитономус"),
            searchKeywords = "lambda sihalotrin cyhalothrin taxtabiti bagaciq sovka yonca karate",
        ),
        PreparationEntry(
            id = "decis_profi",
            tradeName = "Decis 2.5 EC",
            activeIngredient = LocalizedText("Deltametrin (25 q/L)", "Deltamethrin (25 g/L)", "Дельтаметрин (25 г/л)"),
            maxRate = 0.3,
            unit = "L/ha",
            quarantineDays = 20,
            safetyClass = LocalizedText("II Sinif", "Class II", "II Класс"),
            target = LocalizedText("Gəmirici və sorucu həşəratlar, mənənə", "Chewing and sucking pests, aphids", "Грызущие и сосущие вредители, тля"),
            searchKeywords = "deltametrin deltamethrin arpa bugda yonca decis",
        ),
        PreparationEntry(
            id = "vertimec_abamectin",
            tradeName = "Vertimec 018 EC / Envidor",
            activeIngredient = LocalizedText("Abamektin (18 q/L) / Spirodiklofen", "Abamectin (18 g/L) / Spirodiclofen", "Абамектин (18 г/л) / Спиродиклофен"),
            maxRate = 0.5,
            unit = "L/ha",
            quarantineDays = 20,
            safetyClass = LocalizedText("III Sinif", "Class III", "III Класс"),
            target = LocalizedText("Hörümçək gənəsi, trips", "Spider mites, thrips", "Паутинный клещ, трипсы"),
            searchKeywords = "abamektin abamectin spirodiklofen gene trips vertimec envidor pambiq xiyar",
        ),
        PreparationEntry(
            id = "roundup_glyphosate",
            tradeName = "Roundup 36 SL",
            activeIngredient = LocalizedText("Qlifosat (360 q/L)", "Glyphosate (360 g/L)", "Глифосат (360 г/л)"),
            maxRate = 4.0,
            unit = "L/ha",
            quarantineDays = 30,
            safetyClass = LocalizedText("III Sinif", "Class III", "III Класс"),
            target = LocalizedText("Çoxillik və birillik alaq otları", "Annual and perennial weeds", "Однолетние и многолетние сорняки"),
            searchKeywords = "qlifosat glyphosate alaq otlari herbisid roundup",
        ),
    )

    fun threatsForCrop(cropId: String): List<CropThreatEntry> =
        threats.filter { it.cropId == cropId }

    fun searchPreparations(query: String): List<PreparationEntry> {
        val q = normalizeSearch(query)
        if (q.isBlank()) return preparations
        return preparations.filter { entry ->
            normalizeSearch(entry.tradeName).contains(q) ||
                normalizeSearch(entry.activeIngredient.az).contains(q) ||
                normalizeSearch(entry.activeIngredient.en).contains(q) ||
                normalizeSearch(entry.activeIngredient.ru).contains(q) ||
                normalizeSearch(entry.target.az).contains(q) ||
                normalizeSearch(entry.target.en).contains(q) ||
                normalizeSearch(entry.target.ru).contains(q) ||
                normalizeSearch(entry.searchKeywords).contains(q)
        }
    }

    internal fun normalizeSearch(text: String): String {
        return text.lowercase()
            .replace('ə', 'e')
            .replace('ı', 'i')
            .replace('ö', 'o')
            .replace('ü', 'u')
            .replace('ğ', 'g')
            .replace('ş', 's')
            .replace('ç', 'c')
            .trim()
    }
}
