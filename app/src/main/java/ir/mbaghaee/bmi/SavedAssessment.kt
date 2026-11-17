package ir.mbaghaee.bmi

import org.json.JSONArray
import org.json.JSONObject

/** Persist the original clinical result as well as inputs, so history never uses the current form. */
data class SavedAssessment(val patient:Patient,val assessment:Assessment,val birthUtc:Long,val visitUtc:Long) {
 fun encode(id:Long,displayDate:String,report:String,patientId:String=""):JSONObject {
  val p=patient;val a=assessment
  val input=JSONObject().put("name",p.name).put("sex",p.sex).put("days",p.days).put("weight",p.weight).put("height",p.height).put("lying",p.lying).put("premature",p.premature).put("pregnant",p.pregnant).put("waistRestricted",p.waistRestricted)
  p.head?.let{input.put("head",it)};p.waist?.let{input.put("waist",it)}
  p.pregnancy?.let{input.put("pregnancy",PregnancyCodec.encode(it))}
  val metrics=JSONArray();a.metrics.forEach{m->val item=JSONObject().put("title",m.title).put("status",m.status).put("detail",m.detail).put("severity",m.severity).put("indicator",m.indicator);m.z?.let{item.put("z",it)};metrics.put(item)}
  val result=JSONObject().put("bmi",a.bmi).put("group",a.group).put("adult",a.adult).put("metrics",metrics).put("notes",JSONArray(a.notes));a.score?.let{result.put("score",it)}
  a.pregnancy?.let{result.put("pregnancy",PregnancyCodec.encode(it))}
  val reference=if(a.pregnancy!=null)"WHO2006-2007_NICE-NG246_${a.pregnancy.referenceVersion}" else if(a.metrics.any{it.indicator==WaistAssessment.INDICATOR})"WHO2006-2007_NICE-NG246-2026" else "WHO2006-2007-20261116"
  return JSONObject().put("version",3).put("uiSchema",3).put("patientId",patientId).put("referenceVersion",reference).put("id",id).put("name",p.name.ifBlank{"بدون نام"}).put("date",displayDate).put("status",a.metrics.firstOrNull()?.status?:"").put("report",report).put("birthUtc",birthUtc).put("visitUtc",visitUtc).put("patient",input).put("assessment",result)
 }
 companion object {
  fun decode(record:JSONObject):SavedAssessment?=try {
   if(record.optInt("version") !in 2..3)null else {
    val p=record.getJSONObject("patient");val a=record.getJSONObject("assessment")
    fun optional(o:JSONObject,key:String)=if(o.has(key)&&!o.isNull(key))o.getDouble(key).takeIf{it.isFinite()} else null
    require(!p.has("waistRestricted")||p.opt("waistRestricted") is Boolean)
    val pregnancy=if(p.has("pregnancy"))PregnancyCodec.decodeInfo(p.getJSONObject("pregnancy")) else null
    val patient=Patient(p.optString("name"),p.getInt("sex"),p.getInt("days"),p.getDouble("weight"),p.getDouble("height"),p.optBoolean("lying"),optional(p,"head"),optional(p,"waist"),p.optBoolean("premature"),p.optBoolean("pregnant"),p.optBoolean("waistRestricted"),pregnancy)
    require(pregnancy==null||patient.pregnant&&patient.sex==2)
    val array=a.getJSONArray("metrics");val metrics=(0 until array.length()).map{i->val m=array.getJSONObject(i);Metric(m.getString("title"),m.getString("status"),m.optString("detail"),optional(m,"z"),m.optInt("severity"),m.optString("indicator"))}
    val pregnancyResult=if(a.has("pregnancy"))PregnancyCodec.decodeEvaluation(a.getJSONObject("pregnancy")) else null
    require(pregnancyResult==null||patient.pregnant&&pregnancy!=null)
    val n=a.optJSONArray("notes")?:JSONArray();val result=Assessment(a.getDouble("bmi"),a.getString("group"),metrics,(0 until n.length()).map{n.getString(it)},optional(a,"score"),a.getBoolean("adult"),pregnancyResult)
    require(patient.sex in 1..2&&patient.days>=0&&patient.weight>0&&patient.height>0&&patient.weight.isFinite()&&patient.height.isFinite()&&result.bmi.isFinite())
    SavedAssessment(patient,result,record.getLong("birthUtc"),record.getLong("visitUtc"))
   }
  }catch(_:Exception){null}
 }
}
