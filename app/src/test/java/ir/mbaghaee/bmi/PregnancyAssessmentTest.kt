package ir.mbaghaee.bmi

import org.junit.Assert.*
import org.junit.Test

class PregnancyAssessmentTest {
 private fun patient(gain:Double=5.0,gestation:Int=140,plurality:Int=1,episode:String="p1")=
  Patient("نمونه",2,10000,60.0+gain,170.0,pregnant=true,pregnancy=PregnancyInfo(60.0,gestation,plurality,episode))
 @Test fun totalTargetsUsePrePregnancyBmi(){
  for((bmi,range) in listOf(18.49 to (12.5 to 18.0),18.5 to (11.5 to 16.0),25.0 to (7.0 to 11.5),30.0 to (5.0 to 9.0))){
   assertEquals(range,PregnancyAssessment.totalRange(bmi,1))
  }
  assertEquals(16.8 to 24.5,PregnancyAssessment.totalRange(22.0,2))
  assertNull(PregnancyAssessment.totalRange(22.0,3))
 }
 @Test fun currentWeightDoesNotChangeTheBaselineBmiOrGoal(){
  val low=PregnancyAssessment.evaluate(patient(gain=2.0))!!
  val high=PregnancyAssessment.evaluate(patient(gain=20.0))!!
  assertEquals(low.preBmi,high.preBmi,1e-10);assertEquals(low.totalLow,high.totalLow);assertEquals(low.totalHigh,high.totalHigh)
  assertEquals(PregnancyAssessment.BELOW,low.status);assertEquals(PregnancyAssessment.ABOVE,high.status)
 }
 @Test fun interpolatedWeekRangeMeetsPublishedTermEndpoints(){
  for(total in listOf(12.5 to 18.0,11.5 to 16.0,7.0 to 11.5,5.0 to 9.0)){
   val end=PregnancyAssessment.weeklyRange(280,total)!!
   assertEquals(total.first,end.first,1e-10);assertEquals(total.second,end.second,1e-10)
  }
  assertNull(PregnancyAssessment.weeklyRange(97,11.5 to 16.0))
  assertEquals(.5 to 2.0,PregnancyAssessment.weeklyRange(98,11.5 to 16.0))
  assertNull(PregnancyAssessment.weeklyRange(281,11.5 to 16.0))
  val mid=PregnancyAssessment.weeklyRange(140,11.5 to 16.0)!!
  assertEquals(.5+11*6.0/26,mid.first,1e-10);assertEquals(2+14*6.0/26,mid.second,1e-10)
 }
 @Test fun firstTrimesterAndMultiplesDoNotGetAFalseWeeklyClassification(){
  val early=PregnancyAssessment.evaluate(patient(gain=0.0,gestation=97))!!
  assertEquals(PregnancyAssessment.FIRST,early.status);assertNull(early.weekLow)
  val twin=PregnancyAssessment.evaluate(patient(plurality=2))!!
  assertEquals(PregnancyAssessment.INDIVIDUAL,twin.status);assertNull(twin.weekLow);assertNotNull(twin.totalLow)
  val more=PregnancyAssessment.evaluate(patient(plurality=3))!!
  assertNull(more.totalLow);assertNull(more.weekLow)
  assertEquals(PregnancyAssessment.BELOW,PregnancyAssessment.evaluate(patient(gain=0.0,gestation=98))!!.status)
  assertTrue(PregnancyAssessment.canRecord(2,6200))
  assertFalse(PregnancyAssessment.canRecord(1,6200))
  val adolescent=PregnancyAssessment.evaluate(patient().copy(days=6200))!!
  assertEquals(PregnancyAssessment.INDIVIDUAL,adolescent.status);assertNull(adolescent.totalLow)
 }
 @Test fun graphIncludesOnlyTheSamePregnancyAndAllowsWeightLoss(){
  val p=patient(gain=-1.0)
  val same=p.copy(days=9900,weight=58.0,pregnancy=p.pregnancy!!.copy(gestationDays=133))
  val other=p.copy(days=9000,pregnancy=p.pregnancy!!.copy(episodeId="older-pregnancy"))
  val plot=PregnancyAssessment.plot(p,PregnancyAssessment.evaluate(p)!!,listOf(ChartVisit(10,same),ChartVisit(11,other)),visitId=12)!!
  assertEquals(1,plot.history.size);assertEquals(-2.0,plot.history.first().y,1e-10)
  assertEquals(-1.0,plot.point.y,1e-10);assertNull(plot.minYValue);assertTrue(plot.gestationalWeekAxis)
  assertEquals(10L,plot.history.single().visitId)
  assertEquals(12L,plot.point.visitId)
  assertEquals(14.0,plot.envelope!!.lower.first().x,1e-10);assertTrue(plot.curves.isEmpty())
 }
 @Test fun followUpAdvancesGestationButNotPastTheSamePregnancy(){
  val before=PregnancyInfo(60.0,140,1,"episode")
  val after=PregnancyAssessment.advance(before,7)!!
  assertEquals(147,after.gestationDays);assertEquals(before.episodeId,after.episodeId)
  assertEquals(before.preWeight,after.preWeight,0.0)
  assertNull(PregnancyAssessment.advance(before,-1));assertNull(PregnancyAssessment.advance(before,200))
 }
 @Test(expected=IllegalArgumentException::class) fun invalidGestationalDaysRejected(){PregnancyAssessment.evaluate(patient(gestation=301))}
 @Test(expected=IllegalArgumentException::class) fun invalidPluralityRejected(){PregnancyAssessment.evaluate(patient(plurality=0))}
 @Test fun nonPregnantPatientGetsNoPregnancyAssessment(){assertNull(PregnancyAssessment.evaluate(patient().copy(pregnant=false,pregnancy=null)))}
 @Test fun higherOrderGuidesAreForTotalGainAndNeverWeeklyDiagnosis(){
  for((count,pounds) in listOf(3 to (50.0 to 60.0),4 to (50.0 to 75.0),5 to (65.0 to 100.0))){
   val p=patient(gain=2.0,plurality=3).let{it.copy(pregnancy=it.pregnancy!!.copy(fetusCount=count))}
   val e=PregnancyAssessment.evaluate(p)!!
   assertEquals(pounds.first*.45359237,e.totalLow!!,1e-10);assertEquals(pounds.second*.45359237,e.totalHigh!!,1e-10)
   assertNull(e.weekLow);assertEquals(PregnancyAssessment.INDIVIDUAL,e.status)
   assertTrue(e.guidance.contains("تیم مراقبت"));assertTrue(e.detail.contains("راهنمای حدودی"))
   assertNull(PregnancyAssessment.plot(p,e,emptyList())!!.envelope)
  }
  assertNull(HigherOrderPregnancyReference.totalRange(null));assertNull(HigherOrderPregnancyReference.totalRange(6))
 }
 @Test(expected=IllegalArgumentException::class) fun inconsistentFetusCountIsRejected(){
  val p=patient(plurality=2);PregnancyAssessment.evaluate(p.copy(pregnancy=p.pregnancy!!.copy(fetusCount=4)))
 }
}
