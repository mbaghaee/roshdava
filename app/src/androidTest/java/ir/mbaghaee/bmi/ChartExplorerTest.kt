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
  val p=ReferencePlot("هفته","تغییر وزن",0.0,40.0,emptyList(),PlotPoint(20.0,-1.0,10000),"−۱",
   listOf(PlotPoint(19.0,-2.0,9993)),listOf(ReferenceBand(2,null,BandTone.NEUTRAL)),
   envelope=ReferenceEnvelope(listOf(PlotPoint(13.0,.5),PlotPoint(40.0,12.0)),listOf(PlotPoint(13.0,2.0),PlotPoint(40.0,16.0))),minYValue=null,gestationalWeekAxis=true)
  assertEquals(p,ChartExplorerData.decode(JSONObject(ChartExplorerData.encode(p).toString())))
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
