package ir.mbaghaee.bmi
import org.json.JSONArray
import org.json.JSONObject

object HistoryTimeline {
 fun profileId(record:JSONObject)=record.optString("patientId")
 fun records(array:JSONArray)=(0 until array.length()).mapNotNull{array.optJSONObject(it)}
 fun forPatient(array:JSONArray,id:String)=records(array).filter{id.isNotBlank()&&profileId(it)==id}.sortedWith(compareBy<JSONObject>{it.optLong("visitUtc",it.optLong("id"))}.thenBy{it.optLong("id")})
 fun compatible(saved:SavedAssessment,birth:Long?,sex:Int)=saved.birthUtc==birth&&saved.patient.sex==sex
 fun matchingMeasurement(array:JSONArray,id:String,patient:Patient,birth:Long?,visit:Long):JSONObject? = forPatient(array,id).firstOrNull{record->
  val saved=SavedAssessment.decode(record)
  saved!=null&&saved.birthUtc==birth&&saved.visitUtc==visit&&saved.patient.copy(name="")==patient.copy(name="")
 }
}
