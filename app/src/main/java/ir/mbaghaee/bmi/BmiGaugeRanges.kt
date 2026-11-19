package ir.mbaghaee.bmi

import java.util.Locale

/** Uses the existing GrowthEngine cutoffs; it changes presentation only. */
object BmiGaugeRanges {
 fun cuts(adult:Boolean,young:Boolean)=if(adult)listOf(12.0,18.5,25.0,30.0,42.0) else if(young)listOf(-4.0,-2.0,1.0,2.0,3.0,5.0) else listOf(-4.0,-2.0,1.0,2.0,5.0)
 fun labels(adult:Boolean,young:Boolean)=if(adult)listOf("کم‌وزن","مرجع","اضافه","چاق") else if(young)listOf("کم","مرجع","خطر","اضافه","چاق") else listOf("کم","مرجع","اضافه","چاق")
 fun band(value:Double,adult:Boolean,young:Boolean):Int=if(adult)when{value<18.5->0;value<25->1;value<30->2;else->3} else when{
  value< -2->0;value<=1->1;young&&value<=2->2;young&&value<=3->3;young->4;value<=2->2;else->3
 }
 fun adultNumber(value:Double,minDecimals:Int=1):String {
  fun category(v:Double)=when{
   v<18.5->0
   v<25->1
   v<30->2
   v<35->3
   v<40->4
   else->5
  }
  for(digits in minDecimals..8){
   val text=String.format(Locale.US,"%.${digits}f",value)
   if(category(text.toDouble())==category(value))return text
  }
  return value.toString()
 }
 fun number(value:Double,adult:Boolean,young:Boolean):String {
  for(digits in 2..8){
   val text=String.format(Locale.US,"%.${digits}f",value)
   if(band(text.toDouble(),adult,young)==band(value,adult,young))return if(!adult&&value>0)"+$text" else text
  }
  return value.toString()
 }
}
