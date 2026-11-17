package ir.mbaghaee.bmi

import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
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
}
