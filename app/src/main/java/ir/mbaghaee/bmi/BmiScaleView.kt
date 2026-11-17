package ir.mbaghaee.bmi

import android.content.Context
import android.util.AttributeSet

/** Numeric callout and a position marker for adult BMI or the child's existing growth score. */
class BmiScaleView @JvmOverloads constructor(context:Context,attrs:AttributeSet?=null):SegmentedGaugeView(context,attrs){
 private fun frame(value:Double,adult:Boolean,young:Boolean):GaugePresentation {
  val colours=if(!adult&&young)listOf(
   colour(R.color.bmi_gauge_underweight),colour(R.color.waist_gauge_normal),colour(R.color.waist_gauge_increased),
   colour(R.color.bmi_gauge_overweight),colour(R.color.waist_gauge_high)
  ) else listOf(colour(R.color.bmi_gauge_underweight),colour(R.color.waist_gauge_normal),colour(R.color.waist_gauge_increased),colour(R.color.waist_gauge_high))
  val selected=BmiGaugeRanges.band(value,adult,young)
  val marker=when(selected){
   1->colour(R.color.growth_normal_text)
   colours.lastIndex->colour(R.color.waist_gauge_high_ink)
   else->colour(R.color.waist_gauge_increased_ink)
  }
  val number=SolarDate.persian(BmiGaugeRanges.number(value,adult,young))
  return GaugePresentation(value,BmiGaugeRanges.cuts(adult,young),colours,BmiGaugeRanges.labels(adult,young),marker,
   "${if(adult)"BMI" else "Z"} · $number",selected,dark=true,showEndpointTicks=false)
 }
 fun setScore(value:Double,adult:Boolean,young:Boolean){
  if(!value.isFinite()){showGauge(null);contentDescription="جایگاه قابل نمایش نیست";return}
  showGauge(frame(value,adult,young))
  val number=SolarDate.persian(BmiGaugeRanges.number(value,adult,young))
  contentDescription=if(adult)"BMI: $number؛ عدد و نقطهٔ نشانگر روی نوار مرجع مشخص است."
   else "جایگاه استاندارد رشد Z: $number؛ صفر میانهٔ مرجع است. مقدار BMI در بالای کارت آمده است."
 }
 override fun previewGauge()=frame(23.8,true,false)
}
