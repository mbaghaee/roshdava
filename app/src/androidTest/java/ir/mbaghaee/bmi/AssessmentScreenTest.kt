package ir.mbaghaee.bmi

import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssessmentScreenTest {
 private lateinit var prefs:SharedPreferences
 private var oldRecords:String?=null
 private var oldDraft:String?=null
 @Before fun prepareForm(){
  prefs=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("growth_history",0)
  oldRecords=prefs.getString("records",null);oldDraft=prefs.getString("draft",null)
  val birth=1577836800000L
  val draft=JSONObject().put("name","نمونه").put("sex",2).put("selected",true).put("birth",birth).put("visit",birth+2100L*86400000)
   .put("weight","21").put("height","118").put("lying",false).put("pre",false).put("preg",false)
  check(prefs.edit().putString("records","[]").putString("draft",draft.toString()).commit())
 }
 @After fun restoreData(){
  val edit=prefs.edit()
  if(oldRecords==null)edit.remove("records") else edit.putString("records",oldRecords)
  if(oldDraft==null)edit.remove("draft") else edit.putString("draft",oldDraft)
  check(edit.commit())
 }
 private fun views(view:View):Sequence<View> = sequence{
  yield(view)
  if(view is ViewGroup)for(i in 0 until view.childCount)yieldAll(views(view.getChildAt(i)))
 }
 private fun text(activity:MainActivity,value:String)=views(activity.window.decorView).filterIsInstance<TextView>().first{it.text.toString()==value}
 private fun assertSelected(activity:MainActivity,title:String){
  for(label in listOf("فرم ارزیابی","سوابق سنجش","پشتیبان"))assertEquals(label,label==title,text(activity,label).isSelected)
 }
 @Test fun adultNutritionPreviewUsesLiveMacroTargetsWithoutWritingAVisit(){
  val form=JSONObject().put("name","نمونه").put("sex",2).put("approximateAge",true)
   .put("approximateYears","40").put("visit",1761955200000L).put("weight","75").put("height","165")
  check(prefs.edit().putString("draft",form.toString()).commit())
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   scenario.onActivity{activity->
    text(activity,"مشاهدهٔ ارزیابی رشد  ←").performClick()
    activity.findViewById<View>(R.id.nutrition_toggle).performClick()
    activity.findViewById<android.widget.EditText>(R.id.target_calories).setText("1800")
    activity.findViewById<android.widget.EditText>(R.id.protein_percent).setText("20")
    activity.findViewById<android.widget.EditText>(R.id.fat_percent).setText("30")
    val macros=activity.findViewById<TextView>(R.id.carb_percent).text.toString()
    assertTrue(macros.contains("پروتئین · ۲۰٪ · ۹۰ گرم"))
    assertTrue(macros.contains("۱٫۲ گرم به‌ازای هر کیلوگرم"))
    activity.findViewById<View>(R.id.plan_toggle).performClick()
    activity.findViewById<View>(R.id.calculate_plan).performClick()
    activity.findViewById<View>(R.id.preview_plan).performClick()
   }
   onView(withText("نسخهٔ مراجعه‌کننده")).perform(click())
   onView(withText("اشتراک‌گذاری")).check(matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
   assertEquals(0,JSONArray(prefs.getString("records","[]")).length())
  }
 }

 @Test fun adolescentPregnancyIsRecordedAndUsesIndividualCare(){
  val birth=946684800000L
  val form=JSONObject().put("name","نمونه").put("sex",2).put("selected",true)
   .put("birth",birth).put("visit",birth+6200L*86400000).put("weight","61").put("height","165")
   .put("preg",true).put("prePregnancyWeight","60").put("pregnancyWeek","13")
   .put("pregnancyDay","6").put("pregnancyPlurality",1)
  check(prefs.edit().putString("draft",form.toString()).commit())
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   scenario.onActivity{
    assertTrue(it.findViewById<View>(R.id.pregnant_checkbox).isShown)
    text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick()
    assertTrue(it.findViewById<View>(R.id.pregnancy_assessment).isShown)
    assertEquals(PregnancyAssessment.INDIVIDUAL,it.findViewById<TextView>(R.id.status_label).text.toString())
    assertFalse(it.findViewById<View>(R.id.nutrition_toggle).isShown)
    text(it,"ثبت در سوابق دستگاه").performClick()
   }
  }
  val saved=SavedAssessment.decode(JSONArray(prefs.getString("records","[]")).getJSONObject(0))!!
  assertTrue(saved.patient.pregnant)
  assertNull(saved.assessment.pregnancy!!.weekLow)
 }
 @Test fun unsavedResultAndChartsSurviveRecreationWithoutSaving(){
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   scenario.onActivity{text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick();assertSelected(it,"فرم ارزیابی")}
   scenario.recreate()
   scenario.onActivity{
    assertNotNull(text(it,"نمای روشن از وضعیت امروز"))
    assertTrue(views(it.window.decorView).any{v->v is ReferenceChartView})
    assertEquals(0,JSONArray(prefs.getString("records","[]")).length())
    text(it,"ثبت در سوابق دستگاه").performClick()
   }
   scenario.recreate()
   scenario.onActivity{
    text(it,"این سنجش قبلاً ثبت شده است").performClick()
    assertEquals(1,JSONArray(prefs.getString("records","[]")).length())
    text(it,"بازگشت و ویرایش اطلاعات").performClick()
    text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick()
    text(it,"این سنجش قبلاً ثبت شده است").performClick()
    assertEquals(1,JSONArray(prefs.getString("records","[]")).length())
   }
  }
 }
 @Test fun activeNavigationFollowsScreenAndRestores(){
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   scenario.onActivity{assertSelected(it,"فرم ارزیابی");text(it,"سوابق سنجش").performClick();assertSelected(it,"سوابق سنجش")}
   scenario.recreate()
   scenario.onActivity{assertSelected(it,"سوابق سنجش");text(it,"پشتیبان").performClick();assertSelected(it,"پشتیبان")}
   scenario.recreate()
   scenario.onActivity{assertSelected(it,"پشتیبان");text(it,"فرم ارزیابی").performClick();assertSelected(it,"فرم ارزیابی")}
  }
 }
 @Test fun approximateAdultAgeSurvivesSaveBackupAndFollowUpWithoutBirthDate(){
  val firstVisit=1761955200000L
  val form=JSONObject().put("name","نمونه سن تقریبی").put("sex",1).put("visit",firstVisit)
   .put("weight","78").put("height","170")
  check(prefs.edit().putString("draft",form.toString()).commit())
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   scenario.onActivity{
    it.findViewById<View>(R.id.approximate_age_checkbox).performClick()
    val age=it.findViewById<android.widget.EditText>(R.id.approximate_age_input)
    assertTrue(age.isShown)
    assertFalse(it.findViewById<View>(R.id.birth_button).isShown)
    age.setText("۱۸")
    text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick()
    assertTrue(age.isShown)
    assertNotNull(it.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.approximate_age_box).error)
    age.setText("۵۰")
    assertTrue(it.findViewById<View>(R.id.waist_input).isShown)
    text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick()
    assertTrue(it.findViewById<TextView>(R.id.age_sex).text.contains("حدود ۵۰ سال"))
   }
   scenario.recreate()
   scenario.onActivity{
    assertTrue(it.findViewById<TextView>(R.id.age_sex).text.contains("حدود ۵۰ سال"))
    text(it,"ثبت در سوابق دستگاه").performClick()
    text(it,"سوابق سنجش").performClick()
    text(it,"سنجش جدید برای همین فرد").performClick()
    assertFalse(it.findViewById<View>(R.id.birth_button).isShown)
    assertEquals("50",it.findViewById<android.widget.EditText>(R.id.approximate_age_input).text.toString())
   }
  }
  val records=JSONArray(prefs.getString("records","[]"))
  val draft=JSONObject(prefs.getString("draft","{}")!!)
  val saved=records.getJSONObject(0)
  assertFalse(saved.has("birthUtc"))
  assertFalse(draft.has("birth"))
  assertTrue(saved.getString("report").contains("حدود ۵۰ سال"))
  assertFalse(saved.getString("report").contains("تولد:"))
  val restored=BackupCodec.decode(BackupCodec.encode(records,draft))
  assertNull(SavedAssessment.decode(restored.records.getJSONObject(0))!!.birthUtc)
  assertEquals(50,AssessmentAge.approximateYears(SavedAssessment.decode(saved)!!.patient.days))
 }
 @Test fun adultCalorieTargetIsEditableBeforeApplyingAndIndependentOfDetails(){
  val birth=631152000000L
  val draft=JSONObject().put("name","نمونه تغذیه").put("sex",1).put("selected",true)
   .put("birth",birth).put("visit",birth+12784L*86400000).put("weight","117").put("height","180")
  check(prefs.edit().putString("draft",draft.toString()).commit())
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   scenario.onActivity{
    text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick()
    it.findViewById<View>(R.id.nutrition_toggle).performClick()
    val target=it.findViewById<android.widget.EditText>(R.id.target_calories)
    assertTrue(target.isShown)
    assertEquals("",target.text.toString())
    assertFalse(it.findViewById<View>(R.id.plan_inputs).isShown)
    assertFalse(it.findViewById<View>(R.id.apply_suggestion).isShown)
    target.setText("2100")
    it.findViewById<View>(R.id.activity_select).performClick()
   }
   onView(withText("کم‌تحرک · بیشتر روز نشسته")).perform(click())
   scenario.onActivity{
    val target=it.findViewById<android.widget.EditText>(R.id.target_calories)
    val proposal=it.findViewById<TextView>(R.id.suggestion_text)
    val details=it.findViewById<View>(R.id.plan_inputs)
    val apply=it.findViewById<View>(R.id.apply_suggestion)
    assertTrue(proposal.isShown)
    assertTrue(proposal.text.toString().startsWith("کالری هدف پیشنهادی: ۲۰۵۰ کیلوکالری در روز"))
    assertTrue(target.isShown)
    assertEquals("2100",target.text.toString())
    assertFalse(details.isShown)
    assertTrue(apply.isShown && apply.isEnabled)
    assertTrue(it.findViewById<TextView>(R.id.harris_energy).text.contains("با وزن فعلی ثبت‌شده، نه وزن ایده‌آل"))
    assertTrue(it.findViewById<TextView>(R.id.mifflin_energy).text.contains("مبنای پیشنهاد کالری رژیم"))
    it.findViewById<View>(R.id.deficit_picker).performClick()
   }
   onView(withId(R.id.reduction_target_preview)).check(matches(withText("کالری هدف پس از اعمال: ۲۰۵۰ کیلوکالری در روز")))
   onView(withText("انتخاب و اعمال")).perform(click())
   scenario.onActivity{
    val target=it.findViewById<android.widget.EditText>(R.id.target_calories)
    val details=it.findViewById<View>(R.id.plan_inputs)
    val apply=it.findViewById<TextView>(R.id.apply_suggestion)
    assertEquals("2050",target.text.toString())
    assertEquals("هدف تنظیم شد",apply.text.toString())
    assertFalse(apply.isEnabled)
    assertFalse(details.isShown)
    val toggle=it.findViewById<View>(R.id.plan_toggle)
    toggle.performClick()
    assertTrue(details.isShown)
    toggle.performClick()
    assertFalse(details.isShown)
    assertTrue(target.isShown)
    assertEquals("2050",target.text.toString())
   }
   scenario.recreate()
   scenario.onActivity{
    val target=it.findViewById<android.widget.EditText>(R.id.target_calories)
    assertTrue(target.isShown)
    assertEquals("2050",target.text.toString())
    assertFalse(it.findViewById<View>(R.id.plan_inputs).isShown)
   }
  }
 }
 @Test fun childWaistCardSurvivesRecreationAndHistory(){
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   scenario.onActivity{
    assertEquals(View.VISIBLE,it.findViewById<View>(R.id.waist_section).visibility)
    it.findViewById<android.widget.EditText>(R.id.waist_input).setText("24")
    text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick()
    assertNotNull(text(it,WaistAssessment.REVIEW))
    assertTrue(it.findViewById<WaistRatioGaugeView>(R.id.ratio_gauge).contentDescription.toString().contains(WaistAssessment.REVIEW))
    text(it,"بازگشت و ویرایش اطلاعات").performClick()
    it.findViewById<android.widget.EditText>(R.id.waist_input).setText("60")
    text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick()
    assertNotNull(text(it,WaistAssessment.INCREASED))
    val waistCard=it.findViewById<View>(R.id.waist_assessment)
    assertFalse(views(waistCard).filterIsInstance<TextView>().any{v->v.text.toString().startsWith("BMI:")})
    assertTrue(it.findViewById<WaistRatioGaugeView>(R.id.ratio_gauge).contentDescription.toString().contains(WaistAssessment.INCREASED))
    assertFalse(views(it.window.decorView).filterIsInstance<TextView>().any{v->v.text.toString().startsWith("نسبت ۰٫۴ تا کمتر")})
    assertEquals(0,it.findViewById<ViewGroup>(R.id.trends_container).childCount)
    assertTrue(it.findViewById<TextView>(R.id.trend_empty_notice).text.toString().contains("حداقل یک سنجش دیگر"))
   }
   scenario.recreate()
   scenario.onActivity{
    assertNotNull(text(it,WaistAssessment.INCREASED));text(it,"ثبت در سوابق دستگاه").performClick()
    val saved=SavedAssessment.decode(JSONArray(prefs.getString("records","[]")).getJSONObject(0))!!
    assertFalse(saved.patient.waistRestricted);assertEquals(60.0,saved.patient.waist!!,0.0)
    text(it,"سوابق سنجش").performClick();text(it,"مشاهدهٔ پرونده و سنجش‌ها").performClick()
    text(it,"مشاهدهٔ ارزیابی کامل و نمودارها").performClick()
    assertNotNull(text(it,WaistAssessment.INCREASED))
    assertEquals(View.VISIBLE,it.findViewById<View>(R.id.trend_empty_notice).visibility)
    assertSelected(it,"سوابق سنجش")
   }
  }
 }
 @Test fun singlePregnancyCheckboxAutomaticallyReplacesWaistInterpretation(){
  for(waist in listOf("","90")){
   val draft=JSONObject().put("name","نمونه بارداری").put("sex",2).put("selected",true).put("birth",788918400000L)
    .put("weight","65").put("height","170").put("waist",waist).put("preg",true).put("prePregnancyWeight","60")
    .put("pregnancyWeek","20").put("pregnancyDay","0").put("pregnancyPlurality",1).put("waistRestricted",true)
   check(prefs.edit().putString("records","[]").putString("draft",draft.toString()).commit())
   ActivityScenario.launch(MainActivity::class.java).use{scenario->
    scenario.onActivity{
     val checks=views(it.window.decorView).filterIsInstance<com.google.android.material.checkbox.MaterialCheckBox>().filter{v->v.text.contains("باردار")}.toList()
     assertEquals(1,checks.size)
     text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick()
     assertEquals(View.VISIBLE,it.findViewById<View>(R.id.waist_assessment).visibility)
     assertEquals(View.GONE,it.findViewById<View>(R.id.ratio_gauge).visibility)
     assertTrue(it.findViewById<TextView>(R.id.waist_detail).text.toString().contains("به دلیل بارداری"))
     assertEquals(if(waist.isBlank())View.GONE else View.VISIBLE,it.findViewById<View>(R.id.waist_value).visibility)
    }
    scenario.recreate()
    scenario.onActivity{
     assertEquals(View.GONE,it.findViewById<View>(R.id.ratio_gauge).visibility)
     text(it,"ثبت در سوابق دستگاه").performClick()
     val saved=SavedAssessment.decode(JSONArray(prefs.getString("records","[]")).getJSONObject(0))!!
     assertTrue(saved.patient.pregnant);assertFalse(saved.patient.waistRestricted)
     if(waist.isNotBlank())assertEquals(WaistAssessment.UNCLASSIFIED,saved.assessment.metrics.first{m->m.indicator==WaistAssessment.INDICATOR}.status)
     text(it,"سوابق سنجش").performClick();text(it,"مشاهدهٔ پرونده و سنجش‌ها").performClick()
     text(it,"مشاهدهٔ ارزیابی کامل و نمودارها").performClick()
     assertEquals(View.GONE,it.findViewById<View>(R.id.ratio_gauge).visibility)
     assertTrue(it.findViewById<TextView>(R.id.waist_detail).text.toString().contains("به دلیل بارداری"))
    }
   }
  }
 }
 @Test fun nextPregnancyIsStartedFromTheProfileAndDoesNotReplaceTheOldMeasurement(){
  val draft=JSONObject().put("name","نمونه بارداری").put("sex",2).put("selected",true).put("birth",788918400000L)
   .put("weight","65").put("height","170").put("preg",true).put("prePregnancyWeight","60")
   .put("pregnancyWeek","20").put("pregnancyDay","0").put("pregnancyPlurality",1)
  check(prefs.edit().putString("draft",draft.toString()).commit())
  var original="";var episode="";var profile=""
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   scenario.onActivity{
    assertNull(it.findViewById<View>(R.id.new_pregnancy_button))
    text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick();text(it,"ثبت در سوابق دستگاه").performClick()
    val record=JSONArray(prefs.getString("records","[]")).getJSONObject(0);original=record.toString();profile=record.getString("patientId")
    episode=SavedAssessment.decode(record)!!.patient.pregnancy!!.episodeId
    text(it,"سوابق سنجش").performClick();text(it,"مشاهدهٔ پرونده و سنجش‌ها").performClick()
    text(it,"شروع بارداری بعدی").performClick()
   }
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("انصراف")).perform(androidx.test.espresso.action.ViewActions.click())
   scenario.onActivity{
    assertEquals(original,JSONArray(prefs.getString("records","[]")).getJSONObject(0).toString())
    assertEquals(episode,JSONObject(prefs.getString("draft","{}")).getString("pregnancyId"))
    text(it,"شروع بارداری بعدی").performClick()
   }
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("آماده‌سازی فرم بارداری بعدی")).perform(androidx.test.espresso.action.ViewActions.click())
   scenario.onActivity{
    assertNull(it.findViewById<View>(R.id.new_pregnancy_button))
    assertEquals("",it.findViewById<android.widget.EditText>(R.id.pre_weight_input).text.toString())
    assertEquals("",it.findViewById<android.widget.EditText>(R.id.week_input).text.toString())
    assertEquals("",it.findViewById<android.widget.EditText>(R.id.weight_input).text.toString())
    assertTrue(it.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.pregnant_checkbox).isChecked)
    val currentDraft=JSONObject(prefs.getString("draft","{}"));assertEquals(profile,currentDraft.getString("patientId"));assertEquals("",currentDraft.getString("pregnancyId"))
    assertEquals(original,JSONArray(prefs.getString("records","[]")).getJSONObject(0).toString())
    it.findViewById<android.widget.EditText>(R.id.weight_input).setText("66")
    it.findViewById<android.widget.EditText>(R.id.height_input).setText("170")
    it.findViewById<android.widget.EditText>(R.id.pre_weight_input).setText("61")
    it.findViewById<android.widget.EditText>(R.id.week_input).setText("20")
    it.findViewById<View>(R.id.single_button).performClick()
    text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick();text(it,"ثبت در سوابق دستگاه").performClick()
    val records=JSONArray(prefs.getString("records","[]"));assertEquals(2,records.length())
    assertEquals(original,records.getJSONObject(0).toString())
    assertNotEquals(episode,SavedAssessment.decode(records.getJSONObject(1))!!.patient.pregnancy!!.episodeId)
   }
  }
 }
 @Test fun twinReferenceAndPrePregnancyBmiAreVisibleOnFirstResult(){
  val draft=JSONObject().put("name","نمونه دوقلو").put("sex",2).put("selected",true).put("birth",788918400000L)
   .put("weight","68").put("height","170").put("preg",true).put("prePregnancyWeight","60")
   .put("pregnancyWeek","20").put("pregnancyDay","0").put("pregnancyPlurality",2)
  check(prefs.edit().putString("draft",draft.toString()).commit())
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   scenario.onActivity{
    text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick()
    assertEquals(View.VISIBLE,it.findViewById<View>(R.id.bmi_scale).visibility)
    assertEquals(View.VISIBLE,it.findViewById<View>(R.id.gain_gauge).visibility)
    assertTrue(it.findViewById<TextView>(R.id.expected_label).text.toString().contains("دوقلویی CDC"))
   }
   InstrumentationRegistry.getInstrumentation().waitForIdleSync()
   scenario.onActivity{
    val card=it.findViewById<View>(R.id.pregnancy_assessment)
    val chart=card.findViewById<ReferenceChartView>(R.id.chart)
    assertTrue(chart.isShown);assertTrue(chart.width>0&&chart.height>0)
    val bitmap=android.graphics.Bitmap.createBitmap(chart.width,chart.height,android.graphics.Bitmap.Config.ARGB_8888)
    val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.WHITE);chart.draw(canvas)
    var green=0
    for(y in 0 until bitmap.height step 4)for(x in 0 until bitmap.width step 4){
     val colour=bitmap.getPixel(x,y);val r=android.graphics.Color.red(colour);val g=android.graphics.Color.green(colour);val b=android.graphics.Color.blue(colour)
     if(g-r>10&&kotlin.math.abs(g-b)<8)green++
    }
    bitmap.recycle();assertTrue("CDC band should be painted on the first draw",green>200)
   }
  }
 }
 @Test fun higherOrderResultKeepsTheCautionAndWholePregnancyContext(){
  val draft=JSONObject().put("name","نمونه چندقلو").put("sex",2).put("selected",true).put("birth",788918400000L)
   .put("weight","68").put("height","170").put("preg",true).put("prePregnancyWeight","60")
   .put("pregnancyWeek","20").put("pregnancyDay","0").put("pregnancyPlurality",3).put("fetusCount","4")
  check(prefs.edit().putString("draft",draft.toString()).commit())
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   scenario.onActivity{
    assertEquals("4",it.findViewById<android.widget.EditText>(R.id.fetus_count_input).text.toString())
    text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick()
    assertEquals(View.VISIBLE,it.findViewById<View>(R.id.gain_gauge).visibility)
    assertEquals(View.VISIBLE,it.findViewById<View>(R.id.higher_order_caution).visibility)
    assertTrue(it.findViewById<TextView>(R.id.higher_order_caution).text.toString().contains("تیم مراقبت"))
    assertTrue(it.findViewById<TextView>(R.id.expected_label).text.toString().contains("هدف این هفته نیست"))
   }
   scenario.recreate()
   scenario.onActivity{
    assertEquals(View.VISIBLE,it.findViewById<View>(R.id.higher_order_caution).visibility)
    text(it,"ثبت در سوابق دستگاه").performClick()
    val saved=SavedAssessment.decode(JSONArray(prefs.getString("records","[]")).getJSONObject(0))!!
    assertEquals(4,saved.patient.pregnancy!!.fetusCount)
    assertEquals(PregnancyAssessment.INDIVIDUAL,saved.assessment.pregnancy!!.status)
    assertTrue(saved.assessment.pregnancy!!.detail.contains("تیم مراقبت"))
   }
  }
 }
 @Test fun pregnancyInputsResultHistoryAndFollowUpStayTogether(){
  val local=java.util.Calendar.getInstance()
  val today=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply{
   clear();set(local.get(java.util.Calendar.YEAR),local.get(java.util.Calendar.MONTH),local.get(java.util.Calendar.DAY_OF_MONTH))
  }.timeInMillis
  val draft=JSONObject().put("name","نمونه بارداری").put("sex",2).put("selected",true).put("birth",788918400000L).put("visit",today)
   .put("weight","65").put("height","170").put("preg",false)
  check(prefs.edit().putString("draft",draft.toString()).commit())
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   scenario.onActivity{
    val pregnant=it.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.pregnant_checkbox)
    assertEquals(View.VISIBLE,pregnant.visibility);pregnant.isChecked=true
    assertEquals(View.VISIBLE,it.findViewById<View>(R.id.pregnancy_form).visibility)
    text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick()
    assertNotNull(it.findViewById<android.widget.EditText>(R.id.pre_weight_input).error)
    it.findViewById<android.widget.EditText>(R.id.pre_weight_input).setText("60")
    it.findViewById<android.widget.EditText>(R.id.week_input).setText("20")
    it.findViewById<android.widget.EditText>(R.id.day_input).setText("3")
    it.findViewById<View>(R.id.single_button).performClick()
    text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick()
    assertEquals(View.VISIBLE,it.findViewById<View>(R.id.pregnancy_assessment).visibility)
    assertEquals(View.VISIBLE,it.findViewById<View>(R.id.gain_gauge).visibility)
    assertEquals(View.VISIBLE,it.findViewById<View>(R.id.bmi_scale).visibility)
    assertTrue(it.findViewById<View>(R.id.bmi_scale).contentDescription.toString().contains("۲۰.۷۶"))
    assertTrue(it.findViewById<TextView>(R.id.bmi_label).text.toString().contains("پیش از بارداری"))
   }
   scenario.recreate()
   scenario.onActivity{
    assertEquals(View.VISIBLE,it.findViewById<View>(R.id.pregnancy_assessment).visibility)
    text(it,"ثبت در سوابق دستگاه").performClick()
    val saved=SavedAssessment.decode(JSONArray(prefs.getString("records","[]")).getJSONObject(0))!!
    assertEquals(143,saved.patient.pregnancy!!.gestationDays)
    assertEquals(60.0,saved.patient.pregnancy!!.preWeight,0.0)
    assertEquals(PregnancyAssessment.WITHIN,saved.assessment.pregnancy!!.status)
    text(it,"سوابق سنجش").performClick();text(it,"مشاهدهٔ پرونده و سنجش‌ها").performClick()
    text(it,"مشاهدهٔ ارزیابی کامل و نمودارها").performClick()
    assertEquals(View.VISIBLE,it.findViewById<View>(R.id.pregnancy_assessment).visibility)
    text(it,"سنجش جدید برای همین فرد").performClick()
    assertEquals("60.0",it.findViewById<android.widget.EditText>(R.id.pre_weight_input).text.toString())
    assertEquals("20",it.findViewById<android.widget.EditText>(R.id.week_input).text.toString())
    assertEquals("3",it.findViewById<android.widget.EditText>(R.id.day_input).text.toString())
   }
  }
 }
 @Test fun savedAssessmentIsEditedInPlaceFromHistory(){
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   scenario.onActivity{
    text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick()
    text(it,"ثبت در سوابق دستگاه").performClick()
    text(it,"سوابق سنجش").performClick()
    text(it,"مشاهدهٔ پرونده و سنجش‌ها").performClick()
    text(it,"مشاهدهٔ ارزیابی کامل و نمودارها").performClick()
    text(it,"ویرایش این سنجش").performClick()
    assertEquals("نمونه",it.findViewById<android.widget.EditText>(R.id.patient_name).text.toString())
    assertEquals("21",it.findViewById<android.widget.EditText>(R.id.weight_input).text.toString())
    assertEquals("118",it.findViewById<android.widget.EditText>(R.id.height_input).text.toString())
    it.findViewById<android.widget.EditText>(R.id.height_input).setText("119")
    text(it,"ثبت تغییرات و مشاهدهٔ ارزیابی  ←").performClick()
    assertNotNull(text(it,"ویرایش سنجش ثبت‌شده"))
    text(it,"ذخیرهٔ تغییرات این سنجش").performClick()
    assertNotNull(text(it,"ارزیابی ثبت‌شده"))
   }
   val records=JSONArray(prefs.getString("records","[]"))
   assertEquals(1,records.length())
   val saved=SavedAssessment.decode(records.getJSONObject(0))!!
   assertEquals(119.0,saved.patient.height,0.0)
   assertEquals(21.0,saved.patient.weight,0.0)
  }
 }
 @Test fun deletingAProfileCardRemovesEveryVisitAfterConfirmation(){
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   scenario.onActivity{
    text(it,"مشاهدهٔ ارزیابی رشد  ←").performClick()
    text(it,"ثبت در سوابق دستگاه").performClick()
    text(it,"سوابق سنجش").performClick()
    it.findViewById<View>(R.id.delete_profile_button).performClick()
    text(it,"حذف همه").performClick()
    assertNotNull(text(it,"هنوز ارزیابی ذخیره نشده"))
   }
   assertEquals(0,JSONArray(prefs.getString("records","[]")).length())
  }
 }
}
