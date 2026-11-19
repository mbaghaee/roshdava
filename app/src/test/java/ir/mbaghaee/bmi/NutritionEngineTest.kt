package ir.mbaghaee.bmi

import ir.mbaghaee.bmi.NutritionEngine.Group
import org.junit.Assert.*
import org.junit.Test

class NutritionEngineTest {
    @Test
    fun macroReviewShowsGramsAndPercentAndFlagsOnlySharesOutsideTheAdultRanges() {
        val plan = NutritionEngine.target(1800.0, 20.0, 30.0)
        val (protein, carbs, fat) = NutritionEngine.macroShares(plan)
        assertEquals(90.0, protein.grams, 1e-10)
        assertEquals(20.0, protein.percent, 1e-10)
        assertEquals(225.0, carbs.grams, 1e-10)
        assertEquals(60.0, fat.grams, 1e-10)
        assertTrue(listOf(protein, carbs, fat).none { it.outsideAdultRange })

        val boundary = NutritionEngine.macroShares(NutritionEngine.target(1937.0, 10.0, 25.0))
        assertTrue(boundary.none { it.outsideAdultRange })
        val unusual = NutritionEngine.macroShares(NutritionEngine.target(1800.0, 5.0, 50.0))
        assertEquals(listOf("پروتئین", "چربی"), unusual.filter { it.outsideAdultRange }.map { it.label })
    }

    @Test
    fun reportsIncludeEditedMealUnitsAndKeepProfessionalDetailsOutOfThePatientCopy() {
        val milk = NutritionEngine.Group.MILK
        val breakfast = NutritionEngine.Meal.BREAKFAST
        val lunch = NutritionEngine.Meal.LUNCH
        val daily = mapOf(milk to 20)
        val before = linkedMapOf(breakfast to mapOf(milk to 20), lunch to mapOf(milk to 0))
        val allocation = checkNotNull(NutritionEngine.moveUnits(before, breakfast, lunch, milk, 5))
        val notes = mapOf(lunch to "خوراک جایگزین برای ناهار")
        val patientNote = "یادداشت عمومی"
        val reason = "دلیل بالینی انتخاب هدف"

        val patientReport = NutritionPlanReport.format(1800.0, daily, allocation, notes, patientNote)
        assertTrue(patientReport.contains("صبحانه\n• ۱٫۵ واحد شیر"))
        assertTrue(patientReport.contains("ناهار\n• ۰٫۵ واحد شیر"))
        assertTrue(patientReport.contains("حدود ۱ پیمانه"))
        assertTrue(patientReport.contains(notes.getValue(lunch)))
        assertTrue(patientReport.contains(patientNote))
        assertFalse(patientReport.contains(reason))

        val professionalReport = NutritionPlanReport.format(1800.0, daily, allocation, notes, patientNote, reason)
        assertTrue(professionalReport.contains("نسخهٔ متخصص"))
        assertTrue(professionalReport.contains(reason))
    }


    @Test
    fun comparesBothMethodsWithTheSameActivityAndActualWeight() {
        val patient = Patient("نمونه", 1, 10958, 71.5, 164.0)
        val result = NutritionEngine.estimate(patient, NutritionEngine.Activity.LIGHT)
        assertEquals(2293.0, result.harrisDaily, 0.1)
        assertEquals(2193.1, result.mifflinDaily, 0.1)

        val below = patient.copy(weight = 29.49 * 1.64 * 1.64)
        val above = patient.copy(weight = 29.51 * 1.64 * 1.64)
        val delta = NutritionEngine.estimate(above, NutritionEngine.Activity.LIGHT).harrisDaily -
            NutritionEngine.estimate(below, NutritionEngine.Activity.LIGHT).harrisDaily
        assertEquals(1.017, delta, 0.001)
    }

    @Test
    fun finalUnitsKeepFixedGroupsAndReconcileAcrossBothMealTemplates() {
        val fixed = mapOf(Group.MILK to 12, Group.FRUIT to 27, Group.VEGETABLE to 45, Group.SUGAR to 0)
        val units = checkNotNull(NutritionEngine.exchanges(NutritionEngine.target(2293.0, 18.0, 28.0), fixed))
        for ((group, count) in fixed) assertEquals(count, units.getValue(group))
        assertTrue(units.values.all { it >= 0 })
        assertEquals(2293.0, NutritionEngine.totals(units).calories, 30.0)
        for (template in NutritionEngine.Template.values()) {
            val meals = NutritionEngine.split(units, template)
            for ((group, unitsPerDay) in units) {
                assertEquals(unitsPerDay, meals.values.sumOf { it[group] ?: 0 })
            }
            assertEquals(NutritionEngine.totals(units).calories,
                meals.values.sumOf { NutritionEngine.totals(it).calories }, 1e-8)
        }
    }

