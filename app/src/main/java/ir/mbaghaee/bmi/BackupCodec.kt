package ir.mbaghaee.bmi

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

class BackupException(message:String):IllegalArgumentException(message)
data class BackupData(val records:JSONArray,val draft:JSONObject,val createdAt:Long)
data class RestorePlan(val records:JSONArray,val added:Int,val duplicates:Int,val preservedConflicts:Int)

/** A portable JSON backup; checksum detects truncation/corruption, not sender identity. */
object BackupCodec {
 const val MAX_BYTES=16*1024*1024
 val io=Executors.newSingleThreadExecutor()
 private fun hash(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 255)}
 private fun check(ok:Boolean,message:String){if(!ok)throw BackupException(message)}
 fun encode(records:JSONArray,draft:JSONObject,createdAt:Long=System.currentTimeMillis()):String {
  validate(records);validateDraft(draft)
  val payload=JSONObject().put("records",records).put("draft",draft).toString()
  val text=JSONObject().put("format","roshdava-backup").put("version",1).put("createdAt",createdAt).put("payload",payload).put("sha256",hash(payload)).toString(2)
  check(text.toByteArray(Charsets.UTF_8).size<=MAX_BYTES,"حجم پشتیبان بیش از ۱۶ مگابایت است.");return text
 }
 fun decode(text:String):BackupData=try{
  check(text.toByteArray(Charsets.UTF_8).size<=MAX_BYTES,"فایل پشتیبان بیش از ۱۶ مگابایت است.")
  val root=JSONObject(text)
  check(root.optString("format")=="roshdava-backup"&&root.optInt("version")==1,"این فایل، پشتیبان سازگار رشدآوا نیست.")
  val payload=root.getString("payload")
  check(MessageDigest.isEqual(hash(payload).toByteArray(),root.getString("sha256").toByteArray()),"فایل پشتیبان ناقص یا تغییرکرده است.")
  val data=JSONObject(payload);val records=data.getJSONArray("records");val draft=data.optJSONObject("draft")?:JSONObject()
  validate(records);validateDraft(draft);BackupData(records,draft,root.optLong("createdAt"))
 }catch(e:BackupException){throw e}catch(_:Exception){throw BackupException("ساختار فایل پشتیبان معتبر نیست.")}
 fun read(input:InputStream):BackupData {
  val out=ByteArrayOutputStream();val buffer=ByteArray(8192);var size=0
  while(true){val count=input.read(buffer);if(count<0)break;size+=count;check(size<=MAX_BYTES,"فایل پشتیبان بیش از ۱۶ مگابایت است.");out.write(buffer,0,count)}
  return decode(out.toString("UTF-8"))
 }
 fun profileCount(records:JSONArray)=HistoryTimeline.records(records).map{it.optString("patientId")}.distinct().size
 private fun validateDraft(draft:JSONObject){
  for(key in listOf("name","weight","height","head","waist","patientId","pregnancyId","prePregnancyWeight","pregnancyWeek","pregnancyDay","fetusCount"))check(!draft.has(key)||draft.opt(key) is String,"فرم ذخیره‌شده در پشتیبان نامعتبر است.")
  for(key in listOf("selected","lying","pre","preg","waistRestricted"))check(!draft.has(key)||draft.opt(key) is Boolean,"فرم ذخیره‌شده در پشتیبان نامعتبر است.")
  for(key in listOf("birth","visit","sex","pregnancyPlurality"))check(!draft.has(key)||draft.opt(key) is Number,"تاریخ یا مشخصات فرم ذخیره‌شده نامعتبر است.")
  if(draft.optBoolean("selected")){
   check(draft.optInt("sex") in 1..2,"جنسیت فرم ذخیره‌شده نامعتبر است.")
   val birth=draft.optLong("birth");val visit=draft.optLong("visit")
   // A partially completed form may have invalid date order; preserve it for editing, but bound calendar years.
   check(birth in -2208988800000L..7258118400000L&&visit in -2208988800000L..7258118400000L,"تاریخ فرم ذخیره‌شده خارج از محدوده است.")
  }
 }
 private fun validate(records:JSONArray){
  val identities=mutableMapOf<String,Pair<Long,Int>>()
  for(i in 0 until records.length()){
   val r=records.optJSONObject(i)?:throw BackupException("یک سنجش داخل فایل نامعتبر است.")
   val id=r.optString("patientId");check(id.isNotBlank()&&id.length<=200&&r.optLong("id")>0,"شناسهٔ پرونده یا سنجش نامعتبر است.")
   check(r.opt("report") is String&&r.opt("name") is String,"اطلاعات یک گزارش ناقص است.")
   if(r.has("patient")||r.has("assessment")){
    val saved=SavedAssessment.decode(r)?:throw BackupException("دادهٔ ساختارمند یکی از سنجش‌ها ناقص است.")
    val p=saved.patient;check(p.days in 0..43920&&p.weight in .3..400.0&&p.height in 25.0..250.0&&saved.assessment.bmi>0,"مقادیر یک سنجش نامعتبر است.")
    check(saved.birthUtc in -2208988800000L..7258118400000L&&saved.visitUtc>=saved.birthUtc&&(saved.visitUtc-saved.birthUtc)/86400000==p.days.toLong(),"تاریخ و سن یک سنجش سازگار نیست.")
    val identity=saved.birthUtc to p.sex;check(identities[id]==null||identities[id]==identity,"تاریخ تولد یا جنسیت در یک پرونده یکسان نیست.");identities[id]=identity
   }
  }
 }
 private fun sameVisit(a:JSONObject,b:JSONObject):Boolean {
  val x=SavedAssessment.decode(a);val y=SavedAssessment.decode(b)
  if(x!=null&&y!=null)return x.patient==y.patient&&x.birthUtc==y.birthUtc&&x.visitUtc==y.visitUtc
  return a.optString("report")==b.optString("report")
 }
 fun merge(existing:JSONArray,incoming:JSONArray):RestorePlan {
  validate(existing);validate(incoming)
  val result=JSONArray(existing.toString());val lookup=mutableMapOf<String,JSONObject>();val sources=mutableMapOf<String,MutableList<JSONObject>>()
  fun key(r:JSONObject)=r.getString("patientId")+":"+r.getLong("id")
  for(r in HistoryTimeline.records(result)){lookup[key(r)]=r;sources.getOrPut(r.optString("restoreSourceKey").ifBlank{key(r)}){mutableListOf()}.add(r)}
  val demographics=mutableMapOf<String,Pair<Long,Int>>()
  for(r in HistoryTimeline.records(result))SavedAssessment.decode(r)?.let{demographics[r.getString("patientId")]=it.birthUtc to it.patient.sex}
  var next=HistoryTimeline.records(result).maxOfOrNull{it.optLong("id")}?:0L;var added=0;var duplicates=0;var conflicts=0
  for(raw in HistoryTimeline.records(incoming)){
   val record=JSONObject(raw.toString());val id=record.getString("patientId")
   SavedAssessment.decode(record)?.let{saved->val identity=saved.birthUtc to saved.patient.sex;check(demographics[id]==null||demographics[id]==identity,"شناسهٔ یک پرونده با تاریخ تولد یا جنسیت متفاوت موجود است؛ بازیابی انجام نشد.");demographics[id]=identity}
   val previous=lookup[key(record)];val source=record.optString("restoreSourceKey").ifBlank{key(record)}
   if((previous!=null&&sameVisit(previous,record))||sources[source]?.any{sameVisit(it,record)}==true){duplicates++;continue}
   if(previous!=null){check(next<Long.MAX_VALUE,"شناسه سنجش قابل تخصیص نیست.");do{next++;record.put("id",next)}while(lookup.containsKey(key(record))&&next<Long.MAX_VALUE);record.put("restoreSourceKey",source);conflicts++}
   next=maxOf(next,record.getLong("id"));result.put(record);lookup[key(record)]=record;sources.getOrPut(source){mutableListOf()}.add(record);added++
  }
  validate(result);return RestorePlan(result,added,duplicates,conflicts)
 }
}
