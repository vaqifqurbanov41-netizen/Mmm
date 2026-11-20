package com.example.data.ai

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.model.CropProtectionCatalog
import com.example.data.model.CropThreatEntry
import com.example.data.model.DiseaseVisualCatalog
import com.example.data.model.DiseaseVisualProfile
import com.example.data.model.SeedCatalog
import com.example.data.model.ThreatKind
import com.example.i18n.AppLanguage
import com.example.i18n.StringsAz
import com.example.i18n.StringsEn
import com.example.i18n.StringsRu
import kotlin.math.abs
import kotlin.math.roundToInt

data class BitmapVisualClues(
    val greenPct: Int,
    val yellowChlorosisPct: Int,
    val brownDarkLesionPct: Int,
    val whitePowderyPct: Int,
    val rustOrangePct: Int = 0,
    val spotContrastPct: Int = 0,
)

data class VisualThreatMatch(
    val threat: CropThreatEntry,
    val profile: DiseaseVisualProfile,
    val similarityPct: Int,
)

data class PlantPhotoAssessment(
    val matches: List<VisualThreatMatch>,
    val hasMatch: Boolean,
    val looksLikePlantMaterial: Boolean,
    val appearsHealthyGreen: Boolean,
    val clues: BitmapVisualClues?,
)

object OfflineAgronomistAdvisor {

    fun buildPromptContext(question: String, language: AppLanguage): String {
        val matched = findRelevantThreats(question).take(8)
        val fullCatalog = buildScannerCatalogDigest(language)
        return buildString {
            if (matched.isNotEmpty()) {
                appendLine("Most relevant local crop-protection matches for this question:")
                matched.forEach { entry ->
                    val cropName = CropProtectionCatalog.crops.firstOrNull { it.id == entry.cropId }?.name?.get(language).orEmpty()
                    val profile = DiseaseVisualCatalog.profileFor(entry.id)
                    val cue = profile?.visualCue?.get(language).orEmpty()
                    appendLine("- $cropName | ${entry.name.get(language)} | Symptoms: ${entry.symptoms.get(language)} | Visual signs: $cue | Active ingredient: ${entry.activeIngredient.get(language)} | Tip: ${entry.guidance.get(language)}")
                }
                appendLine()
            }
            append(fullCatalog)
        }
    }

    fun buildScannerCatalogDigest(language: AppLanguage): String {
        val grouped = CropProtectionCatalog.threats.groupBy { it.cropId }
        return buildString {
            appendLine("Complete AqroMeslehet Added Crops & Diseases/Pests Reference Base (${CropProtectionCatalog.crops.size} crops, ${CropProtectionCatalog.threats.size} threats):")
            CropProtectionCatalog.crops.forEach { crop ->
                val cropThreats = grouped[crop.id].orEmpty()
                if (cropThreats.isNotEmpty()) {
                    appendLine("[${crop.name.get(language)}]:")
                    cropThreats.forEach { t ->
                        val profile = DiseaseVisualCatalog.profileFor(t.id)
                        val visualHint = profile?.let {
                            " | Visual: ${DiseaseVisualCatalog.patternLabel(it.patternType, language)} (${it.visualCue.get(language)})"
                        }.orEmpty()
                        appendLine("  • ${t.name.get(language)} (${t.kind.name}): ${t.symptoms.get(language)}$visualHint -> Active: ${t.activeIngredient.get(language)}")
                    }
                }
            }
        }
    }

