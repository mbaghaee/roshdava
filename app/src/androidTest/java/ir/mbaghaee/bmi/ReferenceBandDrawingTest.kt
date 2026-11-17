package ir.mbaghaee.bmi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ReferenceBandDrawingTest {
 @Test fun everyReferenceChartPaintsFiveRegionsOnFirstDrawZoomAndPayloadRoundTrip(){
  val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
  instrumentation.runOnMainSync{
   val density=context.resources.displayMetrics.density
   val width=(450*density).roundToInt();val height=(320*density).roundToInt()
   val curves=(-3..3).map{z->ReferenceCurve(z,listOf(PlotPoint(0.0,50.0+10*z),PlotPoint(10.0,50.0+10*z)))}
   for(indicator in listOf("bmi2007","bmianthro","wflanthro","wfhanthro","lenanthro","hfa2007","hcanthro","weianthro")){
    val original=ReferencePlot("سن","اندازه",0.0,10.0,curves,PlotPoint(5.0,50.0),"",bands=ReferenceBands.forIndicator(indicator))
    val view=ReferenceChartView(context,original)
    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height)
    for(plot in listOf(original,ChartViewport.slice(original,2.0,8.0),ChartExplorerData.decode(ChartExplorerData.encode(original)))){
     view.setPlot(plot)
     val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
     val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE);view.draw(canvas)
     val top=38*density;val bottom=height-48*density
     // Reference range 20..80 with 8% padding: 15.2..84.8. Sample away from curves, points and grid lines.
     for((z,fill) in listOf(-3.2 to R.color.growth_chart_outer_fill,-2.5 to R.color.growth_chart_side_fill,
      .5 to R.color.growth_chart_middle_fill,2.5 to R.color.growth_chart_side_fill,3.2 to R.color.growth_chart_outer_fill)){
      val y=(bottom-(bottom-top)*((50+10*z)-15.2)/69.6).roundToInt()
      val expected=ColorUtils.compositeColors(context.getColor(fill),Color.WHITE);val actual=bitmap.getPixel(width/2,y)
      assertTrue("$indicator at Z=$z: expected $expected, got $actual",abs(Color.red(expected)-Color.red(actual))<=2&&abs(Color.green(expected)-Color.green(actual))<=2&&abs(Color.blue(expected)-Color.blue(actual))<=2)
     }
     bitmap.recycle()
    }
   }
  }
 }
}
