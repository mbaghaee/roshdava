package ir.mbaghaee.bmi

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

internal object NutritionEngine {
    const val MIN_TARGET = 1200
    const val MAX_TARGET = 6000
    const val DEFICIT_STEP = 10
    const val MAX_DEFICIT = 1000
    private const val STARTER_CAP_KCAL = 500
    private const val STARTER_CAP_FRACTION = 0.20
    const val PLAN_VERSION = 2
    const val UNIT_SCALE = 10
    private const val HALF_UNIT = UNIT_SCALE / 2

    enum class Activity(val label: String, val shortExample: String, val example: String, val factor: Double) {
        SEDENTARY("کم‌تحرک", "بیشتر روز نشسته", "بیشتر روز نشسته، رفت‌وآمد کم و بدون ورزش منظم.", 1.2),
        LIGHT("فعالیت سبک", "چند پیاده‌روی در هفته", "روز عمدتاً نشسته، با ۱ تا ۳ نوبت پیاده‌روی تند یا دوچرخه‌سواری آرام در هفته، هر نوبت حدود ۲۰ تا ۳۰ دقیقه.", 1.375),
        MODERATE("فعالیت متوسط", "تحرک در بیشتر روزها", "مثلاً پیاده‌روی تند یا دوچرخه‌سواری ۳۰ تا ۴۵ دقیقه در بیشتر روزها، یا شغلی با راه‌رفتن مداوم.", 1.55),
        HIGH("فعالیت زیاد", "تمرین سنگین منظم", "مثلاً تمرین نسبتاً سنگین حدود یک ساعت در بیشتر روزها، یا شغلی با کار بدنی قابل توجه.", 1.725),
        VERY_HIGH("فعالیت بسیار زیاد", "کار بدنی و تمرین سنگین", "مثلاً کار بدنی سنگین همراه با تمرین منظم، یا تمرین شدید و طولانی تقریباً هر روز.", 1.9)
    }

    data class Estimate(val harrisRest: Double, val mifflinRest: Double, val factor: Double) {
        val harrisDaily get() = harrisRest * factor
        val mifflinDaily get() = mifflinRest * factor
    }

    fun estimate(patient: Patient, activity: Activity, weightKg: Double = patient.weight): Estimate {
        val age = patient.days / 365.25
        val w = weightKg
        val h = patient.height
        val male = patient.sex == 1
        val harris = if (male) 66.5 + 13.75 * w + 5.003 * h - 6.75 * age
            else 655.1 + 9.563 * w + 1.85 * h - 4.676 * age
        val mifflin = 10 * w + 6.25 * h - 5 * age + if (male) 5 else -161
        return Estimate(harris, mifflin, activity.factor)
    }

    data class Macros(val protein: Double, val carbs: Double, val fat: Double) {
        val calories get() = 4 * protein + 4 * carbs + 9 * fat
        operator fun plus(other: Macros) = Macros(protein + other.protein, carbs + other.carbs, fat + other.fat)
        operator fun times(units: Double) = Macros(protein * units, carbs * units, fat * units)
    }

    // Exchange counts are integer tenths so daily totals and meal splits reconcile exactly.
    enum class Group(val label: String, val macros: Macros, val householdExample: String) {
        MILK("شیر ۲٪ چربی", Macros(8.0, 12.0, 5.0), "حدود ۱ پیمانه شیر سادهٔ ۲٪ چربی"),
        VEGETABLE("سبزی غیرنشاسته‌ای", Macros(2.0, 5.0, 0.0), "۱ پیمانه خام یا نصف پیمانه پخته"),
        FRUIT("میوه", Macros(0.0, 15.0, 0.0), "مثلاً ۱ سیب کوچک"),
        SUGAR("قند افزوده", Macros(0.0, 15.0, 0.0), "حدود ۴ قاشق چای‌خوری سرصاف شکر (نزدیک ۱۵ گرم)"),
        STARCH("نان و غلات", Macros(2.0, 15.0, 0.0), "مقداری نان یا غلات با حدود ۱۵ گرم کربوهیدرات؛ برحسب نوع خوراک"),
        MEAT("گوشت کم‌چرب", Macros(7.0, 0.0, 3.0), "نزدیک به ۳۰ گرم گوشت پختهٔ کم‌چرب؛ برحسب نوع و برش گوشت"),
        FAT("روغن و چربی", Macros(0.0, 0.0, 9.0), "حدود ۲ قاشق چای‌خوری روغن مایع (۹ گرم)")
    }

