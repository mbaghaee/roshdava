package ir.mbaghaee.bmi

import android.os.Bundle
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

enum class GrowthScreen { FORM, RESULT, HISTORY, BACKUP }

/** Screen/form data only: contains no Activity or View, and uses the existing backup-compatible draft schema. */
class AssessmentUiState {
 var activePatientId=""
 var openedProfileId:String?=null
 var viewedRecord:JSONObject?=null
 var resultSnapshot:JSONObject?=null
 var resultFromHistory=false
 var editingRecordId=0L
 var screen=GrowthScreen.FORM
 var draft=JSONObject()
 var sex=2
 var birth=utc(2000,0,1)
 var visit=today()
 var selectedBirth=false
 var pregnancyId=""

 fun restoreForm(form:JSONObject){
  draft=form;birth=form.optLong("birth",birth);visit=form.optLong("visit",visit)
  selectedBirth=form.optBoolean("selected");sex=form.optInt("sex",2);activePatientId=form.optString("patientId")
  pregnancyId=form.optString("pregnancyId")
 }
 fun save(out:Bundle,scrollY:Int){
  out.putString("draft",draft.toString());out.putString("screen",screen.name)
  out.putString("profile",openedProfileId);out.putInt("scrollY",scrollY);out.putLong("editingRecordId",editingRecordId)
  if(screen==GrowthScreen.RESULT){
   if(resultFromHistory)out.putString("viewedRecord",viewedRecord?.toString())
   else out.putString("resultSnapshot",resultSnapshot?.toString())
  }
 }
 companion object {
  private fun utc(y:Int,m:Int,d:Int)=Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply{clear();set(y,m,d)}.timeInMillis
  private fun today():Long {val c=Calendar.getInstance();return utc(c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH))}
 }
}
