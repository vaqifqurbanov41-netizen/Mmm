package com.example.domain

import com.example.data.model.CustomLink
import com.example.data.model.CustomPesticide
import com.example.data.model.EquipmentItem
import com.example.data.model.FarmField
import com.example.data.model.FinanceEntry
import com.example.data.model.HarvestEntry
import com.example.data.model.SubsidyDeclaration
import com.example.data.repository.FarmRepository
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    fun exportToJson(
        fields: List<FarmField>,
        equipment: List<EquipmentItem>,
        finance: List<FinanceEntry>,
        harvests: List<HarvestEntry>,
        declarations: List<SubsidyDeclaration>,
        customPesticides: List<CustomPesticide>,
        customLinks: List<CustomLink>,
    ): String {
        val root = JSONObject()
        root.put("app", "AqroMeslehet")
        root.put("version", 1)

        root.put("fields", JSONArray().apply {
            fields.forEach { f ->
                put(JSONObject().apply {
                    put("name", f.name)
                    put("crop", f.crop)
                    put("sizeHa", f.sizeHa)
                    put("widthM", f.widthM)
                    put("lengthM", f.lengthM)
                    put("location", f.location)
                    put("coords", JSONArray(f.coords))
                })
            }
        })

        root.put("equipment", JSONArray().apply {
            equipment.forEach { e ->
                put(JSONObject().apply {
                    put("name", e.name)
                    put("model", e.model)
                    put("plateCode", e.plateCode)
                    put("inspectionDate", e.inspectionDate)
                    put("insuranceDate", e.insuranceDate)
                })
            }
        })

        root.put("finance", JSONArray().apply {
            finance.forEach { x ->
                put(JSONObject().apply {
                    put("type", x.type)
                    put("date", x.date)
                    put("fieldName", x.fieldName)
                    put("amount", x.amount)
                    put("category", x.category)
                })
            }
        })

        root.put("harvests", JSONArray().apply {
            harvests.forEach { h ->
                put(JSONObject().apply {
                    put("date", h.date)
                    put("crop", h.crop)
                    put("quantityKg", h.quantityKg)
                    put("pricePerKg", h.pricePerKg)
                })
            }
        })

        root.put("declarations", JSONArray().apply {
            declarations.forEach { d ->
                put(JSONObject().apply {
                    put("date", d.date)
                    put("title", d.title)
                })
            }
        })

        root.put("customPesticides", JSONArray().apply {
            customPesticides.forEach { p ->
                put(JSONObject().apply {
                    put("name", p.name)
                    put("maxRate", p.maxRate)
                    put("unit", p.unit)
                    put("quarantineDays", p.quarantineDays)
                    put("safetyClass", p.safetyClass)
                    put("target", p.target)
                })
            }
        })

        root.put("customLinks", JSONArray().apply {
            customLinks.forEach { l ->
                put(JSONObject().apply {
                    put("name", l.name)
                    put("url", l.url)
                })
            }
        })

        return root.toString(2)
    }

    suspend fun importFromJson(jsonText: String, repo: FarmRepository): Int {
        val root = JSONObject(jsonText)
        var importedCount = 0

        root.optJSONArray("fields")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                val crop = o.optString("crop")
                val sizeHa = o.optDouble("sizeHa", 0.0)
                if (name.isNotBlank() && crop.isNotBlank() && sizeHa > 0) {
                    val coords = mutableListOf<String>()
                    o.optJSONArray("coords")?.let { ca ->
                        for (j in 0 until ca.length()) coords.add(ca.optString(j))
                    }
                    repo.addField(
                        name = name,
                        crop = crop,
                        sizeHa = sizeHa,
                        widthM = o.optDouble("widthM", 0.0),
                        lengthM = o.optDouble("lengthM", 0.0),
                        location = o.optString("location"),
                        coords = coords.filter { it.isNotBlank() },
                    )
                    importedCount++
                }
            }
        }

        root.optJSONArray("equipment")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                if (name.isNotBlank()) {
                    repo.saveEquipment(
                        existingItem = null,
                        name = name,
                        model = o.optString("model"),
                        plateCode = o.optString("plateCode"),
                        inspectionDate = o.optString("inspectionDate"),
                        insuranceDate = o.optString("insuranceDate"),
                    )
                    importedCount++
                }
            }
        }

        root.optJSONArray("finance")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val amount = o.optDouble("amount", 0.0)
                if (amount > 0) {
                    repo.addFinance(
                        type = o.optString("type", "expense"),
                        date = o.optString("date"),
                        fieldName = o.optString("fieldName"),
                        amount = amount,
                        category = o.optString("category"),
                    )
                    importedCount++
                }
            }
        }

        root.optJSONArray("harvests")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val crop = o.optString("crop")
                val qty = o.optDouble("quantityKg", 0.0)
                if (crop.isNotBlank() && qty > 0) {
                    repo.addHarvest(
                        date = o.optString("date"),
                        crop = crop,
                        quantityKg = qty,
                        pricePerKg = o.optDouble("pricePerKg", 0.0),
                    )
                    importedCount++
                }
            }
        }

        root.optJSONArray("declarations")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val date = o.optString("date")
                val title = o.optString("title")
                if (date.isNotBlank() && title.isNotBlank()) {
                    repo.addDeclaration(date, title)
                    importedCount++
                }
            }
        }

        root.optJSONArray("customPesticides")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                val maxRate = o.optDouble("maxRate", 0.0)
                if (name.isNotBlank() && maxRate > 0) {
                    repo.addCustomPesticide(
                        name = name,
                        maxRate = maxRate,
                        unit = o.optString("unit", "L/ha"),
                        quarantineDays = o.optInt("quarantineDays", 0),
                        safetyClass = o.optString("safetyClass"),
                        target = o.optString("target"),
                    )
                    importedCount++
                }
            }
        }

        root.optJSONArray("customLinks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                val url = o.optString("url")
                if (name.isNotBlank() && url.isNotBlank()) {
                    repo.addCustomLink(name, url)
                    importedCount++
                }
            }
        }

        return importedCount
    }
}
