package com.aistudio.texnonezaret.domain

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

enum class ColumnField(val key: String, val labelAz: String) {
    DQN("dqn", "DQN"),
    DQS("dqs", "DQŞ"),
    DATE("date", "Texniki baxış tarixi"),
    NAME("name", "Mülkiyyətçi"),
    PHONE("phone", "Telefon"),
}

enum class SearchMode {
    PLATE,
    NAME,
}

enum class InspectionStatusKind {
    NONE,
    OVERDUE,
    TODAY,
    SOON,
    VALID,
}

enum class StatusFilter(val labelAz: String) {
    ALL("Hamısı"),
    VALID("Qüvvədədir"),
    SOON("Tezliklə bitir"),
    OVERDUE("Müddəti bitib"),
    NONE("Təqdim edilməyib"),
}

data class ColumnMapping(
    val dqn: Int = -1,
    val dqs: Int = -1,
    val date: Int = -1,
    val name: Int = -1,
    val phone: Int = -1,
) {
    fun get(field: ColumnField): Int = when (field) {
        ColumnField.DQN -> dqn
        ColumnField.DQS -> dqs
        ColumnField.DATE -> date
        ColumnField.NAME -> name
        ColumnField.PHONE -> phone
    }

    fun withField(field: ColumnField, colIdx: Int): ColumnMapping = when (field) {
        ColumnField.DQN -> copy(dqn = colIdx)
        ColumnField.DQS -> copy(dqs = colIdx)
        ColumnField.DATE -> copy(date = colIdx)
        ColumnField.NAME -> copy(name = colIdx)
        ColumnField.PHONE -> copy(phone = colIdx)
    }

    fun needsReview(): Boolean = (dqn < 0 && dqs < 0) || date < 0 || name < 0
}

data class VehicleRow(
    val plate: String,
    val chassis: String,
    val normPlate: String,
    val normChassis: String,
    val inspectionUtcMillis: Long?,
    val owner: String,
    val foldedOwner: String,
    val phone: String,
)

data class EvaluatedVehicleStatus(
    val kind: InspectionStatusKind,
    val label: String,
    val inspectionDateText: String,
    val expiryDateText: String,
    val daysDelta: Long? = null,
)

data class SearchOutcome(
    val matches: List<VehicleRow>,
    val totalCount: Int,
    val extraHiddenCount: Int,
    val promptMessage: String? = null,
)

object TexnonezaretEngine {

    private val azLocale = Locale.forLanguageTag("az")
    private const val DAY_MS = 86_400_000L

    fun normPlate(s: String?): String {
        if (s == null) return ""
        val upper = s.uppercase(azLocale)
        val sb = StringBuilder(upper.length)
        for (ch in upper) {
            val mapped = if (ch == 'İ') 'I' else ch
            if (mapped in '0'..'9' || mapped in 'A'..'Z' || mapped in "ƏÖÜÇŞĞI") {
                sb.append(mapped)
            }
        }
        return sb.toString()
    }

    fun foldText(s: String?): String {
        if (s == null) return ""
        val lower = s.lowercase(azLocale).replace("\u0307", "")
        val sb = StringBuilder(lower.length)
        var prevSpace = true
        for (ch in lower) {
            val c = when (ch) {
                'ə' -> 'e'
                'ı' -> 'i'
                'ö' -> 'o'
                'ü' -> 'u'
                'ç' -> 'c'
                'ş' -> 's'
                'ğ' -> 'g'
                else -> ch
            }
            if (c in 'a'..'z' || c in '0'..'9') {
                sb.append(c)
                prevSpace = false
            } else if (c.isWhitespace()) {
                if (!prevSpace) {
                    sb.append(' ')
                    prevSpace = true
                }
            }
        }
        return sb.toString().trim()
    }

