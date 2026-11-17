package ir.mbaghaee.bmi

import org.junit.Assert.*
import org.junit.Test

class WaistAssessmentTest {
 private fun patient(ratio:Double=.45,days:Int=10000)=Patient("نمونه",2,days,60.0,200.0,waist=ratio*200)
 private fun status(ratio:Double)=WaistAssessment.metric(patient(ratio),22.0)!!.status
 @Test fun thresholdsUseUnroundedRatio(){
  assertEquals(WaistAssessment.REVIEW,status(.3999))
  assertEquals(WaistAssessment.NORMAL,status(.4))
  assertEquals(WaistAssessment.NORMAL,status(.4999))
  assertEquals(WaistAssessment.INCREASED,status(.5))
  assertEquals(WaistAssessment.INCREASED,status(.5999))
  assertEquals(WaistAssessment.HIGH,status(.6))
 }
 @Test fun displayedRatioDoesNotRoundIntoAnotherCategory(){
  assertEquals("0.3999",WaistAssessment.ratioNumber(.3999))
  assertEquals("0.4999",WaistAssessment.ratioNumber(.4999))
  assertEquals("0.5999",WaistAssessment.ratioNumber(.5999))
  assertEquals("0.600",WaistAssessment.ratioNumber(.6))
 }
 @Test fun missingWaistDoesNotInventAnAssessmentOrPoint(){
  val p=patient().copy(waist=null)
  assertNull(WaistAssessment.metric(p,22.0));assertNull(WaistAssessment.trend(p,emptyList(),true))
 }
 @Test fun ageFiveAndAdultBmiLimitAreAppliedSeparately(){
  assertEquals(WaistAssessment.UNCLASSIFIED,WaistAssessment.metric(patient(days=1826),22.0)!!.status)
  assertEquals(WaistAssessment.NORMAL,WaistAssessment.metric(patient(days=1827),22.0)!!.status)
  assertEquals(WaistAssessment.NORMAL,WaistAssessment.metric(patient(),34.999)!!.status)
  assertEquals(WaistAssessment.UNCLASSIFIED,WaistAssessment.metric(patient(),35.0)!!.status)
  assertEquals(WaistAssessment.NORMAL,WaistAssessment.metric(patient(days=6000),35.0)!!.status)
 }
 @Test fun pregnancyAndRestrictingConditionsSuppressClassification(){
  assertEquals(WaistAssessment.UNCLASSIFIED,WaistAssessment.metric(patient(.6).copy(pregnant=true),22.0)!!.status)
  assertEquals(WaistAssessment.UNCLASSIFIED,WaistAssessment.metric(patient(.6).copy(waistRestricted=true),22.0)!!.status)
  assertEquals(WaistAssessment.UNCLASSIFIED,WaistAssessment.metric(patient(.6,2200).copy(waistRestricted=true),22.0)!!.status)
  assertEquals(WaistAssessment.UNCLASSIFIED,WaistAssessment.metric(patient(days=2200),22.0,referenceBmiValid=false)!!.status)
 }
 @Test fun chartSkipsMissingValuesAndUsesEachVisitsHeight(){
  val p=patient(.5);val old=p.copy(days=9000,height=180.0,waist=90.0);val missing=p.copy(days=9500,waist=null)
  val ratio=WaistAssessment.trend(p,listOf(old,missing),true)!!
  assertEquals(1,ratio.history.size);assertEquals(.5,ratio.history.first().y,1e-10)
  assertEquals("سن · سال",ratio.xLabel);assertEquals(3,ratio.yDecimals)
  assertEquals(.01,ratio.minYPadding,1e-10);assertTrue(ratio.curves.isEmpty());assertTrue(ratio.bands.isEmpty())
  val waist=WaistAssessment.trend(p,listOf(old,missing),false)!!
  assertEquals(90.0,waist.history.first().y,1e-10);assertEquals(100.0,waist.point.y,1e-10)
 }
 @Test fun comparisonUsesClosestEarlierDayWithWaist(){
  val p=patient();val old=p.copy(days=9900,waist=95.0);val missing=p.copy(days=9950,waist=null);val future=p.copy(days=10100)
  assertEquals(old,WaistAssessment.previous(p,listOf(old,missing,future)))
  assertNull(WaistAssessment.previous(p,listOf(missing,future)))
 }
}
