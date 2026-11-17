package ir.mbaghaee.bmi

import android.content.Context
import android.util.AttributeSet
import kotlin.math.max
import kotlin.math.min

class PregnancyGainGaugeView @JvmOverloads constructor(context:Context,attrs:AttributeSet?=null):SegmentedGaugeView(context,attrs){
 private fun frame(e:PregnancyEvaluation,total:Boolean=false):GaugePresentation? {
  val low=(if(total)e.totalLow else e.weekLow)?:return null;val high=(if(total)e.totalHigh else e.weekHigh)?:return null
  if(high<=low)return null
  val pad=max(1.0,(high-low)*.5)
  val cuts=listOf(min(low-pad,e.gain-pad),low,high,max(high+pad,e.gain+pad))
  val selected=when{e.gain<low->0;e.gain>high->2;else->1}
  val marker=colour(if(total)R.color.growth_muted else if(selected==1)R.color.growth_normal_text else R.color.waist_gauge_increased_ink)
  val colours=listOf(colour(if(total)R.color.growth_control_border else R.color.bmi_gauge_underweight),colour(R.color.waist_gauge_normal),colour(R.color.waist_gauge_increased))
  val labels=if(total)listOf("تا بازهٔ کل","راهنمای کل","بالاتر") else listOf("پایین‌تر","راهنما","بالاتر")
  return GaugePresentation(e.gain,cuts,colours,labels,marker,"${SolarDate.persian(PregnancyAssessment.signed(e.gain))} کیلوگرم",selected,showEndpointTicks=false)
 }
 fun setEvaluation(value:PregnancyEvaluation){
  showGauge(frame(value));contentDescription="تغییر وزن مادر ${SolarDate.persian(PregnancyAssessment.signed(value.gain))} کیلوگرم؛ ${value.status}."
 }
 fun setTotalReference(value:PregnancyEvaluation){
  showGauge(frame(value,total=true));contentDescription="تغییر وزن مادر ${SolarDate.persian(PregnancyAssessment.signed(value.gain))} کیلوگرم؛ مقایسه با راهنمای حدودی کل بارداری. هدف این هفته نیست؛ هدف نهایی با تیم مراقبت تعیین شود."
 }
 override fun previewGauge():GaugePresentation?=frame(PregnancyEvaluation(22.0,7.0,"",PregnancyAssessment.WITHIN,"",11.5,16.0,5.0,8.0))
}
