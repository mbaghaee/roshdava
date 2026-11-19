package ir.mbaghaee.bmi

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ArrayAdapter
import android.content.DialogInterface
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import ir.mbaghaee.bmi.NutritionEngine.Group
import ir.mbaghaee.bmi.NutritionEngine.Meal
import ir.mbaghaee.bmi.databinding.CardNutritionBinding
import ir.mbaghaee.bmi.databinding.DialogCalorieReductionBinding
import ir.mbaghaee.bmi.databinding.DialogNutritionTransferBinding
import ir.mbaghaee.bmi.databinding.ItemNutritionMealBinding
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.math.abs

internal class NutritionCard(
    private val binding: CardNutritionBinding,
    private val patient: Patient,
    private val adult: Boolean,
    initial: JSONObject?,
    private val onCommitPregnancy: (JSONObject) -> Boolean
) {
    private val context: Context = binding.root.context
    private enum class Care(val label: String) {
        GENERAL("ارزیابی عمومی"),
        LACTATION("شیردهی"),
        SPECIAL("شرایط نیازمند ارزیابی اختصاصی")
    }
    private val fields = listOf(
        "target" to binding.targetCalories,
        "protein" to binding.proteinPercent,
        "fat" to binding.fatPercent,
        "milk" to binding.milkUnits,
        "vegetable" to binding.vegetableUnits,
        "fruit" to binding.fruitUnits,
        "sugar" to binding.sugarUnits
    )
    private val defaults = mapOf("protein" to "18", "fat" to "28")
    private val groupFields = linkedMapOf(
        Group.MILK to binding.milkUnits, Group.VEGETABLE to binding.vegetableUnits,
        Group.FRUIT to binding.fruitUnits, Group.SUGAR to binding.sugarUnits
    )
    private val customGroups = mutableSetOf<Group>()
    private var updatingUnits = false
    private var version = initial?.optInt("version", 1) ?: NutritionEngine.PLAN_VERSION
    private var deficitOverride = initial?.let {
        if (it.has("deficitOverride")) it.optInt("deficitOverride", -1)
        else it.optInt("deficit", -1)
    }?.takeIf(NutritionEngine::isValidDeficit)
    private var care = Care.values().firstOrNull { it.name == initial?.optString("care") } ?: Care.GENERAL
    private var activity = NutritionEngine.Activity.values().firstOrNull { it.name == initial?.optString("activity") }
    private var pregnancyActivity = PregnancyEnergy.Activity.values()
        .firstOrNull { it.name == initial?.optString("pregnancyActivity") }
    private var pregnancyEstimate: PregnancyEnergy.Estimate? = null
    private val savedPregnancyMaintenance = initial?.optString("pregnancyMaintenanceKcal").orEmpty()
    private var template = NutritionEngine.Template.values().firstOrNull { it.name == initial?.optString("template") }
        ?: NutritionEngine.Template.SNACK
    private var notes = initial?.optJSONObject("meals")?.let { JSONObject(it.toString()) } ?: JSONObject()
    private var expanded = initial?.optBoolean("expanded") == true
    private var planExpanded = initial?.optBoolean("planExpanded") ?: (initial != null)
    private var mealMode = initial?.optBoolean("mealMode", true) ?: true
    private var pregnancyApproved = patient.pregnant && initial?.optBoolean("approved") == true
    private var plan: Map<Group, Int>? = null
    private var mealAllocation: Map<Meal, Map<Group, Int>>? = null
    private var energy: NutritionEngine.Estimate? = null
    private var proposedTarget: Int? = null
    private val bmi = NutritionEngine.adultBmi(patient)
    private val reductionEligible = NutritionEngine.mayPreviewReduction(patient, adult)
    private val olderAdult = patient.days / 365.25 >= 65

    init {
        if (patient.pregnant) {
            bindPregnancy()
        } else if (!adult || patient.premature) {
            binding.nutritionSummary.text = if (patient.premature)
                "تغذیهٔ نوزاد نارس با سن اصلاح‌شده و روند رشد ارزیابی می‌شود."
            else "نیاز انرژی کودکان و نوجوانان با توجه به سن و روند رشد جداگانه ارزیابی می‌شود."
            binding.nutritionToggle.visibility = View.GONE
        } else {
            binding.energyStageTitle.visibility = View.VISIBLE
            binding.nutritionGoalReason.setText(initial?.optString("goalReason").orEmpty())
            binding.nutritionPatientNote.setText(initial?.optString("patientNote").orEmpty())
            val storedGroups = initial?.optJSONArray("customGroups")
            if (storedGroups != null) {
                for (i in 0 until storedGroups.length()) {
                    NutritionEngine.fixedGroups.firstOrNull { it.name == storedGroups.optString(i) }
                        ?.let(customGroups::add)
                }
            } else if (initial != null) {
                customGroups.addAll(NutritionEngine.fixedGroups.filter { initial.optString(it.name.lowercase()).isNotBlank() })
            }
            for ((key, field) in fields) {
                field.setText(initial?.optString(key) ?: defaults[key].orEmpty())
            }
            refreshStarterUnits(read(binding.targetCalories)?.toInt())
            for ((_, field) in fields) {
                field.doAfterTextChanged {
                    if (updatingUnits) return@doAfterTextChanged
                    groupFields.entries.firstOrNull { it.value == field }?.key?.let(customGroups::add)
                    field.error = null
                    invalidatePlan()
                    updateMacroTarget()
                    if (field == binding.targetCalories) {
                        updateSuggestionAction()
                        read(field)?.toInt()?.takeIf { it in NutritionEngine.MIN_TARGET..NutritionEngine.MAX_TARGET }
                            ?.let(::refreshStarterUnits)
                    }
                }
            }
            binding.nutritionToggle.setOnClickListener { expanded = !expanded; showExpansion() }
            binding.planToggle.setOnClickListener {
                if (!planExpanded && readTarget(showErrors = true) == null) return@setOnClickListener
                planExpanded = !planExpanded
                showPlanDetails()
            }
            binding.activitySelect.setOnClickListener { selectActivity() }
            binding.careContext.setOnClickListener { selectCare() }
            binding.applyMaintenance.setOnClickListener {
                maintenanceTarget()?.let(::setPlanTarget)
            }
            binding.applySuggestion.setOnClickListener { proposedTarget?.let(::setPlanTarget) }
            binding.deficitPicker.setOnClickListener {
                if (energy == null) selectActivity() else selectDeficit()
            }
            binding.unitsGuide.setOnClickListener { showUnitsGuide() }
            binding.calculatePlan.setOnClickListener { calculate(showErrors = true) }
            binding.planModeGroup.check(if (mealMode) R.id.meals_mode else R.id.daily_mode)
            binding.planModeGroup.addOnButtonCheckedListener { _, id, checked ->
                if (checked) {
                    mealMode = id == R.id.meals_mode
                    showMode()
                }
            }
            binding.templateSelect.setOnClickListener { selectTemplate() }
            binding.resetMealSplit.setOnClickListener {
                mealAllocation = null
                renderMeals()
            }
            binding.previewPlan.setOnClickListener { choosePlanPreview() }
            updateMacroTarget()
            showEstimate()
            if (care == Care.GENERAL && initial?.optBoolean("calculated") == true)
                calculate(showErrors = false, storedAllocation = initial.optJSONObject("mealAllocation"))
            showExpansion()
        }
    }

    fun capture(): JSONObject = JSONObject().apply {
        put("version", version)
        put("expanded", expanded)
        put("planExpanded", planExpanded)
        put("mealMode", mealMode)
        put("template", template.name)
        put("activity", activity?.name.orEmpty())
        put("deficitOverride", deficitOverride ?: JSONObject.NULL)
        put("care", care.name)
        put("pregnancyActivity", pregnancyActivity?.name.orEmpty())
        put("customGroups", JSONArray(NutritionEngine.fixedGroups.filter { it in customGroups }.map { it.name }))
        put("calculated", plan != null)
        put("approved", pregnancyApproved && pregnancyEstimate != null)
        put("goalReason", binding.nutritionGoalReason.text.toString())
        put("patientNote", binding.nutritionPatientNote.text.toString())
        pregnancyEstimate?.let {
            val recommendation = it.recommend(maintenanceKcal())
            put("pregnancyEerKcal", it.daily.roundToInt())
            put("pregnancyEnergyReference", "DRI 2023")
            put("pregnancyMaintenanceKcal", binding.pregnancyMaintenance.text.toString())
            put("pregnancyRecommendedKcal", recommendation.daily.roundToInt())
            put("pregnancyRecommendationBasis", if (recommendation.usesMaintenance) "usualIntake" else "DRI 2023")
        }
        put("meals", JSONObject(notes.toString()))
        mealAllocation?.let { allocation ->
            put("mealAllocation", JSONObject().apply {
                for ((meal, units) in allocation) {
                    put(meal.name, JSONObject().apply {
                        for ((group, count) in units) put(group.name, count)
                    })
                }
            })
        }
        for ((key, field) in fields) put(key, field.text.toString())
    }

    private fun bindPregnancy() {
        binding.careContext.visibility = View.GONE
        binding.clinicalNotice.text = "دیابت بارداری و سایر شرایط خاص به هدف اختصاصی تیم مراقبت نیاز دارند."
        binding.planToggle.visibility = View.GONE
        binding.harrisEnergy.visibility = View.GONE
        binding.mifflinEnergy.visibility = View.GONE
        val unavailable = PregnancyEnergy.unavailableReason(patient, adult)
        if (unavailable != null) {
            binding.nutritionSummary.text = unavailable
            binding.nutritionToggle.visibility = View.GONE
            return
        }
        binding.nutritionToggle.setOnClickListener { expanded = !expanded; showExpansion() }
        binding.activitySelect.setOnClickListener {
            val activityLevels = PregnancyEnergy.Activity.values()
            MaterialAlertDialogBuilder(context).setTitle("سطح فعالیت فعلی")
                .setSingleChoiceItems(activityLevels.map { it.label }.toTypedArray(),
                    pregnancyActivity?.ordinal ?: -1) { dialog, index ->
                    if (pregnancyActivity != activityLevels[index]) pregnancyApproved = false
                    pregnancyActivity = activityLevels[index]
                    showPregnancyEstimate()
                    dialog.dismiss()
                }.setNegativeButton("بازگشت", null).show()
        }
        binding.pregnancyMaintenance.setText(savedPregnancyMaintenance)
        binding.pregnancyMaintenance.doAfterTextChanged {
            pregnancyApproved = false
            pregnancyEstimate?.let(::showPregnancyRecommendation)
        }
        binding.pregnancySave.setOnClickListener {
            if (pregnancyEstimate != null && onCommitPregnancy(capture().put("approved", true).put("expanded", false))) {
                pregnancyApproved = true
                showPregnancyEstimate()
            }
        }
        showPregnancyEstimate()
        showExpansion()
    }

    private fun showPregnancyEstimate() {
        val pregnancy = patient.pregnancy ?: return
        pregnancyEstimate = pregnancyActivity?.let { PregnancyEnergy.estimate(patient, adult, it) }
        val estimate = pregnancyEstimate
        binding.activitySelect.text = pregnancyActivity?.let { "فعالیت فعلی: ${it.label}" }
            ?: "انتخاب سطح فعالیت فعلی"
        binding.activityDetail.text = "تک‌قلویی · ${PregnancyAssessment.weekLabel(pregnancy.gestationDays)} · " +
            "وزن پیش از بارداری ${decimal(pregnancy.preWeight)} کیلوگرم"
        val visibility = if (estimate == null) View.GONE else View.VISIBLE
        binding.pregnancyComparison.visibility = visibility
        binding.pregnancySave.visibility = visibility
        if (estimate == null) {
            binding.nutritionSummary.text = "برآورد انرژی بارداری · DRI 2023"
            binding.energyDetails.text = "تحرک در کار، رفت‌وآمد، خانه و ورزش را در یک هفتهٔ معمول در نظر بگیرید."
            return
        }

        val stage = listOf("اول", "دوم", "سوم")[estimate.trimester - 1]
        val bmiText = "BMI پیش از بارداری: ${decimal(estimate.preBmi, 2)}"
        binding.energyDetails.text = "سه‌ماههٔ $stage · $bmiText"
        binding.pregnancyDriEnergy.text = dailyCalories(estimate.daily)
        binding.pregnancyDriDetail.text = if (estimate.trimester == 1) {
            "معادلهٔ نگهدارندهٔ غیربارداری با وزن پیش از بارداری و فعالیت فعلی."
        } else {
            val sign = if (estimate.tissueEnergy < 0) "−" else "+"
            val tissue = whole(abs(estimate.tissueEnergy).toDouble())
            "معادلهٔ بارداری با وزن فعلی، هفتهٔ بارداری و فعالیت فعلی. جزء انرژی بافت بر اساس BMI پیش از بارداری: " +
                "${sign}${tissue} کیلوکالری در روز."
        }
        binding.pregnancyMaintenanceLayout.visibility = if (estimate.canUseMaintenance) View.VISIBLE else View.GONE

        val legacy = PregnancyEnergy.legacyEstimate(patient, estimate)
        binding.pregnancyHarrisEnergy.text = dailyCalories(legacy.harrisDaily)
        binding.pregnancyMifflinEnergy.text = dailyCalories(legacy.mifflinDaily)
        binding.pregnancyLegacyDetail.text =
            "روش قدیمی: وزن پیش از بارداری و ضریب ${decimal(legacy.baseline.factor, 3)} برای فعالیت فعلی، " +
                "به‌علاوهٔ ${whole(legacy.extraKcal.toDouble())} کیلوکالری برای این سه‌ماهه (IOM 2005)."
        showPregnancyRecommendation(estimate)
    }

    private fun dailyCalories(value: Double) = "${whole(value)} کیلوکالری در روز"

    private fun maintenanceKcal(): Int? = read(binding.pregnancyMaintenance)
        ?.takeIf { it % 1 == 0.0 }?.toInt()?.takeIf(PregnancyEnergy::isValidMaintenance)

    private fun showPregnancyRecommendation(estimate: PregnancyEnergy.Estimate) {
        val maintenance = maintenanceKcal()
        val invalidIntake = estimate.canUseMaintenance && binding.pregnancyMaintenance.text?.isNotBlank() == true &&
            maintenance == null
        binding.pregnancyMaintenanceLayout.error = if (invalidIntake) {
            "مقدار روزانه را بین ${whole(NutritionEngine.MIN_TARGET.toDouble())} و " +
                "${whole(NutritionEngine.MAX_TARGET.toDouble())} وارد کنید."
        } else null
        val recommendation = estimate.recommend(maintenance)
        val title = "پیشنهاد اولیه: ${dailyCalories(recommendation.daily)}"
        binding.nutritionSummary.text = title
        binding.pregnancyRecommendation.text = title
        val reason = when {
            recommendation.usesMaintenance ->
                "مبنا دریافت معمول ثبت‌شده برای وزن پایدار با فعالیت مشابه است. افزودهٔ استاندارد بارداری در سه‌ماههٔ اول صفر است."
            estimate.trimester == 1 && estimate.canUseMaintenance ->
                "اگر دریافت نگهدارندهٔ قابل‌اتکا در دسترس نباشد، مبنا معادلهٔ غیربارداری DRI با فعالیت فعلی است. " +
                    "افزودهٔ استاندارد بارداری در سه‌ماههٔ اول صفر است."
            estimate.trimester == 1 ->
                "مبنا DRI برای زن غیرباردار با فعالیت فعلی است. افزودهٔ استاندارد بارداری در سه‌ماههٔ اول صفر است."
            else ->
                "مبنا DRI بارداری است که وزن فعلی، هفتهٔ بارداری، فعالیت فعلی و BMI پیش از بارداری را در نظر می‌گیرد."
        }
        binding.pregnancyRecommendationReason.text = "$reason هدف را با روند وزن‌گیری و نظر تیم مراقبت بازبینی کنید."
        binding.pregnancySave.isEnabled = !pregnancyApproved && !invalidIntake
        binding.pregnancySave.text = if (pregnancyApproved) "برآورد بارداری ثبت شد" else "ثبت برآورد بارداری در این سنجش"
    }

    private fun selectCare() {
        val options = Care.values()
        MaterialAlertDialogBuilder(context).setTitle("شرایط تغذیه در این نوبت")
            .setSingleChoiceItems(options.map { it.label }.toTypedArray(), care.ordinal) { dialog, index ->
                if (care != options[index]) {
                    care = options[index]
                }
                showEstimate()
                dialog.dismiss()
            }.setNegativeButton("بازگشت", null).show()
    }

    private fun refreshStarterUnits(calories: Int?) {
        updatingUnits = true
        try {
            for ((group, tenths) in NutritionEngine.starterUnits(calories)) {
                if (group in customGroups) continue
                val field = groupFields.getValue(group)
                val count = (tenths.toDouble() / NutritionEngine.UNIT_SCALE).toString().removeSuffix(".0")
                if (field.text.toString() != count) {
                    field.setText(count)
                    field.error = null
                }
            }
        } finally {
            updatingUnits = false
        }
    }

    private fun showExpansion() {
        binding.nutritionBody.visibility = if (expanded) View.VISIBLE else View.GONE
        if (patient.pregnant) binding.nutritionSummary.visibility = if (expanded) View.GONE else View.VISIBLE
        binding.nutritionToggle.text = if (expanded) "بستن تنظیمات تغذیه" else "تنظیم تغذیه"
    }

    private fun selectActivity() {
        val options = NutritionEngine.Activity.values()
        val choices = options.map { "${it.label} · ${it.shortExample}" }.toTypedArray()
        MaterialAlertDialogBuilder(context).setTitle("سطح فعالیت معمول")
            .setSingleChoiceItems(choices, activity?.ordinal ?: -1) { dialog, index ->
                activity = options[index]
                showEstimate()
                dialog.dismiss()
            }.setNegativeButton("بازگشت", null).show()
    }

    private fun showEstimate() {
        val general = care == Care.GENERAL
        energy = if (general) activity?.let { NutritionEngine.estimate(patient, it) } else null
        binding.careContext.text = "شرایط تغذیه: ${care.label}"
        binding.activitySelect.visibility = if (general) View.VISIBLE else View.GONE
        binding.activityDetail.visibility = binding.activitySelect.visibility
        binding.activitySelect.text = activity?.label ?: "انتخاب سطح فعالیت"
        binding.activityDetail.text = activity?.example
            ?: "رفت‌وآمد، تحرک سر کار و مدت و شدت ورزش در یک هفتهٔ معمول را در نظر بگیرید."
        binding.clinicalNotice.text = when {
            care == Care.LACTATION -> "در شیردهی، نیاز انرژی به زمان پس از زایمان و میزان شیردهی وابسته است."
            care == Care.SPECIAL -> "بیماری کلیوی، داروهای مؤثر بر قند یا اشتها، اختلال خوردن و کاهش وزن ناخواسته هر کدام ارزیابی جداگانه می‌خواهند."
            olderAdult -> "در سالمندی، هدف تغذیه با بررسی اشتها، کاهش وزن ناخواسته، قدرت عضلانی و بیماری‌ها تعیین شود."
            bmi < 18.5 -> "در کمبود وزن، علت و روند کاهش وزن بررسی شود. هدف افزایش وزن و کالری آن را متخصص تنظیم کند."
            bmi < 25 -> "پیشنهاد عمومی، حفظ وزن است. اگر دور کمر یا سایر عوامل خطر بالا هستند، هدف را متخصص تعیین کند."
            bmi < 30 -> "در اضافه‌وزن، حفظ وزن یا کاهش آن با توجه به دور کمر، وضعیت سلامت و ترجیح فرد انتخاب می‌شود."
            else -> "در چاقی، هدف با توجه به بیماری‌ها، داروها، توان بدنی و ترجیح فرد تعیین می‌شود."
        }
        val estimate = energy
        binding.energyDetails.text = when {
            !general -> "کالری و الگوی غذایی را پس از مشاورهٔ اختصاصی تعیین کنید."
            estimate == null -> "برای مقایسهٔ برآوردها سطح فعالیت را انتخاب کنید."
            else -> "برآورد حفظ وزن · ضریب فعالیت ${decimal(estimate.factor, 3)} · کیلوکالری در روز"
        }
        binding.harrisEnergy.visibility = if (estimate == null) View.GONE else View.VISIBLE
        binding.mifflinEnergy.visibility = binding.harrisEnergy.visibility
        binding.applyMaintenance.visibility = if (maintenanceTarget() == null) View.GONE else View.VISIBLE
        if (estimate != null) {
            binding.harrisEnergy.text = energyLine(
                "هریس–بندیکت", estimate.harrisDaily, estimate.harrisRest,
                "با وزن فعلی ثبت‌شده، نه وزن ایده‌آل"
            )
            binding.mifflinEnergy.text = energyLine(
                "میفلین–سن ژئور", estimate.mifflinDaily, estimate.mifflinRest,
                "مبنای پیشنهاد کالری رژیم", planBasis = true
            )
        }
        binding.planTarget.visibility = if (general) View.VISIBLE else View.GONE
        binding.planToggle.visibility = binding.planTarget.visibility
        showPlanDetails()
        showSuggestion()
        updateSummary()
    }

    private fun energyLine(name: String, daily: Double, rest: Double, note: String, planBasis: Boolean = false): CharSequence {
        val mark = if (planBasis) "✓\u00a0" else ""
        val primary = "$mark$name  ${whole(daily)}"
        return SpannableString("$primary\nکالری موردنیاز بدن در حالت استراحت: ${whole(rest)}\n$note").apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, primary.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (planBasis) {
                setSpan(ForegroundColorSpan(context.getColor(R.color.growth_normal_text)),
                    0, mark.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            setSpan(RelativeSizeSpan(0.8f), primary.length, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(context.getColor(R.color.growth_muted)),
                primary.length, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun showPlanDetails() {
        binding.planInputs.visibility = if (care == Care.GENERAL && planExpanded) View.VISIBLE else View.GONE
        binding.planToggle.text = if (planExpanded) "بستن واحدها و وعده‌ها" else "ادامه به واحدها و وعده‌ها"
    }

    private fun setPlanTarget(target: Int) {
        if (read(binding.targetCalories) != target.toDouble()) {
            binding.targetCalories.setText(target.toString())
        }
    }

    private fun showSuggestion() {
        val estimate = energy
        val mayShow = reductionEligible && care == Care.GENERAL
        val deficit = if (mayShow) estimate?.let(::activeDeficit) else null
        val target = if (estimate != null && deficit != null) reductionTarget(estimate, deficit) else null
        proposedTarget = target
        updateSuggestionAction()
        binding.applySuggestion.visibility = if (mayShow && estimate != null) View.VISIBLE else View.GONE
        binding.targetSuggestion.visibility = if (mayShow) View.VISIBLE else View.GONE
        binding.reductionAmount.visibility = if (deficit == null) View.GONE else View.VISIBLE
        binding.reductionAmount.text = deficit?.let(::reductionAmountLabel)
        if (!mayShow) return
        binding.reductionNotice.text = if (olderAdult) {
            "پیش از اعمال پیشنهاد در سالمندی، قدرت عضلانی، اشتها و کاهش وزن ناخواسته را بررسی کنید."
        } else {
            "این عدد نقطهٔ شروع است. پیش از اعمال، مناسب بودن کاهش وزن را با توجه به روند وزن، بیماری‌ها و داروها بررسی کنید."
        }
        if (estimate == null) {
            binding.suggestionText.text = "پیشنهاد کاهش کالری با انتخاب سطح فعالیت محاسبه می‌شود."
            binding.deficitPicker.text = "انتخاب فعالیت"
            return
        }

        binding.suggestionText.text = when {
            deficit == null -> "پیشنهاد اولیه در محدودهٔ ابزار نیست. مقدار کاهش یا کالری هدف را تنظیم کنید."
            target == null -> "کالری هدف حاصل از مقدار کاهش انتخاب‌شده، خارج از محدودهٔ پیشنهاد است."
            else -> "کالری هدف پیشنهادی: ${whole(target.toDouble())} کیلوکالری در روز"
        }
        binding.deficitPicker.text = "تنظیم کاهش کالری"
    }

    private fun reductionAmountLabel(deficit: Int): CharSequence {
        val label = "کاهش روزانه: "
        return SpannableString("$label${whole(deficit.toDouble())} کیلوکالری").apply {
            setSpan(ForegroundColorSpan(context.getColor(R.color.growth_point)),
                label.length, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), label.length, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun updateSuggestionAction() {
        val target = proposedTarget
        val alreadySet = target != null && read(binding.targetCalories) == target.toDouble()
        binding.applySuggestion.text = if (alreadySet) "هدف تنظیم شد" else "اعمال در رژیم"
        binding.applySuggestion.isEnabled = target != null && !alreadySet
    }

    private fun activeDeficit(estimate: NutritionEngine.Estimate): Int? =
        if (reductionEligible && care == Care.GENERAL)
            deficitOverride ?: NutritionEngine.initialDeficit(patient, adult, estimate)
        else null

    private fun reductionTarget(estimate: NutritionEngine.Estimate, deficit: Int): Int? =
        NutritionEngine.suggestedTarget(estimate, deficit)?.takeIf { it > NutritionEngine.MIN_TARGET }

    private fun maintenanceTarget(): Int? =
        if (bmi >= 18.5 && !olderAdult && care == Care.GENERAL) energy?.let { NutritionEngine.suggestedTarget(it, 0) }
        else null

    private fun selectDeficit() {
        val estimate = energy ?: return
        val step = NutritionEngine.DEFICIT_STEP
        val content = DialogCalorieReductionBinding.inflate(LayoutInflater.from(context))
        val wheel = content.reductionWheel.apply {
            minValue = 0
            maxValue = NutritionEngine.MAX_DEFICIT / step
            displayedValues = Array(maxValue + 1) { whole((it * step).toDouble()) }
            value = (activeDeficit(estimate) ?: 0) / step
            wrapSelectorWheel = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            contentDescription = "کاهش کالری روزانه با گام ده کیلوکالری"
            val ink = context.getColor(R.color.growth_ink)
            if (Build.VERSION.SDK_INT >= 29) setTextColor(ink)
            for (index in 0 until childCount) (getChildAt(index) as? EditText)?.setTextColor(ink)
        }
        content.maintenance.text = "کالری حفظ وزن بر اساس میفلین: ${whole(estimate.mifflinDaily)} کیلوکالری در روز"
        fun applyReduction(overrideValue: Int?, target: Int) {
            deficitOverride = overrideValue
            setPlanTarget(target)
            showSuggestion()
        }

        val initialTarget = NutritionEngine.initialDeficit(patient, adult, estimate)
            ?.let { reductionTarget(estimate, it) }
        val builder = MaterialAlertDialogBuilder(context).setTitle("مقدار کاهش کالری روزانه")
            .setView(content.root)
            .setPositiveButton("انتخاب و اعمال") { _, _ ->
                val daily = wheel.value * step
                reductionTarget(estimate, daily)?.let { applyReduction(daily, it) }
            }
            .setNegativeButton("بازگشت", null)
        if (initialTarget != null) {
            builder.setNeutralButton("اعمال پیشنهاد اولیه") { _, _ -> applyReduction(null, initialTarget) }
        }
        val dialog = builder.show()
        val confirm = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        fun showPreview(value: Int) {
            val daily = value * step
            content.currentReduction.text = "${whole(daily.toDouble())} کیلوکالری کمتر در روز"
            val percent = NutritionEngine.weeklyWeightEnergyPercent(patient.weight, daily)
            content.weeklyEquivalent.text = percent?.let {
                "معادل انرژی در ۷ روز: حدود ${decimal(it, 2)}٪ وزن فعلی"
            }
            val target = reductionTarget(estimate, daily)
            content.reductionTargetPreview.text = if (target == null)
                "کالری هدف با این مقدار کاهش در محدودهٔ ابزار نیست. مقدار دیگری انتخاب کنید."
            else "کالری هدف پس از اعمال: ${whole(target.toDouble())} کیلوکالری در روز"
            confirm.isEnabled = target != null
        }
        showPreview(wheel.value)
        wheel.setOnValueChangedListener { _, _, value -> showPreview(value) }
    }

    private fun invalidatePlan() {
        plan = null
        mealAllocation = null
        binding.planResult.visibility = View.GONE
        binding.planError.text = ""
        updateSummary()
    }

    private fun updateSummary() {
        val actual = plan?.let(NutritionEngine::totals)
        val estimate = energy
        binding.nutritionSummary.text = when {
            care != Care.GENERAL -> "نیازمند ارزیابی اختصاصی تغذیه · ${care.label}"
            actual != null -> "پیش‌نویس برنامه · حدود ${whole(actual.calories)} کیلوکالری در روز"
            estimate != null -> "هریس–بندیکت ${whole(estimate.harrisDaily)} · میفلین ${whole(estimate.mifflinDaily)} کیلوکالری"
            else -> "برآورد کالری، واحدهای غذایی و الگوی وعده‌ها"
        }
    }

    private fun updateMacroTarget() {
        val target = readTarget()
        binding.carbPercent.text = if (target == null) {
            "با وارد کردن کالری هدف، گرم و درصد درشت‌مغذی‌ها نمایش داده می‌شود."
        } else {
            macroSummary(target)
        }
        val review = target?.let(::macroReview)
        binding.macroReview.text = review
        binding.macroReview.visibility = if (review == null) View.GONE else View.VISIBLE
    }

    private fun macroSummary(macros: NutritionEngine.Macros): String =
        NutritionEngine.macroShares(macros).joinToString("\n") {
            "${it.label} · ${decimal(it.percent)}٪ · ${decimal(it.grams)} گرم در روز"
        } + "\nپروتئین بر اساس وزن ثبت‌شده · ${decimal(macros.protein / patient.weight, 2)} گرم به‌ازای هر کیلوگرم"

    private fun macroReview(macros: NutritionEngine.Macros): String? {
        val outside = NutritionEngine.macroShares(macros).filter { it.outsideAdultRange }
        if (outside.isEmpty()) return null
        val ranges = outside.joinToString("، ") {
            "${it.label} ${whole(it.adultRange.first.toDouble())} تا ${whole(it.adultRange.last.toDouble())}٪"
        }
        return "خارج از بازهٔ عمومی DRI برای بزرگسال سالم · $ranges. سهم‌های انتخابی را با شرایط فرد تطبیق دهید."
    }

    private fun readTarget(showErrors: Boolean = false): NutritionEngine.Macros? {
        val kcal = read(binding.targetCalories)
        val protein = read(binding.proteinPercent)
        val fat = read(binding.fatPercent)
        if (kcal == null || kcal % 1 != 0.0 || kcal !in NutritionEngine.MIN_TARGET.toDouble()..NutritionEngine.MAX_TARGET.toDouble()) {
            invalid(binding.targetCalories, "عدد صحیح بین ۱۲۰۰ و ۶۰۰۰؛ محدودهٔ این ابزار", showErrors)
            return null
        }
        if (protein == null || protein <= 0 || protein >= 100) {
            invalid(binding.proteinPercent, "درصد پروتئین باید بیشتر از صفر و کمتر از ۱۰۰ باشد.", showErrors)
            return null
        }
        if (fat == null || fat <= 0 || protein + fat >= 100) {
            invalid(binding.fatPercent, "سهم چربی باید مثبت و مجموع چربی و پروتئین کمتر از ۱۰۰٪ باشد.", showErrors)
            return null
        }
        return NutritionEngine.target(kcal, protein, fat)
    }

    private fun calculate(showErrors: Boolean, storedAllocation: JSONObject? = null) {
        binding.planError.text = ""
        val target = readTarget(showErrors) ?: return
        val fixed = mutableMapOf<Group, Int>()
        for ((group, field) in groupFields) {
            val units = if (group == Group.SUGAR && field.text.isNullOrBlank()) 0.0 else read(field)
            val tenths = unitTenths(units)
            if (tenths == null) {
                return invalid(field, "سهم را از صفر تا ۱۰۰ با حداکثر یک رقم اعشار وارد کنید.", showErrors)
            }
            fixed[group] = tenths
        }

        val distribution = NutritionEngine.exchanges(target, fixed)
        if (distribution == null) {
            if (showErrors) binding.planError.text = "واحدهای واردشده از هدف دست‌کم یک درشت‌مغذی بیشترند. مقدارها یا هدف را بازبینی کنید."
            return
        }

        if (plan != distribution) mealAllocation = readAllocation(storedAllocation, distribution)
        plan = distribution
        if (showErrors) version = NutritionEngine.PLAN_VERSION
        val actual = NutritionEngine.totals(distribution)
        val difference = actual.calories - target.calories
        val sign = if (difference >= 0) "+" else "−"
        val totalsText = "برآورد از واحدهای نهایی: ${decimal(actual.calories)} کیلوکالری\n" +
            "هدف: ${decimal(target.calories)} · اختلاف: $sign${decimal(kotlin.math.abs(difference))}\n" +
            "پروتئین ${decimal(actual.protein)} / ${decimal(target.protein)} · " +
            "کربوهیدرات ${decimal(actual.carbs)} / ${decimal(target.carbs)} · " +
            "چربی ${decimal(actual.fat)} / ${decimal(target.fat)} گرم (برآورد / هدف)\n" +
            "پروتئین پیش‌نویس: ${decimal(actual.protein / patient.weight, 2)} گرم به‌ازای هر کیلوگرم وزن ثبت‌شده"
        binding.planTotals.text = listOfNotNull(totalsText, macroReview(actual)).joinToString("\n")
        binding.exchangeView.text = distribution.entries.sortedBy { it.key.ordinal }.joinToString("\n") { (group, tenths) ->
            "${group.label}: ${unitNumber(tenths)} واحد در روز"
        }
        binding.planResult.visibility = View.VISIBLE
        updateSummary()
        showMode()
    }

    private fun showMode() {
        val teal = context.getColor(R.color.growth_teal)
        val ink = context.getColor(R.color.growth_ink)
        for ((button, selected) in listOf(binding.dailyMode to !mealMode, binding.mealsMode to mealMode)) {
            button.backgroundTintList = ColorStateList.valueOf(if (selected) teal else Color.WHITE)
            button.strokeColor = ColorStateList.valueOf(teal)
            button.setTextColor(if (selected) Color.WHITE else ink)
            button.isSelected = selected
        }
        binding.exchangeView.visibility = if (mealMode) View.GONE else View.VISIBLE
        binding.mealView.visibility = if (mealMode) View.VISIBLE else View.GONE
        if (mealMode) renderMeals()
    }

    private fun selectTemplate() {
        val options = NutritionEngine.Template.values()
        MaterialAlertDialogBuilder(context).setTitle("تقسیم واحدهای روزانه بین وعده‌ها")
            .setSingleChoiceItems(options.map { it.label }.toTypedArray(), template.ordinal) { dialog, index ->
                if (template != options[index]) {
                    template = options[index]
                    version = NutritionEngine.PLAN_VERSION
                    mealAllocation = null
                    renderMeals()
                }
                dialog.dismiss()
            }.setNegativeButton("بازگشت", null).show()
    }

    private fun renderMeals() {
        val current = plan ?: return
        val allocation = mealAllocation ?: NutritionEngine.split(current, template, version)
        binding.templateSelect.text = "تقسیم در ${template.label}"
        binding.resetMealSplit.visibility = if (mealAllocation == null) View.GONE else View.VISIBLE
        binding.mealRows.removeAllViews()
        for ((meal, units) in allocation) {
            val row = ItemNutritionMealBinding.inflate(LayoutInflater.from(context), binding.mealRows, true)
            val summary = units.entries.filter { it.value > 0 }.sortedBy { it.key.ordinal }
                .joinToString("\n") { "${unitNumber(it.value)} واحد ${it.key.label}" }
            row.mealTitle.text = "${meal.label} · ${decimal(NutritionEngine.totals(units).calories)} کیلوکالری"
            row.mealUnits.text = summary.ifBlank { "سهم غذایی برای این وعده در نظر گرفته نشده است." }
            row.moveUnits.visibility = if (units.values.any { it > 0 }) View.VISIBLE else View.GONE
            row.moveUnits.setOnClickListener { selectTransferGroup(meal, allocation) }
            row.mealInput.setText(notes.optString(meal.name))
            row.mealInput.doAfterTextChanged {
                notes.put(meal.name, it.toString())
            }
        }
    }

    private fun readAllocation(stored: JSONObject?, daily: Map<Group, Int>): Map<Meal, Map<Group, Int>>? {
        if (stored == null || stored.length() != template.meals.size) return null
        val allocation = template.meals.associateWith { meal ->
            val storedMeal = stored.optJSONObject(meal.name) ?: return null
            daily.keys.associateWith { group ->
                val count = storedMeal.optInt(group.name, 0)
                if (count !in 0..daily.getValue(group)) return null
                count
            }
        }
        return allocation.takeIf { daily.all { (group, count) -> allocation.values.sumOf { it.getValue(group) } == count } }
    }

    private fun selectTransferGroup(from: Meal, allocation: Map<Meal, Map<Group, Int>>) {
        val groups = allocation.getValue(from).filterValues { it > 0 }.entries.sortedBy { it.key.ordinal }
        MaterialAlertDialogBuilder(context).setTitle("انتقال سهم از ${from.label}")
            .setItems(groups.map { "${it.key.label} · ${unitNumber(it.value)} واحد" }.toTypedArray()) { _, index ->
                val (group, available) = groups[index]
                selectTransferAmount(from, group, available, allocation)
            }.setNegativeButton("بازگشت", null).show()
    }

    private fun selectTransferAmount(from: Meal, group: Group, available: Int, allocation: Map<Meal, Map<Group, Int>>) {
        val transfer = DialogNutritionTransferBinding.inflate(LayoutInflater.from(context))
        val destinations = template.meals.filter { it != from }
        transfer.transferLimit.text = "از ${from.label} می‌توان تا ${unitNumber(available)} واحد منتقل کرد."
        transfer.destination.adapter = ArrayAdapter(
            context, android.R.layout.simple_spinner_item, destinations.map { it.label }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val dialog = MaterialAlertDialogBuilder(context).setTitle("انتقال سهم ${group.label}")
            .setView(transfer.root)
            .setPositiveButton("انتقال", null)
            .setNegativeButton("بازگشت", null).show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val count = unitTenths(read(transfer.amount))
            val to = destinations.getOrNull(transfer.destination.selectedItemPosition)
            val updated = if (count != null && to != null)
                NutritionEngine.moveUnits(allocation, from, to, group, count) else null
            if (updated == null) {
                transfer.amount.error = "مقدار مثبت تا ${unitNumber(available)} واحد وارد کنید."
                return@setOnClickListener
            }
            mealAllocation = updated
            renderMeals()
            dialog.dismiss()
        }
    }

    private fun choosePlanPreview() {
        val daily = plan ?: return
        val target = readTarget() ?: return
        val allocation = mealAllocation ?: NutritionEngine.split(daily, template, version)
        val mealNotes = template.meals.associateWith { notes.optString(it.name) }
        val patientNote = binding.nutritionPatientNote.text.toString()
        val titles = arrayOf("نسخهٔ مراجعه‌کننده", "نسخهٔ متخصص")
        MaterialAlertDialogBuilder(context).setTitle("پیش‌نمایش برنامهٔ غذایی")
            .setItems(titles) { _, index ->
                val details = if (index == 1) professionalDetails(target, NutritionEngine.totals(daily)) else null
                val report = NutritionPlanReport.format(target.calories, daily, allocation, mealNotes, patientNote, details)
                MaterialAlertDialogBuilder(context).setTitle(titles[index]).setMessage(report)
                    .setPositiveButton("اشتراک‌گذاری") { _, _ ->
                        val message = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(message, "اشتراک برنامهٔ غذایی"))
                    }
                    .setNegativeButton("بستن", null)
                    .show()
            }
            .setNegativeButton("بازگشت", null)
            .show()
    }

    private fun professionalDetails(target: NutritionEngine.Macros, actual: NutritionEngine.Macros): String = buildString {
        if (patient.name.isNotBlank()) appendLine("مراجعه‌کننده · ${patient.name}")
        energy?.let { estimate ->
            appendLine("فعالیت · ${activity?.label} · ضریب ${decimal(estimate.factor, 3)}")
            appendLine("برآورد نگهدارنده · هریس–بندیکت ${whole(estimate.harrisDaily)} و میفلین–سن ژئور ${whole(estimate.mifflinDaily)} کیلوکالری")
        }
        val reason = binding.nutritionGoalReason.text.toString().trim()
        if (reason.isNotEmpty()) appendLine("دلیل انتخاب هدف · $reason")
        appendLine("درشت‌مغذی‌های هدف")
        appendLine(macroSummary(target))
        macroReview(target)?.let { appendLine(it) }
        appendLine("توزیع برآوردی برنامه")
        appendLine(macroSummary(actual))
        macroReview(actual)?.let { appendLine(it) }
    }.trimEnd()

    private fun showUnitsGuide() {
        val details = Group.values().joinToString("\n\n") { group ->
            val macro = group.macros
            "${group.label} · ${group.householdExample}\n" +
                "هر واحد: ${whole(macro.protein)} گرم پروتئین، ${whole(macro.carbs)} گرم کربوهیدرات، ${whole(macro.fat)} گرم چربی"
        }
        MaterialAlertDialogBuilder(context).setTitle("اندازهٔ تقریبی یک واحد")
            .setMessage(details)
            .setPositiveButton("متوجه شدم", null)
            .show()
    }

    private fun invalid(field: TextInputEditText, message: String, show: Boolean) {
        if (show) {
            field.error = message
            field.requestFocus()
        }
    }

    private fun whole(number: Double) = NutritionEngine.whole(number)

    private fun unitNumber(tenths: Int): String = decimal(tenths.toDouble() / NutritionEngine.UNIT_SCALE)

    private fun unitTenths(value: Double?): Int? {
        if (value == null || value !in 0.0..100.0) return null
        val scaled = value * NutritionEngine.UNIT_SCALE
        val rounded = scaled.roundToInt()
        return rounded.takeIf { abs(scaled - it) < 1e-8 }
    }

    private fun decimal(number: Double, precision: Int = 1): String =
        NutritionEngine.decimal(number, precision)

    private fun read(field: TextInputEditText): Double? = field.text.toString().trim()
        .map { c -> when (c) {
            in '۰'..'۹' -> '0' + (c - '۰')
            in '٠'..'٩' -> '0' + (c - '٠')
            '٫', ',' -> '.'
            else -> c
        } }.joinToString("").toDoubleOrNull()?.takeIf { it.isFinite() }
}
