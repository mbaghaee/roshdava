package ir.mbaghaee.bmi

/** DRI 2023 tables 5-16 and 5-17. Gestational weeks include the fractional days. */
internal object PregnancyEnergy {
    private data class Coefficients(val constant: Double, val height: Double, val weight: Double) {
        fun base(heightCm: Double, weightKg: Double) = constant + height * heightCm + weight * weightKg
    }

    enum class Activity(val label: String, val legacyActivity: NutritionEngine.Activity) {
        INACTIVE("کم‌تحرک · عمدتاً کار نشسته و فعالیت‌های عادی روزمره", NutritionEngine.Activity.SEDENTARY),
        LOW_ACTIVE("فعالیت سبک · تحرک افزون بر کارهای عادی در بیشتر روزها", NutritionEngine.Activity.LIGHT),
        ACTIVE("فعال · تحرک قابل‌توجه در شغل یا فعالیت بدنی منظم و طولانی", NutritionEngine.Activity.MODERATE),
        VERY_ACTIVE("بسیار فعال · کار بدنی سنگین یا تمرین شدید و طولانی", NutritionEngine.Activity.VERY_HIGH)
    }

    data class Estimate(
        val daily: Double,
        val trimester: Int,
        val preBmi: Double,
        val tissueEnergy: Int,
        val activity: Activity
    ) {
        val canUseMaintenance get() = trimester == 1 && preBmi >= 18.5 && preBmi < 25

        fun recommend(maintenanceKcal: Int?): Recommendation {
            val usualIntake = maintenanceKcal?.takeIf { canUseMaintenance && isValidMaintenance(it) }
            return Recommendation(usualIntake?.toDouble() ?: daily, usualIntake != null)
        }
    }

    data class LegacyEstimate(val baseline: NutritionEngine.Estimate, val extraKcal: Int) {
        val harrisDaily get() = baseline.harrisDaily + extraKcal
        val mifflinDaily get() = baseline.mifflinDaily + extraKcal
    }

    data class Recommendation(val daily: Double, val usesMaintenance: Boolean)

    fun isValidMaintenance(kcal: Int) = kcal in NutritionEngine.MIN_TARGET..NutritionEngine.MAX_TARGET

    fun legacyEstimate(patient: Patient, estimate: Estimate): LegacyEstimate {
        val baseline = NutritionEngine.estimate(
            patient, estimate.activity.legacyActivity, weightKg = checkNotNull(patient.pregnancy).preWeight
        )
        // IOM 2005 trimester additions to a nonpregnant maintenance estimate.
        val extraKcal = when (estimate.trimester) {
            1 -> 0
            2 -> 340
            else -> 452
        }
        return LegacyEstimate(baseline, extraKcal)
    }

    private val early = mapOf(
        Activity.INACTIVE to Coefficients(584.90, 5.72, 11.71),
        Activity.LOW_ACTIVE to Coefficients(575.77, 6.60, 12.14),
        Activity.ACTIVE to Coefficients(710.25, 6.54, 12.34),
        Activity.VERY_ACTIVE to Coefficients(511.83, 9.07, 12.56)
    )

    private val later = mapOf(
        Activity.INACTIVE to Coefficients(1131.20, .34, 12.15),
        Activity.LOW_ACTIVE to Coefficients(693.35, 5.73, 10.20),
        Activity.ACTIVE to Coefficients(-223.84, 13.23, 8.15),
        Activity.VERY_ACTIVE to Coefficients(-779.72, 18.45, 8.73)
    )

    fun unavailableReason(patient: Patient, adult: Boolean): String? {
        val pregnancy = patient.pregnancy
        return when {
            !adult -> "تغذیهٔ بارداری در نوجوانی به ارزیابی اختصاصی تیم مراقبت نیاز دارد."
            pregnancy == null -> "برای برآورد انرژی، وزن پیش از بارداری، هفته و تعداد جنین را ثبت کنید."
            pregnancy.plurality != 1 -> "برای دوقلویی یا چندقلویی، هدف انرژی را تیم مراقبت با توجه به رشد جنین‌ها و روند وزن‌گیری تعیین کند."
            pregnancy.gestationDays !in (7 until 42 * 7) -> "برای این سن بارداری، هدف انرژی با تیم مراقبت تعیین شود."
            else -> null
        }
    }

    fun estimate(patient: Patient, adult: Boolean, activity: Activity): Estimate? {
        if (!patient.pregnant || patient.sex != 2 || unavailableReason(patient, adult) != null) return null
        val pregnancy = patient.pregnancy ?: return null
        val age = patient.days / 365.25
        val height = patient.height
        if (!height.isFinite() || height <= 0 || !pregnancy.preWeight.isFinite() ||
            pregnancy.preWeight <= 0 || !patient.weight.isFinite() || patient.weight <= 0) return null
        val preBmi = PregnancyAssessment.bmi(pregnancy.preWeight, height)
        val week = pregnancy.gestationDays / 7.0
        val trimester = PregnancyAssessment.trimester(pregnancy.gestationDays)
        if (trimester == 1) {
            val daily = early.getValue(activity).base(height, pregnancy.preWeight) - 7.01 * age
            return Estimate(daily, 1, preBmi, 0, activity)
        }
        val tissueEnergy = when {
            preBmi < 18.5 -> 300
            preBmi < 25 -> 200
            preBmi < 30 -> 150
            else -> -50
        }
        val daily = later.getValue(activity).base(height, patient.weight) - 2.04 * age + 9.16 * week + tissueEnergy
        return Estimate(daily, trimester, preBmi, tissueEnergy, activity)
    }
}
