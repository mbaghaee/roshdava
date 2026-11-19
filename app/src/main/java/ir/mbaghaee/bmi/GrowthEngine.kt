package ir.mbaghaee.bmi
import kotlin.math.*
data class GrowthRow(val sex:Int,val x:Double,val l:Double,val m:Double,val s:Double)
data class Patient(val name:String,val sex:Int,val days:Int,val weight:Double,val height:Double,val lying:Boolean=false,val head:Double?=null,val waist:Double?=null,val premature:Boolean=false,val pregnant:Boolean=false,val waistRestricted:Boolean=false,val pregnancy:PregnancyInfo?=null)
data class Metric(val title:String,val status:String,val detail:String,val z:Double?=null,val severity:Int=0,val indicator:String="")
data class Assessment(val bmi:Double,val group:String,val metrics:List<Metric>,val notes:List<String>,val score:Double?,val adult:Boolean,val pregnancy:PregnancyEvaluation?=null)
class GrowthEngine(private val tables:Map<String,List<GrowthRow>>) {
 private val indexed=tables.mapValues{(_,rows)->rows.groupBy{it.sex}}
 companion object {
  fun parse(text:String)=text.lineSequence().drop(1).filter{it.isNotBlank()}.map {val c=it.split(',');GrowthRow(c[0].toInt(),c[1].toDouble(),c[2].toDouble(),c[3].toDouble(),c[4].toDouble())}.toList()
  fun z(value:Double,r:GrowthRow,tail:Boolean=true):Double {
   val raw=if(abs(r.l)<1e-10)ln(value/r.m)/r.s else ((value/r.m).pow(r.l)-1)/(r.l*r.s)
   fun sd(k:Int)=if(abs(r.l)<1e-10)r.m*exp(r.s*k) else r.m*(1+r.l*r.s*k).pow(1/r.l)
   return if(tail&&raw>3)3+(value-sd(3))/(sd(3)-sd(2)) else if(tail&&raw< -3)-3+(value-sd(-3))/(sd(-2)-sd(-3)) else raw
  }
 }
 private fun ref(table:String,sex:Int,x:Double):GrowthRow? {
  val rows=indexed[table]?.get(sex)?:return null
  if(rows.isEmpty()||x<rows.first().x||x>rows.last().x)return null
  val i=rows.binarySearchBy(x){it.x};if(i>=0)return rows[i]
  val hi=-i-1;if(hi==0||hi>=rows.size)return null
  val a=rows[hi-1];val b=rows[hi];val t=(x-a.x)/(b.x-a.x)
  return GrowthRow(sex,x,a.l+(b.l-a.l)*t,a.m+(b.m-a.m)*t,a.s+(b.s-a.s)*t)
 }
 private fun standardHeight(p:Patient):Double {
  if(p.days/30.4375>=60)return p.height
  return p.height+if(p.days<731&&!p.lying).7 else if(p.days>=731&&p.lying)-.7 else 0.0
 }
 fun plotFor(p:Patient,m:Metric,fullRange:Boolean=false,visits:List<ChartVisit> = emptyList(),visitId:Long?=null):ReferencePlot? {
  if(m.z==null||m.indicator.isBlank())return null
  val table=m.indicator;val rows=indexed[table]?.get(p.sex)?:return null
  val height=standardHeight(p);val byHeight=table=="wflanthro"||table=="wfhanthro"
  val daily=table in listOf("lenanthro","weianthro","hcanthro","bmianthro")
  val factor=if(daily)30.4375 else 1.0
  val pointX=if(byHeight)height else p.days/30.4375
  val pointY=when(table){"lenanthro","hfa2007"->height;"bmi2007","bmianthro"->p.weight/(height/100).pow(2);"hcanthro"->p.head?:return null;else->p.weight}
  val trail=visits.mapNotNull{visit->
    val old=visit.patient
    if(old.sex!=p.sex||old.premature)return@mapNotNull null
   val oldHeight=if(table=="wflanthro")old.height+(if(old.lying)0.0 else .7) else if(table=="wfhanthro")old.height-(if(old.lying).7 else 0.0) else standardHeight(old)
   if(byHeight&&old.days/30.4375>=60)return@mapNotNull null
   val x=if(byHeight)oldHeight else old.days/30.4375
   val value=when(table){"lenanthro","hfa2007"->oldHeight;"bmi2007","bmianthro"->old.weight/(oldHeight/100).pow(2);"hcanthro"->old.head?:return@mapNotNull null;else->old.weight}
   val r=ref(table,old.sex,(x*factor))?:return@mapNotNull null
   val score=z(value,r,table!="lenanthro"&&table!="hfa2007"&&table!="hcanthro")
   if(!score.isFinite()||abs(score)>if(table=="lenanthro"||table=="hfa2007")6 else if(table=="weianthro"&&score<0)6 else 5)return@mapNotNull null
   PlotPoint(x,value,old.days,visit.id)
  }.sortedBy{it.day}
  val radius=if(byHeight)15.0 else if(pointX<24)6.0 else if(daily)12.0 else 24.0
  val low=if(fullRange)rows.first().x/factor else max(rows.first().x/factor,min(pointX,trail.minOfOrNull{it.x}?:pointX)-radius)
  val high=if(fullRange)rows.last().x/factor else min(rows.last().x/factor,max(pointX,trail.maxOfOrNull{it.x}?:pointX)+radius)
  if(high<=low)return null
  val bands=ReferenceBands.forIndicator(table)
  val levels=(listOf(-3,-2,-1,0,1,2,3)+bands.flatMap{listOfNotNull(it.lowerZ,it.upperZ)}).distinct().sorted()
  val curves=levels.map{k->
   ReferenceCurve(k,(0..80).mapNotNull{i->
    val x=low+(high-low)*i/80
    val r=ref(table,p.sex,(x*factor).coerceIn(rows.first().x,rows.last().x))?:return@mapNotNull null
    val y=if(abs(r.l)<1e-10)r.m*exp(r.s*k) else r.m*(1+r.l*r.s*k).pow(1/r.l)
    if(y.isFinite())PlotPoint(x,y) else null
   })
  }
  if(curves.any{it.points.size<2})return null
  val yLabel=when(table){"lenanthro","hfa2007"->"قد · سانتی‌متر";"bmi2007","bmianthro"->"BMI · kg/m²";"hcanthro"->"دور سر · سانتی‌متر";else->"وزن · کیلوگرم"}
  return ReferencePlot(if(byHeight)"قد / طول بدن · سانتی‌متر" else "سن · ماه",yLabel,low,high,curves,PlotPoint(pointX,pointY,p.days,visitId),"${m.title}: ${String.format(java.util.Locale.US,"%.1f",pointY)}",trail,bands)
 }
 fun measurementTrend(p:Patient,kind:String,visits:List<ChartVisit>,visitId:Long?=null):ReferencePlot {
  val years=p.days/30.4375>=228
  val ageUnit=if(years)365.25 else 30.4375
  fun point(v:Patient,id:Long?)=PlotPoint(v.days/ageUnit,when(kind){"height"->v.height;"bmi"->v.weight/(standardHeight(v)/100).pow(2);else->v.weight},v.days,id)
  val trail=visits.filter{it.patient.sex==p.sex}.map{point(it.patient,it.id)}.filter{it.y.isFinite()}.sortedBy{it.day}
  val current=point(p,visitId);val all=trail+current
  val margin=if(years)1.0/12 else 1.0
  val low=max(0.0,all.minOf{it.x}-margin);val high=max(low+2*margin,all.maxOf{it.x}+margin)
  val label=when(kind){"height"->"قد ثبت‌شده · سانتی‌متر";"bmi"->"BMI";else->"وزن ثبت‌شده · کیلوگرم"}
  return ReferencePlot(if(years)"سن · سال" else "سن · ماه",label,low,high,emptyList(),current,"${String.format(java.util.Locale.US,"%.1f",current.y)}",trail)
 }
 fun assess(p:Patient):Assessment {
  require(p.sex in 1..2){"جنسیت را انتخاب کنید."}
  require(p.days in 0..(120*366)){"سن باید بین تولد تا ۱۲۰ سال باشد."}
  require(p.weight.isFinite()&&p.weight in 0.3..400.0){"وزن باید بین ۰٫۳ و ۴۰۰ کیلوگرم باشد."}
  require(p.height.isFinite()&&p.height in 25.0..250.0){"قد را به سانتی‌متر، بین ۲۵ تا ۲۵۰ وارد کنید."}
  require(p.head==null||p.head.isFinite()&&p.head in 15.0..80.0){"دور سر نامعتبر است."}
  require(p.waist==null||p.waist.isFinite()&&p.waist in 20.0..250.0){"دور کمر نامعتبر است."}
  val months=p.days/30.4375;val adult=months>=228;val small=months<60;val infant=p.days<731
  val height=standardHeight(p)
  val bmi=p.weight/(height/100).pow(2);val notes=mutableListOf<String>();val metrics=mutableListOf<Metric>()
  if(p.pregnant){
   require(p.sex==2){"اطلاعات جنسیت و بارداری را بررسی کنید."}
   val pregnancy=PregnancyAssessment.evaluate(p)
   metrics+=pregnancy?.let{PregnancyAssessment.metric(it)}?:Metric("وضعیت وزن","بارداری؛ اطلاعات تکمیلی لازم","وزن پیش از بارداری، هفته و تعداد جنین برای ارزیابی لازم‌اند.",severity=1)
   WaistAssessment.metric(p,bmi)?.let{metrics+=it}
   notes+="وزن‌گیری بارداری همراه ماما یا پزشک و روند ثبت‌های همین بارداری بررسی شود."
   return Assessment(bmi,"بارداری · وزن‌گیری مادر",metrics,notes,null,adult,pregnancy)
  }
  if(small&&abs(height-p.height)>.01)notes+="برای تطبیق با مرجع، قد ${if(infant) "۰٫۷ سانتی‌متر افزایش" else "۰٫۷ سانتی‌متر کاهش"} داده شد."
  if(!adult&&p.premature)return Assessment(bmi,"نوزاد نارس",listOf(Metric("ارزیابی رشد","نیاز به سن اصلاح‌شده","سن بارداری هنگام تولد و مرجع مناسب باید توسط متخصص تعیین شود؛ طبقه‌بندی عمومی صادر نشد.",severity=1)),notes,null,false)
  if(adult){
   if(p.pregnant)metrics+=Metric("وضعیت وزن","بارداری؛ ارزیابی اختصاصی","BMI فعلی برای ارزیابی بارداری کافی نیست؛ BMI پیش از بارداری لازم است.",severity=1)
   else metrics+=Metric("وضعیت وزن",when{bmi<18.5->"کمبود وزن";bmi<25->"در محدوده مرجع";bmi<30->"اضافه‌وزن";bmi<35->"چاقی درجه یک";bmi<40->"چاقی درجه دو";else->"چاقی درجه سه"},"طبقه‌بندی BMI بزرگسالان از ۱۹ سالگی",severity=if(bmi<18.5||bmi>=30)2 else if(bmi>=25)1 else 0)
   metrics+=Metric("وضعیت قد","قد ثبت‌شده","مرجع کوتاه‌قدی کودکان برای بزرگسالان به کار نمی‌رود.")
   WaistAssessment.metric(p,bmi)?.let{metrics+=it}
   notes+=if(months>=65*12)"در سالمندی، تغییر وزن ناخواسته، اشتها و قدرت عضلانی هم باید بررسی شوند؛ BMI به‌تنهایی کافی نیست." else "BMI عضله را از چربی تفکیک نمی‌کند؛ شرایط فرد را در تفسیر نتیجه در نظر بگیرید."
  }else{
   fun score(table:String,x:Double,v:Double,tail:Boolean=true)=ref(table,p.sex,x)?.let{z(v,it,tail)}
   fun metric(title:String,v:Double?,limit:Double,status:(Double)->String,detail:String):Metric {
    if(v==null)return Metric(title,"خارج از پوشش مرجع","برای این اندازه یا سن، مرجع معتبر موجود نیست.",severity=1)
    if(!v.isFinite()||abs(v)>limit)return Metric(title,"اندازه‌گیری را بازبینی کنید","بیرون محدوده کنترل کیفیت WHO؛ برچسب صادر نشد.",severity=2)
    return Metric(title,status(v),detail,v,if(v< -2||v>2)2 else if(v>1)1 else 0)
   }
   val hz=score(if(small)"lenanthro" else "hfa2007",if(small)p.days.toDouble() else months,height,false)
   metrics+=metric("قد نسبت به سن",hz,6.0,{when{it< -3->"کوتاه‌قدی شدید";it< -2->"کوتاه‌قد";it>3->"بلندقد؛ بررسی شود";else->"در محدوده مرجع"}},"مقایسه با مرجع هم‌سن و هم‌جنس").copy(indicator=if(small)"lenanthro" else "hfa2007")
   val wz=if(small)score(if(infant)"wflanthro" else "wfhanthro",height,p.weight) else score("bmi2007",months,bmi)
   metrics.add(0,metric(if(small)"وزن نسبت به قد" else "BMI نسبت به سن",wz,5.0,{when{it< -3->"لاغری شدید";it< -2->"لاغر";small&&it>3->"چاق";small&&it>2->"اضافه‌وزن";small&&it>1->"در معرض اضافه‌وزن";!small&&it>2->"چاق";!small&&it>1->"اضافه‌وزن";else->"در محدوده مرجع"}},if(small)"معیار تناسب وزن زیر پنج سال؛ WHO 2006" else "مرجع WHO 2007 با سن و جنسیت").copy(indicator=if(small)if(infant)"wflanthro" else "wfhanthro" else "bmi2007"))
   if(small){val wa=score("weianthro",p.days.toDouble(),p.weight);metrics+=metric("وزن نسبت به سن",wa,if(wa!=null&&wa<0)6.0 else 5.0,{when{it< -3->"کم‌وزنی شدید";it< -2->"کم‌وزن";else->"کم‌وزنی مشاهده نشد"}},"برای تشخیص اضافه‌وزن از این شاخص استفاده نمی‌شود.").copy(indicator="weianthro")
    p.head?.let{hc->metrics+=metric("دور سر نسبت به سن",score("hcanthro",p.days.toDouble(),hc,false),5.0,{if(abs(it)>2)"نیازمند بررسی متخصص" else "در محدوده مرجع"},"تفسیر همراه معاینه و روند رشد انجام شود.").copy(indicator="hcanthro")}}
   WaistAssessment.metric(p,bmi,referenceBmiValid=metrics.firstOrNull()?.z!=null)?.let{metrics+=it}
   notes+="روند چند اندازه‌گیری برای ارزیابی رشد اهمیت دارد؛ یک اندازه‌گیری تشخیص قطعی نیست."
   if(metrics.any{it.severity>0&&!WaistAssessment.isWaistMetric(it)})notes+="اندازه‌ها و تاریخ تولد را بازبینی و نتیجه خارج از مرجع را با کارشناس سلامت پیگیری کنید."
   return Assessment(bmi,if(small)if(infant)"شیرخوار · WHO 2006" else "کودک · WHO 2006" else "کودک و نوجوان · WHO 2007",metrics,notes,wz?.takeIf{it.isFinite()&&abs(it)<=5},false)
  }
  return Assessment(bmi,"بزرگسال · BMI",metrics,notes,if(p.pregnant)null else bmi,true)
 }
}