    data class MacroShare(val label: String, val grams: Double, val percent: Double, val adultRange: IntRange) {
        val outsideAdultRange get() = percent < adultRange.first - 1e-8 || percent > adultRange.last + 1e-8
    }

    fun macroShares(macros: Macros): List<MacroShare> {
        if (!macros.calories.isFinite() || macros.calories <= 0) return emptyList()
        fun share(label: String, grams: Double, energy: Double, range: IntRange): MacroShare {
            val percent = 100 * energy / macros.calories
            return MacroShare(label, grams, percent, range)
        }
        // DRI AMDR for generally healthy adults aged 19 and over. These ranges prompt review.
        return listOf(
            share("پروتئین", macros.protein, macros.protein * 4, 10..35),
            share("کربوهیدرات", macros.carbs, macros.carbs * 4, 45..65),
            share("چربی", macros.fat, macros.fat * 9, 20..35)
        )
    }

    val fixedGroups = listOf(Group.MILK, Group.VEGETABLE, Group.FRUIT, Group.SUGAR)

    fun isValidDeficit(deficit: Int) = deficit in 0..MAX_DEFICIT && deficit % DEFICIT_STEP == 0

    fun adultBmi(patient: Patient): Double {
        val height = patient.height / 100
        return patient.weight / (height * height)
    }

    fun mayPreviewReduction(patient: Patient, adult: Boolean): Boolean {
        if (!adult || patient.pregnant || patient.premature) return false
        val bmi = adultBmi(patient)
        return bmi.isFinite() && bmi >= 25
    }

    // Conservative software default for a clinician-confirmed goal, not a guideline formula.
    // 500 kcal is the low end of the common 500–750 kcal reference range. Also cap it
    // relative to estimated energy and avoid the NICE NG246 low-energy range.
    fun initialDeficit(patient: Patient, adult: Boolean, estimate: Estimate): Int? {
        if (!mayPreviewReduction(patient, adult)) return null
        val maintenance = estimate.mifflinDaily
        if (!maintenance.isFinite() || maintenance <= MIN_TARGET) return null
        val ceiling = minOf(STARTER_CAP_KCAL.toDouble(), maintenance * STARTER_CAP_FRACTION)
        val deficit = floor(ceiling / DEFICIT_STEP).toInt() * DEFICIT_STEP
        return deficit.takeIf { (suggestedTarget(estimate, it) ?: 0) > MIN_TARGET }
    }

    fun suggestedTarget(estimate: Estimate, deficit: Int): Int? {
        if (!isValidDeficit(deficit) || !estimate.mifflinDaily.isFinite()) return null
        return (estimate.mifflinDaily.roundToInt() - deficit).takeIf { it in MIN_TARGET..MAX_TARGET }
    }

    /** Static energy equivalent for the picker preview; not a forecast of scale weight. */
    fun weeklyWeightEnergyPercent(weightKg: Double, dailyReduction: Int): Double? {
        if (!weightKg.isFinite() || weightKg <= 0 || !isValidDeficit(dailyReduction)) return null
        return dailyReduction * 7.0 / 7700 / weightKg * 100
    }

    // Editable starter quantities, expressed in tenths of an exchange.
    fun starterUnits(calories: Int?): Map<Group, Int> {
        val (milk, veg, fruit) = when {
            calories == null || calories < 1600 -> Triple(20, 30, 20)
            calories < 2200 -> Triple(20, 40, 25)
            calories < 2600 -> Triple(25, 40, 30)
            else -> Triple(30, 50, 35)
        }
        return mapOf(Group.MILK to milk, Group.VEGETABLE to veg, Group.FRUIT to fruit, Group.SUGAR to 0)
    }

    fun totals(units: Map<Group, Int>): Macros =
        units.entries.fold(Macros(0.0, 0.0, 0.0)) { sum, (group, tenths) ->
            sum + group.macros * (tenths.toDouble() / UNIT_SCALE)
        }

    fun target(calories: Double, proteinPercent: Double, fatPercent: Double) = Macros(
        calories * proteinPercent / 400,
        calories * (100 - proteinPercent - fatPercent) / 400,
        calories * fatPercent / 900
    )