    fun colName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    fun parseDateUtcMillis(rawValue: String?): Long? {
        val s = rawValue?.trim().orEmpty()
        if (s.isEmpty()) return null

        // Excel numeric serial date check (20000 .. 80000 covers 1954 .. 2119)
        val num = s.toDoubleOrNull()
        if (num != null && num > 20000.0 && num < 80000.0) {
            return (kotlin.math.floor(num).toLong() - 25569L) * DAY_MS
        }

        val dmy = Regex("""^(\d{1,2})[./-](\d{1,2})[./-](\d{4})\b""").find(s)
        if (dmy != null) {
            val day = dmy.groupValues[1].toIntOrNull() ?: return null
            val month = dmy.groupValues[2].toIntOrNull() ?: return null
            val year = dmy.groupValues[3].toIntOrNull() ?: return null
            return utcMidnight(year, month, day)
        }

        val ymd = Regex("""^(\d{4})[./-](\d{1,2})[./-](\d{1,2})\b""").find(s)
        if (ymd != null) {
            val year = ymd.groupValues[1].toIntOrNull() ?: return null
            val month = ymd.groupValues[2].toIntOrNull() ?: return null
            val day = ymd.groupValues[3].toIntOrNull() ?: return null
            return utcMidnight(year, month, day)
        }
        return null
    }

