package ir.mbaghaee.bmi

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ir.mbaghaee.bmi.databinding.ActivityChartExplorerBinding
import ir.mbaghaee.bmi.databinding.ItemChartVisitBinding
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.*

class ChartExplorerActivity:AppCompatActivity(){
 private lateinit var ui:ActivityChartExplorerBinding
 private lateinit var base:ReferencePlot
 private lateinit var payload:JSONObject
 private var sourceFile:File?=null
 private var fromDay:Int?=null
 private var selected:PlotPoint?=null
 private var updating=false
 private var recordsById=emptyMap<Long,Pair<JSONObject,SavedAssessment>>()
 private val regular by lazy{ResourcesCompat.getFont(this,R.font.vazirmatn_regular)}
 private val bold by lazy{ResourcesCompat.getFont(this,R.font.vazirmatn_bold)}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  try{
   val name=intent.getStringExtra("file")?:error("Missing chart")
   require(name.matches(Regex("chart-[a-f0-9-]+[.]json")))
   sourceFile=File(cacheDir,name);payload=JSONObject(sourceFile!!.readText());base=ChartExplorerData.decode(payload.getJSONObject("plot"))
   val records=payload.getJSONArray("records")
   recordsById=HistoryTimeline.records(records).mapNotNull{record->
    SavedAssessment.decode(record)?.let{ChartExplorerData.visitId(record) to (record to it)}
   }.toMap()
  }catch(_:Exception){Toast.makeText(this,"دادهٔ نمودار در دسترس نیست؛ آن را از ارزیابی دوباره باز کنید.",Toast.LENGTH_LONG).show();finish();return}
  ui=ActivityChartExplorerBinding.inflate(layoutInflater);setContentView(ui.root)
  ViewCompat.setOnApplyWindowInsetsListener(ui.root){v,insets->val b=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);insets}
  ui.title.text=payload.getString("title");ui.closeButton.setOnClickListener{finish()}
  ui.legendLabel.text=payload.optString("legend")
  ui.rangeSlider.valueFrom=base.xMin.toFloat();ui.rangeSlider.valueTo=base.xMax.toFloat()
  val lo=(savedInstanceState?.getDouble("lo")?:base.xMin).toFloat();val hi=(savedInstanceState?.getDouble("hi")?:base.xMax).toFloat()
  ui.rangeSlider.setValues(lo,hi)
  ui.rangeSlider.setLabelFormatter{SolarDate.persian(String.format(Locale.US,"%.2f",it))}
  ui.rangeSlider.addOnChangeListener{_,_,_->if(!updating){
   val values=ui.rangeSlider.values;val minimum=max((base.xMax-base.xMin)/1000,1e-5)
   if(values[1]-values[0]<minimum)setRange(values[0].toDouble(),values[0]+minimum) else redraw()
  }}
  ui.zoomInButton.setOnClickListener{zoom(.7)};ui.zoomOutButton.setOnClickListener{zoom(1.4)}
  ui.resetButton.setOnClickListener{ui.periodSpinner.setSelection(0);setRange(base.xMin,base.xMax)}
  val periods=listOf("همهٔ سنجش‌ها","۳۰ روز اخیر","۹۰ روز اخیر","۱۸۰ روز اخیر","۳۶۵ روز اخیر")
  ui.periodSpinner.adapter=object:ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,periods){
   override fun getView(position:Int,convertView:View?,parent:ViewGroup):View=super.getView(position,convertView,parent).also{fonts(it)}
   override fun getDropDownView(position:Int,convertView:View?,parent:ViewGroup):View=super.getDropDownView(position,convertView,parent).also{fonts(it)}
  }
  ui.periodSpinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
   override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
   override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){
    val end=ChartViewport.points(base).maxOfOrNull{it.day}?:0
    fromDay=if(position==0)null else end-listOf(0,30,90,180,365)[position]
    redraw();fonts(ui.periodSpinner)
   }
  }
  ui.periodSpinner.setSelection(savedInstanceState?.getInt("period")?:0)
  ui.chart.onPointSelected={point->selected=point;ui.coordinates.text=info(point);ui.coordinates.visibility=View.VISIBLE;renderRows(point)}
  ui.chart.onSelectionCleared={selected=null;ui.coordinates.visibility=View.GONE;renderRows()}
  installGestures();fonts(ui.root);redraw()
 }
 private fun fonts(v:View){if(v is TextView)v.typeface=if(v.typeface?.isBold==true)bold else regular;if(v is ViewGroup)for(i in 0 until v.childCount)fonts(v.getChildAt(i))}
 private fun chartAnchor()=if(payload.has("chartAnchor"))payload.getLong("chartAnchor") else payload.getLong("birth")
 private fun info(p:PlotPoint)=ui.chart.describePoint(p)+"\nتاریخ سنجش: "+SolarDate.fromUtc(chartAnchor()+p.day*AssessmentAge.DAY_MS).display()
 private fun setRange(low:Double,high:Double){
  val minSpan=max((base.xMax-base.xMin)/1000,1e-5);val span=(high-low).coerceIn(minSpan,base.xMax-base.xMin)
  val lo=low.coerceIn(base.xMin,base.xMax-span);updating=true
  ui.rangeSlider.setValues(lo.toFloat(),(lo+span).toFloat());updating=false;redraw()
 }
 private fun zoom(factor:Double,focus:Double=.5){
  val v=ui.rangeSlider.values;val span=(v[1]-v[0]).toDouble();val anchor=v[0]+span*focus
  setRange(anchor-span*factor*focus,anchor+span*factor*(1-focus))
 }
 private fun redraw(){
  val v=ui.rangeSlider.values;if(v[1]<=v[0])return
  val plot=ChartViewport.slice(base,v[0].toDouble(),v[1].toDouble(),fromDay)
  ui.chart.setPlot(plot);ui.emptyNotice.visibility=if(ChartViewport.points(plot).isEmpty())View.VISIBLE else View.GONE
  renderRows()
 }
 private fun renderRows(only:PlotPoint?=null){
  ui.visitsContainer.removeAllViews()
  val range=ui.rangeSlider.values
  val points=ChartViewport.points(base).filter{it.x>=range[0]&&it.x<=range[1]&&(fromDay==null||it.day>=fromDay!!)}.sortedBy{it.day}
  val shown=if(only==null)points else points.filter{it.x==only.x&&it.y==only.y&&it.day==only.day}
  for(point in shown){
   val row=ItemChartVisitBinding.inflate(layoutInflater,ui.visitsContainer,true)
   val match=point.visitId?.let(recordsById::get)
   row.values.text=info(point)
   if(match==null){
    row.openButton.visibility=View.GONE
   }else{
    val (record,saved)=match
    row.values.append(SolarDate.persian("\nوزن نوبت: ${saved.patient.weight} کیلوگرم · قد: ${saved.patient.height} سانتی‌متر"))
    val current=point.visitId==ChartExplorerData.CURRENT_VISIT_ID
    row.openButton.text=if(current)"بازگشت به ارزیابی جاری" else "نمایش ارزیابی این سنجش"
    row.openButton.setOnClickListener{if(!current)setResult(RESULT_OK,Intent().putExtra("record",record.toString()));finish()}
   }
  }
  fonts(ui.visitsContainer)
 }
 @SuppressLint("ClickableViewAccessibility") private fun installGestures(){
  var lastX=0f;var downX=0f;var downY=0f;var moved=false
  val slop=ViewConfiguration.get(this).scaledTouchSlop
  val detector=ScaleGestureDetector(this,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
   override fun onScale(d:ScaleGestureDetector):Boolean{moved=true;zoom(1.0/d.scaleFactor,(d.focusX/ui.chart.width).toDouble().coerceIn(0.0,1.0));return true}
  })
  ui.chart.setOnTouchListener{view,event->
   detector.onTouchEvent(event)
   when(event.actionMasked){
    MotionEvent.ACTION_DOWN->{view.parent.requestDisallowInterceptTouchEvent(true);downX=event.x;lastX=event.x;downY=event.y;moved=false;ui.chart.onTouchEvent(event)}
    MotionEvent.ACTION_MOVE->{
     if(!detector.isInProgress&&event.pointerCount==1&&(moved||abs(event.x-downX)>slop&&abs(event.x-downX)>abs(event.y-downY))){
      moved=true;val v=ui.rangeSlider.values;val delta=(lastX-event.x)/max(1,view.width)*(v[1]-v[0]);setRange((v[0]+delta).toDouble(),(v[1]+delta).toDouble())
     }else if(!moved&&abs(event.y-downY)>slop&&abs(event.y-downY)>abs(event.x-downX))view.parent.requestDisallowInterceptTouchEvent(false)
     lastX=event.x
    }
    MotionEvent.ACTION_UP->{if(!moved)ui.chart.onTouchEvent(event) else ui.chart.clearSelection()}
    MotionEvent.ACTION_CANCEL->ui.chart.clearSelection()
   };true
  }
 }
 override fun onSaveInstanceState(out:Bundle){if(::ui.isInitialized){val v=ui.rangeSlider.values;out.putDouble("lo",v[0].toDouble());out.putDouble("hi",v[1].toDouble());out.putInt("period",ui.periodSpinner.selectedItemPosition)};super.onSaveInstanceState(out)}
 override fun onDestroy(){if(isFinishing)sourceFile?.delete();super.onDestroy()}
}
