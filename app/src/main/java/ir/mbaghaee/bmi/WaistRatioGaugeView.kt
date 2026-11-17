package ir.mbaghaee.bmi

import android.content.Context
import android.util.AttributeSet

/** The reference palette is always visible; only the patient's marker depends on eligibility. */
class WaistRatioGaugeView @JvmOverloads constructor(context:Context,attrs:AttributeSet?=null):SegmentedGaugeView(context,attrs){
 private fun eligible(status:String?)=status in setOf(WaistAssessment.NORMAL,WaistAssessment.INCREASED,WaistAssessment.HIGH,WaistAssessment.REVIEW)
 private fun frame(value:Double,status:String?):GaugePresentation {
  val selected=when(status){WaistAssessment.REVIEW->0;WaistAssessment.NORMAL->1;WaistAssessment.INCREASED->2;WaistAssessment.HIGH->3;else->-1}
  val marker=when(status){
   WaistAssessment.NORMAL->colour(R.color.growth_normal_text)
   WaistAssessment.INCREASED->colour(R.color.waist_gauge_increased_ink)
   WaistAssessment.HIGH->colour(R.color.waist_gauge_high_ink)
   else->colour(R.color.growth_muted)
  }
  return GaugePresentation(value,listOf(.2,.4,.5,.6,.8),
   listOf(colour(R.color.waist_gauge_neutral),colour(R.color.waist_gauge_normal),colour(R.color.waist_gauge_increased),colour(R.color.waist_gauge_high)),
   listOf("بازبینی","مرجع","افزایش","بالا"),marker,
   "شما · ${SolarDate.persian(WaistAssessment.ratioNumber(value))}",selected)
 }
 fun setRatio(value:Double,recordedStatus:String?){
  if(!value.isFinite()||value<=0){showGauge(null);contentDescription="نسبت دور کمر ثبت نشده است";return}
  showGauge(frame(value,recordedStatus))
  contentDescription="نسبت دور کمر به قد: ${SolarDate.persian(WaistAssessment.ratioNumber(value))}، ${recordedStatus?:"تفسیر ثبت نشده"}"+
   if(eligible(recordedStatus))". نوار از کم به زیاد: بازبینی، محدودهٔ مرجع، افزایش‌یافته، بالا."
   else ". رنگ‌های نوار فقط راهنمای مرجع هستند؛ نشانگر خنثی است و تفسیر صادر نشده است."
 }
 override fun previewGauge()=frame(.541,WaistAssessment.INCREASED)
}
