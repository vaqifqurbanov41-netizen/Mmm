package com.example.data.ai

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.model.AqroSigortaCatalog
import com.example.data.model.CropProtectionCatalog
import com.example.data.model.CropThreatEntry
import com.example.data.model.DiseaseVisualCatalog
import com.example.data.model.DiseaseVisualProfile
import com.example.data.model.FarmField
import com.example.data.model.SeedCatalog
import com.example.data.model.ThreatKind
import com.example.i18n.AppLanguage
import com.example.i18n.StringsAz
import com.example.i18n.StringsEn
import com.example.i18n.StringsRu
import java.util.Locale
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

    fun buildFarmContextSummary(
        fields: List<FarmField>,
        region: String = "",
        tempC: Double? = null,
        hazardLabels: List<String> = emptyList(),
    ): String {
        val parts = mutableListOf<String>()
        if (region.isNotBlank()) {
            parts += "Region: $region"
        }
        if (fields.isNotEmpty()) {
            val totalHa = fields.sumOf { it.sizeHa }
            val fieldList = fields.take(5).joinToString("; ") { f ->
                val cropText = f.crop.ifBlank { "ümumi sahə" }
                val areaText = String.format(Locale.US, "%.2f ha", f.sizeHa)
                "${f.name} ($cropText, $areaText)"
            }
            parts += "Farmer fields (${fields.size}, total ${String.format(Locale.US, "%.2f ha", totalHa)}): $fieldList"
        }
        if (tempC != null) {
            parts += "Current temperature: ${String.format(Locale.US, "%.1f°C", tempC)}"
        }
        if (hazardLabels.isNotEmpty()) {
            parts += "7-day weather warnings: ${hazardLabels.distinct().take(4).joinToString(", ")}"
        }
        return parts.joinToString("\n")
    }

    fun buildPromptContext(question: String, language: AppLanguage): String {
        val norm = CropProtectionCatalog.normalizeSearch(question)
        val matched = findRelevantThreats(question).take(6)
        val wantsOverview = norm.contains("butun bitki") || norm.contains("hansi bitki") ||
            norm.contains("butun xestelik") || norm.contains("hamis") ||
            norm.contains("all crop") || norm.contains("all disease") ||
            norm.contains("все культур") || norm.contains("все болезн")
        val wantsInsurance = norm.contains("sigort") || norm.contains("aqrosigort") ||
            norm.contains("1651") || norm.contains("filial") ||
            norm.contains("insur") || norm.contains("страх")
        val cropSummary = CropProtectionCatalog.crops.joinToString(", ") { it.name.get(language) }
        return buildString {
            appendLine("AqroMəsləhət local crops (${CropProtectionCatalog.crops.size}): $cropSummary.")
            if (matched.isNotEmpty()) {
                appendLine("Most relevant local crop-protection matches for this question:")
                matched.forEach { entry ->
                    val cropName = CropProtectionCatalog.crops.firstOrNull { it.id == entry.cropId }?.name?.get(language).orEmpty()
                    val profile = DiseaseVisualCatalog.profileFor(entry.id)
                    val cue = profile?.visualCue?.get(language).orEmpty()
                    appendLine("- $cropName | ${entry.name.get(language)} | Symptoms: ${entry.symptoms.get(language)} | Visual signs: $cue | Active ingredient: ${entry.activeIngredient.get(language)} | Tip: ${entry.guidance.get(language)}")
                }
            } else if (wantsOverview) {
                appendLine()
                append(buildScannerCatalogDigest(language))
            }
            if (wantsInsurance) {
                appendLine()
                appendLine("Azerbaijan Aqrar Sığorta Fondu (Aqrosığorta, 50% state premium subsidy, hotline 1651) regional branches:")
                AqroSigortaCatalog.branches.forEach { b ->
                    appendLine("- ${b.name}: ${b.address} | Tel: ${b.phone}")
                }
            }
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
        farmContextSummary: String = "",
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
                if (found.size <= 2 || normalizedQ.contains("butun") || normalizedQ.contains("hamis") || normalizedQ.contains("xestelik") || normalizedQ.contains("all") || normalizedQ.contains("все")) {
                    (found + cropAll).distinctBy { it.id }
                } else {
                    found.take(6)
                }
            } else {
                found.take(6)
            }
        }

        val isFertilizerTopic = containsAny(
            normalizedQ,
            "gubre", "npk", "azot", "karbamid", "sora", "fosfor", "kalium", "ammofos", "peyin", "yemleme",
            "fertiliz", "urea", "nitrogen", "phosphor", "potassium",
            "удобрен", "азот", "карбамид", "селитр", "фосфор", "калий", "подкорм"
        )
        val isIrrigationTopic = containsAny(
            normalizedQ,
            "suvarma", "suvar", "damci", "damcila", "rutubet", "quraq", "nemlik",
            "irrigat", "drip", "moisture", "drought",
            "полив", "капельн", "орошен", "засух"
        )
        val isInsuranceOrSubsidyTopic = containsAny(
            normalizedQ,
            "sigorta", "aqrosigorta", "subsidiya", "ektis", "ekti", "1651", "1652", "filial",
            "insur", "subsid", "branch",
            "страх", "субсид", "филиал"
        )
        val isSoilTopic = containsAny(
            normalizedQ,
            "torpaq", "ph", "soran", "duz", "humus", "soil", "salin", "почв", "засолен", "солонч"
        )
        val isOrchardTopic = containsAny(
            normalizedQ,
            "budama", "bag", "ting", "agac", "prun", "orchard", "обрезк", "сад", "саженц"
        )
        val isGreenhouseTopic = containsAny(
            normalizedQ,
            "istixana", "parnik", "greenhouse", "теплиц"
        )
        val hasExplicitDiseaseKeyword = containsAny(
            normalizedQ,
            "xestelik", "leke", "qurd", "pas", "fitoftor", "alternar", "kif", "bocek", "bit", "gobelek",
            "disease", "blight", "mildew", "rust", "pest", "aphid", "rot",
            "болезн", "вред", "фитофтор", "ржавчин", "пятн", "мучнист", "тля"
        )
        val isDedicatedAgronomyTopic = isFertilizerTopic || isIrrigationTopic || isInsuranceOrSubsidyTopic ||
            isSoilTopic || isOrchardTopic || isGreenhouseTopic

        if (threats.isNotEmpty() && (!isDedicatedAgronomyTopic || hasExplicitDiseaseKeyword)) {
            val header = when (language) {
                AppLanguage.AZ -> "📚 Yerli Aqronom Bələdçisi (Bitki Mühafizə Bazası)"
                AppLanguage.EN -> "📚 Offline Agronomist Guide (Local Crop Protection Base)"
                AppLanguage.RU -> "📚 Локальный Агроном (База защиты растений)"
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
        }.take(4)
        if (matchingVarieties.isNotEmpty() &&
            containsAny(normalizedQ, "sort", "toxum", "sepin", "ekin", "mehsuldar", "seed", "variety", "sow", "семен", "сорт", "посев")
        ) {
            val body = buildString {
                appendLine(
                    when (language) {
                        AppLanguage.AZ -> "🌱 Toxum və Sort Bələdçisi"
                        AppLanguage.EN -> "🌱 Seed & Variety Guide"
                        AppLanguage.RU -> "🌱 Гид по семенам и сортам"
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

        // 4. Aqrosığorta & Subsidy branches
        if (isInsuranceOrSubsidyTopic) {
            val branchesText = AqroSigortaCatalog.branches.joinToString("\n") { b ->
                "• ${b.name} — ${b.address} (Tel: ${b.phone})"
            }
            val body = when (language) {
                AppLanguage.AZ -> """
                    🛡️ Aqrosığorta və Subsidiya Bələdçisi

                    • Dövlət Dəstəyi: Aqrar Sığorta Fondu vasitəsilə hesablanan sığorta haqqının 50%-ni dövlət qarşılayır.
                    • Qaynar xətt: 1651 (Aqrar Sığorta) | 1652 (KTN) | EKTİS (eagro.az)
                    • Məsləhət: Səpin və ya bağ bəyannaməsini vaxtında EKTİS-ə daxil edin; tətbiqdəki «Sığorta» kalkulyatoru və «Məntəqələr → Aqrosığorta» bölməsindən birbaşa zəng edə bilərsiniz.

                    📍 Regional Aqrosığorta Filialları:
                    $branchesText
                """.trimIndent()
                AppLanguage.EN -> """
                    🛡️ Agricultural Insurance & Subsidy Guide

                    • State Support: 50% of the agricultural insurance premium is subsidized by the state via the Agrarian Insurance Fund.
                    • Hotlines: 1651 (AqroSığorta) | 1652 (Ministry) | EKTIS (eagro.az)

                    📍 Regional Aqrosığorta Branches:
                    $branchesText
                """.trimIndent()
                AppLanguage.RU -> """
                    🛡️ Аграрное страхование и субсидии

                    • Господдержка: государство компенсирует 50% страхового взноса через Фонд аграрного страхования.
                    • Горячая линия: 1651 (AqroSığorta) | 1652 | EKTIS (eagro.az)

                    📍 Региональные филиалы Aqrosığorta:
                    $branchesText
                """.trimIndent()
            }
            return appendNoteIfNeeded(body, aiFailureNote, language)
        }

        // 5. NPK & Fertilization advice
        if (isFertilizerTopic) {
            val cropHint = if (matchedCrops.isNotEmpty()) {
                val names = matchedCrops.mapNotNull { id -> CropProtectionCatalog.crops.firstOrNull { it.id == id }?.name?.get(language) }
                names.joinToString(", ")
            } else ""
            val body = when (language) {
                AppLanguage.AZ -> """
                    🧪 Gübrələmə və NPK Məsləhəti${if (cropHint.isNotBlank()) " ($cropHint)" else ""}

                    • Əsas Səpin (Payız/Yaz): Fosfor (Ammofos/Superfosfat) və Kalium gübrələrini şum altına və ya səpinlə birlikdə verin — kök sistemini gücləndirir.
                    • Yemləmə (Vegetasiya): Azotu (Karbamid 46% N və ya Ammonium şorası 34% N) 2–3 mərhələdə bölərək verin; taxıllarda kollanma və boruyaçıxma dövrü kritikdir.
                    • Tətbiq Qaydası: Azot gübrəsini yağışdan əvvəl və ya suvarmadan dərhal öncə verin ki, buxarlanma itkisi (ammonyak uçması) olmasın.
                    • Şoran və Qələvi Torpaqlar: pH > 7.5 olan sahələrdə ammonium sulfat və ya damcı gübrələmə (fertigasiya) daha yüksək mənimsəmə verir.
                    • Məsləhət: Hektar üzrə dəqiq kq hesablaması üçün «NPK» bölməsindən istifadə edin.
                """.trimIndent()
                AppLanguage.EN -> """
                    🧪 Fertilization & NPK Advice${if (cropHint.isNotBlank()) " ($cropHint)" else ""}

                    • Basal Application: Apply Phosphorus (MAP/DAP/Superphosphate) and Potassium before or at planting to boost root establishment.
                    • Top-Dressing: Split Nitrogen (Urea 46% N or Ammonium Nitrate 34% N) into 2–3 doses during active tillering/vegetative growth.
                    • Efficiency Tip: Incorporate Urea or irrigate lightly after application to reduce volatilization losses; use fertigation on high-pH soils.
                    • Use the «NPK» module for exact per-hectare bag counts.
                """.trimIndent()
                AppLanguage.RU -> """
                    🧪 Рекомендации по удобрениям и NPK${if (cropHint.isNotBlank()) " ($cropHint)" else ""}

                    • Основное внесение: Фосфор (Аммофос/Суперфосфат) и Калий вносите под вспашку или при посеве для развития корней.
                    • Подкормки: Азот (Карбамид 46% N или Аммиачная селитра 34% N) делите на 2–3 приёма в фазах кущения и активного роста.
                    • Потери: Вносите азот перед поливом или дождём, чтобы избежать потерь аммиака. Точную норму рассчитайте в разделе «NPK».
                """.trimIndent()
            }
            return appendNoteIfNeeded(body, aiFailureNote, language)
        }

        // 6. Irrigation & Moisture advice
        if (isIrrigationTopic) {
            val body = when (language) {
                AppLanguage.AZ -> """
                    💧 Suvarma və Rütubət İdarəetməsi

                    • Vaxt Seçimi: Səhər tezdən (05:00–09:00) və ya axşamüstü suvarın; günorta suvarmasında 30–40% buxarlanma itkisi yaranır.
                    • Damcılı Suvarma: Tərəvəz (pomidor, xiyar) və meyvə bağlarında su sərfini 45–55% azaldır, fitoftoroz və alternarioz kimi göbələk xəstəliklərini kəskin aşağı salır.
                    • Kritik Mərhələlər: Taxıllarda boruyaçıxma–sünbülləmə, pambıqda qönçələmə–çiçəkləmə, tərəvəzdə isə meyvə bağlama dövründə rütubət stressinə yol verməyin.
                    • Kök Dərinliyi Testi: 15–20 sm dərinlikdə torpağı əlinizdə sıxdıqda dağılırsa, suvarma vaxtı çatıb.
                """.trimIndent()
                AppLanguage.EN -> """
                    💧 Irrigation & Moisture Management

                    • Timing: Irrigate early morning or late afternoon to cut evaporation losses by 30–40% and prevent leaf scald.
                    • Drip Systems: Reduce water usage by 45–55% and keep foliage dry to suppress blight and leaf spots.
                    • Critical Stages: Avoid moisture stress during grain heading, cotton flowering, and vegetable fruit set.
                """.trimIndent()
                AppLanguage.RU -> """
                    💧 Полив и управление влагой

                    • Время полива: Проводите полив рано утром или вечером, чтобы снизить испарение на 30–40%.
                    • Капельное орошение: Экономит 45–55% воды и сохраняет листья сухими, снижая риск фитофтороза и пятнистостей.
                    • Критические фазы: Не допускайте засухи в фазы колошения зерновых, цветения хлопчатника и налива плодов.
                """.trimIndent()
            }
            return appendNoteIfNeeded(body, aiFailureNote, language)
        }

        // 7. Soil, pH & Salinity
        if (containsAny(normalizedQ, "torpaq", "ph", "soran", "duz", "humus", "soil", "salin", "почв", "засолен", "солонч")) {
            val body = when (language) {
                AppLanguage.AZ -> """
                    🌱 Torpaq Sağlamlığı, pH və Şoranlıqla Mübarizə

                    • Nümunə Götürmə: Hər 5–10 ha sahədən 0–30 sm dərinlikdən ziqzaq üsulu ilə qarışıq nümunə götürüb «Məntəqələr → Aqrokimyəvi laboratoriya»da analiz etdirin.
                    • Şoran Sahələr: Qış yuyulması (duzların drenaja axıdılması), gips tətbiqi və üzvi gübrə (çürümüş peyin/kompost) torpağın strukturunu bərpa edir.
                    • Optimal pH: Əksər bitkilər üçün pH 6.0–7.2 idealdır; yüksək qələvi torpaqda kükürd və mikroelementlər (dəmir, sink) faydalıdır.
                """.trimIndent()
                AppLanguage.EN -> """
                    🌱 Soil Health, pH & Salinity Control

                    • Sampling: Collect zigzag composite samples (0–30 cm) per 5–10 ha and send to an agrochemical lab.
                    • Saline Soils: Winter leaching with working drainage, gypsum amendment, and well-rotted compost restore soil structure.
                    • Optimal pH: 6.0–7.2 fits most crops; apply elemental sulfur and chelated micronutrients on alkaline soils.
                """.trimIndent()
                AppLanguage.RU -> """
                    🌱 Здоровье почвы, pH и засоление

                    • Отбор проб: Отбирайте смешанный образец с 0–30 см зигзагом с каждых 5–10 га для агрохимического анализа.
                    • Засолённые почвы: Зимняя промывка при рабочем дренаже, гипсование и перепревший навоз восстанавливают структуру.
                    • Оптимальный pH: 6.0–7.2 для большинства культур; на щелочных почвах полезны сера и хелаты микроэлементов.
                """.trimIndent()
            }
            return appendNoteIfNeeded(body, aiFailureNote, language)
        }

        // 8. Orchard Pruning & Greenhouse
        if (containsAny(normalizedQ, "budama", "bag", "ting", "agac", "prun", "orchard", "обрезк", "сад", "саженц")) {
            val body = when (language) {
                AppLanguage.AZ -> """
                    🌳 Bağçılıq və Budama Tövsiyələri

                    • Qış Budaması: Şaxtalar bitdikdən sonra, tumurcuqlar açmazdan əvvəl (fevral–mart) qurumuş, xəstə və içəri böyüyən budaqları kəsin.
                    • Kəsiklərin Qorunması: 2 sm-dən qalın kəsik yerlərini bağ məlhəmi (var) ilə örtün və budama sonrası 1–3% Bordo məhlulu və ya mis preparatı ilə çiləmə aparın.
                    • Fındıq və Meyvə Bağları: İşıqlanmanı və hava dövranını təmin edən kasa forması göbələk xəstəliklərini azaldır.
                """.trimIndent()
                AppLanguage.EN -> """
                    🌳 Orchard Care & Pruning Tips

                    • Dormant Pruning: Prune dead, diseased, and crossing branches in late winter before bud break.
                    • Wound Protection: Seal cuts >2 cm and apply a copper spray right after pruning to block bacterial/fungal entry.
                """.trimIndent()
                AppLanguage.RU -> """
                    🌳 Уход за садом и обрезка

                    • Зимняя обрезка: До распускания почек удалите сухие, больные и загущающие крону ветви.
                    • Защита срезов: Замажьте срезы >2 см садовым варом и проведите обработку медьсодержащим препаратом.
                """.trimIndent()
            }
            return appendNoteIfNeeded(body, aiFailureNote, language)
        }

        if (containsAny(normalizedQ, "istixana", "parnik", "greenhouse", "теплиц")) {
            val body = when (language) {
                AppLanguage.AZ -> """
                    🏡 İstixana (Parnik) İdarəetməsi

                    • Rütubət Nəzarəti: Nisbi rütubəti 65–75% arasında saxlayın; səhər tezdən qısa havalandırma kondensatı və boz çürümə/fitoftorozu önləyir.
                    • Temperatur: Pomidor üçün gündüz 22–26°C, gecə 16–18°C; xiyar üçün gündüz 24–28°C, gecə 18–20°C idealdır.
                    • Tozlanma və Gigiyena: Çiçəkləmədə arı ailələri (bombus) və sarı/mavi yapışqan tələlərdən istifadə edin.
                """.trimIndent()
                AppLanguage.EN -> """
                    🏡 Greenhouse Management

                    • Humidity Control: Keep RH at 65–75% and ventilate early morning to clear condensation and prevent Botrytis/blight.
                    • Temperatures: Tomato 22–26°C day / 16–18°C night; Cucumber 24–28°C day / 18–20°C night.
                """.trimIndent()
                AppLanguage.RU -> """
                    🏡 Управление теплицей

                    • Влажность: Держите 65–75% и проветривайте утром для удаления конденсата (защита от серой гнили и фитофторы).
                    • Температура: Томат 22–26°C днём / 16–18°C ночью; огурец 24–28°C днём / 18–20°C ночью.
                """.trimIndent()
            }
            return appendNoteIfNeeded(body, aiFailureNote, language)
        }

        // 9. Greetings & conversational check
        if (containsAny(normalizedQ, "salam", "hello", "hi", "hey", "привет", "здравств", "komek", "help", "помощ", "necesen")) {
            val contextHint = if (farmContextSummary.isNotBlank()) "\n\n📋 ${farmContextSummary.replace("\n", " • ")}" else ""
            val body = when (language) {
                AppLanguage.AZ -> """
                    Salam! 👋 Mən AqroMəsləhət AI Aqronom köməkçinizəm.

                    Sizə bu mövzularda dərhal kömək edə bilərəm:
                    • 🦠 Xəstəlik və zərərverici diaqnostikası («Pomidor fitoftoroz», «Buğda pası»)
                    • 🧪 Gübrələmə və NPK hesablaması («Buğda azot yemləməsi»)
                    • 💧 Suvarma, torpaq şoranlığı və istixana rejimi
                    • 🛡️ Aqrosığorta filialları (1651), subsidiya və sertifikatlı sortlar$contextHint
                """.trimIndent()
                AppLanguage.EN -> """
                    Hello! 👋 I am your AqroMəsləhət AI Agronomist assistant.

                    Ask me about:
                    • 🦠 Crop diseases & pests ("Tomato late blight", "Wheat rust")
                    • 🧪 NPK fertilization & soil salinity
                    • 💧 Drip irrigation & greenhouse management
                    • 🛡️ Aqrosığorta branches (1651), subsidies & certified varieties$contextHint
                """.trimIndent()
                AppLanguage.RU -> """
                    Здравствуйте! 👋 Я ваш AI Агроном AqroMəsləhət.

                    Можете спросить меня о:
                    • 🦠 Болезнях и вредителях («Фитофтороз томата», «Ржавчина пшеницы»)
                    • 🧪 Удобрениях NPK и засолении почвы
                    • 💧 Капельном поливе и микроклимате теплиц
                    • 🛡️ Филиалах Aqrosığorta (1651), субсидиях и сортах семян$contextHint
                """.trimIndent()
            }
            return appendNoteIfNeeded(body, aiFailureNote, language)
        }

        // 10. General helpful offline summary listing all 11 crops + broad topics
        val cropListText = CropProtectionCatalog.crops.joinToString(", ") { it.name.get(language) }
        val contextNote = if (farmContextSummary.isNotBlank()) "\n\n📋 ${farmContextSummary.replace("\n", " • ")}" else ""
        val fallback = when (language) {
            AppLanguage.AZ -> """
                🌿 Yerli Aqronom Bələdçisi aktivdir

                Bazamızdakı bütün bitkilər ($cropListText) üzrə ${CropProtectionCatalog.threats.size} xəstəlik/zərərverici, gübrələmə, suvarma və Aqrosığorta məlumatları əlçatandır. Nümunə sual yazın:
                • «Pomidor fitoftoroz müalicəsi»
                • «Buğda azot və fosfor gübrəsi»
                • «Damcılı suvarma rejimi»
                • «Aqrosığorta filialları»
                • «Bütün bitkilər və xəstəliklər»$contextNote
            """.trimIndent()
            AppLanguage.EN -> """
                🌿 Local Agronomist Guide Active

                Ask about ${CropProtectionCatalog.threats.size} diseases & pests across ${CropProtectionCatalog.crops.size} crops ($cropListText), fertilization, irrigation, or Aqrosığorta branches:
                • "Tomato late blight"
                • "Wheat NPK fertilization"
                • "Drip irrigation timing"
                • "Aqrosığorta branches"$contextNote
            """.trimIndent()
            AppLanguage.RU -> """
                🌿 Локальный Агроном активен

                Доступна база по ${CropProtectionCatalog.threats.size} болезням и вредителям (${CropProtectionCatalog.crops.size} культур: $cropListText), удобрениям, поливу и филиалам Aqrosığorta:
                • «Фитофтороз томата»
                • «Удобрения NPK для пшеницы»
                • «Капельный полив»
                • «Филиалы Aqrosığorta»$contextNote
            """.trimIndent()
        }
        return appendNoteIfNeeded(fallback, aiFailureNote, language)
    }

    private fun containsAny(normText: String, vararg keywords: String): Boolean =
        keywords.any { normText.contains(it) }

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
            AppLanguage.AZ -> "ℹ️ Bulud AI hazırda əlçatan olmadığından yerli aqronom bazası üzrə cavab verildi"
            AppLanguage.EN -> "ℹ️ Answered from local catalog because Cloud AI is unavailable or offline"
            AppLanguage.RU -> "ℹ️ Ответ сформирован по локальной базе (облачный AI недоступен)"
        }
        return "$base\n\n$prefix ($err)"
    }
}
