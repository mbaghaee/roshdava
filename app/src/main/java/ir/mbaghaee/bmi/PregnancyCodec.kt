package ir.mbaghaee.bmi

import org.json.JSONObject

/** Optional fields keep v2/v3 history and existing backups readable. */
object PregnancyCodec {
 fun encode(info:PregnancyInfo)=JSONObject().put("preWeight",info.preWeight).put("gestationDays",info.gestationDays).put("plurality",info.plurality).put("episodeId",info.episodeId).apply{info.fetusCount?.let{put("fetusCount",it)}}
 fun decodeInfo(o:JSONObject):PregnancyInfo {
  val count=if(o.has("fetusCount"))o.getInt("fetusCount").also{require(o.getDouble("fetusCount")==it.toDouble())} else null
  val info=PregnancyInfo(o.getDouble("preWeight"),o.getInt("gestationDays"),o.getInt("plurality"),o.getString("episodeId"),count)
  require(o.getDouble("gestationDays")==info.gestationDays.toDouble()&&o.getDouble("plurality")==info.plurality.toDouble())
  require(info.preWeight.isFinite()&&info.preWeight in .3..400.0&&info.gestationDays in 7..300&&info.plurality in 1..3&&info.episodeId.isNotBlank()&&info.episodeId.length<=200)
  require(count==null||info.plurality==3&&count in 3..12)
  return info
 }
 fun encode(value:PregnancyEvaluation):JSONObject {
  val o=JSONObject().put("preBmi",value.preBmi).put("gain",value.gain).put("category",value.category).put("status",value.status).put("detail",value.detail).put("referenceVersion",value.referenceVersion).put("guidance",value.guidance)
  value.totalLow?.let{o.put("totalLow",it)};value.totalHigh?.let{o.put("totalHigh",it)}
  value.weekLow?.let{o.put("weekLow",it)};value.weekHigh?.let{o.put("weekHigh",it)}
  return o
 }
 fun decodeEvaluation(o:JSONObject):PregnancyEvaluation {
  fun optional(key:String)=if(o.has(key)&&!o.isNull(key))o.getDouble(key).also{require(it.isFinite())} else null
  val value=PregnancyEvaluation(o.getDouble("preBmi"),o.getDouble("gain"),o.getString("category"),o.getString("status"),o.getString("detail"),optional("totalLow"),optional("totalHigh"),optional("weekLow"),optional("weekHigh"),o.getString("referenceVersion"),o.optString("guidance"))
  require(value.preBmi.isFinite()&&value.preBmi>0&&value.gain.isFinite())
  require((value.totalLow==null)==(value.totalHigh==null)&&(value.weekLow==null)==(value.weekHigh==null))
  require(value.totalLow==null||value.totalLow>=0&&value.totalLow<value.totalHigh!!)
  require(value.weekLow==null||value.weekLow>=0&&value.weekLow<value.weekHigh!!)
  return value
 }
}
