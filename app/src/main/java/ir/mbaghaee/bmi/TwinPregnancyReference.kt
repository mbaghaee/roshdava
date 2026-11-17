package ir.mbaghaee.bmi

/**
 * Offline approximate tracing of page 4 of the four CDC twin pregnancy trackers (CS262136A).
 * Polygon vertices were extracted from the source PDF, snapped to labelled weeks, calibrated
 * to the published zero and terminal pound goals, and linearly interpolated only between adjacent traced points.
 * This is a digitised graphic, not a published clinical data table or a singleton-derived rate.
 * The underweight chart ends at week 38; the other charts end at week 40. No extrapolation.
 */
object TwinPregnancyReference {
 const val VERSION="CDC_TWIN_TRACKER_DIGITISED_20261117_V1"
 private const val LB_TO_KG=0.45359237
 private fun rows(text:String)=text.split(';').mapIndexed{i,row->
  val values=row.split(',');Triple((i+5).toDouble(),values[0].toDouble()*LB_TO_KG,values[1].toDouble()*LB_TO_KG)
 }
 // SHA-256 of CDC source: a7b161440fa68b441a5d3aa468400857033b51fcb5253fb5e08953811d13a52a
 private val underweight=rows("0.00,0.00;1.52,2.28;3.23,4.55;4.94,7.02;6.66,9.29;8.36,11.66;9.89,14.03;11.60,16.31;13.30,18.58;15.02,20.86;16.73,23.32;18.25,25.60;19.96,27.87;21.67,30.34;23.38,32.61;25.10,34.89;26.42,36.78;27.94,38.49;29.47,40.20;30.99,41.90;32.50,43.61;34.03,45.50;35.55,47.21;37.07,48.92;38.40,50.24;39.63,51.57;40.87,52.90;42.20,54.23;43.53,55.46;44.87,56.69;46.19,58.02;47.46,59.35;48.73,60.67;50.00,62.00")
 // SHA-256 of CDC source: d192582936296d65ff51be99410b015a8bf1471d39ccaa966e767697b169a378
 private val normal=rows("0.00,0.00;1.06,1.55;2.04,3.01;3.17,4.63;4.16,6.10;5.29,7.72;6.43,9.18;7.41,10.81;8.54,12.27;9.52,13.89;10.65,15.35;11.64,16.98;12.77,18.44;13.75,20.06;14.89,21.52;15.86,23.15;17.00,24.61;17.97,26.23;19.12,27.69;20.09,29.32;21.23,30.78;22.28,32.32;23.34,33.86;24.31,35.49;25.45,36.95;26.43,38.57;27.49,40.03;28.55,41.66;29.68,43.12;30.66,44.74;31.79,46.21;32.77,47.83;33.91,49.29;34.89,50.91;36.03,52.38;37.00,54.00")
 // SHA-256 of CDC source: 304d3a9a3352989797c69372c7a78d798da6c7849b2bc93e16427a038f5b1126
 private val overweight=rows("0.00,0.00;0.90,1.39;1.71,2.85;2.69,4.32;3.50,5.78;4.48,7.08;5.29,8.54;6.27,10.01;7.08,11.47;8.06,12.93;8.88,14.23;9.69,15.70;10.67,17.16;11.48,18.62;12.45,20.09;13.35,21.39;14.24,22.85;15.06,24.31;16.03,25.78;16.84,27.24;17.82,28.54;18.63,30.00;19.45,31.47;20.42,32.93;21.23,34.39;22.21,35.69;23.02,37.16;24.01,38.62;24.82,40.08;25.80,41.55;26.61,42.85;27.58,44.31;28.40,45.77;29.21,47.24;30.19,48.70;31.00,50.00")
 // SHA-256 of CDC source: 5cf1c074636e75c35ca4045f119d6759098926161849dd9b575700ea7a53f8c1
 private val obesity=rows("0.00,0.00;0.68,1.16;1.41,2.38;2.13,3.60;2.86,4.82;3.59,6.04;4.32,7.26;5.05,8.36;5.66,9.57;6.39,10.79;7.12,12.01;7.85,13.23;8.58,14.45;9.31,15.67;10.04,16.77;10.64,17.99;11.37,19.20;12.10,20.42;12.84,21.64;13.57,22.86;14.30,24.08;15.02,25.18;15.63,26.40;16.36,27.62;17.09,28.83;17.82,30.05;18.56,31.27;19.28,32.37;20.01,33.59;20.74,34.81;21.35,36.03;22.08,37.25;22.81,38.47;23.53,39.68;24.26,40.78;25.00,42.00")
 private fun data(bmi:Double)=when{bmi<18.5->underweight;bmi<25->normal;bmi<30->overweight;else->obesity}
 fun lastWeek(bmi:Double)=data(bmi).last().first.toInt()
 fun sourceUrl(bmi:Double)="https://www.cdc.gov/maternal-infant-health/media/pdfs/"+when{
  bmi<18.5->"Underweight_Twin_Tracker_508Tagged.pdf"
  bmi<25->"Normal_Twin_Weight_Tracker__508Tagged.pdf"
  bmi<30->"Overweight_Twin_Tracker__508Tagged.pdf"
  else->"Obese_Twin_Weight_Tracker__508Tagged.pdf"
 }
 fun envelope(bmi:Double):ReferenceEnvelope {
  val rows=data(bmi)
  return ReferenceEnvelope(rows.map{PlotPoint(it.first,it.second)},rows.map{PlotPoint(it.first,it.third)})
 }
 fun range(bmi:Double,gestationDays:Int):Pair<Double,Double>? {
  val week=gestationDays/7.0;val rows=data(bmi)
  if(week<=rows.first().first||week>rows.last().first)return null
  val hi=rows.indexOfFirst{it.first>=week};val a=rows[hi-1];val b=rows[hi];val t=(week-a.first)/(b.first-a.first)
  return (a.second+(b.second-a.second)*t) to (a.third+(b.third-a.third)*t)
 }
}
