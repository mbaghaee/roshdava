package ir.mbaghaee.bmi

import org.junit.Assert.*
import org.junit.Test

class MeasurementReviewTest {
 private fun saved(day:Int,weight:Double=60.0,height:Double=170.0,indicator:String="bmi2007",z:Double=0.0):SavedAssessment {
  val p=Patient("نمونه",2,day,weight,height)
  val a=Assessment(weight/2.89,"",listOf(Metric("BMI نسبت به سن","","",z,indicator=indicator)),emptyList(),null,false)
  return SavedAssessment(p,a,0,day*86400000L)
 }
 @Test fun previousUsesOnlyTheSamePersonAndEarlierDate(){
  val current=saved(10000)
  val correct=saved(9990)
  assertEquals(correct,MeasurementReview.previous(current,listOf(saved(10010),saved(10000),saved(9980),correct,saved(9999).copy(birthUtc=1))))
 }
 @Test fun summaryHasElapsedDaysUnitsAndComparablePercentiles(){
  val text=MeasurementReview.summary(saved(10000,60.6,171.2,z=1.0),saved(9955))
  assertTrue(text.contains("۴۵ روز"));assertTrue(text.contains("۶۰۰ گرم"));assertTrue(text.contains("۱.۲"));assertTrue(text.contains("۸۴.۱"))
  val changed=MeasurementReview.summary(saved(10000,indicator="hfa2007"),saved(9955))
  assertFalse(changed.contains(": صدک قبلی"))
 }
 @Test fun entryWarningsAreSpecificAndDoNotChangeMeasurements(){
  val old=Patient("",2,10000,60.0,170.0)
  val p=old.copy(days=10010,weight=80.0,height=168.0)
  assertEquals(2,MeasurementReview.warnings(p,old).size)
  assertEquals(80.0,p.weight,0.0);assertEquals(168.0,p.height,0.0)
  assertTrue(MeasurementReview.warnings(old.copy(days=10010,weight=60.5,height=170.1),old).isEmpty())
  assertTrue(MeasurementReview.warnings(old.copy(days=10000,weight=80.0),old).isEmpty())
  assertTrue(MeasurementReview.warnings(old.copy(days=10010,lying=true,height=168.0),old).isEmpty())
 }
}
