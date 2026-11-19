package ir.mbaghaee.bmi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class PatientTimelineTest {
 private val engine by lazy{val assets=InstrumentationRegistry.getInstrumentation().targetContext.assets;GrowthEngine(listOf("bmianthro","lenanthro","weianthro","wfhanthro","wflanthro","hcanthro","bmi2007","hfa2007").associateWith{GrowthEngine.parse(assets.open("$it.csv").bufferedReader().use{it.readText()})})}
 private fun utc(y:Int,m:Int,d:Int)=Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply{clear();set(y,m-1,d)}.timeInMillis
 @Test fun recoverExplicitLegacyReport(){
  val birth=utc(2020,11,16);val visit=utc(2026,11,16);val days=(visit-birth)/86400000
  val report="گزارش سنجش رشدآوا\nتاریخ سنجش: 2026/11/16\nتولد: 2020/11/16 (میلادی)\nسن: $days روز\nجنسیت: دختر / زن\nوزن: 29.0 کیلوگرم | قد ثبت‌شده: 125.0 سانتی‌متر\nBMI: 18.56\n"
  val saved=LegacyAssessmentReader.read(JSONObject().put("name","نمونه").put("report",report),engine)!!
  assertEquals(visit,saved.visitUtc);assertEquals(29.0,saved.patient.weight,0.0)
  assertNotNull(engine.plotFor(saved.patient,saved.assessment.metrics[0]))
  assertNotNull(engine.plotFor(saved.patient,saved.assessment.metrics[1]))
 }
 @Test fun incompleteLegacyDoesNotInventData(){assertNull(LegacyAssessmentReader.read(JSONObject().put("report","گزارش سنجش رشدآوا\nBMI: 18"),engine))}
 @Test fun namesDoNotMergeDifferentProfiles(){
  val array=JSONArray().put(JSONObject().put("id",1).put("name","همنام").put("patientId","a")).put(JSONObject().put("id",2).put("name","همنام").put("patientId","b")).put(JSONObject().put("id",3).put("patientId","a"))
  assertEquals(listOf(1L,3L),HistoryTimeline.forPatient(array,"a").map{it.getLong("id")})
 }
 @Test fun duplicateMeasurementIgnoresReportIdAndDisplayName(){
  val p=Patient("نام قبلی",2,2100,21.0,118.0);val birth=utc(2020,1,1);val visit=birth+2100L*86400000
  val record=SavedAssessment(p,engine.assess(p),birth,visit).encode(1,"","","profile")
  val records=JSONArray().put(record)
  assertNotNull(HistoryTimeline.matchingMeasurement(records,"profile",p.copy(name="نام اصلاح شده"),birth,visit))
  assertNull(HistoryTimeline.matchingMeasurement(records,"other",p,birth,visit))
  assertNull(HistoryTimeline.matchingMeasurement(records,"profile",p.copy(weight=21.1),birth,visit))
  assertNull(HistoryTimeline.matchingMeasurement(records,"profile",p,birth,visit+86400000))
  assertNull(HistoryTimeline.matchingMeasurement(records,"profile",p.copy(lying=true),birth,visit))
 }
 @Test fun referencesIncludeEarlierMeasurements(){
  val current=Patient("",2,2300,23.0,122.0);val previous=listOf(current.copy(days=2100,weight=21.0,height=118.0),current.copy(days=2200,weight=22.0,height=120.0))
  for(m in engine.assess(current).metrics){val plot=engine.plotFor(current,m,visits=previous.mapIndexed{i,p->ChartVisit(i+1L,p)})!!;assertEquals(2,plot.history.size);assertTrue(plot.history.all{it.x in plot.xMin..plot.xMax});assertEquals(listOf(2100,2200),plot.history.map{it.day})}
 }
 @Test fun trendDoesNotLosePointsAcrossReferenceTransition(){
  val current=Patient("",2,1900,19.0,111.0);val previous=listOf(current.copy(days=1800,height=108.0,weight=18.0))
  val plot=engine.measurementTrend(current,"height",previous.map{ChartVisit(1,it)});assertEquals(1,plot.history.size);assertTrue(plot.curves.isEmpty());assertEquals(108.0,plot.history[0].y,0.0)
 }
 @Test fun inconsistentDemographicsAreNotCompatible(){val p=Patient("",2,2100,21.0,118.0);val s=SavedAssessment(p,engine.assess(p),1000,2000);assertTrue(HistoryTimeline.compatible(s,1000,2));assertFalse(HistoryTimeline.compatible(s,1001,2));assertFalse(HistoryTimeline.compatible(s,1000,1))}
 @Test fun optionalMissingFlagsDoNotHideWholeReport(){
  val p=Patient("",2,2100,21.0,118.0);val record=SavedAssessment(p,engine.assess(p),1000,2000).encode(1,"","","profile")
  for(key in listOf("lying","premature","pregnant"))record.getJSONObject("patient").remove(key)
  assertNotNull(SavedAssessment.decode(record))
 }
 @Test fun waistConditionsAndOriginalResultSurviveBackup(){
  val birth=utc(2020,1,1);val p=Patient("نمونه",2,2200,22.0,120.0,waist=66.0,waistRestricted=true)
  val saved=SavedAssessment(p,engine.assess(p),birth,birth+2200L*86400000)
  val record=saved.encode(15,"","گزارش","profile")
  val data=BackupCodec.decode(BackupCodec.encode(JSONArray().put(record),JSONObject().put("waistRestricted",true)))
  assertEquals(saved,SavedAssessment.decode(data.records.getJSONObject(0)))
  assertTrue(data.draft.getBoolean("waistRestricted"))
  assertEquals(WaistAssessment.UNCLASSIFIED,SavedAssessment.decode(data.records.getJSONObject(0))!!.assessment.metrics.first{it.indicator==WaistAssessment.INDICATOR}.status)
 }
 @Test fun legacyWaistReportDoesNotGainANewClinicalLabel(){
  val birth=utc(2000,1,1);val p=Patient("نمونه",2,10000,65.0,170.0,waist=90.0)
  val original=engine.assess(p).copy(metrics=listOf(Metric("نسبت دور کمر به قد","0.53","ثبت عدد")))
  val record=SavedAssessment(p,original,birth,birth+10000L*86400000).encode(16,"","گزارش قدیمی","profile")
  record.put("version",2);record.getJSONObject("patient").remove("waistRestricted")
  val decoded=SavedAssessment.decode(record)!!
  assertFalse(decoded.patient.waistRestricted);assertEquals("0.53",decoded.assessment.metrics.first().status)
  assertFalse(decoded.assessment.metrics.any{it.indicator==WaistAssessment.INDICATOR})
 }
 @Test fun pregnancyDataAndOriginalEvaluationSurviveBackup(){
  val birth=utc(1995,1,1)
  val p=Patient("نمونه",2,10000,65.0,170.0,pregnant=true,pregnancy=PregnancyInfo(60.0,140,1,"pregnancy-1"))
  val saved=SavedAssessment(p,engine.assess(p),birth,birth+10000L*86400000)
  val record=saved.encode(22,"","گزارش","profile")
  val draft=JSONObject().put("preg",true).put("pregnancyId","pregnancy-1").put("prePregnancyWeight","60").put("pregnancyWeek","20").put("pregnancyDay","0").put("pregnancyPlurality",1)
  val data=BackupCodec.decode(BackupCodec.encode(JSONArray().put(record),draft))
  assertEquals(saved,SavedAssessment.decode(data.records.getJSONObject(0)))
  val state=AssessmentUiState();state.restoreForm(data.draft);assertEquals("pregnancy-1",state.pregnancyId)
 }
 @Test fun oldPregnancyWithoutInputsRemainsUnclassified(){
  val p=Patient("نمونه",2,10000,65.0,170.0,pregnant=true)
  val record=SavedAssessment(p,engine.assess(p),0,10000L*86400000).encode(23,"","گزارش قدیمی","profile")
  record.put("version",2)
  val decoded=SavedAssessment.decode(record)!!
  assertNull(decoded.patient.pregnancy);assertNull(decoded.assessment.pregnancy)
 }
 @Test fun fractionalGestationInBackupIsRejected(){
  val p=Patient("نمونه",2,10000,65.0,170.0,pregnant=true,pregnancy=PregnancyInfo(60.0,140,1,"episode"))
  val record=SavedAssessment(p,engine.assess(p),0,10000L*86400000).encode(24,"","گزارش","profile")
  record.getJSONObject("patient").getJSONObject("pregnancy").put("gestationDays",140.5)
  assertNull(SavedAssessment.decode(record))
 }
 @Test fun higherOrderCountAndCautionSurviveBackupAndFollowUp(){
  val p=Patient("نمونه",2,10000,68.0,170.0,pregnant=true,pregnancy=PregnancyInfo(60.0,140,3,"multiple",4))
  val saved=SavedAssessment(p,engine.assess(p),0,10000L*86400000)
  val record=saved.encode(25,"","گزارش","profile")
  val data=BackupCodec.decode(BackupCodec.encode(JSONArray().put(record),JSONObject().put("fetusCount","4")))
  assertEquals(saved,SavedAssessment.decode(data.records.getJSONObject(0)))
  assertEquals("4",data.draft.getString("fetusCount"))
  assertEquals(4,PregnancyAssessment.advance(p.pregnancy!!,7)!!.fetusCount)
  record.getJSONObject("patient").getJSONObject("pregnancy").put("fetusCount",4.5)
  assertNull(SavedAssessment.decode(record))
 }
}