    @Test
    fun refusesToProduceNegativeUnitsWhenFixedGroupsExceedTheTarget() {
        val target = NutritionEngine.target(1200.0, 18.0, 28.0)
        assertNull(NutritionEngine.exchanges(target, mapOf(Group.FRUIT to 150)))
    }

    @Test
    fun usesMifflinForDeficitAndRejectsTargetsOutsideToolRange() {
        val estimate = NutritionEngine.Estimate(2100.0, 1700.0, 1.0)
        assertEquals(1200, NutritionEngine.suggestedTarget(estimate, 500))
        assertEquals(1210, NutritionEngine.suggestedTarget(estimate, 490))
        assertNull(NutritionEngine.suggestedTarget(estimate, 510))
        assertNull(NutritionEngine.suggestedTarget(estimate, 495))
    }

    @Test
    fun starterDeficitFollowsMaintenanceAndAvoidsLowEnergyTargets() {
        fun estimate(maintenance: Double) = NutritionEngine.Estimate(2100.0, maintenance, 1.0)
        val adult = Patient("نمونه", 2, 10958, 78.0, 164.0)
        assertEquals(320, NutritionEngine.initialDeficit(adult, true, estimate(1600.0)))
        assertEquals(440, NutritionEngine.initialDeficit(adult, true, estimate(2200.0)))
        assertEquals(500, NutritionEngine.initialDeficit(adult, true, estimate(3330.0)))
        assertNull(NutritionEngine.initialDeficit(adult, true, estimate(1410.0)))
        assertNull(NutritionEngine.initialDeficit(adult, true, estimate(Double.NaN)))
        assertNull(NutritionEngine.initialDeficit(adult.copy(weight = 60.0), true, estimate(2200.0)))
        assertNull(NutritionEngine.initialDeficit(adult.copy(weight = 45.0), true, estimate(2200.0)))
        val maleWithBmi36 = adult.copy(sex = 1, days = 25000, weight = 36 * 1.64 * 1.64)
        assertEquals(440, NutritionEngine.initialDeficit(maleWithBmi36, true, estimate(2200.0)))
        assertNull(NutritionEngine.initialDeficit(adult.copy(pregnant = true), true, estimate(2200.0)))
        assertNull(NutritionEngine.initialDeficit(adult, false, estimate(2200.0)))
    }

    @Test
    fun weeklyEnergyEquivalentUsesCurrentWeightAndSevenDays() {
        assertEquals(.4545, NutritionEngine.weeklyWeightEnergyPercent(100.0, 500)!!, .0001)
        assertEquals(0.0, NutritionEngine.weeklyWeightEnergyPercent(100.0, 0)!!, 0.0)
        assertNull(NutritionEngine.weeklyWeightEnergyPercent(0.0, 500))
    }

    @Test
    fun snackEmphasizesMilkAndFruitAndOldTemplateKeepsItsDistribution() {
        val units = mapOf(Group.MILK to 20, Group.VEGETABLE to 30, Group.FRUIT to 20, Group.MEAT to 50)
        val template = NutritionEngine.Template.SNACK
        val newSnack = NutritionEngine.split(units, template).getValue(NutritionEngine.Meal.SNACK)
        val savedSnack = NutritionEngine.split(units, template, version = 1).getValue(NutritionEngine.Meal.SNACK)
        assertEquals(10, newSnack.getValue(Group.MILK))
        assertEquals(10, newSnack.getValue(Group.FRUIT))
        assertEquals(0, newSnack.getValue(Group.VEGETABLE))
        assertEquals(0, newSnack.getValue(Group.MEAT))
        assertTrue(savedSnack.getValue(Group.MEAT) > 0)
    }

    @Test
    fun transferringATenthKeepsDailyTotalsAndCannotOverdrawAMeal() {
        val breakfast = NutritionEngine.Meal.BREAKFAST
        val dinner = NutritionEngine.Meal.DINNER
        val initial = mapOf(breakfast to mapOf(Group.MILK to 12), dinner to mapOf(Group.MILK to 0))
        val revised = checkNotNull(NutritionEngine.moveUnits(initial, breakfast, dinner, Group.MILK, 3))
        assertEquals(9, revised.getValue(breakfast).getValue(Group.MILK))
        assertEquals(3, revised.getValue(dinner).getValue(Group.MILK))
        assertEquals(12, revised.values.sumOf { it.getValue(Group.MILK) })
        assertNull(NutritionEngine.moveUnits(revised, breakfast, dinner, Group.MILK, 10))
    }
}
