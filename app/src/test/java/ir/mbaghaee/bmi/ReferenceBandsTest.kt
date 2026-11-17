package ir.mbaghaee.bmi

import org.junit.Assert.*
import org.junit.Test

class ReferenceBandsTest {
 private fun tone(indicator:String,z:Double)=ReferenceBands.forIndicator(indicator).sortedBy{it.tone.ordinal}.firstOrNull{
  val lower=it.lowerZ;val upper=it.upperZ
  (lower==null||z>=lower)&&(upper==null||z<=upper)
 }?.tone
 @Test fun clinicalBmiGaugeRemainsIndependentOfPercentileBackground(){
  assertEquals(BandTone.NORMAL,tone("bmi2007",1.5))
  assertEquals(2,BmiGaugeRanges.band(1.5,false,false))
  assertEquals(3,BmiGaugeRanges.band(2.5,false,false))
  assertTrue(ReferenceBands.legend("bmi2007").contains("مرزهای بالینی"))
 }
 @Test fun allEightReferenceIndicatorsHaveSymmetricPercentileBands(){
  for(indicator in listOf("bmi2007","bmianthro","wflanthro","wfhanthro","lenanthro","hfa2007","hcanthro","weianthro")){
   for(z in listOf(-2.0,0.0,1.5,2.0))assertEquals(BandTone.NORMAL,tone(indicator,z))
   for(z in listOf(-3.0,-2.00001,2.00001,2.5,3.0))assertEquals(BandTone.CAUTION,tone(indicator,z))
   for(z in listOf(-3.00001,3.00001,4.0))assertEquals(BandTone.ALERT,tone(indicator,z))
   for(z in listOf(.5,1.5,2.5,3.5))assertEquals(tone(indicator,-z),tone(indicator,z))
  }
 }
 @Test fun lowWeightBoundariesPreserveStrictWhoCutoffs(){
  for(indicator in listOf("bmi2007","wflanthro","wfhanthro","weianthro")){
   assertEquals(BandTone.NORMAL,tone(indicator,-2.0))
   assertEquals(BandTone.CAUTION,tone(indicator,-2.00001))
   assertEquals(BandTone.CAUTION,tone(indicator,-3.0))
   assertEquals(BandTone.ALERT,tone(indicator,-3.00001))
  }
 }
 @Test fun weightForAgeLegendDistinguishesPercentileColoursFromDiagnosis(){
  val legend=ReferenceBands.legend("weianthro")
  assertTrue(legend.contains("جایگاه صدکی"));assertTrue(legend.contains("به‌تنهایی به معنی اضافه‌وزن یا چاقی نیست"))
  assertFalse(ReferenceBands.forIndicator("weianthro").any{it.tone==BandTone.NEUTRAL})
 }
 @Test fun headCircumferenceUsesTheSameVisualPercentileLimits(){
  for(z in listOf(-2.0,0.0,2.0))assertEquals(BandTone.NORMAL,tone("hcanthro",z))
  for(z in listOf(-2.00001,2.00001))assertEquals(BandTone.CAUTION,tone("hcanthro",z))
  for(z in listOf(-3.00001,3.00001))assertEquals(BandTone.ALERT,tone("hcanthro",z))
 }
 @Test fun bandsCoverTheWholePlotWithoutGapsOrOverlaps(){
  for(indicator in listOf("bmi2007","bmianthro","wflanthro","wfhanthro","lenanthro","hfa2007","hcanthro","weianthro")){
   val bands=ReferenceBands.forIndicator(indicator)
   assertNull(bands.first().lowerZ);assertNull(bands.last().upperZ)
   assertTrue(bands.zipWithNext().all{(a,b)->a.upperZ==b.lowerZ})
  }
 }
 @Test fun unknownIndicatorDoesNotGetInventedBands(){assertTrue(ReferenceBands.forIndicator("unknown").isEmpty())}
}
