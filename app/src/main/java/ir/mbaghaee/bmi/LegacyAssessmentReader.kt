package ir.mbaghaee.bmi

import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

/** Recover only explicit inputs from this app's own old report format; never infer a patient's age or sex. */
object LegacyAssessmentReader {
 private fun normalize(s:String):String {
  var v=s.replace('٫','.');for(i in 0..9)v=v.replace("۰۱۲۳۴۵۶۷۸۹"[i],('0'.code+i).toChar()).replace("٠١٢٣٤٥٦٧٨٩"[i],('0'.code+i).toChar());return v
 }
 fun read(record:JSONObject,engine:GrowthEngine):SavedAssessment?=try {
  val raw=record.optString("report");val text=normalize(raw)
  if(!text.startsWith("گزارش سنجش رشدآوا"))null else {
   fun field(pattern:String)=Regex(pattern).find(text)?.groupValues?.get(1)?:error("Missing explicit report field")
   fun date(label:String):Long {
    val match=Regex("$label: ([0-9]{4})/([0-9]{2})/([0-9]{2})").find(text)?:error("date")
    val (y,m,d)=match.groupValues.drop(1).map{it.toInt()}
    return if(y<1700)SolarDate(y,m,d).toUtc() else Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply{clear();isLenient=false;set(y,m-1,d)}.timeInMillis
   }
   val birth=date("تولد");val visit=date("تاریخ سنجش");val days=field("سن: ([0-9]+) روز").toInt()
   require(visit>=birth&&(visit-birth)/86400000==days.toLong())
   val sexText=field("جنسیت: ([^\\n]+)");val sex=when{sexText.contains("دختر")->2;sexText.contains("پسر")->1;else->error("sex")}
   val weight=field("وزن: ([0-9.]+) کیلوگرم").toDouble();val height=field("قد ثبت‌شده: ([0-9.]+) سانتی").toDouble()
   val lying=if(days<731)!text.contains("سانتی‌متر افزایش") else days<1827&&text.contains("سانتی‌متر کاهش")
   val p=Patient(record.optString("name"),sex,days,weight,height,lying,premature=text.contains("مرجع: نوزاد نارس"),pregnant=text.contains("بارداری؛ ارزیابی اختصاصی"))
   val a=engine.assess(p);val reportedBmi=field("(?m)^BMI: ([0-9.]+)").toDouble()
   require(abs(reportedBmi-a.bmi)<.02)
   val extra=mutableListOf<Metric>()
   for(title in listOf("دور سر نسبت به سن","نسبت دور کمر به قد")){
    val status=Regex("(?m)^$title: ([^\\n]+)").find(text)?.groupValues?.get(1)
    if(status!=null)extra+=Metric(title,status,"نتیجهٔ متن اصلی حفظ شده است؛ اندازهٔ خام این شاخص در سابقهٔ قدیمی ثبت نشده و برای آن نمودار ساخته نمی‌شود.")
   }
   SavedAssessment(p,a.copy(metrics=a.metrics+extra,notes=a.notes+"این نمایش از ورودی‌های صریح گزارش قدیمی بازسازی شده است؛ متن گزارش اولیه نیز حفظ شده است."),birth,visit)
  }
 }catch(_:Exception){null}
}