    fun analyzeBitmapVisualClues(bitmap: Bitmap): BitmapVisualClues? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val stepX = (w / 36).coerceAtLeast(1)
        val stepY = (h / 36).coerceAtLeast(1)
        var total = 0
        var green = 0
        var yellow = 0
        var brownDark = 0
        var powderyWhite = 0
        var rustOrange = 0
        var highContrastSamples = 0
        var prevAvg = -1

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                total++
                val avg = (r + g + b) / 3
                if (prevAvg >= 0 && abs(avg - prevAvg) >= 26) {
                    highContrastSamples++
                }
                prevAvg = avg
                val maxCh = maxOf(r, g, b)
                val minCh = minOf(r, g, b)
                when {
                    r > 195 && g > 195 && b > 195 && (maxCh - minCh) < 28 -> powderyWhite++
                    r > 148 && g in 55..155 && b < 85 && (r - g) >= 22 -> rustOrange++
                    r > 125 && g > 110 && b < 110 && abs(r - g) <= 55 -> yellow++
                    g > r + 10 && g > b + 8 -> green++
                    (r >= g && r >= b && avg in 25..155) || avg < 65 -> brownDark++
                }
                x += stepX
            }
            prevAvg = -1
            y += stepY
        }
        if (total == 0) return null
        return BitmapVisualClues(
            greenPct = (green * 100) / total,
            yellowChlorosisPct = (yellow * 100) / total,
            brownDarkLesionPct = (brownDark * 100) / total,
            whitePowderyPct = (powderyWhite * 100) / total,
            rustOrangePct = (rustOrange * 100) / total,
            spotContrastPct = ((highContrastSamples * 100) / total).coerceIn(0, 100),
        )
    }

    fun matchThreatsWithVisualComparison(
        bitmap: Bitmap?,
        note: String,
        cropIdFilter: String? = null,
        realPhotoSimilarities: Map<String, Float> = emptyMap(),
        limit: Int = 4,
    ): List<VisualThreatMatch> {
        return assessPlantPhoto(
            bitmap = bitmap,
            note = note,
            cropIdFilter = cropIdFilter,
            realPhotoSimilarities = realPhotoSimilarities,
            limit = limit,
        ).matches
    }

    fun assessPlantPhoto(
        bitmap: Bitmap?,
        note: String,
        cropIdFilter: String? = null,
        realPhotoSimilarities: Map<String, Float> = emptyMap(),
        limit: Int = 4,
    ): PlantPhotoAssessment {
        val clues = bitmap?.let { analyzeBitmapVisualClues(it) }
        val normQuery = CropProtectionCatalog.normalizeSearch(note)
        val tokens = normQuery.split(Regex("[^a-zа-я0-9]+"))
            .filter { it.length >= 3 }
            .distinct()

        val matchedCropIds = buildSet {
            if (!cropIdFilter.isNullOrBlank()) add(cropIdFilter)
            CropProtectionCatalog.crops.forEach { crop ->
                val names = listOf(crop.name.az, crop.name.en, crop.name.ru)
                    .map { CropProtectionCatalog.normalizeSearch(it) }
                if (names.any { cname -> normQuery.contains(cname.take(4)) || tokens.any { cname.startsWith(it.take(4)) } }) {
                    add(crop.id)
                }
            }
        }

        val candidates = if (!cropIdFilter.isNullOrBlank()) {
            CropProtectionCatalog.threatsForCrop(cropIdFilter)
        } else {
            CropProtectionCatalog.threats
        }

        val ranked = candidates.mapNotNull { threat ->
            val profile = DiseaseVisualCatalog.profileFor(threat.id) ?: return@mapNotNull null
            var score = 0f

            if (threat.cropId in matchedCropIds) score += 26f

            val searchable = CropProtectionCatalog.normalizeSearch(
                listOf(
                    threat.name.az, threat.name.en, threat.name.ru,
                    threat.symptoms.az, threat.symptoms.en, threat.symptoms.ru,
                    profile.visualCue.az, profile.visualCue.en, profile.visualCue.ru,
                    threat.activeIngredient.az, threat.activeIngredient.en, threat.activeIngredient.ru,
                ).joinToString(" ")
            )
            tokens.forEach { tok ->
                val stem = if (tok.length >= 5) tok.take(5) else tok
                if (searchable.contains(tok)) score += 18f
                else if (stem.length >= 4 && searchable.contains(stem)) score += 9f
            }

            if (clues != null) {
                val w = profile.weights
                val brownSignal = (clues.brownDarkLesionPct / 45f).coerceIn(0f, 1f)
                val yellowSignal = (clues.yellowChlorosisPct / 45f).coerceIn(0f, 1f)
                val whiteSignal = (clues.whitePowderyPct / 45f).coerceIn(0f, 1f)
                val rustSignal = (clues.rustOrangePct / 35f).coerceIn(0f, 1f)
                val contrastSignal = (clues.spotContrastPct / 50f).coerceIn(0f, 1f)
                val visualAlignment =
                    w.brownDark * brownSignal +
                        w.yellowChlorosis * yellowSignal +
                        w.whitePowdery * whiteSignal +
                        w.rustOrange * rustSignal +
                        w.spotContrast * contrastSignal
                val realPhotoSim = realPhotoSimilarities[threat.id]?.coerceIn(0f, 1f)
                if (realPhotoSim != null) {
                    score += visualAlignment * 38f + realPhotoSim * 44f
                } else {
                    score += visualAlignment * 62f
                }
            } else if (tokens.isEmpty() && matchedCropIds.isEmpty()) {
                score += 10f
            }

            val pct = score.roundToInt().coerceIn(18, 96)
            VisualThreatMatch(threat = threat, profile = profile, similarityPct = pct) to score
        }.sortedByDescending { it.second }

        val topMatches = ranked.take(limit).map { it.first }
        val topScore = ranked.firstOrNull()?.second ?: 0f
        val topRealSim = topMatches.firstOrNull()?.let { realPhotoSimilarities[it.threat.id] } ?: 0f

        val plantSignalSum = if (clues != null) {
            clues.greenPct + clues.yellowChlorosisPct + clues.brownDarkLesionPct + clues.rustOrangePct
        } else {
            100
        }
        val looksLikePlantMaterial = clues == null ||
            plantSignalSum >= 22 ||
            tokens.isNotEmpty() ||
            topRealSim >= 0.72f

        val appearsHealthyGreen = clues != null &&
            tokens.isEmpty() &&
            clues.greenPct >= 62 &&
            clues.yellowChlorosisPct < 8 &&
            clues.brownDarkLesionPct < 10 &&
            clues.rustOrangePct < 5 &&
            clues.whitePowderyPct < 12 &&
            clues.spotContrastPct < 18 &&
            topRealSim < 0.72f

        val hasSymptomTokens = tokens.any { tok ->
            CropProtectionCatalog.threats.any { t ->
                val text = CropProtectionCatalog.normalizeSearch("${t.name.az} ${t.name.en} ${t.name.ru} ${t.symptoms.az} ${t.symptoms.en}")
                text.contains(tok)
            }
        }

        val hasMatch = when {
            !looksLikePlantMaterial -> false
            appearsHealthyGreen -> false
            bitmap != null && (topScore >= 24f || topRealSim >= 0.62f || hasSymptomTokens) -> true
            bitmap == null && (tokens.isNotEmpty() && topScore >= 22f) -> true
            else -> false
        }

        return PlantPhotoAssessment(
            matches = topMatches,
            hasMatch = hasMatch,
            looksLikePlantMaterial = looksLikePlantMaterial,
            appearsHealthyGreen = appearsHealthyGreen,
            clues = clues,
        )
    }

    fun describeBitmapClues(bitmap: Bitmap?, language: AppLanguage): String {
        val clues = bitmap?.let { analyzeBitmapVisualClues(it) } ?: return ""
        return when (language) {
            AppLanguage.AZ -> "Şəkil rəng/toxuma analizi: sağlam yaşıl ${clues.greenPct}%, saralma/xloroz ${clues.yellowChlorosisPct}%, qəhvəyi-qara ləkə/nekroz ${clues.brownDarkLesionPct}%, pas-narıncı sahələr ${clues.rustOrangePct}%, ağ-boz tozvari örtük ${clues.whitePowderyPct}%, ləkə sərhəd kəskinliyi ${clues.spotContrastPct}%."
            AppLanguage.EN -> "Image color/texture profile: healthy green ${clues.greenPct}%, yellowing/chlorosis ${clues.yellowChlorosisPct}%, dark/brown necrosis ${clues.brownDarkLesionPct}%, rust-orange pustules ${clues.rustOrangePct}%, pale/powdery areas ${clues.whitePowderyPct}%, lesion contrast ${clues.spotContrastPct}%."
            AppLanguage.RU -> "Цвето-текстурный профиль фото: зелёная ткань ${clues.greenPct}%, хлороз ${clues.yellowChlorosisPct}%, буро-чёрный некроз ${clues.brownDarkLesionPct}%, ржаво-оранжевые пятна ${clues.rustOrangePct}%, белёсый налёт ${clues.whitePowderyPct}%, контраст пятен ${clues.spotContrastPct}%."
        }
    }

    fun diagnosePlantPhotoOffline(
        bitmap: Bitmap?,
        note: String,
        language: AppLanguage,
        aiFailureNote: String? = null,
        cropIdFilter: String? = null,
        realPhotoSimilarities: Map<String, Float> = emptyMap(),
    ): String {
        val strings = when (language) {
            AppLanguage.AZ -> StringsAz
            AppLanguage.EN -> StringsEn
            AppLanguage.RU -> StringsRu
        }
        val assessment = assessPlantPhoto(
            bitmap = bitmap,
            note = note,
            cropIdFilter = cropIdFilter,
            realPhotoSimilarities = realPhotoSimilarities,
            limit = 4,
        )
        val clues = assessment.clues
        val matches = assessment.matches

        if (!assessment.hasMatch) {
            val noMatchBody = buildString {
                appendLine(
                    when (language) {
                        AppLanguage.AZ -> "✅ Aydın xəstəlik/zərərverici uyğunluğu tapılmadı"
                        AppLanguage.EN -> "✅ No clear disease or pest match found"
                        AppLanguage.RU -> "✅ Явных совпадений с болезнями/вредителями не найдено"
                    }
                )
                if (clues != null) {
                    appendLine()
                    appendLine("📷 ${describeBitmapClues(bitmap, language)}")
                }
                appendLine()
                val explanation = when {
                    !assessment.looksLikePlantMaterial -> when (language) {
                        AppLanguage.AZ -> "Şəkildə bitki yarpağı, gövdəsi və ya zədələnmiş sahə aydın seçilmir. Zəhmət olmasa bitkinin şübhəli hissəsini yaxından və işıqlı şəraitdə yenidən çəkin."
                        AppLanguage.EN -> "Plant foliage, stems, or clear disease symptoms are not distinguishable in this frame. Please take a closer, well-lit photo of the plant part."
                        AppLanguage.RU -> "На фото не распознаются листья, стебель или признаки поражения. Сделайте более чёткий снимок растения крупным планом."
                    }
                    assessment.appearsHealthyGreen -> when (language) {
                        AppLanguage.AZ -> "Bitki toxuması əsasən sağlam yaşıl görünür; real xəstəlik şəkilləri ilə ciddi uyğunluq (ləkə, pas, kif və ya zədələnmə) aşkarlanmadı."
                        AppLanguage.EN -> "Foliage appears mostly healthy green with no significant match against reference disease photos (spots, rust, powdery mildew, or chewing damage)."
                        AppLanguage.RU -> "Ткань растения выглядит преимущественно здоровой и зелёной; значимых совпадений с реальными фото болезней не обнаружено."
                    }
                    else -> when (language) {
                        AppLanguage.AZ -> "Bazadakı real xəstəlik fotoları və əlamətlərlə güclü uyğunluq tapılmadı. Dəqiq qiymətləndirmə üçün bitki adını seçib yarpaq üstünü və altını yaxından çəkin."
                        AppLanguage.EN -> "No strong match was found against reference disease photos. Select the crop above and capture a closer photo of affected spots or leaf undersides."
                        AppLanguage.RU -> "Сильных совпадений с базой фото болезней не найдено. Выберите культуру выше и снимите поражённую часть ближе."
                    }
                }
                appendLine(explanation)
                appendLine()
                append("⚠️ ${strings.scannerConsultAgronomistTitle}: ${strings.scannerConsultAgronomistBody}")
            }
            return appendNoteIfNeeded(noMatchBody, aiFailureNote, language)
        }

        val result = buildString {
            appendLine(
                when (language) {
                    AppLanguage.AZ -> "🔬 Real Xəstəlik Şəkilləri + Daxili Baza Müqayisəsi Nəticəsi"
                    AppLanguage.EN -> "🔬 Real Reference Photo + Internal Database Comparison"
                    AppLanguage.RU -> "🔬 Сравнение с реальными фото болезней и локальной базой"
                }
            )
            if (clues != null) {
                appendLine()
                appendLine("📷 ${describeBitmapClues(bitmap, language)}")
                val patternHint = when {
                    clues.rustOrangePct >= 10 -> when (language) {
                        AppLanguage.AZ -> "Narıncı-pas çalarlı sahələr seçilir (sarı/qonur/gövdə pası, fuzarioz və ya kuskuta əlamətləri ilə müqayisə edilir)."
                        AppLanguage.EN -> "Rust-orange pustule hues detected (compared against cereal rusts, alfalfa rust, fusarium, and dodder)."
                        AppLanguage.RU -> "Обнаружены ржаво-оранжевые участки (сравнение со ржавчиной, фузариозом и повиликой)."
                    }
                    clues.brownDarkLesionPct >= 16 -> when (language) {
                        AppLanguage.AZ -> "Tünd-qəhvəyi/nekrotik ləkə payı yüksəkdir (fitoftoroz, alternarioz, qonur ləkəlik və ya bakterial yanğı ilə müqayisə olunur)."
                        AppLanguage.EN -> "High share of dark/brown lesions detected (compared with late/early blight, leaf spot, or bacterial blight)."
                        AppLanguage.RU -> "Заметна высокая доля буро-тёмных пятен (сравнение с фитофторозом, альтернариозом, пятнистостями и бактериозом)."
                    }
                    clues.yellowChlorosisPct >= 16 -> when (language) {
                        AppLanguage.AZ -> "Saralma/xloroz göstəricisi nəzərəçarpan səviyyədədir (peronosporoz, sarı ləkəlik, mənənə/virus və ya qida çatışmazlığı ehtimalı)."
                        AppLanguage.EN -> "Notable yellowing/chlorosis detected (possible downy mildew, tan spot, aphids/virus, or nutrient stress)."
                        AppLanguage.RU -> "Выражено пожелтение/хлороз (возможен пероноспороз, жёлтая пятнистость, тля/вирусы или дефицит питания)."
                    }
                    clues.whitePowderyPct >= 18 -> when (language) {
                        AppLanguage.AZ -> "Ağ-boz tozvari örtük əlaməti müşahidə olunur (unlu şeh / oidium / fındıq külələməsi ilə müqayisə olunur)."
                        AppLanguage.EN -> "Pale/powdery surface pattern detected (compared with powdery mildew / oidium profiles)."
                        AppLanguage.RU -> "Заметны белёсые мучнистые участки (сравнение с мучнистой росой / оидиумом)."
                    }
                    else -> when (language) {
                        AppLanguage.AZ -> "Bitkidə vizual dəyişiklik əlamətləri aşkarlandı; real referans şəkillərlə müqayisə edildi."
                        AppLanguage.EN -> "Visual symptom deviations detected and compared against real reference disease photos."
                        AppLanguage.RU -> "Обнаружены визуальные отклонения; выполнено сравнение с реальными фото болезней."
                    }
                }
                appendLine("• $patternHint")
            }

            if (matches.isNotEmpty()) {
                appendLine()
                appendLine(
                    when (language) {
                        AppLanguage.AZ -> if (realPhotoSimilarities.isNotEmpty()) {
                            "📸 Real xəstəlik şəkilləri üzrə ən yaxın uyğunluqlar:"
                        } else {
                            "📚 Daxili bazadan vizual əlamət müqayisəsi üzrə ən yaxın ehtimallar:"
                        }
                        AppLanguage.EN -> if (realPhotoSimilarities.isNotEmpty()) {
                            "📸 Top matches compared against real reference photos:"
                        } else {
                            "📚 Top disease/pest matches by visual symptom comparison:"
                        }
                        AppLanguage.RU -> if (realPhotoSimilarities.isNotEmpty()) {
                            "📸 Наиболее близкие совпадения с реальными фото болезней:"
                        } else {
                            "📚 Наиболее вероятные совпадения при визуальном сравнении:"
                        }
                    }
                )
                matches.forEachIndexed { idx, match ->
                    val t = match.threat
                    val cropName = CropProtectionCatalog.crops.firstOrNull { it.id == t.cropId }?.name?.get(language).orEmpty()
                    val patternName = DiseaseVisualCatalog.patternLabel(match.profile.patternType, language)
                    appendLine("${idx + 1}) $cropName — ${t.name.get(language)} (~${match.similarityPct}%)")
                    when (language) {
                        AppLanguage.AZ -> {
                            appendLine("   • Vizual müqayisə (${patternName}): ${match.profile.visualCue.az}")
                            appendLine("   • Təsiredici ana maddə: ${t.activeIngredient.az}")
                            appendLine("   • Tövsiyə: ${t.guidance.az}")
                        }
                        AppLanguage.EN -> {
                            appendLine("   • Visual cue ($patternName): ${match.profile.visualCue.en}")
                            appendLine("   • Active ingredient: ${t.activeIngredient.en}")
                            appendLine("   • Guidance: ${t.guidance.en}")
                        }
                        AppLanguage.RU -> {
                            appendLine("   • Визуальный признак ($patternName): ${match.profile.visualCue.ru}")
                            appendLine("   • Действующее вещество: ${t.activeIngredient.ru}")
                            appendLine("   • Рекомендация: ${t.guidance.ru}")
                        }
                    }
                }
            }

            appendLine()
            append("⚠️ ${strings.scannerConsultAgronomistTitle}: ${strings.scannerConsultAgronomistBody}")
        }
        return appendNoteIfNeeded(result, aiFailureNote, language)
    }

    fun answerOffline(
        question: String,
        language: AppLanguage,
        aiFailureNote: String? = null,
    ): String {
        val normalizedQ = CropProtectionCatalog.normalizeSearch(question)
        val strings = when (language) {
            AppLanguage.AZ -> StringsAz
            AppLanguage.EN -> StringsEn
            AppLanguage.RU -> StringsRu
        }

        // 1. Check banned pesticide mentions first
        val bannedMatch = strings.bannedPesticides.firstOrNull { banned ->
            val nameMatch = CropProtectionCatalog.normalizeSearch(banned.name)
                .split(" ", "/", "(", ")")
                .any { it.length >= 3 && normalizedQ.contains(it) }
            val aliasMatch = banned.aliases.any { alias ->
                normalizedQ.contains(CropProtectionCatalog.normalizeSearch(alias))
            }
            nameMatch || aliasMatch
        }
        if (bannedMatch != null) {
            val base = when (language) {
                AppLanguage.AZ -> """
                    🚫 Oflayn Aqronom Xəbərdarlığı — Qadağan Olunmuş Maddə!

                    • Maddə: ${bannedMatch.name}
                    • Səbəb: ${bannedMatch.reason}
                    • Status: ${bannedMatch.legalStatus}
                    • Tövsiyə: Pestisid bölməsindən bitki və xəstəliyə uyğun icazəli ana maddəni seçin.
                """.trimIndent()
                AppLanguage.EN -> """
                    🚫 Offline Agronomist Warning — Banned Substance!

                    • Substance: ${bannedMatch.name}
                    • Hazard: ${bannedMatch.reason}
                    • Status: ${bannedMatch.legalStatus}
                    • Advice: Check the Pesticide section for approved active ingredients per crop.
                """.trimIndent()
                AppLanguage.RU -> """
                    🚫 Офлайн Агроном — Запрещённое вещество!

                    • Вещество: ${bannedMatch.name}
                    • Причина: ${bannedMatch.reason}
                    • Статус: ${bannedMatch.legalStatus}
                    • Совет: Выберите разрешённое действующее вещество в разделе Пестициды.
                """.trimIndent()
            }
            return appendNoteIfNeeded(base, aiFailureNote, language)
        }

        // Check if user asks for all crops/all diseases overview
        val asksAllCropsOverview = (normalizedQ.contains("butun bitki") || normalizedQ.contains("hansi bitki") ||
            normalizedQ.contains("butun xestelik") || normalizedQ.contains("all crop") || normalizedQ.contains("all disease") ||
            normalizedQ.contains("все культур") || normalizedQ.contains("все болезн"))

        if (asksAllCropsOverview) {
            val overview = buildScannerCatalogDigest(language)
            return appendNoteIfNeeded(overview, aiFailureNote, language)
        }

        // 2. Match diseases & pests from CropProtectionCatalog
        val matchedCrops = findMatchedCropIds(normalizedQ)
        val threats = findRelevantThreats(question).let { found ->
            if (matchedCrops.size == 1) {
                val cropAll = CropProtectionCatalog.threatsForCrop(matchedCrops.first())
                // If asking generally about a single crop or top hits, include ALL diseases of that crop so none are missed!
                if (found.size <= 2 || normalizedQ.contains("butun") || normalizedQ.contains("hamis") || normalizedQ.contains("xestelik") || normalizedQ.contains("all") || normalizedQ.contains("все")) {
                    (found + cropAll).distinctBy { it.id }
                } else {
                    found.take(6)
                }
            } else {
                found.take(6)
            }
        }

        if (threats.isNotEmpty()) {
            val header = when (language) {
                AppLanguage.AZ -> "📚 Oflayn Aqronom Bələdçisi (Bitki Mühafizə Bazası)"
                AppLanguage.EN -> "📚 Offline Agronomist Guide (Local Crop Protection Base)"
                AppLanguage.RU -> "📚 Офлайн Агроном (База защиты растений)"
            }
            val body = buildString {
                appendLine(header)
                threats.forEachIndexed { idx, t ->
                    val cropLabel = CropProtectionCatalog.crops
                        .firstOrNull { it.id == t.cropId }
                        ?.name?.get(language)
                        .orEmpty()
                    val kindLabel = when (t.kind) {
                        ThreatKind.DISEASE -> when (language) {
                            AppLanguage.AZ -> "Xəstəlik"
                            AppLanguage.EN -> "Disease"
                            AppLanguage.RU -> "Болезнь"
                        }
                        ThreatKind.PEST -> when (language) {
                            AppLanguage.AZ -> "Zərərverici"
                            AppLanguage.EN -> "Pest"
                            AppLanguage.RU -> "Вредитель"
                        }
                    }
                    val visualCue = DiseaseVisualCatalog.profileFor(t.id)?.visualCue?.get(language)
                    appendLine()
                    appendLine("${idx + 1}. $cropLabel — ${t.name.get(language)} ($kindLabel)")
                    when (language) {
                        AppLanguage.AZ -> {
                            appendLine("• Əlamətlər: ${t.symptoms.az}")
                            if (!visualCue.isNullOrBlank()) appendLine("• Vizual müqayisə: $visualCue")
                            appendLine("• Tövsiyə olunan ana maddə: ${t.activeIngredient.az}")
                            appendLine("• Tətbiq məsləhəti: ${t.guidance.az}")
                        }
                        AppLanguage.EN -> {
                            appendLine("• Symptoms: ${t.symptoms.en}")
                            if (!visualCue.isNullOrBlank()) appendLine("• Visual sign: $visualCue")
                            appendLine("• Active ingredient: ${t.activeIngredient.en}")
                            appendLine("• Guidance: ${t.guidance.en}")
                        }
                        AppLanguage.RU -> {
                            appendLine("• Симптомы: ${t.symptoms.ru}")
                            if (!visualCue.isNullOrBlank()) appendLine("• Визуальный признак: $visualCue")
                            appendLine("• Действующее вещество: ${t.activeIngredient.ru}")
                            appendLine("• Рекомендация: ${t.guidance.ru}")
                        }
                    }
                }
                appendLine()
                append(
                    when (language) {
                        AppLanguage.AZ -> "💡 Qeyd: Seçilmiş ana maddəyə uyğun preparatları və maksimum hektar normasını «Pestisid → Preparat» bölməsində, şəkil müqayisəsini isə «Skaner» bölməsində yoxlaya bilərsiniz."
                        AppLanguage.EN -> "💡 Tip: Search commercial products by active ingredient in «Pesticides → Products» or run photo visual comparison in the Scanner."
                        AppLanguage.RU -> "💡 Совет: Подобрать препараты по ДВ можно в «Пестициды → Препарат», а сравнить по фото — в Сканере."
                    }
                )
            }
            return appendNoteIfNeeded(body, aiFailureNote, language)
        }

        // 3. Check seed / variety questions
        val matchingVarieties = SeedCatalog.varieties.filter { v ->
            val crop = SeedCatalog.crops.firstOrNull { it.id == v.cropId }
            val tokens = normalizedQ.split(Regex("\\s+")).filter { it.length >= 3 }
            tokens.any { tok ->
                CropProtectionCatalog.normalizeSearch(v.name).contains(tok) ||
                    CropProtectionCatalog.normalizeSearch(crop?.name?.az.orEmpty()).contains(tok) ||
                    CropProtectionCatalog.normalizeSearch(crop?.name?.en.orEmpty()).contains(tok) ||
                    CropProtectionCatalog.normalizeSearch(crop?.name?.ru.orEmpty()).contains(tok)
            }
        }.take(3)
        if (matchingVarieties.isNotEmpty() &&
            (normalizedQ.contains("sort") || normalizedQ.contains("toxum") || normalizedQ.contains("sepin") ||
                normalizedQ.contains("seed") || normalizedQ.contains("variety") || normalizedQ.contains("семен") || normalizedQ.contains("сорт"))
        ) {
            val body = buildString {
                appendLine(
                    when (language) {
                        AppLanguage.AZ -> "🌱 Oflayn Toxum və Sort Bələdçisi"
                        AppLanguage.EN -> "🌱 Offline Seed & Variety Guide"
                        AppLanguage.RU -> "🌱 Офлайн гид по семенам и сортам"
                    }
                )
                matchingVarieties.forEach { v ->
                    appendLine()
                    appendLine("• ${v.name} — ${v.summary.get(language)}")
                    appendLine("  ${v.sowingWindow.get(language)} | ${v.seedingRate.get(language)}")
                    appendLine("  ${v.agronomyTip.get(language)}")
                }
            }
            return appendNoteIfNeeded(body, aiFailureNote, language)
        }

        // 4. General helpful offline summary listing all 11 crops
        val cropListText = CropProtectionCatalog.crops.joinToString(", ") { it.name.get(language) }
        val fallback = when (language) {
            AppLanguage.AZ -> """
                📚 Oflayn Aqronom Bələdçisi (Yerli Baza aktivdir)

                Bazamızdakı bütün bitkilər ($cropListText) üzrə ${CropProtectionCatalog.threats.size} xəstəlik və zərərverici əlamətləri ilə tanınır. Sualınızda bitki və ya əlamət yazın:
                • «Kartof xəstəlikləri və fitoftoroz»
                • «Buğda sarı pası / fuzarioz»
                • «Yonca qonur ləkəlik / kuskuta»
                • «Fındıq unlu şeh / bakterial yanığı»
                • «Pomidor yarpaqları saralır»

                İstənilən bitkinin bütün xəstəliklərini görmək üçün «Bütün bitkilər və xəstəliklər» yaza bilərsiniz.
            """.trimIndent()
            AppLanguage.EN -> """
                📚 Offline Agronomist Guide (Local Database Active)

                Our assistant recognizes ${CropProtectionCatalog.threats.size} diseases and pests across all ${CropProtectionCatalog.crops.size} crops ($cropListText). Ask by crop or symptom:
                • "Potato diseases / early blight"
                • "Wheat rust / fusarium / bunt"
                • "Alfalfa leaf spot / rust"
                • "Hazelnut powdery mildew / blight"
            """.trimIndent()
            AppLanguage.RU -> """
                📚 Офлайн Агроном (Локальная база активна)

                Ассистент знает все ${CropProtectionCatalog.threats.size} болезней и вредителей по ${CropProtectionCatalog.crops.size} культурам ($cropListText). Укажите культуру или симптом:
                • «Болезни картофеля / альтернариоз»
                • «Ржавчина или фузариоз пшеницы»
                • «Бурая пятнистость люцерны»
                • «Мучнистая роса и ожог фундука»
            """.trimIndent()
        }
        return appendNoteIfNeeded(fallback, aiFailureNote, language)
    }

    private fun findMatchedCropIds(normQuery: String): Set<String> {
        val tokens = normQuery.split(Regex("[^a-zа-я0-9]+")).filter { it.length >= 3 }
        return CropProtectionCatalog.crops.filter { crop ->
            val allNames = listOf(crop.name.az, crop.name.en, crop.name.ru)
                .map { CropProtectionCatalog.normalizeSearch(it) }
            allNames.any { cname ->
                normQuery.contains(cname.take(4)) || tokens.any { tok -> cname.startsWith(tok.take(4)) }
            }
        }.map { it.id }.toSet()
    }

    fun findRelevantThreats(query: String): List<CropThreatEntry> {
        val normQuery = CropProtectionCatalog.normalizeSearch(query)
        if (normQuery.isBlank()) return emptyList()

        val tokens = normQuery
            .split(Regex("[^a-zа-я0-9]+"))
            .filter { it.length >= 3 }
            .distinct()

        val matchedCropIds = findMatchedCropIds(normQuery)

        val scored = CropProtectionCatalog.threats.map { threat ->
            var score = 0
            if (threat.cropId in matchedCropIds) score += 6

            val visualCue = DiseaseVisualCatalog.profileFor(threat.id)?.visualCue
            val searchable = CropProtectionCatalog.normalizeSearch(
                listOfNotNull(
                    threat.name.az, threat.name.en, threat.name.ru,
                    threat.symptoms.az, threat.symptoms.en, threat.symptoms.ru,
                    visualCue?.az, visualCue?.en, visualCue?.ru,
                    threat.activeIngredient.az, threat.activeIngredient.en, threat.activeIngredient.ru,
                    threat.searchIngredientKey,
                ).joinToString(" ")
            )

            tokens.forEach { tok ->
                val stem = if (tok.length >= 5) tok.take(5) else tok
                if (searchable.contains(tok)) score += 4
                else if (stem.length >= 4 && searchable.contains(stem)) score += 2
            }

            if ((normQuery.contains("saral") || normQuery.contains("leke") || normQuery.contains("quru") || normQuery.contains("yellow") || normQuery.contains("spot") || normQuery.contains("пятн")) &&
                threat.kind == ThreatKind.DISEASE
            ) {
                score += 2
            }
            if ((normQuery.contains("qurd") || normQuery.contains("bocek") || normQuery.contains("hesarat") || normQuery.contains("pest") || normQuery.contains("worm") || normQuery.contains("вред") || normQuery.contains("жук")) &&
                threat.kind == ThreatKind.PEST
            ) {
                score += 2
            }
            threat to score
        }

        val minThreshold = if (matchedCropIds.isNotEmpty()) 6 else 4
        return scored
            .filter { it.second >= minThreshold }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun appendNoteIfNeeded(
        base: String,
        aiFailureNote: String?,
        language: AppLanguage,
    ): String {
        val err = aiFailureNote?.trim().orEmpty()
        if (err.isBlank()) return base
        val prefix = when (language) {
            AppLanguage.AZ -> "ℹ️ Bulud AI hazırda əlçatan olmadığından (və ya oflayn rejimdə) yerli baza üzrə cavab verildi"
            AppLanguage.EN -> "ℹ️ Answered from local catalog because Cloud AI is unavailable or offline"
            AppLanguage.RU -> "ℹ️ Ответ сформирован по локальной базе (облачный AI недоступен или офлайн)"
        }
        return "$base\n\n$prefix ($err)"
    }
}
