package ir.mbaghaee.bmi

/** Clip reference curves at the viewport boundaries; filter measurements without inventing points. */
object ChartViewport {
 fun points(plot:ReferencePlot)=plot.history+listOfNotNull(plot.point.takeIf{plot.showCurrentPoint})
 private fun edge(points:List<PlotPoint>,lo:Double,hi:Double):List<PlotPoint> {
  if(points.isEmpty())return emptyList()
  fun at(x:Double):PlotPoint? {
   if(x<points.first().x||x>points.last().x)return null
   points.firstOrNull{it.x==x}?.let{return it}
   val j=points.indexOfFirst{it.x>x};if(j<=0)return null
   val a=points[j-1];val b=points[j];return PlotPoint(x,a.y+(b.y-a.y)*(x-a.x)/(b.x-a.x))
  }
  return (listOfNotNull(at(lo))+points.filter{it.x>lo&&it.x<hi}+listOfNotNull(at(hi))).distinctBy{it.x}
 }
 fun slice(plot:ReferencePlot,lo:Double,hi:Double,fromDay:Int?=null):ReferencePlot {
  require(lo.isFinite()&&hi.isFinite()&&hi>lo)
  fun visible(p:PlotPoint)=p.x in lo..hi&&(fromDay==null||p.day>=fromDay)
  val envelope=plot.envelope?.let{ReferenceEnvelope(edge(it.lower,lo,hi),edge(it.upper,lo,hi))}
   ?.takeIf{it.lower.size>1&&it.upper.size>1}
  return plot.copy(xMin=lo,xMax=hi,history=plot.history.filter(::visible),showCurrentPoint=plot.showCurrentPoint&&visible(plot.point),
   curves=plot.curves.map{it.copy(points=edge(it.points,lo,hi))}.filter{it.points.size>1},envelope=envelope)
 }
}
