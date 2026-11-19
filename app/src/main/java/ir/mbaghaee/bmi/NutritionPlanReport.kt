package ir.mbaghaee.bmi

import ir.mbaghaee.bmi.NutritionEngine.Group
import ir.mbaghaee.bmi.NutritionEngine.Meal
import ir.mbaghaee.bmi.NutritionEngine.decimal
import ir.mbaghaee.bmi.NutritionEngine.totals

internal object NutritionPlanReport {
    fun format(
        targetCalories: Double,
        daily: Map<Group, Int>,
        allocation: Map<Meal, Map<Group, Int>>,
        notes: Map<Meal, String>,
        patientNote: String,
        professionalDetails: String? = null
    ): String = buildString {
        appendLine("رشدآوا · برنامهٔ غذایی")
        appendLine(if (professionalDetails == null) "نسخهٔ مراجعه‌کننده" else "نسخهٔ متخصص")
        appendLine()
        appendLine("انرژی هدف روزانه · ${decimal(targetCalories)} کیلوکالری")
        appendLine("انرژی برآوردی از واحدها · ${decimal(totals(daily).calories)} کیلوکالری")
        professionalDetails?.let {
            appendLine()
            appendLine(it)
        }
        for ((meal, units) in allocation) {
            val portions = units.filterValues { it > 0 }
            val note = notes[meal].orEmpty().trim()
            if (portions.isEmpty() && note.isEmpty()) continue
            appendLine()
            appendLine(meal.label)
            for (group in Group.values()) {
                val amount = portions[group] ?: continue
                appendLine("• ${decimal(amount.toDouble() / NutritionEngine.UNIT_SCALE)} واحد ${group.label}")
            }
            if (note.isNotEmpty()) appendLine(note)
        }
        if (daily.values.any { it > 0 }) {
            appendLine()
            appendLine("نمونهٔ اندازهٔ هر واحد")
            for (group in Group.values()) {
                if ((daily[group] ?: 0) > 0) appendLine("• ${group.label} · ${group.householdExample}")
            }
        }
        if (patientNote.isNotBlank()) {
            appendLine()
            appendLine("یادداشت برای مراجعه‌کننده")
            appendLine(patientNote.trim())
        }
    }.trimEnd()
}
