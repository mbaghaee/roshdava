package ir.mbaghaee.bmi

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class GaugePresentation(
 val value:Double,val cuts:List<Double>,val colours:List<Int>,val labels:List<String>,
 val markerColour:Int,val valueText:String,val selectedBand:Int=-1,val dark:Boolean=false,
 val showEndpointTicks:Boolean=true
)

/** Shared renderer. Reference colours never depend on the current marker or diagnostic eligibility. */
abstract class SegmentedGaugeView(context:Context,attrs:AttributeSet?):View(context,attrs){
 private var presentation:GaugePresentation?=null
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 private val textPaint=TextPaint(Paint.ANTI_ALIAS_FLAG)
 private val regular=ResourcesCompat.getFont(context,R.font.vazirmatn_regular)
 private val bold=ResourcesCompat.getFont(context,R.font.vazirmatn_bold)
 private fun dp(v:Float)=v*resources.displayMetrics.density
 private fun sp(v:Float)=v*resources.displayMetrics.scaledDensity
 protected fun colour(id:Int)=context.getColor(id)
 protected fun showGauge(value:GaugePresentation?){
  if(value!=null){
   require(value.value.isFinite()&&value.cuts.size>=2)
   require(value.cuts.zipWithNext().all{(a,b)->a.isFinite()&&b.isFinite()&&a<b})
   require(value.colours.size==value.cuts.size-1&&value.labels.size==value.colours.size)
  }
  presentation=value;invalidate()
 }
 protected open fun previewGauge():GaugePresentation?=null
 init{importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;isFocusable=true}
 override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
  val base=max(suggestedMinimumHeight,resources.getDimensionPixelSize(R.dimen.assessment_gauge_height))
  val extra=max(0f,resources.configuration.fontScale-1f)*dp(48f)
  setMeasuredDimension(resolveSize(dp(280f).toInt()+paddingLeft+paddingRight,widthMeasureSpec),resolveSize(base+extra.toInt()+paddingTop+paddingBottom,heightMeasureSpec))
 }
 override fun onDraw(canvas:Canvas){
  super.onDraw(canvas)
  val model=presentation?:if(isInEditMode)previewGauge() else null
  if(model==null)return
  val left=paddingLeft+dp(8f);val right=width-paddingRight-dp(8f);val span=right-left
  if(span<dp(140f))return
  val low=model.cuts.first();val high=model.cuts.last()
  fun x(value:Double)=(left+span*(value-low)/(high-low)).toFloat()
  val marker=x(model.value.coerceIn(low,high))
  val muted=colour(if(model.dark)R.color.growth_hero_soft else R.color.growth_muted)
  val ink=colour(if(model.dark)R.color.white else R.color.growth_ink)
  textPaint.typeface=bold;textPaint.textSize=sp(18f);textPaint.textAlign=Paint.Align.CENTER
  val valueText=when{model.value<low->"← ${model.valueText}";model.value>high->"${model.valueText} →";else->model.valueText}
  while(textPaint.measureText(valueText)>span-dp(24f)&&textPaint.textSize>sp(12f))textPaint.textSize-=sp(1f)
  val fitted=TextUtils.ellipsize(valueText,textPaint,span-dp(24f),TextUtils.TruncateAt.END).toString()
  val badgeHeight=max(dp(34f),textPaint.descent()-textPaint.ascent()+dp(12f))
  val badgeWidth=min(span,max(dp(88f),textPaint.measureText(fitted)+dp(24f)))
  val badgeLeft=(marker-badgeWidth/2).coerceIn(left,max(left,right-badgeWidth))
  val badge=RectF(badgeLeft,paddingTop+dp(6f),badgeLeft+badgeWidth,paddingTop+dp(6f)+badgeHeight)
  paint.shader=null;paint.style=Paint.Style.FILL
  paint.color=if(model.dark)Color.WHITE else ColorUtils.blendARGB(Color.WHITE,model.markerColour,.1f)
  canvas.drawRoundRect(badge,dp(13f),dp(13f),paint)
  paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(1f)
  paint.color=ColorUtils.blendARGB(Color.WHITE,model.markerColour,.3f)
  canvas.drawRoundRect(badge,dp(13f),dp(13f),paint)
  textPaint.color=model.markerColour
  canvas.drawText(fitted,badge.centerX(),badge.centerY()-(textPaint.ascent()+textPaint.descent())/2,textPaint)
  val trackTop=badge.bottom+dp(18f)
  val track=RectF(left,trackTop,right,trackTop+dp(18f))
  val outline=Path().apply{addRoundRect(track,dp(9f),dp(9f),Path.Direction.CW)}
  canvas.save();canvas.clipPath(outline)
  for(i in model.colours.indices){
   val c=model.colours[i];paint.style=Paint.Style.FILL
   paint.shader=LinearGradient(0f,track.top,0f,track.bottom,ColorUtils.blendARGB(c,Color.WHITE,.24f),c,Shader.TileMode.CLAMP)
   canvas.drawRect(x(model.cuts[i]),track.top,x(model.cuts[i+1]),track.bottom,paint)
  }
  paint.shader=null;paint.color=Color.WHITE;paint.strokeWidth=dp(2f)
  for(i in 1 until model.cuts.lastIndex)canvas.drawLine(x(model.cuts[i]),track.top,x(model.cuts[i]),track.bottom,paint)
  canvas.restore()
  paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(1.5f);paint.color=model.markerColour
  canvas.drawLine(marker,badge.bottom+dp(4f),marker,track.bottom+dp(5f),paint)
  paint.style=Paint.Style.FILL;paint.color=Color.WHITE;canvas.drawCircle(marker,track.centerY(),dp(8f),paint)
  paint.color=model.markerColour;canvas.drawCircle(marker,track.centerY(),dp(5f),paint)
  textPaint.typeface=regular;textPaint.textSize=sp(11f);textPaint.color=muted
  val tickBaseline=track.bottom+dp(9f)-textPaint.ascent()
  val indices=if(model.showEndpointTicks)model.cuts.indices else 1 until model.cuts.lastIndex
  for(i in indices){
   textPaint.textAlign=when{!model.showEndpointTicks->Paint.Align.CENTER;i==0->Paint.Align.LEFT;i==model.cuts.lastIndex->Paint.Align.RIGHT;else->Paint.Align.CENTER}
   val tick=String.format(Locale.US,"%.1f",model.cuts[i]).trimEnd('0').trimEnd('.')
   canvas.drawText(SolarDate.persian(tick),x(model.cuts[i]),tickBaseline,textPaint)
  }
  val labelBaseline=tickBaseline+textPaint.descent()-textPaint.ascent()+dp(8f)
  textPaint.textAlign=Paint.Align.CENTER
  for(i in model.labels.indices){
   textPaint.typeface=if(i==model.selectedBand)bold else regular
   textPaint.color=if(i==model.selectedBand)ink else muted;textPaint.textSize=sp(11f)
   val room=max(dp(12f),x(model.cuts[i+1])-x(model.cuts[i])-dp(4f))
   while(textPaint.measureText(model.labels[i])>room&&textPaint.textSize>sp(9f))textPaint.textSize-=sp(.5f)
   val label=TextUtils.ellipsize(model.labels[i],textPaint,room,TextUtils.TruncateAt.END).toString()
   canvas.drawText(label,(x(model.cuts[i])+x(model.cuts[i+1]))/2,labelBaseline,textPaint)
  }
 }
}
