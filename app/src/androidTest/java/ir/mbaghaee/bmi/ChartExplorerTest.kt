package ir.mbaghaee.bmi

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ChartExplorerTest {
 @Test fun payloadPreservesCurvesNeutralBandsAndNegativePregnancyAxis(){
  val p=ReferencePlot("هفته","تغییر وزن",0.0,40.0,emptyList(),PlotPoint(20.0,-1.0,10000,11),"−۱",
   listOf(PlotPoint(19.0,-2.0,9993,10)),listOf(ReferenceBand(2,null,BandTone.NEUTRAL)),
   envelope=ReferenceEnvelope(listOf(PlotPoint(13.0,.5),PlotPoint(40.0,12.0)),listOf(PlotPoint(13.0,2.0),PlotPoint(40.0,16.0))),minYValue=null,gestationalWeekAxis=true)
  assertEquals(p,ChartExplorerData.decode(JSONObject(ChartExplorerData.encode(p).toString())))
 }
 @Test fun twoPointsOnOneDayShowTheirOwnRecords(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val name="chart-${UUID.randomUUID()}.json";val file=File(context.cacheDir,name)
  val patient=Patient("نمونه",2,2100,15.0,118.0)
  val assessment=Assessment(11.0,"مرجع",emptyList(),emptyList(),null,false)
  val records=JSONArray()
  for((id,weight) in listOf(1L to 15.0,2L to 16.0)){
   records.put(SavedAssessment(patient.copy(weight=weight),assessment,0L,2100L*86400000).encode(id,"","","p"))
  }
  val first=PlotPoint(69.0,15.0,2100,1)
  val second=PlotPoint(69.0,16.0,2100,2)
  val plot=ReferencePlot("سن","وزن",60.0,80.0,emptyList(),second,"",listOf(first))
  file.writeText(JSONObject().put("plot",ChartExplorerData.encode(plot)).put("birth",0)
   .put("title","روند وزن").put("records",records).toString())
  try{ActivityScenario.launch<ChartExplorerActivity>(Intent(context,ChartExplorerActivity::class.java)
   .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("file",name)).use{scenario->
    scenario.onActivity{activity->
     val rows=activity.findViewById<android.view.ViewGroup>(R.id.visits_container)
     assertEquals(2,rows.childCount)
     val values=(0 until rows.childCount).map{i->rows.getChildAt(i).findViewById<android.widget.TextView>(R.id.values).text.toString()}
     assertTrue(values[0].contains("وزن نوبت: ۱۵"))
     assertTrue(values[1].contains("وزن نوبت: ۱۶"))
    }
   }}finally{file.delete()}
 }
 @Test fun selectedPeriodAndZoomSurviveRecreation(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val name="chart-${UUID.randomUUID()}.json";val file=File(context.cacheDir,name)
  val p=ReferencePlot("سن · سال","وزن",20.0,30.0,emptyList(),PlotPoint(29.0,70.0,10500),"۷۰",listOf(PlotPoint(25.0,65.0,9000)))
  file.writeText(JSONObject().put("plot",ChartExplorerData.encode(p)).put("birth",0).put("title","روند وزن").put("records",JSONArray()).toString())
  try{ActivityScenario.launch<ChartExplorerActivity>(Intent(context,ChartExplorerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("file",name)).use{scenario->
   scenario.onActivity{it.findViewById<com.google.android.material.slider.RangeSlider>(R.id.range_slider).setValues(24f,30f);it.findViewById<android.widget.Spinner>(R.id.period_spinner).setSelection(1)}
   scenario.recreate()
   scenario.onActivity{
    assertEquals(listOf(24f,30f),it.findViewById<com.google.android.material.slider.RangeSlider>(R.id.range_slider).values)
    assertEquals(1,it.findViewById<android.widget.Spinner>(R.id.period_spinner).selectedItemPosition)
   }
  }}finally{file.delete()}
 }
}
