package ir.mbaghaee.bmi

import java.util.Locale
import kotlin.math.*

/** Normal CDF on the WHO reportable interval. Approximation error < 8e-8. */
object Percentiles {
 fun value(z:Double):Double? {
  if(!z.isFinite()||abs(z)>3)return null
  if(z==0.0)return 50.0
  val x=abs(z);val t=1.0/(1.0+0.2316419*x)
  val tail=exp(-x*x/2)/sqrt(2*Math.PI)*t*(0.319381530+t*(-0.356563782+t*(1.781477937+t*(-1.821255978+t*1.330274429))))
  return (if(z>0)1-tail else tail)*100
 }
 fun summary(m:Metric):String? {
  val z=m.z?:return null
  if(!z.isFinite())return null
  val (subject,comparison)=when(m.indicator){
   "lenanthro","hfa2007"->"قد این کودک" to "کودکان هم‌سن و هم‌جنس"
   "weianthro"->"وزن این کودک" to "کودکان هم‌سن و هم‌جنس"
   "wflanthro","wfhanthro"->"وزن این کودک" to "کودکان هم‌جنس با همین قد یا طول بدن"
   "bmi2007"->"BMI این کودک" to "کودکان هم‌سن و هم‌جنس"
   "hcanthro"->"دور سر این کودک" to "کودکان هم‌سن و هم‌جنس"
   else->return null
  }
  val p=value(z)?:return "$subject در ${if(z>0)"بالاترین" else "پایین‌ترین"} بخش مرجع قرار دارد؛ صدک دقیق در این فاصله گزارش نمی‌شود."
  fun number(v:Double)=SolarDate.persian(String.format(Locale.US,"%.1f",v))
  return "در مرجع رشد، $subject از حدود ${number(p)}٪ $comparison بیشتر و از حدود ${number(100-p)}٪ آن‌ها کمتر است."
 }
 fun explanation(z:Double):String {
  val number=SolarDate.persian(String.format(Locale.US,"%+.2f",z))
  return "نمرهٔ استاندارد (Z): $number\nاین عدد فاصله از میانهٔ مرجع را نشان می‌دهد؛ صفر یعنی میانه، عدد مثبت یعنی بالاتر و عدد منفی یعنی پایین‌تر. هر یک واحد، یک فاصلهٔ استاندارد است."
 }
}

data class ChartVisit(val id:Long,val patient:Patient)
data class PlotPoint(val x:Double,val y:Double,val day:Int=0,val visitId:Long?=null)
data class ReferenceCurve(val z:Int,val points:List<PlotPoint>)
data class ReferenceEnvelope(val lower:List<PlotPoint>,val upper:List<PlotPoint>)
enum class BandTone { NORMAL, CAUTION, ALERT, NEUTRAL }
data class ReferenceBand(val lowerZ:Int?,val upperZ:Int?,val tone:BandTone)

object ReferenceBands {
 // All WHO reference charts use the same visual percentile bands. Clinical interpretation stays in the cards/BMI gauge.
 private val lowWeight=listOf(ReferenceBand(null,-3,BandTone.ALERT),ReferenceBand(-3,-2,BandTone.CAUTION))
 private val percentileBands=lowWeight+listOf(ReferenceBand(-2,2,BandTone.NORMAL),ReferenceBand(2,3,BandTone.CAUTION),ReferenceBand(3,null,BandTone.ALERT))
 private const val PERCENTILE_LEGEND="سایه‌های خاکستری جایگاه صدکی را نشان می‌دهند. از روشن به تیره: صدک ۲٫۳ تا ۹۷٫۷؛ صدک ۰٫۱ تا ۲٫۳ و ۹۷٫۷ تا ۹۹٫۹؛ پایین‌تر از ۰٫۱ یا بالاتر از ۹۹٫۹. اعداد صدک گرد شده‌اند. تفسیر هر شاخص در کارت نتیجه آمده است."
 fun forIndicator(indicator:String):List<ReferenceBand> = when(indicator){
  "bmi2007","bmianthro","wflanthro","wfhanthro","lenanthro","hfa2007","hcanthro","weianthro"->percentileBands
  else->emptyList()
 }
 fun legend(indicator:String):String=when(indicator){
  "bmi2007"->PERCENTILE_LEGEND+" برای BMI سنین ۵ تا ۱۹ سال، Z بالاتر از +۱ اضافه‌وزن و بالاتر از +۲ چاقی است؛ وضعیت و نوار اصلی BMI همین مرزهای بالینی را نشان می‌دهند."
  "bmianthro","wflanthro","wfhanthro","lenanthro","hfa2007","hcanthro"->PERCENTILE_LEGEND
  "weianthro"->PERCENTILE_LEGEND+" در این نمودار، ناحیهٔ بالایی به‌تنهایی به معنی اضافه‌وزن یا چاقی نیست؛ تناسب وزن با قد یا BMI نسبت به سن را بررسی کنید."
  else->"عدد کنار هر خط، صدک مرجع را نشان می‌دهد."
 }
}
data class ReferencePlot(val xLabel:String,val yLabel:String,val xMin:Double,val xMax:Double,
 val curves:List<ReferenceCurve>,val point:PlotPoint,val pointText:String,val history:List<PlotPoint> = emptyList(),val bands:List<ReferenceBand> = emptyList(),val minYPadding:Double=.5,val yDecimals:Int?=null,val envelope:ReferenceEnvelope?=null,val minYValue:Double?=0.0,val gestationalWeekAxis:Boolean=false,val showCurrentPoint:Boolean=true,val dateBasedAge:Boolean=false)