    /** Preserve clinician-entered groups and minimize energy and macro differences (in kcal). */
    fun exchanges(target: Macros, fixed: Map<Group, Int>): Map<Group, Int>? {
        val base = totals(fixed)
        val tolerance = 1e-8
        if (base.protein - target.protein > tolerance || base.carbs - target.carbs > tolerance ||
            base.fat - target.fat > tolerance) return null
        val starchMax = floor((target.carbs - base.carbs) / 7.5).toInt() + 1
        val meatMax = floor((target.protein - base.protein) / 3.5).toInt() + 1
        var best = Double.POSITIVE_INFINITY
        var result = fixed
        for (starch in 0..starchMax) {
            val afterStarch = base + Group.STARCH.macros * (starch / 2.0)
            for (meat in 0..meatMax) {
                val partial = afterStarch + Group.MEAT.macros * (meat / 2.0)
                val byFat = floor((target.fat - partial.fat) / 4.5).toInt().coerceAtLeast(0)
                val byEnergy = floor((target.calories - partial.calories) / 40.5).toInt().coerceAtLeast(0)
                for (fat in intArrayOf(byFat, byFat + 1, byEnergy, byEnergy + 1)) {
                    val actual = partial + Group.FAT.macros * (fat / 2.0)
                    val error = abs(actual.calories - target.calories) +
                        4 * abs(actual.protein - target.protein) + 4 * abs(actual.carbs - target.carbs) +
                        9 * abs(actual.fat - target.fat)
                    if (error < best) {
                        best = error
                        result = fixed + mapOf(
                            Group.STARCH to starch * HALF_UNIT, Group.MEAT to meat * HALF_UNIT,
                            Group.FAT to fat * HALF_UNIT
                        )
                    }
                }
            }
        }
        return result
    }

    enum class Meal(val label: String) {
        BREAKFAST("صبحانه"), LUNCH("ناهار"), SNACK("میان‌وعده"), DINNER("شام")
    }

    enum class Template(val label: String, val meals: List<Meal>, val shares: List<Int>) {
        THREE("سه وعده", listOf(Meal.BREAKFAST, Meal.LUNCH, Meal.DINNER), listOf(30, 40, 30)),
        SNACK("سه وعده و میان‌وعده", Meal.values().toList(), listOf(25, 35, 15, 25))
    }

    fun split(units: Map<Group, Int>, template: Template, version: Int = PLAN_VERSION): Map<Meal, Map<Group, Int>> {
        val meals = template.meals.associateWith { mutableMapOf<Group, Int>() }
        for ((group, count) in units) {
            val shares = if (version < PLAN_VERSION) template.shares else mealShares(group, template)
            // Preserve existing half-unit splits; use tenths when the entered amount needs them.
            val step = if (count % HALF_UNIT == 0) HALF_UNIT else 1
            val portions = count / step
            val distribution = shares.map { portions * it / 100 }.toMutableList()
            val order = shares.indices.sortedByDescending { (portions * shares[it]) % 100 }
            order.take(portions - distribution.sum()).forEach { distribution[it]++ }
            template.meals.forEachIndexed { i, meal -> meals.getValue(meal)[group] = distribution[i] * step }
        }
        return meals
    }

    fun moveUnits(
        meals: Map<Meal, Map<Group, Int>>, from: Meal, to: Meal, group: Group, count: Int
    ): Map<Meal, Map<Group, Int>>? {
        val source = meals[from] ?: return null
        val destination = meals[to] ?: return null
        val available = source[group] ?: 0
        if (from == to || count !in 1..available) return null
        return meals + mapOf(
            from to (source + (group to (available - count))),
            to to (destination + (group to ((destination[group] ?: 0) + count)))
        )
    }

    private fun mealShares(group: Group, template: Template): List<Int> = when (template) {
        Template.THREE -> when (group) {
            Group.MILK -> listOf(60, 10, 30)
            Group.FRUIT -> listOf(50, 25, 25)
            Group.VEGETABLE -> listOf(0, 50, 50)
            Group.MEAT -> listOf(10, 50, 40)
            else -> template.shares
        }
        Template.SNACK -> when (group) {
            Group.MILK -> listOf(50, 0, 50, 0)
            Group.FRUIT -> listOf(25, 0, 50, 25)
            Group.VEGETABLE -> listOf(0, 50, 0, 50)
            Group.MEAT -> listOf(10, 50, 0, 40)
            Group.FAT -> listOf(20, 40, 0, 40)
            else -> template.shares
        }
    }

    fun whole(value: Double) = SolarDate.persian(value.roundToInt().toString())

    fun decimal(value: Double, precision: Int = 1): String {
        val formatted = String.format(Locale.US, "%.${precision}f", value)
        val text = if (precision > 0) formatted.trimEnd('0').trimEnd('.') else formatted
        return SolarDate.persian(text.replace('.', '٫'))
    }
}
