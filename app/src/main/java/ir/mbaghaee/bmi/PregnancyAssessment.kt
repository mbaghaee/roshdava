package ir.mbaghaee.bmi

import java.util.Locale
import kotlin.math.max

data class PregnancyInfo(val preWeight:Double,val gestationDays:Int,val plurality:Int,val episodeId:String,val fetusCount:Int?=null)
data class PregnancyEvaluation(
 val preBmi:Double,val gain:Double,val category:String,val status:String,val detail:String,
 val totalLow:Double?,val totalHigh:Double?,val weekLow:Double?,val weekHigh:Double?,
 val referenceVersion:String=PregnancyAssessment.VERSION,val guidance:String=""
)

/** NAM/IOM 2009 endpoints. The 13–40 week corridor is an explicitly labelled interpolation, not a percentile reference. */
object PregnancyAssessment {
 const val VERSION="NAM2009_INTERPOLATED_13_40_V1"
 const val INDICATOR="pregnancy_gain_v1"
 const val WITHIN="در بازهٔ برآوردی این هفته"
 const val BELOW="پایین‌تر از بازهٔ برآوردی"
 const val ABOVE="بالاتر از بازهٔ برآوردی"
 const val FIRST="سه‌ماههٔ اول؛ پیگیری روند"
 const val INDIVIDUAL="نیازمند هدف اختصاصی"
 fun bmi(preWeight:Double,height:Double)=preWeight/(height/100)/(height/100)
 fun category(bmi:Double)=when{bmi<18.5->"کمبود وزن پیش از بارداری";bmi<25->"وزن پیش از بارداری در محدودهٔ مرجع";bmi<30->"اضافه‌وزن پیش از بارداری";else->"چاقی پیش از بارداری"}
 fun totalRange(bmi:Double,plurality:Int,fetusCount:Int?=null):Pair<Double,Double>?=when(plurality){
  1->when{bmi<18.5->12.5 to 18.0;bmi<25->11.5 to 16.0;bmi<30->7.0 to 11.5;else->5.0 to 9.0}
  2->when{bmi<18.5->22.7 to 28.1;bmi<25->16.8 to 24.5;bmi<30->14.1 to 22.7;else->11.3 to 19.1}
  3->HigherOrderPregnancyReference.totalRange(fetusCount)
  else->null
 }
 fun weeklyRange(gestationDays:Int,total:Pair<Double,Double>):Pair<Double,Double>? {
  val week=gestationDays/7.0
  if(week<=13||week>40)return null
  val progress=(week-13)/27
  return (.5+(total.first-.5)*progress) to (2+(total.second-2)*progress)
 }
 fun evaluate(p:Patient):PregnancyEvaluation? {
  if(!p.pregnant)return null
  val info=p.pregnancy?:return null
  require(p.sex==2){"اطلاعات جنسیت و بارداری را بررسی کنید."}
  require(info.preWeight.isFinite()&&info.preWeight in .3..400.0){"وزن پیش از بارداری معتبر نیست."}
  require(info.gestationDays in 7..300){"هفتهٔ کامل بارداری باید ۱ تا ۴۲ و روز اضافه ۰ تا ۶ باشد."}
  require(info.plurality in 1..3){"تعداد جنین را انتخاب کنید."}
  require(info.fetusCount==null||info.plurality==3&&info.fetusCount in 3..12){"تعداد جنین برای چندقلویی باید عددی بین ۳ و ۱۲ باشد."}
  require(info.episodeId.isNotBlank()&&info.episodeId.length<=200){ "شناسهٔ بارداری معتبر نیست." }
  require(p.height.isFinite()&&p.height in 25.0..250.0&&p.weight.isFinite()&&p.weight in .3..400.0){"قد و وزن فعلی معتبر نیستند."}
  val preBmi=bmi(info.preWeight,p.height);val gain=p.weight-info.preWeight
  val adult=p.days>=6575
  val total=if(adult)totalRange(preBmi,info.plurality,info.fetusCount) else null
  val weekly=if(adult&&info.plurality==1&&total!=null)weeklyRange(info.gestationDays,total) else null
  val status=when{
   !adult||info.plurality!=1||info.gestationDays>280->INDIVIDUAL
   info.gestationDays<=91->FIRST
   weekly==null->INDIVIDUAL
   gain<weekly.first->BELOW
   gain>weekly.second->ABOVE
   else->WITHIN
  }
  val explanation=when{
   !adult->"بارداری زیر ۱۸ سال به ارزیابی متناسب با سن توسط تیم مراقبت نیاز دارد؛ هدف عمومی صادر نشد."
   info.plurality==3&&total!=null->HigherOrderPregnancyReference.NOTICE+"\nمنبع: "+HigherOrderPregnancyReference.source(info.fetusCount)
   info.plurality==3->"برای تعداد نامشخص یا شش‌قلویی و بیشتر، بازهٔ قابل‌اتکای عمومی پیدا نشد؛ هدف وزن‌گیری باید توسط تیم مراقبت تعیین شود."
   info.plurality==2->"هدف کل و راهنمای گرافیکی دوقلویی CDC نمایش داده می‌شوند. محدودهٔ نمودار، راهنمای عمومی است؛ هدف شخصی این هفته با تیم مراقبت تعیین شود."
   info.gestationDays>280->"پس از هفتهٔ ۴۰، محدودهٔ هفتگی ادامه داده نمی‌شود؛ بررسی با تیم مراقبت انجام شود."
   info.gestationDays<=91->"در سه‌ماههٔ اول، وزن‌گیری می‌تواند اندک یا بدون افزایش باشد. راهنمای پایان این سه‌ماهه حدود ۰٫۵ تا ۲ کیلوگرم است؛ برای هر هفته از آن هدف خطی صادر نمی‌شود."
   status==WITHIN->"افزایش وزن در بازهٔ تقریبی این هفته قرار دارد. ارزیابی با روند وزن‌گیری و مراقبت بارداری تکمیل شود."
   else->"روند و اندازه‌گیری با ماما یا پزشک بررسی شود؛ خروج از این بازه به‌تنهایی تشخیص نیست و توصیهٔ کاهش وزن صادر نمی‌شود."
  }
  val detail=SolarDate.persian("بارداری: ${weekLabel(info.gestationDays)} · ${pluralityLabel(info.plurality,info.fetusCount)}\nوزن پیش از بارداری: ${n(info.preWeight)} کیلوگرم · BMI پیش از بارداری: ${n(preBmi,2)}\nتغییر وزن: ${signed(gain)} کیلوگرم")+
   (weekly?.let{"\nبازهٔ برآوردی تا این هفته: ${range(it)} کیلوگرم"}?:"")+
   (total?.let{"\n${if(info.plurality==3)"راهنمای حدودی افزایش وزن کل" else "هدف کلی بارداری"}: ${range(it)} کیلوگرم"}?:"")+"\n"+explanation+
   (when(info.plurality){3->"";2->"\nمرجع: NAM/IOM 2009 و CDC. راهنمای گرافیکی دوقلویی، بازخوانی تقریبی نمودار CDC است؛ هدف اختصاصی با تیم مراقبت تعیین شود.";else->"\nمرجع: NAM/IOM 2009 و CDC. بازهٔ هفته‌ای، برآورد بین هفتهٔ ۱۳ و هدف هفتهٔ ۴۰ است؛ منحنی صدکی نیست."})
  return PregnancyEvaluation(preBmi,gain,if(adult)category(preBmi) else "نیازمند ارزیابی متناسب با سن",status,detail,total?.first,total?.second,weekly?.first,weekly?.second,referenceVersion=when(info.plurality){2->TwinPregnancyReference.VERSION;3->HigherOrderPregnancyReference.VERSION;else->VERSION},guidance=explanation)
 }
 fun metric(value:PregnancyEvaluation)=Metric("وزن‌گیری بارداری",value.status,value.detail,severity=if(value.status==WITHIN)0 else 1,indicator=INDICATOR)
 fun n(value:Double,digits:Int=1)=String.format(Locale.US,"%.${digits}f",value)
 fun signed(value:Double)=if(value>0)"+${n(value)}" else n(value)
 fun range(value:Pair<Double,Double>)=SolarDate.persian("${n(value.first)} تا ${n(value.second)}")
 fun weekLabel(days:Int)=SolarDate.persian("${days/7} هفته و ${days%7} روز")
 fun pluralityLabel(value:Int,count:Int?=null)=when(value){1->"تک‌قلویی";2->"دوقلویی";else->if(count!=null)SolarDate.persian("$count جنین") else "چندقلویی؛ تعداد نامشخص"}
 fun advance(info:PregnancyInfo,elapsedDays:Long):PregnancyInfo? {
  if(elapsedDays<0||elapsedDays>300)return null
  val days=info.gestationDays+elapsedDays
  return if(days in 7L..300L)info.copy(gestationDays=days.toInt()) else null
 }
 fun plot(p:Patient,e:PregnancyEvaluation,visits:List<Patient>):ReferencePlot? {
  val info=p.pregnancy?:return null
  val history=visits.filter{it.pregnant&&it.pregnancy?.episodeId==info.episodeId}.mapNotNull{old->
   val oldInfo=old.pregnancy?:return@mapNotNull null
   PlotPoint(oldInfo.gestationDays/7.0,old.weight-info.preWeight,old.days)
  }.sortedBy{it.day}
  val point=PlotPoint(info.gestationDays/7.0,e.gain,p.days)
  val envelope=if(info.plurality==2&&p.days>=6575&&e.totalLow!=null&&e.totalHigh!=null){
   TwinPregnancyReference.envelope(e.preBmi)
  }else if(e.referenceVersion==VERSION&&info.plurality==1&&e.totalLow!=null&&e.totalHigh!=null){
   val lower=(13..40).map{w->PlotPoint(w.toDouble(),.5+(e.totalLow-.5)*(w-13)/27)}
   val upper=(13..40).map{w->PlotPoint(w.toDouble(),2+(e.totalHigh-2)*(w-13)/27)}
   ReferenceEnvelope(lower,upper)
  }else null
  return ReferencePlot("هفتهٔ بارداری","تغییر وزن مادر · کیلوگرم",0.0,max(40.0,(history+point).maxOf{it.x}+1),emptyList(),point,signed(e.gain),history,envelope=envelope,minYValue=null,gestationalWeekAxis=true)
 }
}
