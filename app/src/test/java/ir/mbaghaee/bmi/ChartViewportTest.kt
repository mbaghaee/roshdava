package ir.mbaghaee.bmi

import org.junit.Assert.*
import org.junit.Test

class ChartViewportTest {
 private fun plot()=ReferencePlot("سن","وزن",0.0,10.0,listOf(ReferenceCurve(0,listOf(PlotPoint(0.0,10.0),PlotPoint(10.0,20.0)))),
  PlotPoint(9.0,19.0,90),"",listOf(PlotPoint(2.0,12.0,20),PlotPoint(5.0,15.0,50)))
 @Test fun croppingInterpolatesOnlyReferenceBoundariesAndHidesExcludedCurrent(){
  val cropped=ChartViewport.slice(plot(),3.0,7.0)
  assertEquals(listOf(PlotPoint(3.0,13.0),PlotPoint(7.0,17.0)),cropped.curves.single().points)
  assertFalse(cropped.showCurrentPoint);assertEquals(listOf(PlotPoint(5.0,15.0,50)),ChartViewport.points(cropped))
 }
 @Test fun timeFilterDoesNotInventMeasurements(){
  val cropped=ChartViewport.slice(plot(),0.0,10.0,60)
  assertEquals(listOf(plot().point),ChartViewport.points(cropped));assertEquals(plot().curves,cropped.curves)
 }
 @Test fun pregnancyBandIsClippedWithoutClampingNegativeGain(){
  val original=plot().copy(curves=emptyList(),point=PlotPoint(4.0,-1.0,40),minYValue=null,
   envelope=ReferenceEnvelope(listOf(PlotPoint(0.0,0.0),PlotPoint(10.0,10.0)),listOf(PlotPoint(0.0,2.0),PlotPoint(10.0,20.0))))
  val cropped=ChartViewport.slice(original,3.0,7.0)
  assertEquals(-1.0,cropped.point.y,0.0);assertNull(cropped.minYValue)
  assertEquals(3.0,cropped.envelope!!.lower.first().x,0.0);assertEquals(7.0,cropped.envelope!!.upper.last().x,0.0)
 }
}
