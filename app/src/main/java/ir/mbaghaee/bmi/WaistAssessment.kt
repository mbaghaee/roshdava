package ir.mbaghaee.bmi

import java.util.Locale
import kotlin.math.max
import kotlin.math.pow

/** NICE NG246 central-adiposity screening, separate from WHO paediatric BMI categories. */
object WaistAssessment {
 const val INDICATOR="whtr_ng246_2026"
 const val MIN_AGE_DAYS=1827
 private const val ADULT_AGE_DAYS=6575 // 18 years for this guideline; WHO BMI transitions separately at 19.
 const val NORMAL="چربی مرکزی در محدودهٔ مرجع"
 const val INCREASED="چربی مرکزی افزایش‌یافته"
 const val HIGH="چربی مرکزی بالا"
 const val REVIEW="اندازه‌ها و وضعیت فرد بررسی شوند"
 const val UNCLASSIFIED="تفسیر خودکار انجام نشد"
 const val BMI_LIMIT_DETAIL="عدد نسبت برای پیگیری ثبت می‌شود. طبق NICE، دسته‌بندی چربی مرکزی با این نسبت در بزرگسالان برای BMI کمتر از ۳۵ است. وضعیت وزنی را در کارت BMI ببینید."
 // Iranian National Committee of Obesity, Azizi et al. (2010), PMID 20433230.
 const val IRANIAN_WAIST_THRESHOLD_CM=90.0
 fun iranianWaistPosition(waist:Double):String{
  val difference=waist-IRANIAN_WAIST_THRESHOLD_CM
  if(difference==0.0)return "روی آستانهٔ مرجع ۹۰ سانتی‌متر"
  val amount=kotlin.math.abs(difference)
  val distance=if(amount<.1)"کمتر از ۰٫۱" else SolarDate.persian(number(amount).replace('.', '٫'))
  return "$distance سانتی‌متر ${if(difference>0)"بالاتر" else "پایین‌تر"} از آستانهٔ ۹۰ سانتی‌متر"
 }
 fun outsideAdultRatioReference(p:Patient,bmi:Double)=p.days>=ADULT_AGE_DAYS&&bmi.isFinite()&&bmi>=35
 fun ratio(p:Patient):Double?=p.waist?.takeIf{it.isFinite()&&it>0}?.let{if(p.height.isFinite()&&p.height>0)it/p.height else null}
 fun metric(p:Patient,bmi:Double,referenceBmiValid:Boolean=true):Metric? {
  val r=ratio(p)?:return null
  val reason=when{
   p.days<MIN_AGE_DAYS->"این تفسیر برای زیر پنج سال به کار نمی‌رود."
   p.pregnant->"در بارداری، این نسبت برای طبقه‌بندی عمومی چربی شکمی مناسب نیست."
   p.waistRestricted->"وجود بارداری، اختلال خوردن یا بیماری مؤثر بر قد/اندازهٔ شکم به ارزیابی اختصاصی نیاز دارد."
   !referenceBmiValid||!bmi.isFinite()||bmi<=0->"BMI معتبر برای تفسیر هم‌زمان موجود نیست؛ اندازه‌ها و ارزیابی رشد بازبینی شوند."
   outsideAdultRatioReference(p,bmi)->BMI_LIMIT_DETAIL
   else->null
  }
  val status=when{reason!=null->UNCLASSIFIED;r<.4->REVIEW;r<.5->NORMAL;r<.6->INCREASED;else->HIGH}
  val interpretation=reason?:when{
   r<.4->"نسبت کمتر از ۰٫۴ است؛ اندازه‌گیری و وضعیت وزن بررسی شوند و برچسب خودکار سالم صادر نمی‌شود."
   r<.5->"نسبت از ۰٫۴ تا کمتر از ۰٫۵ است؛ افزایش خطر ناشی از چربی مرکزی در این طبقه‌بندی نشان داده نشد."
   r<.6->"نسبت از ۰٫۵ تا کمتر از ۰٫۶ است؛ چربی مرکزی و خطرهای مرتبط افزایش یافته‌اند. نتیجه با کارشناس سلامت پیگیری شود."
   else->"نسبت ۰٫۶ یا بیشتر است؛ چربی مرکزی و خطرهای مرتبط بیشترند. بررسی عوامل خطر توسط کارشناس سلامت پیشنهاد می‌شود."
  }
  val detail=SolarDate.persian("دور کمر: ${number(p.waist!!)} سانتی‌متر · نسبت دور کمر به قد: ${ratioNumber(r)} · BMI: ${number(bmi,2)}")+"\n"+interpretation+"\nتفسیر چربی شکمی در کنار BMI است؛ تشخیص بیماری یا درصد چربی بدن نیست. مرجع: NICE NG246."
  return Metric("نسبت دور کمر به قد",status,detail,severity=when(status){NORMAL->0;HIGH->2;else->1},indicator=INDICATOR)
 }
 fun isWaistMetric(m:Metric)=m.indicator==INDICATOR||m.title=="نسبت دور کمر به قد"
 fun number(value:Double,digits:Int=1)=String.format(Locale.US,"%.${digits}f",value)
 fun ratioNumber(value:Double):String {
  fun band(v:Double)=when{v<.4->0;v<.5->1;v<.6->2;else->3}
  // Preserve the category near a cutoff instead of displaying e.g. 0.600 for a value below 0.6.
  for(digits in 3..8){val text=number(value,digits);if(band(text.toDouble())==band(value))return text}
  return value.toString()
 }
 fun previous(p:Patient,visits:List<Patient>)=visits.filter{it.sex==p.sex&&it.days<p.days&&ratio(it)!=null}.maxByOrNull{it.days}
 fun comparison(p:Patient,visits:List<Patient>):String {
  val current=ratio(p)?:return ""
  val old=previous(p,visits)?:return "هنوز نوبت قبلیِ دارای دور کمر وجود ندارد؛ با ثبت نوبت بعد، تغییرات قابل مقایسه می‌شوند."
  fun change(value:Double,digits:Int,unit:String="")=if(kotlin.math.abs(value)<.5/10.0.pow(digits))"تقریبا ثابت مانده" else (if(value>0)"افزایش " else "کاهش ")+number(kotlin.math.abs(value),digits)+unit
  val waistChange=change(p.waist!!-old.waist!!,1," سانتی‌متر")
  val ratioChange=change(current-ratio(old)!!,3)
  return SolarDate.persian("نسبت به ${p.days-old.days} روز قبل: دور کمر $waistChange؛ نسبت دور کمر به قد $ratioChange.")+
   if(p.days<ADULT_AGE_DAYS)"\nدر کودک، رشد قد هم باید در نظر گرفته شود." else ""
 }
 fun trend(p:Patient,visits:List<ChartVisit>,asRatio:Boolean,visitId:Long?=null):ReferencePlot? {
  val currentValue=ratio(p)?:return null
  val years=p.days/30.4375>=228;val unit=if(years)365.25 else 30.4375
  fun point(v:Patient,id:Long):PlotPoint?=ratio(v)?.let{PlotPoint(v.days/unit,if(asRatio)it else v.waist!!,v.days,id)}
  val history=visits.filter{it.patient.sex==p.sex}.mapNotNull{point(it.patient,it.id)}.sortedBy{it.day}
  val current=PlotPoint(p.days/unit,if(asRatio)currentValue else p.waist!!,p.days,visitId)
  val all=history+current;val margin=if(years)1.0/12 else 1.0
  val low=max(0.0,all.minOf{it.x}-margin);val high=max(low+2*margin,all.maxOf{it.x}+margin)
  return ReferencePlot(if(years)"سن · سال" else "سن · ماه",if(asRatio)"نسبت دور کمر به قد" else "دور کمر · سانتی‌متر",low,high,emptyList(),current,if(asRatio)ratioNumber(current.y) else number(current.y),history,minYPadding=if(asRatio).01 else .5,yDecimals=if(asRatio)3 else null)
 }
}
