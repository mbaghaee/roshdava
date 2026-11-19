package ir.mbaghaee.bmi

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import androidx.core.content.res.ResourcesCompat
import java.util.Locale
import kotlin.math.*

/** Actual WHO reference curves; the blue point is the current measurement, not a trend. */
class ReferenceChartView private constructor(context:Context,initial:ReferencePlot,attrs:AttributeSet?):View(context,attrs){
 constructor(context:Context,initial:ReferencePlot):this(context,initial,null)
 constructor(context:Context,attrs:AttributeSet?):this(context,ReferencePlot("سن","اندازه",0.0,1.0,emptyList(),PlotPoint(.5,.5),""),attrs){xmlPlaceholder=true}
 private var xmlPlaceholder=false
 private var plot=initial
 var onPointSelected:((PlotPoint)->Unit)?=null
 var onSelectionCleared:(()->Unit)?=null
 fun clearSelection(){showPointTooltip=false;armedPoint=null;onSelectionCleared?.invoke();invalidate()}
 private var selectedPoint=initial.point
 private var showPointTooltip=false
 private var hitTargets:List<Pair<PlotPoint,PointF>> = emptyList()
 private var armedPoint:PlotPoint?=null
 private var downX=0f;private var downY=0f
 private val touchSlop=ViewConfiguration.get(context).scaledTouchSlop
 fun describePoint(point:PlotPoint):String {
  fun exact(value:Double,digits:Int=2)=SolarDate.persian((if(digits==3)WaistAssessment.ratioNumber(value) else String.format(Locale.US,"%.${digits}f",value)).trimEnd('0').trimEnd('.'))
  val age=if(plot.dateBasedAge)"" else if(point.day/30.4375>=228)"${if(plot.gestationalWeekAxis)"سن مادر" else "سن"}: ${exact(point.day/365.25)} سال" else "سن دقیق: ${SolarDate.persian(point.day.toString())} روز"
  val horizontal=if(plot.gestationalWeekAxis)PregnancyAssessment.weekLabel((point.x*7).roundToInt()) else exact(point.x)
  return "محور افقی · ${plot.xLabel}: $horizontal\nمحور عمودی · ${plot.yLabel}: ${exact(point.y,plot.yDecimals?:2)}"+if(age.isEmpty())"" else "\n$age"
 }
 override fun onTouchEvent(event:MotionEvent):Boolean {
  when(event.actionMasked){
   MotionEvent.ACTION_DOWN->{
    val nearest=hitTargets.minByOrNull{(_,position)->hypot((position.x-event.x).toDouble(),(position.y-event.y).toDouble())}
    if(nearest==null||hypot((nearest.second.x-event.x).toDouble(),(nearest.second.y-event.y).toDouble())>dp(32f)){clearSelection();return false}
    downX=event.x;downY=event.y;armedPoint=nearest.first;return true
   }
   MotionEvent.ACTION_MOVE->{if(abs(event.x-downX)>touchSlop||abs(event.y-downY)>touchSlop)armedPoint=null;return true}
   MotionEvent.ACTION_UP->{if(armedPoint!=null){performClick();return true};return false}
   MotionEvent.ACTION_CANCEL->{armedPoint=null;return false}
  }
  return super.onTouchEvent(event)
 }
 override fun performClick():Boolean {
  super.performClick()
  val target=armedPoint?:hitTargets.takeIf{it.isNotEmpty()}?.let{it[(it.indexOfFirst{pair->pair.first==selectedPoint}+1).mod(it.size)].first}
  armedPoint=null
  if(target!=null){selectedPoint=target;showPointTooltip=true;onPointSelected?.invoke(target);announceForAccessibility(describePoint(target));invalidate()}
  return true
 }
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=ResourcesCompat.getFont(context,R.font.vazirmatn_regular)}
 private fun dp(n:Float)=n*resources.displayMetrics.density
 private val median=context.getColor(R.color.growth_chart_middle_line)
 private val referenceLine=context.getColor(R.color.growth_chart_inner_line)
 private fun curveColor(z:Int)=if(z==0)median else referenceLine
 init{isClickable=true;isFocusable=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;updateDescription()}
 fun setPlot(value:ReferencePlot){xmlPlaceholder=false;plot=value;selectedPoint=value.point;showPointTooltip=false;hitTargets=emptyList();updateDescription();onSelectionCleared?.invoke();invalidate()}
 fun snapshotPlot()=plot
 private fun updateDescription(){val kind=if(plot.envelope!=null)"نمودار راهنمای وزن‌گیری بارداری" else if(plot.curves.isEmpty())"نمودار سنجش‌ها" else "نمودار مرجع";contentDescription="$kind ${plot.yLabel} در برابر ${plot.xLabel}. نقطهٔ پررنگ: ${plot.pointText}؛ ${plot.history.size} سنجش دیگر در نمودار"}
 private fun number(v:Double,decimals:Int=if(abs(v)<10)1 else 0)=SolarDate.persian(String.format(Locale.US,"%.${decimals}f",v))
 override fun onDraw(canvas:Canvas){
  super.onDraw(canvas);if(width<dp(180f))return
  if(xmlPlaceholder){if(isInEditMode){paint.color=Color.rgb(93,115,127);paint.textSize=14*resources.displayMetrics.scaledDensity;paint.textAlign=Paint.Align.CENTER;canvas.drawText("نمودار مرجع / روند سنجش‌ها",width/2f,height/2f,paint)};return}
  val left=dp(48f);val right=width-dp(44f);val top=dp(38f);val bottom=height-dp(48f)
  if(bottom<=top||right<=left)return
  val ys=plot.curves.flatMap{it.points}.map{it.y}+ChartViewport.points(plot).map{it.y}+(plot.envelope?.let{(it.lower+it.upper).map{p->p.y}}?:emptyList())
  val rawMin=ys.minOrNull()?:return;val rawMax=ys.maxOrNull()?:return
  val pad=max((rawMax-rawMin)*.08,plot.minYPadding);val yMin=plot.minYValue?.let{max(it,rawMin-pad)}?:rawMin-pad;val yMax=rawMax+pad
  fun px(x:Double)=(left+(right-left)*(x-plot.xMin)/(plot.xMax-plot.xMin)).toFloat()
  fun py(y:Double)=(bottom-(bottom-top)*(y-yMin)/(yMax-yMin)).toFloat()
  hitTargets=ChartViewport.points(plot).map{it to PointF(px(it.x),py(it.y))}
  paint.style=Paint.Style.FILL;paint.textSize=dp(12f);paint.color=Color.rgb(93,115,127);paint.textAlign=Paint.Align.LEFT
  canvas.drawText(plot.yLabel,left,dp(19f),paint);paint.textSize=dp(10f)
  for(i in 0..4){
   val value=yMin+(yMax-yMin)*i/4;val y=py(value)
   paint.color=Color.rgb(228,235,237);paint.strokeWidth=dp(1f);canvas.drawLine(left,y,right,y,paint)
   paint.color=Color.rgb(93,115,127);paint.textAlign=Paint.Align.RIGHT;canvas.drawText(number(value,plot.yDecimals?:if(abs(value)<10)1 else 0),left-dp(7f),y+dp(3f),paint)
   val x=plot.xMin+(plot.xMax-plot.xMin)*i/4
   val xText=if(plot.gestationalWeekAxis)number(x,if(abs(x-round(x))<1e-8)0 else 1) else if(plot.xLabel=="سن · سال")number(x,if(plot.xMax-plot.xMin<1)2 else if(plot.xMax-plot.xMin<5)1 else 0) else number(x)
   paint.textAlign=Paint.Align.CENTER;canvas.drawText(xText,px(x),bottom+dp(19f),paint)
  }
  canvas.save();canvas.clipRect(left,top,right,bottom)
  plot.envelope?.let{band->
   if(band.lower.size>1&&band.upper.size>1){
    val area=Path();band.lower.forEachIndexed{i,p->if(i==0)area.moveTo(px(p.x),py(p.y)) else area.lineTo(px(p.x),py(p.y))}
    band.upper.asReversed().forEach{area.lineTo(px(it.x),py(it.y))};area.close()
    paint.shader=null;paint.style=Paint.Style.FILL;paint.color=Color.argb(42,0,116,112);canvas.drawPath(area,paint)
    for(edge in listOf(band.lower,band.upper)){
     val path=Path();edge.forEachIndexed{i,p->if(i==0)path.moveTo(px(p.x),py(p.y)) else path.lineTo(px(p.x),py(p.y))}
     paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(1.5f);paint.color=median;canvas.drawPath(path,paint)
    }
   }
  }
  if(yMin<0&&yMax>0){paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(1f);paint.color=Color.rgb(146,165,173);canvas.drawLine(left,py(0.0),right,py(0.0),paint)}
  if(plot.curves.isNotEmpty()){
  for(band in plot.bands){
   fun edge(z:Int?,bottomEdge:Boolean):List<PlotPoint> = if(z==null)plot.curves.first().points.map{PlotPoint(it.x,if(bottomEdge)yMin else yMax)} else plot.curves.firstOrNull{it.z==z}?.points?:emptyList()
   val lower=edge(band.lowerZ,true);val upper=edge(band.upperZ,false)
   if(lower.isEmpty()||upper.isEmpty())continue
   val region=Path();lower.forEachIndexed{i,p->if(i==0)region.moveTo(px(p.x),py(p.y)) else region.lineTo(px(p.x),py(p.y))};upper.asReversed().forEach{region.lineTo(px(it.x),py(it.y))};region.close()
   paint.style=Paint.Style.FILL;paint.color=context.getColor(when(band.tone){BandTone.NORMAL->R.color.growth_chart_middle_fill;BandTone.CAUTION->R.color.growth_chart_side_fill;BandTone.ALERT->R.color.growth_chart_outer_fill;BandTone.NEUTRAL->R.color.growth_chart_neutral_fill});canvas.drawPath(region,paint)
  }
  for(curve in plot.curves){
   val path=Path();curve.points.forEachIndexed{i,p->if(i==0)path.moveTo(px(p.x),py(p.y)) else path.lineTo(px(p.x),py(p.y))}
   paint.style=Paint.Style.STROKE;paint.color=curveColor(curve.z);paint.strokeWidth=dp(if(curve.z==0)2.2f else 1.2f);canvas.drawPath(path,paint)
  }
  }
  val sequence=ChartViewport.points(plot).sortedBy{it.day}
  if(sequence.size>1){val line=Path();sequence.forEachIndexed{i,p->if(i==0)line.moveTo(px(p.x),py(p.y)) else line.lineTo(px(p.x),py(p.y))};paint.style=Paint.Style.STROKE;paint.color=Color.rgb(77,111,180);paint.strokeWidth=dp(2f);canvas.drawPath(line,paint)}
  canvas.restore()
  for(p in plot.history){paint.style=Paint.Style.FILL;paint.color=Color.WHITE;canvas.drawCircle(px(p.x),py(p.y),dp(5f),paint);paint.color=Color.rgb(99,132,183);canvas.drawCircle(px(p.x),py(p.y),dp(3.5f),paint)}
  val dotX=px(plot.point.x);val dotY=py(plot.point.y)
  if(plot.showCurrentPoint){paint.style=Paint.Style.FILL;paint.color=Color.WHITE;canvas.drawCircle(dotX,dotY,dp(7f),paint);paint.color=Color.rgb(44,75,172);canvas.drawCircle(dotX,dotY,dp(4.5f),paint)}
  val selectedX=px(selectedPoint.x);val selectedY=py(selectedPoint.y)
  if(showPointTooltip){
  paint.color=Color.rgb(0,116,112);paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(2f);canvas.drawCircle(selectedX,selectedY,dp(10f),paint)
  paint.strokeWidth=dp(1f);paint.pathEffect=DashPathEffect(floatArrayOf(dp(3f),dp(4f)),0f);canvas.drawLine(left,selectedY,right,selectedY,paint);canvas.drawLine(selectedX,top,selectedX,bottom,paint);paint.pathEffect=null;paint.style=Paint.Style.FILL
  }
  // Move endpoint labels apart so short reference ranges remain legible.
  val labels=plot.curves.map{c->c to py(c.points.last().y)}.sortedBy{it.second}
  var previous=top-dp(13f)
  for((curve,y) in labels){val labelY=max(y,previous+dp(13f)).coerceAtMost(bottom);previous=labelY;paint.style=Paint.Style.FILL;paint.textAlign=Paint.Align.LEFT;paint.color=curveColor(curve.z);canvas.drawText(when(curve.z){-3->"۰٫۱";-2->"۲٫۳";-1->"۱۵٫۹";0->"۵۰";1->"۸۴٫۱";2->"۹۷٫۷";else->"۹۹٫۹"},right+dp(5f),labelY+dp(3f),paint)}
  paint.color=Color.rgb(93,115,127);paint.textAlign=Paint.Align.CENTER;paint.textSize=dp(12f);canvas.drawText(plot.xLabel,(left+right)/2,height-dp(7f),paint)
  if(showPointTooltip){
   fun exact(value:Double,digits:Int=2)=SolarDate.persian((if(digits==3)WaistAssessment.ratioNumber(value) else String.format(Locale.US,"%.${digits}f",value)).trimEnd('0').trimEnd('.'))
   val horizontal=if(plot.gestationalWeekAxis)PregnancyAssessment.weekLabel((selectedPoint.x*7).roundToInt()) else exact(selectedPoint.x)
   val lines=listOf((if(plot.gestationalWeekAxis)"بارداری" else plot.xLabel)+": "+horizontal,plot.yLabel+": "+exact(selectedPoint.y,plot.yDecimals?:2))
   paint.textSize=dp(11f);paint.textAlign=Paint.Align.RIGHT
   val boxWidth=min(width-dp(16f),max(dp(150f),lines.maxOf{paint.measureText(it)}+dp(20f)))
   val boxHeight=dp(52f);val bx=(selectedX-boxWidth/2).coerceIn(dp(8f),width-boxWidth-dp(8f))
   val by=(if(selectedY-boxHeight-dp(12f)>top)selectedY-boxHeight-dp(12f) else selectedY+dp(12f)).coerceIn(dp(4f),height-boxHeight-dp(4f))
   val rect=RectF(bx,by,bx+boxWidth,by+boxHeight)
   paint.style=Paint.Style.FILL;paint.color=Color.argb(248,255,255,255);canvas.drawRoundRect(rect,dp(9f),dp(9f),paint)
   paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(1f);paint.color=median;canvas.drawRoundRect(rect,dp(9f),dp(9f),paint)
   paint.style=Paint.Style.FILL;paint.color=Color.rgb(21,52,65)
   lines.forEachIndexed{i,line->canvas.drawText(line,rect.right-dp(10f),rect.top+dp(20f+i*20f),paint)}
  }

 }
}
