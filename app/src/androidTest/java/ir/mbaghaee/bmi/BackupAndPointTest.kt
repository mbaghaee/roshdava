package ir.mbaghaee.bmi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupAndPointTest {
 private fun record(id:Long=1,profile:String="p",weight:Double=20.0,birth:Long=0):JSONObject {
  val patient=Patient("نمونه",2,2100,weight,118.0)
  val assessment=Assessment(14.36,"مرجع",listOf(Metric("قد","طبیعی","",0.0,indicator="hfa2007")),emptyList(),0.0,false)
  return SavedAssessment(patient,assessment,birth,birth+2100L*86400000).encode(id,"۱۴۰۵/۰۸/۲۵","گزارش",profile)
 }
 @Test fun backupRoundTripPreservesProfilesAndDraft(){val r=record();val form=JSONObject().put("name","فرم جاری").put("sex",2).put("selected",false);val restored=BackupCodec.decode(BackupCodec.encode(JSONArray().put(r),form,42));assertEquals(42L,restored.createdAt);assertEquals("فرم جاری",restored.draft.getString("name"));assertEquals(SavedAssessment.decode(r),SavedAssessment.decode(restored.records.getJSONObject(0)))}
 @Test(expected=BackupException::class) fun modifiedPayloadRejected(){val root=JSONObject(BackupCodec.encode(JSONArray().put(record()),JSONObject()));root.put("payload",root.getString("payload")+" ");BackupCodec.decode(root.toString())}
 @Test fun restoreDoesNotDuplicateSameVisit(){val r=record();val result=BackupCodec.merge(JSONArray().put(r),JSONArray().put(JSONObject(r.toString())));assertEquals(0,result.added);assertEquals(1,result.duplicates);assertEquals(1,result.records.length())}
 @Test fun conflictingVisitIdsPreserveBothMeasurements(){val result=BackupCodec.merge(JSONArray().put(record()),JSONArray().put(record(weight=21.0)));assertEquals(2,result.records.length());assertEquals(1,result.preservedConflicts);assertNotEquals(result.records.getJSONObject(0).getLong("id"),result.records.getJSONObject(1).getLong("id"))}
 @Test fun restoringConflictingBackupTwiceDoesNotDuplicate(){val incoming=JSONArray().put(record(weight=21.0));val first=BackupCodec.merge(JSONArray().put(record()),incoming);val second=BackupCodec.merge(first.records,incoming);assertEquals(0,second.added);assertEquals(1,second.duplicates);assertEquals(2,second.records.length())}
 @Test(expected=BackupException::class) fun incompatiblePatientIdentityRejected(){BackupCodec.merge(JSONArray().put(record()),JSONArray().put(record(2,birth=86400000)))}
 @Test fun unrelatedProfilesAreNotMergedByName(){val result=BackupCodec.merge(JSONArray().put(record()),JSONArray().put(record(profile="other")));assertEquals(2,BackupCodec.profileCount(result.records));assertEquals(1,result.added)}
 @Test fun emptyBackupIsValid(){val data=BackupCodec.decode(BackupCodec.encode(JSONArray(),JSONObject()));assertEquals(0,data.records.length())}
 @Test fun legacyReportSurvivesBackup(){val old=JSONObject().put("id",1).put("patientId","old").put("name","نمونه").put("report","متن نسخه قدیمی");val data=BackupCodec.decode(BackupCodec.encode(JSONArray().put(old),JSONObject()));assertEquals("متن نسخه قدیمی",data.records.getJSONObject(0).getString("report"))}
 @Test fun backupRetriesWithAVisitSavedDuringTheMerge(){
  var current=JSONArray().put(record(1)).toString()
  var attempts=0
  val (restored,draftRestored)=BackupCodec.mergeLatest(JSONArray().put(record(2,weight=21.0)),{current}){snapshot,plan->
   if(attempts++==0)current=JSONArray(current).put(record(3,weight=22.0)).toString()
   if(current!=snapshot)null else {
    current=plan.records.toString()
    false
   }
  }
  assertEquals(2,attempts)
  assertEquals(listOf(1L,3L,2L),HistoryTimeline.records(restored.records).map{it.getLong("id")})
  assertFalse(draftRestored)
 }
 @Test fun touchSelectsTheOldPointAndReportsAxisValues(){
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  instrumentation.runOnMainSync{
   val context=instrumentation.targetContext;val old=PlotPoint(10.0,20.0,304);val current=PlotPoint(20.0,30.0,609)
   val plot=ReferencePlot("سن · ماه","وزن · کیلوگرم",0.0,30.0,emptyList(),current,"",listOf(old))
   val chart=ReferenceChartView(context,plot);var selected:PlotPoint?=null;chart.onPointSelected={selected=it}
   val density=context.resources.displayMetrics.density;val width=(320*density).toInt();val height=(280*density).toInt()
   chart.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));chart.layout(0,0,width,height)
   chart.draw(Canvas(Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)))
   val left=48*density;val right=width-44*density;val top=38*density;val bottom=height-48*density
   val x=left+(right-left)/3;val y=bottom-(bottom-top)*(.8/11.6).toFloat()
   val down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,x,y,0);val up=MotionEvent.obtain(0,10,MotionEvent.ACTION_UP,x,y,0)
   assertTrue(chart.onTouchEvent(down));assertTrue(chart.onTouchEvent(up));down.recycle();up.recycle()
   assertEquals(old,selected);assertTrue(chart.describePoint(old).contains("محور افقی"));assertTrue(chart.describePoint(old).contains("محور عمودی"))
   var dismissed=false;chart.onSelectionCleared={dismissed=true}
   val outside=MotionEvent.obtain(0,20,MotionEvent.ACTION_DOWN,1f,1f,0)
   assertFalse(chart.onTouchEvent(outside));outside.recycle();assertTrue(dismissed)
  }
 }
 @Test fun percentileBackgroundUsesNeutralShadesOnBothSides(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync{
   val context=InstrumentationRegistry.getInstrumentation().targetContext
   val curves=(-3..3).map{z->ReferenceCurve(z,listOf(PlotPoint(0.0,z+4.0),PlotPoint(10.0,z+4.0)))}
   val plot=ReferencePlot("سن","وزن",0.0,10.0,curves,PlotPoint(5.0,4.0),"",bands=ReferenceBands.forIndicator("weianthro"))
   val chart=ReferenceChartView(context,plot);val density=context.resources.displayMetrics.density
   val width=(360*density).toInt();val height=(280*density).toInt()
   chart.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));chart.layout(0,0,width,height)
   val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);chart.draw(Canvas(bitmap))
   val left=48*density;val right=width-44*density;val top=38*density;val bottom=height-48*density
   fun pixel(value:Double)=bitmap.getPixel((left+(right-left)*.25f).toInt(),(bottom-(bottom-top)*(value-.5)/7.0).toInt())
   val middle=pixel(3.5);val outer=pixel(.75);val shoulder=pixel(1.5)
   for(color in listOf(middle,outer,shoulder)){
    assertTrue(kotlin.math.abs(android.graphics.Color.red(color)-android.graphics.Color.green(color))<=12)
    assertTrue(kotlin.math.abs(android.graphics.Color.green(color)-android.graphics.Color.blue(color))<=12)
   }
   assertEquals(outer,pixel(7.25));assertEquals(shoulder,pixel(6.5))
   assertTrue(android.graphics.Color.red(middle)>android.graphics.Color.red(shoulder))
   assertTrue(android.graphics.Color.red(shoulder)>android.graphics.Color.red(outer))
   bitmap.recycle()
  }
 }
}
