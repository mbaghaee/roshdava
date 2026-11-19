package ir.mbaghaee.bmi

import org.junit.Assert.*
import org.junit.Test

class PregnancyEnergyTest {
    private fun patient(days: Int, preWeight: Double = 60.0, plurality: Int = 1) =
        Patient("نمونه", 2, 10958, 68.0, 164.0, pregnant = true,
            pregnancy = PregnancyInfo(preWeight, days, plurality, "test"))

    @Test
    fun firstTrimesterUsesPrePregnancyWeightAndLaterUsesCurrentWeightAndGestationalAge() {
        val inactive = PregnancyEnergy.Activity.INACTIVE
        val first = checkNotNull(PregnancyEnergy.estimate(patient(97), true, inactive))
        val second = checkNotNull(PregnancyEnergy.estimate(patient(140), true, inactive))
        assertEquals(1, first.trimester)
        assertEquals(2015.27, first.daily, .01)
        assertEquals(2, checkNotNull(PregnancyEnergy.estimate(patient(98), true, inactive)).trimester)
        assertEquals(200, second.tissueEnergy)
        assertEquals(2335.16, second.daily, .01)
        assertEquals(3, checkNotNull(PregnancyEnergy.estimate(patient(196), true, inactive)).trimester)
    }

    @Test
    fun oldMethodsUsePrePregnancyWeightAndAddEachTrimesterAllowance() {
        val currentActivity = PregnancyEnergy.Activity.LOW_ACTIVE
        for ((days, extra) in listOf(56 to 0, 98 to 340, 196 to 452)) {
            val subject = patient(days, preWeight = 70.0).copy(height = 173.0)
            val dri = checkNotNull(PregnancyEnergy.estimate(subject, true, currentActivity))
            val legacy = PregnancyEnergy.legacyEstimate(subject, dri)
            assertEquals(extra, legacy.extraKcal)
            assertEquals(2068.38 + extra, legacy.harrisDaily, .03)
            assertEquals(2021.59 + extra, legacy.mifflinDaily, .03)
        }
    }

    @Test
    fun bothOldMethodsDeriveTheirFactorFromTheSelectedDriActivity() {
        val subject = patient(56)
        val factors = mapOf(
            PregnancyEnergy.Activity.INACTIVE to 1.2,
            PregnancyEnergy.Activity.LOW_ACTIVE to 1.375,
            PregnancyEnergy.Activity.ACTIVE to 1.55,
            PregnancyEnergy.Activity.VERY_ACTIVE to 1.9
        )
        for ((activity, expectedFactor) in factors) {
            val dri = checkNotNull(PregnancyEnergy.estimate(subject, true, activity))
            val legacy = PregnancyEnergy.legacyEstimate(subject, dri)
            assertEquals(expectedFactor, legacy.baseline.factor, .0001)
        }
    }

    @Test
    fun earlyRecommendationUsesReliableMaintenanceWhenProvidedAndLaterUsesDri() {
        val activity = PregnancyEnergy.Activity.ACTIVE
        val first = checkNotNull(PregnancyEnergy.estimate(patient(56, 70.0).copy(height = 173.0), true, activity))
        assertEquals(2495.17, first.daily, .02)
        assertEquals(PregnancyEnergy.Recommendation(2000.0, true), first.recommend(2000))
        assertEquals(PregnancyEnergy.Recommendation(first.daily, false), first.recommend(null))
        assertEquals(PregnancyEnergy.Recommendation(first.daily, false), first.recommend(500))

        val second = checkNotNull(PregnancyEnergy.estimate(patient(98), true, activity))
        assertEquals(PregnancyEnergy.Recommendation(second.daily, false), second.recommend(2000))
        val highBmi = checkNotNull(PregnancyEnergy.estimate(patient(56, 85.0), true, activity))
        assertEquals(PregnancyEnergy.Recommendation(highBmi.daily, false), highBmi.recommend(2000))
    }

    @Test
    fun depositionDependsOnPrePregnancyBmiAndSingletonEstimateIsNotUsedForTwins() {
        val inactive = PregnancyEnergy.Activity.INACTIVE
        for ((weight, adjustment) in listOf(45.0 to 300, 60.0 to 200, 75.0 to 150, 85.0 to -50)) {
            assertEquals(adjustment, checkNotNull(PregnancyEnergy.estimate(patient(140, weight), true, inactive)).tissueEnergy)
        }
        assertNull(PregnancyEnergy.estimate(patient(140, plurality = 2), true, inactive))
        assertNull(PregnancyEnergy.estimate(patient(140), false, inactive))
    }
}
