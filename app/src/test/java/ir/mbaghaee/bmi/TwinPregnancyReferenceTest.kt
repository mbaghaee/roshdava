package ir.mbaghaee.bmi

import org.junit.Assert.*
import org.junit.Test

class TwinPregnancyReferenceTest {
 @Test fun chartEndpointsMatchThePublishedTwinPoundGoals(){
  for((bmi,low,high) in listOf(Triple(18.49,50.0,62.0),Triple(18.5,37.0,54.0),Triple(25.0,31.0,50.0),Triple(30.0,25.0,42.0))){
   val band=TwinPregnancyReference.envelope(bmi)
   assertEquals(low*.45359237,band.lower.last().y,1e-8)
   assertEquals(high*.45359237,band.upper.last().y,1e-8)
   assertEquals(5.0,band.lower.first().x,0.0);assertEquals(0.0,band.lower.first().y,0.0)
   assertTrue(band.lower.zip(band.upper).drop(1).all{(a,b)->a.x==b.x&&a.y<b.y})
  }
 }
 @Test fun underweightDomainStopsAt38AndOtherChartsAt40(){
  assertEquals(38,TwinPregnancyReference.lastWeek(18.49));assertEquals(40,TwinPregnancyReference.lastWeek(18.5))
  assertNotNull(TwinPregnancyReference.range(18.49,266));assertNull(TwinPregnancyReference.range(18.49,267))
  assertNotNull(TwinPregnancyReference.range(22.0,280));assertNull(TwinPregnancyReference.range(22.0,281))
  assertNull(TwinPregnancyReference.range(22.0,35));assertNull(TwinPregnancyReference.range(22.0,28))
 }
 @Test fun dayFractionsInterpolateOnlyInsideTheTracedBand(){
  val a=TwinPregnancyReference.range(22.0,140)!!;val b=TwinPregnancyReference.range(22.0,147)!!
  val fraction=TwinPregnancyReference.range(22.0,143)!!
  assertEquals(a.first+(b.first-a.first)*3/7,fraction.first,1e-10)
  assertEquals(a.second+(b.second-a.second)*3/7,fraction.second,1e-10)
 }
 @Test fun twinPlotHasItsOwnBandOnTheFirstMeasurement(){
  val p=Patient("نمونه",2,10000,68.0,170.0,pregnant=true,pregnancy=PregnancyInfo(60.0,140,2,"twin"))
  val evaluation=PregnancyAssessment.evaluate(p)!!
  val plot=PregnancyAssessment.plot(p,evaluation,emptyList())!!
  assertTrue(plot.history.isEmpty());assertNotNull(plot.envelope)
  assertEquals(TwinPregnancyReference.envelope(evaluation.preBmi),plot.envelope)
  assertNotEquals(PregnancyAssessment.VERSION,evaluation.referenceVersion)
  assertEquals(8.0,plot.point.y,0.0)
  val more=p.copy(pregnancy=p.pregnancy!!.copy(plurality=3))
  assertNull(PregnancyAssessment.plot(more,PregnancyAssessment.evaluate(more)!!,emptyList())!!.envelope)
 }
}
