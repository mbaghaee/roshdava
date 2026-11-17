package ir.mbaghaee.bmi

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Borkowski year-start algorithm, adapted from jalaali-js (MIT; license in assets). */
data class SolarDate(val year:Int,val month:Int,val day:Int) {
 fun toUtc():Long {
  require(month in 1..12 && day in 1..monthLength(year,month))
  val offset=if(month<=6)(month-1)*31 else 186+(month-7)*30
  return yearStart(year)+(offset+day-1)*DAY
 }
 fun display()=persian(String.format(Locale.US,"%04d/%02d/%02d",year,month,day))
 companion object {
  const val DAY=86400000L
  val months=arrayOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
  private val breaks=intArrayOf(-61,9,38,199,426,686,756,818,1111,1181,1210,1635,2060,2097,2192,2262,2324,2394,2456,3178)
  fun persian(value:String)=value.map{if(it in '0'..'9')"۰۱۲۳۴۵۶۷۸۹"[it-'0'] else it}.joinToString("")
  private fun yearStart(year:Int):Long {
   require(year in 1..3177)
   val gy=year+621;var leapJ=-14;var jp=breaks[0];var jump=0
   for(i in 1 until breaks.size){val jm=breaks[i];jump=jm-jp;if(year<jm)break;leapJ+=jump/33*8+(jump%33)/4;jp=jm}
   val n=year-jp;leapJ+=n/33*8+((n%33)+3)/4
   if(jump%33==4&&jump-n==4)leapJ++
   val leapG=gy/4-((gy/100+1)*3)/4-150
   val march=20+leapJ-leapG
   return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply{clear();set(gy,Calendar.MARCH,march)}.timeInMillis
  }
  fun monthLength(year:Int,month:Int):Int {
   require(month in 1..12)
   return if(month<=6)31 else if(month<=11)30 else if((yearStart(year+1)-yearStart(year))/DAY==366L)30 else 29
  }
  fun fromUtc(utc:Long):SolarDate {
   val cal=Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply{timeInMillis=utc}
   var year=cal.get(Calendar.YEAR)-621
   if(utc<yearStart(year))year--
   val offset=((utc-yearStart(year))/DAY).toInt()
   return if(offset<186)SolarDate(year,offset/31+1,offset%31+1) else SolarDate(year,(offset-186)/30+7,(offset-186)%30+1)
  }
 }
}
