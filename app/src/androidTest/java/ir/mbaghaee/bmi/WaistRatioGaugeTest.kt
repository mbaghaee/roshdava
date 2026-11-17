package ir.mbaghaee.bmi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaistRatioGaugeTest {
 @Test fun firstLowPointAndIneligibleResultsKeepReferenceColoursVisible(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync{
   val context=InstrumentationRegistry.getInstrumentation().targetContext
   val view=WaistRatioGaugeView(context);val density=context.resources.displayMetrics.density
   val width=(360*density).toInt();val height=(180*density).toInt()
   view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height)
   fun assertPalette(value:Double,status:String?){
    view.setRatio(value,status)
    val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
    var green=0;var red=0
    for(y in 0 until height)for(x in 0 until width){
     val c=bitmap.getPixel(x,y)
     if(Color.green(c)>Color.red(c)+20&&Color.green(c)>Color.blue(c))green++
     if(Color.red(c)>Color.green(c)*1.4&&Color.red(c)>Color.blue(c)+20)red++
    }
    bitmap.recycle();assertTrue("Green reference band missing",green>0);assertTrue("Red reference band missing",red>0)
   }
   assertPalette(.2,WaistAssessment.REVIEW)
   assertPalette(.2,WaistAssessment.UNCLASSIFIED)
   assertTrue(view.contentDescription.toString().contains("نشانگر خنثی"))
   assertPalette(.2,null)
   assertPalette(.541,WaistAssessment.INCREASED)
   assertPalette(.2,WaistAssessment.REVIEW)
  }
 }
 @Test fun endpointClippingDoesNotChangeTheReportedValue(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync{
   val view=WaistRatioGaugeView(InstrumentationRegistry.getInstrumentation().targetContext)
   for(value in listOf(.15,.95)){
    view.setRatio(value,if(value<.4)WaistAssessment.REVIEW else WaistAssessment.HIGH)
    assertTrue(view.contentDescription.toString().contains(SolarDate.persian(WaistAssessment.ratioNumber(value))))
   }
  }
 }
 @Test fun bmiCalloutUpdatesForAdultAndChildScores(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync{
   val view=BmiScaleView(InstrumentationRegistry.getInstrumentation().targetContext)
   view.setScore(24.9999,true,false)
   assertTrue(view.contentDescription.toString().contains("BMI"))
   assertTrue(view.contentDescription.toString().contains(SolarDate.persian("24.9999")))
   view.setScore(1.0001,false,true)
   assertTrue(view.contentDescription.toString().contains("Z"))
   assertTrue(view.contentDescription.toString().contains(SolarDate.persian("+1.0001")))
  }
 }
}
