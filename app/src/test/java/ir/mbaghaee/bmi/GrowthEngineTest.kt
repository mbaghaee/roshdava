package ir.mbaghaee.bmi
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class GrowthEngineTest {
 private val engine=GrowthEngine(listOf("bmianthro","lenanthro","weianthro","wfhanthro","wflanthro","hcanthro","bmi2007","hfa2007").associateWith{GrowthEngine.parse(File("src/main/assets/$it.csv").readText())})
 private fun p(days:Int=3653,weight:Double=30.0,height:Double=135.0,sex:Int=1)=Patient("test",sex,days,weight,height)
 @Test fun whoPublishedUpperTail(){val a=engine.assess(p((132*30.4375).toInt(),67.5,150.0));assertEquals("چاق",a.metrics.first().status);assertEquals(3.35,a.score!!,.03)}
 @Test fun whoPublishedLowerTail(){val a=engine.assess(p((192*30.4375).toInt(),40.46,170.0));assertEquals("لاغری شدید",a.metrics.first().status);assertEquals(-3.8,a.score!!,.03)}
 @Test fun newbornAtMedian(){val a=engine.assess(p(0,3.3464,49.8842).copy(lying=true));assertEquals("در محدوده مرجع",a.metrics[0].status);assertEquals(0.0,a.metrics[1].z!!,1e-8);assertTrue(a.metrics[0].title.contains("قد"))}
 @Test fun everyReferenceChartHasAllSevenPercentileCurves(){
  for(patient in listOf(p(1400,15.0,100.0),p(3653,30.0,135.0))){
   for(metric in engine.assess(patient).metrics){
    val plot=engine.plotFor(patient,metric)?:continue
    assertEquals(listOf(-3,-2,-1,0,1,2,3),plot.curves.map{it.z})
    assertEquals(ReferenceBands.forIndicator(metric.indicator),plot.bands)
    assertTrue(plot.curves.all{it.points.size==81})
   }
  }
 }
 @Test fun noReferenceOutsideLengthRange(){val a=engine.assess(p(1,1.5,35.0).copy(lying=true));assertEquals("خارج از پوشش مرجع",a.metrics[0].status)}
 @Test fun standingLengthCorrection(){val a=engine.assess(p(300,9.0,70.0));val b=engine.assess(p(300,9.0,70.7).copy(lying=true));assertEquals(a.bmi,b.bmi,1e-8);assertEquals(a.metrics[1].z!!,b.metrics[1].z!!,1e-8)}
 @Test fun ageTransitionHasReference(){for(day in listOf(730,731,1826,1827,1856,1857,6939,6940)){val a=engine.assess(p(day,if(day<800)10.0 else if(day<2000)18.0 else 60.0,if(day<800)80.0 else if(day<2000)110.0 else 170.0));assertFalse("day $day",a.metrics.any{it.status=="خارج از پوشش مرجع"})}}
 @Test fun adultBoundaries(){for((bmi,label) in listOf(18.49 to "کمبود وزن",18.5 to "در محدوده مرجع",25.0 to "اضافه‌وزن",30.0 to "چاقی درجه یک",35.0 to "چاقی درجه دو",40.0 to "چاقی درجه سه")){val a=engine.assess(p(30*365,bmi*4,200.0));assertEquals(label,a.metrics[0].status);assertNull(a.metrics[1].z)}}
 @Test fun specialContextsAvoidGenericDiagnosis(){assertEquals("نیاز به سن اصلاح‌شده",engine.assess(p(60,4.0,55.0).copy(premature=true)).metrics[0].status);assertTrue(engine.assess(p(10000,65.0,170.0,2).copy(pregnant=true)).metrics[0].status.contains("بارداری"))}
 @Test fun olderAdultsGetRelevantExplanation(){assertTrue(engine.assess(p(80*365,70.0,170.0)).notes.any{it.contains("سالمندی")})}
 @Test fun pregnancyUsesPrePregnancyBmiAndSuppressesOrdinaryBmiScale(){
  val patient=p(10000,65.0,170.0,2).copy(pregnant=true,pregnancy=PregnancyInfo(60.0,140,1,"episode"))
  val result=engine.assess(patient)
  assertEquals(PregnancyAssessment.WITHIN,result.metrics.first().status)
  assertEquals(60.0/(1.7*1.7),result.pregnancy!!.preBmi,1e-10)
  assertNull(result.score);assertFalse(result.metrics.any{it.indicator=="bmi2007"})
 }
 @Test fun waistInterpretationComplementsBmiForChildrenAndAdults(){
  for(patient in listOf(p(2200,22.0,120.0).copy(waist=66.0),p(10000,65.0,170.0).copy(waist=93.5))){
   val assessment=engine.assess(patient)
   assertEquals(WaistAssessment.INCREASED,assessment.metrics.first{it.indicator==WaistAssessment.INDICATOR}.status)
   assertNotEquals(WaistAssessment.INDICATOR,assessment.metrics.first().indicator)
  }
  val invalid=engine.assess(p(2200,150.0,100.0).copy(waist=45.0))
  assertEquals(WaistAssessment.UNCLASSIFIED,invalid.metrics.first{it.indicator==WaistAssessment.INDICATOR}.status)
 }
 @Test fun implausibleDoesNotBecomeHealthy(){assertEquals("اندازه‌گیری را بازبینی کنید",engine.assess(p(2200,150.0,100.0)).metrics[0].status)}
 @Test(expected=IllegalArgumentException::class) fun zeroWeightRejected(){engine.assess(p(weight=0.0))}
 @Test(expected=IllegalArgumentException::class) fun nanRejected(){engine.assess(p(weight=Double.NaN))}
 @Test(expected=IllegalArgumentException::class) fun futureBirthRejected(){engine.assess(p(days=-1))}
}
