package ir.mbaghaee.bmi

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedAssessmentTest {
 @Test fun preservesPatientAndOriginalAssessment(){
  val p=Patient("نمونه",2,3653,30.5,135.2,head=50.0,waist=60.0)
  val a=Assessment(16.7,"WHO 2007",listOf(Metric("قد نسبت به سن","در محدوده مرجع","توضیح",-.5,0,"hfa2007")),listOf("متن اصلی"),.25,false)
  val saved=SavedAssessment(p,a,100000L,100000L+3653L*86400000)
  val restored=SavedAssessment.decode(saved.encode(42L,"۱۴۰۵/۰۸/۲۵","گزارش اصلی"))!!
  assertEquals(saved,restored)
  assertEquals("گزارش اصلی",saved.encode(42L,"date","گزارش اصلی").getString("report"))
 }
 @Test fun nullableMeasurementsAndScoresStayMissing(){
  val saved=SavedAssessment(Patient("",1,30,4.0,55.0,premature=true),Assessment(13.2,"نارس",listOf(Metric("رشد","نیاز به بررسی","")),emptyList(),null,false),0,30L*86400000)
  assertEquals(saved,SavedAssessment.decode(saved.encode(7L,"","")))
 }
 @Test fun legacyTextDoesNotInventClinicalInputs(){assertNull(SavedAssessment.decode(JSONObject().put("report","old text").put("name","نمونه")))}
 @Test fun incompleteStructuredRecordDoesNotCrash(){assertNull(SavedAssessment.decode(JSONObject().put("version",2)))}
}
