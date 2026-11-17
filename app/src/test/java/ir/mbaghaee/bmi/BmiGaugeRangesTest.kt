package ir.mbaghaee.bmi

import org.junit.Assert.*
import org.junit.Test

class BmiGaugeRangesTest {
 @Test fun adultBandsMatchExistingBmiBoundaries(){
  for((value,band) in listOf(18.49 to 0,18.5 to 1,24.9999 to 1,25.0 to 2,29.9999 to 2,30.0 to 3)){
   assertEquals(band,BmiGaugeRanges.band(value,true,false))
  }
 }
 @Test fun childBoundaryEqualityRemainsInTheCorrectCategory(){
  assertEquals(1,BmiGaugeRanges.band(-2.0,false,true))
  assertEquals(1,BmiGaugeRanges.band(1.0,false,true))
  assertEquals(2,BmiGaugeRanges.band(1.0001,false,true))
  assertEquals(2,BmiGaugeRanges.band(2.0,false,true))
  assertEquals(3,BmiGaugeRanges.band(2.0001,false,true))
  assertEquals(3,BmiGaugeRanges.band(3.0,false,true))
  assertEquals(4,BmiGaugeRanges.band(3.0001,false,true))
  assertEquals(2,BmiGaugeRanges.band(2.0,false,false))
  assertEquals(3,BmiGaugeRanges.band(2.0001,false,false))
 }
 @Test fun calloutDoesNotRoundAcrossAClassificationBoundary(){
  assertEquals("24.9999",BmiGaugeRanges.number(24.9999,true,false))
  assertEquals("+1.0001",BmiGaugeRanges.number(1.0001,false,true))
  assertEquals("-2.0001",BmiGaugeRanges.number(-2.0001,false,false))
 }
 @Test fun everyScaleHasOneLabelPerBand(){
  for((adult,young) in listOf(true to false,false to true,false to false)){
   val cuts=BmiGaugeRanges.cuts(adult,young)
   assertTrue(cuts.zipWithNext().all{(a,b)->a<b})
   assertEquals(cuts.size-1,BmiGaugeRanges.labels(adult,young).size)
  }
 }
}
