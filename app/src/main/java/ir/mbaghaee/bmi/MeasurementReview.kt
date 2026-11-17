package ir.mbaghaee.bmi

import java.util.Locale
import kotlin.math.abs

/** Entry-quality prompts, not clinical growth-velocity limits or diagnoses. */
object MeasurementReview {
 fun previous(current:SavedAssessment,visits:List<SavedAssessment>)=visits.filter{
  it.birthUtc==current.birthUtc&&it.patient.sex==current.patient.sex&&it.visitUtc<current.visitUtc
 }.maxByOrNull{it.visitUtc}
 private fun number(v:Double,digits:Int=1)=SolarDate.persian(String.format(Locale.US,"%.${digits}f",v))
 private fun delta(v:Double,unit:String,digits:Int=1)=if(abs(v)<.5/Math.pow(10.0,digits.toDouble()))"تقریباً بدون تغییر" else "${if(v>0)"افزایش" else "کاهش"} ${number(abs(v),digits)} $unit"
 fun summary(current:SavedAssessment,old:SavedAssessment):String {
  val p=current.patient;val before=old.patient;val days=(current.visitUtc-old.visitUtc)/86400000
  return buildString{
   append("از سنجش قبلی ${number(days.toDouble(),0)} روز گذشته است؛ تاریخ نوبت قبل: ${SolarDate.fromUtc(old.visitUtc).display()}\n")
   val weight=p.weight-before.weight
   append("وزن: "+if(abs(weight)<1)delta(weight*1000,"گرم",0) else delta(weight,"کیلوگرم"))
   append("\nقد: ${delta(p.height-before.height,"سانتی‌متر")}")
   if(p.lying!=before.lying)append("\nروش اندازه‌گیری قد تغییر کرده است؛ مقایسهٔ قد با توجه به خوابیده یا ایستاده بودن انجام شود.")
   if(p.waist!=null&&before.waist!=null)append("\nدور کمر: ${delta(p.waist-before.waist,"سانتی‌متر")}")
   for(metric in current.assessment.metrics){
    if(metric.indicator.isBlank()||metric.z==null)continue
    val prior=old.assessment.metrics.firstOrNull{it.indicator==metric.indicator&&it.z!=null}?:continue
    val now=Percentiles.value(metric.z)?:continue;val then=Percentiles.value(prior.z!!)?:continue
    append("\n${metric.title}: صدک قبلی ${number(then)}؛ این نوبت ${number(now)}؛ ${delta(now-then,"واحد صدک")}")
   }
   append("\nتغییر عدد یا صدک به‌تنهایی به معنی بهبود یا بدتر شدن نیست؛ تغییر مرجع با صدک قبلی مقایسه نمی‌شود.")
  }
 }
 fun warnings(p:Patient,old:Patient?):List<String> {
  if(old==null||p.sex!=old.sex||p.days<=old.days)return emptyList()
  val days=p.days-old.days;val messages=mutableListOf<String>()
  if(p.lying==old.lying&&p.height<old.height-1.0)messages+="قد از ${number(old.height)} به ${number(p.height)} سانتی‌متر کاهش یافته است؛ عدد، روش اندازه‌گیری و تاریخ را بازبینی کنید."
  if(days<=30&&p.height-old.height>10.0)messages+="قد در ${number(days.toDouble(),0)} روز بیش از ۱۰ سانتی‌متر افزایش یافته است؛ احتمال جابه‌جایی رقم یا واحد را بررسی کنید."
  if(days<=30&&old.weight>0&&abs(p.weight-old.weight)/old.weight>=.20)messages+="وزن در ${number(days.toDouble(),0)} روز از ${number(old.weight)} به ${number(p.weight)} کیلوگرم تغییر کرده است؛ مقدار و واحد را بازبینی کنید."
  return messages
 }
}