    private fun utcMidnight(year: Int, month1Based: Int, day: Int): Long? {
        if (year !in 1950..2150 || month1Based !in 1..12 || day !in 1..31) return null
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            isLenient = false
            clear()
            set(year, month1Based - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return runCatching { cal.timeInMillis }.getOrNull()
    }

    fun formatUtcDate(utcMillis: Long): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            timeInMillis = utcMillis
        }
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val m = cal.get(Calendar.MONTH) + 1
        val y = cal.get(Calendar.YEAR)
        return String.format(Locale.US, "%02d.%02d.%04d", d, m, y)
    }

    fun todayUtcMidnightMillis(): Long {
        val local = Calendar.getInstance()
        return utcMidnight(
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH) + 1,
            local.get(Calendar.DAY_OF_MONTH),
        ) ?: System.currentTimeMillis()
    }

    fun detectColumns(raw: List<List<String>>): Pair<Int, ColumnMapping> {
        if (raw.isEmpty()) return 0 to ColumnMapping()
        var hdr = 0
        for (i in 0 until minOf(10, raw.size)) {
            if (raw[i].any { normPlate(it).contains("DQN") }) {
                hdr = i
                break
            }
        }
        val headers = raw.getOrElse(hdr) { emptyList() }.map { it.lowercase(azLocale) }
        fun findHeader(pred: (String) -> Boolean): Int {
            for (i in headers.indices) {
                if (headers[i].isNotBlank() && pred(headers[i])) return i
            }
            return -1
        }

        var dqn = findHeader { it.contains("dqn") }
        var dqs = findHeader { it.contains("dqş") || it.contains("dqs") }
        var date = findHeader { it.contains("baxış") || it.contains("baxis") || it.contains("tarix") }
        var name = findHeader { it.contains("mülkiyyət") || it.contains("mulkiyyet") }
        if (name < 0) {
            name = findHeader {
                Regex("""soyad|sahib|f\.?a\.?ə|fiziki|\bad\b""").containsMatchIn(it)
            }
        }
        var phone = findHeader {
            Regex("""telefon|mobil|əlaqə|\btel\b""").containsMatchIn(it)
        }

        val sample = raw.drop(hdr + 1).take(60)
        val colCount = maxOf(headers.size, raw.take(50).maxOfOrNull { it.size } ?: 0)

        fun bestCol(skip: Set<Int>, pred: (String) -> Boolean): Int {
            var bestIdx = -1
            var bestScore = 2
            for (c in 0 until colCount) {
                if (c in skip) continue
                var score = 0
                for (row in sample) {
                    val v = row.getOrNull(c).orEmpty()
                    if (pred(v)) score++
                }
                if (score > bestScore) {
                    bestScore = score
                    bestIdx = c
                }
            }
            return bestIdx
        }

        fun usedSet() = setOf(dqn, dqs, date, name, phone).filter { it >= 0 }.toSet()

        if (date < 0) {
            date = bestCol(usedSet()) { parseDateUtcMillis(it) != null }
        }
        if (phone < 0) {
            phone = bestCol(usedSet()) { v ->
                val digits = v.count { it.isDigit() }
                digits in 7..13
            }
        }
        if (name < 0) {
            name = bestCol(usedSet()) { v ->
                val t = v.trim()
                t.length >= 8 && t.none { it.isDigit() } && t.contains(' ')
            }
        }

        return hdr to ColumnMapping(
            dqn = dqn,
            dqs = dqs,
            date = date,
            name = name,
            phone = phone,
        )
    }

    fun buildRows(raw: List<List<String>>, hdr: Int, map: ColumnMapping): List<VehicleRow> {
        if (raw.size <= hdr + 1) return emptyList()
        val result = ArrayList<VehicleRow>(raw.size - hdr - 1)
        for (i in (hdr + 1) until raw.size) {
            val row = raw[i]
            val p = if (map.dqn >= 0) row.getOrNull(map.dqn)?.trim().orEmpty() else ""
            val c = if (map.dqs >= 0) row.getOrNull(map.dqs)?.trim().orEmpty() else ""
            if (p.isEmpty() && c.isEmpty()) continue
            val dMillis = if (map.date >= 0) parseDateUtcMillis(row.getOrNull(map.date)) else null
            val owner = if (map.name >= 0) row.getOrNull(map.name)?.trim().orEmpty() else ""
            val tel = if (map.phone >= 0) row.getOrNull(map.phone)?.trim().orEmpty() else ""
            result.add(
                VehicleRow(
                    plate = p,
                    chassis = c,
                    normPlate = normPlate(p),
                    normChassis = normPlate(c),
                    inspectionUtcMillis = dMillis,
                    owner = owner,
                    foldedOwner = foldText(owner),
                    phone = tel,
                )
            )
        }
        return result
    }

    fun evaluateStatus(
        inspectionUtcMillis: Long?,
        todayUtcMillis: Long = todayUtcMidnightMillis(),
    ): EvaluatedVehicleStatus {
        if (inspectionUtcMillis == null) {
            return EvaluatedVehicleStatus(
                kind = InspectionStatusKind.NONE,
                label = "Texniki baxışa təqdim edilməyib",
                inspectionDateText = "—",
                expiryDateText = "—",
            )
        }
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            timeInMillis = inspectionUtcMillis
            add(Calendar.YEAR, 1)
        }
        val expiryMillis = cal.timeInMillis
        val diffDays = ((expiryMillis - todayUtcMillis).toDouble() / DAY_MS.toDouble()).roundToLong()
        val inspText = formatUtcDate(inspectionUtcMillis)
        val expText = formatUtcDate(expiryMillis)
        return when {
            diffDays < 0 -> EvaluatedVehicleStatus(
                kind = InspectionStatusKind.OVERDUE,
                label = "Müddəti bitib (${-diffDays} gün əvvəl)",
                inspectionDateText = inspText,
                expiryDateText = expText,
                daysDelta = diffDays,
            )
            diffDays == 0L -> EvaluatedVehicleStatus(
                kind = InspectionStatusKind.TODAY,
                label = "Baxış bu gün bitir",
                inspectionDateText = inspText,
                expiryDateText = expText,
                daysDelta = 0L,
            )
            diffDays <= 30L -> EvaluatedVehicleStatus(
                kind = InspectionStatusKind.SOON,
                label = "Baxışa $diffDays gün qalıb",
                inspectionDateText = inspText,
                expiryDateText = expText,
                daysDelta = diffDays,
            )
            else -> EvaluatedVehicleStatus(
                kind = InspectionStatusKind.VALID,
                label = "Baxış qüvvədədir ($diffDays gün qalıb)",
                inspectionDateText = inspText,
                expiryDateText = expText,
                daysDelta = diffDays,
            )
        }
    }

    fun searchRows(
        rows: List<VehicleRow>,
        plateQuery: String,
        nameQuery: String,
        mode: SearchMode,
        filter: StatusFilter = StatusFilter.ALL,
        todayUtcMillis: Long = todayUtcMidnightMillis(),
    ): SearchOutcome {
        if (rows.isEmpty()) {
            return SearchOutcome(
                matches = emptyList(),
                totalCount = 0,
                extraHiddenCount = 0,
                promptMessage = null,
            )
        }

        val statusFiltered = if (filter == StatusFilter.ALL) {
            rows
        } else {
            rows.filter { r ->
                val kind = evaluateStatus(r.inspectionUtcMillis, todayUtcMillis).kind
                when (filter) {
                    StatusFilter.ALL -> true
                    StatusFilter.VALID -> kind == InspectionStatusKind.VALID
                    StatusFilter.SOON -> kind == InspectionStatusKind.SOON || kind == InspectionStatusKind.TODAY
                    StatusFilter.OVERDUE -> kind == InspectionStatusKind.OVERDUE
                    StatusFilter.NONE -> kind == InspectionStatusKind.NONE
                }
            }
        }

        if (mode == SearchMode.NAME) {
            val tokens = foldText(nameQuery).split(" ").filter { it.isNotEmpty() }
            if (tokens.isEmpty()) {
                return if (filter == StatusFilter.ALL) {
                    SearchOutcome(
                        matches = emptyList(),
                        totalCount = 0,
                        extraHiddenCount = 0,
                        promptMessage = "Ad, soyad və ya təşkilat adını yazın.",
                    )
                } else {
                    val shown = statusFiltered.take(40)
                    SearchOutcome(
                        matches = shown,
                        totalCount = statusFiltered.size,
                        extraHiddenCount = (statusFiltered.size - shown.size).coerceAtLeast(0),
                    )
                }
            }
            val found = statusFiltered.filter { row -> tokens.all { tok -> row.foldedOwner.contains(tok) } }
            val shown = found.take(25)
            return SearchOutcome(
                matches = shown,
                totalCount = found.size,
                extraHiddenCount = (found.size - shown.size).coerceAtLeast(0),
                promptMessage = if (found.isEmpty()) "“${nameQuery.trim()}” üzrə nəticə tapılmadı." else null,
            )
        } else {
            val key = normPlate(plateQuery)
            if (key.isEmpty()) {
                return if (filter == StatusFilter.ALL) {
                    SearchOutcome(
                        matches = emptyList(),
                        totalCount = 0,
                        extraHiddenCount = 0,
                        promptMessage = "DQN və ya DQŞ-in hamısını və ya bir hissəsini yazın.",
                    )
                } else {
                    val shown = statusFiltered.take(40)
                    SearchOutcome(
                        matches = shown,
                        totalCount = statusFiltered.size,
                        extraHiddenCount = (statusFiltered.size - shown.size).coerceAtLeast(0),
                    )
                }
            }
            val exact = statusFiltered.filter { it.normPlate == key || it.normChassis == key }
            val partial = statusFiltered.filter {
                it.normPlate != key &&
                    it.normChassis != key &&
                    (it.normPlate.contains(key) || it.normChassis.contains(key))
            }
            val combined = exact + partial
            val shown = combined.take(25)
            return SearchOutcome(
                matches = shown,
                totalCount = combined.size,
                extraHiddenCount = (combined.size - shown.size).coerceAtLeast(0),
                promptMessage = if (combined.isEmpty()) "“${plateQuery.trim()}” üzrə nəticə tapılmadı." else null,
            )
        }
    }

    /**
     * Converts voice-recognized utterances (including Azerbaijani spoken digits/numbers)
     * into normalized uppercase plate/DQŞ search text (e.g. "sıfır dörd BA yüz yetmiş iki" -> "04BA172").
     */
    fun parseSpokenPlateQuery(rawSpeech: String?): String {
        if (rawSpeech.isNullOrBlank()) return ""
        val zeroWords = setOf("sıfır", "sifir", "zero", "ноль", "нуль", "o")
        val onesMap = mapOf(
            "bir" to 1, "one" to 1, "один" to 1, "одна" to 1,
            "iki" to 2, "two" to 2, "два" to 2, "две" to 2,
            "üç" to 3, "uc" to 3, "three" to 3, "три" to 3,
            "dörd" to 4, "dord" to 4, "four" to 4, "четыре" to 4,
            "beş" to 5, "bes" to 5, "five" to 5, "пять" to 5,
            "altı" to 6, "alti" to 6, "six" to 6, "шесть" to 6,
            "yeddi" to 7, "seven" to 7, "семь" to 7,
            "səkkiz" to 8, "sekkiz" to 8, "eight" to 8, "восемь" to 8,
            "doqquz" to 9, "nine" to 9, "девять" to 9,
        )
        val tensMap = mapOf(
            "on" to 10, "ten" to 10, "десять" to 10,
            "iyirmi" to 20, "twenty" to 20, "двадцать" to 20,
            "otuz" to 30, "thirty" to 30, "тридцать" to 30,
            "qırx" to 40, "qirx" to 40, "forty" to 40, "сорок" to 40,
            "əlli" to 50, "elli" to 50, "fifty" to 50, "пятьдесят" to 50,
            "altmış" to 60, "altmis" to 60, "sixty" to 60, "шестьдесят" to 60,
            "yetmiş" to 70, "yetmis" to 70, "seventy" to 70, "семьдесят" to 70,
            "səksən" to 80, "seksen" to 80, "eighty" to 80, "восемьдесят" to 80,
            "doxsan" to 90, "ninety" to 90, "девяносто" to 90,
        )
        val hundredWords = setOf("yüz", "yuz", "hundred", "сто")
        val thousandWords = setOf("min", "thousand", "тысяча")

        val tokens = rawSpeech
            .trim()
            .replace('-', ' ')
            .replace('/', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val out = StringBuilder()
        var currentNumber: Int? = null

        fun flushNumber() {
            currentNumber?.let {
                out.append(it)
                currentNumber = null
            }
        }

        for (rawTok in tokens) {
            val lower = rawTok.lowercase(azLocale).replace(Regex("[^\\p{L}\\p{Nd}]"), "")
            if (lower.isEmpty()) continue

            when {
                lower in zeroWords -> {
                    flushNumber()
                    out.append('0')
                }
                onesMap.containsKey(lower) -> {
                    val d = onesMap.getValue(lower)
                    val cur = currentNumber
                    if (cur != null && cur >= 10 && cur % 10 == 0) {
                        currentNumber = cur + d
                    } else {
                        flushNumber()
                        currentNumber = d
                    }
                }
                tensMap.containsKey(lower) -> {
                    val t = tensMap.getValue(lower)
                    val cur = currentNumber
                    if (cur != null && cur >= 100 && cur % 100 == 0) {
                        currentNumber = cur + t
                    } else {
                        flushNumber()
                        currentNumber = t
                    }
                }
                lower in hundredWords -> {
                    val cur = currentNumber
                    currentNumber = when {
                        cur != null && cur in 1..9 -> cur * 100
                        cur != null && cur >= 1000 && cur % 1000 == 0 -> cur + 100
                        else -> {
                            flushNumber()
                            100
                        }
                    }
                }
                lower in thousandWords -> {
                    val cur = currentNumber
                    currentNumber = if (cur != null && cur in 1..999) {
                        cur * 1000
                    } else {
                        flushNumber()
                        1000
                    }
                }
                else -> {
                    flushNumber()
                    out.append(normPlate(rawTok))
                }
            }
        }
        flushNumber()
        return out.toString()
    }

    fun pickBestVoicePlateQuery(candidates: List<String>, rows: List<VehicleRow>): String {
        val parsedList = candidates.map { parseSpokenPlateQuery(it) }.filter { it.isNotBlank() }
        if (parsedList.isEmpty()) return ""
        if (rows.isNotEmpty()) {
            val exactHit = parsedList.firstOrNull { key ->
                rows.any { it.normPlate == key || it.normChassis == key }
            }
            if (exactHit != null) return exactHit
            val partialHit = parsedList.firstOrNull { key ->
                rows.any { it.normPlate.contains(key) || it.normChassis.contains(key) }
            }
            if (partialHit != null) return partialHit
        }
        return parsedList.firstOrNull { candidate -> candidate.any { it.isDigit() } } ?: parsedList.first()
    }

    /**
     * Deterministic FNV-1a 32-bit salted hash matching Texnonəzarət's PIN verification.
     */
    fun hashPin(pin: String): String {
        var h = 2166136261uL.toUInt()
        val salted = "TXN_SALT_2026_$pin"
        for (ch in salted) {
            h = h xor ch.code.toUInt()
            h *= 16777619u
        }
        return h.toString(16)
    }
}
