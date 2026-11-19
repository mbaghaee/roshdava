package ir.mbaghaee.bmi

import org.json.JSONArray
import org.json.JSONObject

/** Private on-device payload for the chart screen; only a filename is passed through the Intent. */
object ChartExplorerData {
 const val CURRENT_VISIT_ID=0L
 private fun point(p:PlotPoint)=JSONArray().put(p.x).put(p.y).put(p.day).apply{p.visitId?.let{put(it)}}
 private fun points(values:List<PlotPoint>)=JSONArray().apply{values.forEach{put(point(it))}}
 private fun readPoint(p:JSONArray)=PlotPoint(p.getDouble(0),p.getDouble(1),p.getInt(2),if(p.isNull(3))null else p.getLong(3))
 private fun readPoints(a:JSONArray)=(0 until a.length()).map{readPoint(a.getJSONArray(it))}
 fun visitId(record:JSONObject)=if(record.optBoolean("explorerCurrent"))CURRENT_VISIT_ID else record.getLong("id")
 fun encode(plot:ReferencePlot):JSONObject=JSONObject().apply{
  put("xLabel",plot.xLabel);put("yLabel",plot.yLabel);put("xMin",plot.xMin);put("xMax",plot.xMax)
  put("point",point(plot.point));put("pointText",plot.pointText);put("history",points(plot.history))
  put("curves",JSONArray().apply{plot.curves.forEach{put(JSONObject().put("z",it.z).put("points",points(it.points)))}})
  put("bands",JSONArray().apply{plot.bands.forEach{put(JSONObject().put("low",it.lowerZ?:JSONObject.NULL).put("high",it.upperZ?:JSONObject.NULL).put("tone",it.tone.name))}})
  put("padding",plot.minYPadding);put("floor",plot.minYValue?:JSONObject.NULL);put("decimals",plot.yDecimals?:JSONObject.NULL)
  put("gestation",plot.gestationalWeekAxis);put("showCurrent",plot.showCurrentPoint);put("dateBasedAge",plot.dateBasedAge)
  plot.envelope?.let{put("envelope",JSONObject().put("lower",points(it.lower)).put("upper",points(it.upper)))}
 }
 fun decode(o:JSONObject):ReferencePlot {
  val curves=o.getJSONArray("curves");val bands=o.getJSONArray("bands")
  fun optionalInt(value:JSONObject,key:String)=if(value.isNull(key))null else value.getInt(key)
  return ReferencePlot(o.getString("xLabel"),o.getString("yLabel"),o.getDouble("xMin"),o.getDouble("xMax"),
   (0 until curves.length()).map{val c=curves.getJSONObject(it);ReferenceCurve(c.getInt("z"),readPoints(c.getJSONArray("points")))},
   readPoint(o.getJSONArray("point")),o.getString("pointText"),readPoints(o.getJSONArray("history")),
   (0 until bands.length()).map{val b=bands.getJSONObject(it);ReferenceBand(optionalInt(b,"low"),optionalInt(b,"high"),BandTone.valueOf(b.getString("tone")))},
   minYPadding=o.getDouble("padding"),yDecimals=optionalInt(o,"decimals"),
   envelope=o.optJSONObject("envelope")?.let{ReferenceEnvelope(readPoints(it.getJSONArray("lower")),readPoints(it.getJSONArray("upper")))},
   minYValue=if(o.isNull("floor"))null else o.getDouble("floor"),gestationalWeekAxis=o.getBoolean("gestation"),showCurrentPoint=o.optBoolean("showCurrent",true),dateBasedAge=o.optBoolean("dateBasedAge"))
 }
}
