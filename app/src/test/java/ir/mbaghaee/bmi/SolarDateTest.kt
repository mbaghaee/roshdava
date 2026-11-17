package ir.mbaghaee.bmi
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
class SolarDateTest {
 private fun utc(y:Int,m:Int,d:Int)=Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply{clear();set(y,m-1,d)}.timeInMillis
 @Test fun knownDates(){assertEquals(SolarDate(1405,8,25),SolarDate.fromUtc(utc(2026,11,16)));assertEquals(SolarDate(1395,1,23),SolarDate.fromUtc(utc(2016,4,11)));assertEquals(utc(2025,3,21),SolarDate(1404,1,1).toUtc())}
 @Test fun esfandBoundaries(){assertEquals(30,SolarDate.monthLength(1403,12));assertEquals(29,SolarDate.monthLength(1404,12));assertEquals(utc(2025,3,20),SolarDate(1403,12,30).toUtc())}
 @Test fun roundTripForPatientYears(){for(y in 1280..1405)for(m in 1..12)for(d in listOf(1,SolarDate.monthLength(y,m))){val s=SolarDate(y,m,d);assertEquals(s,SolarDate.fromUtc(s.toUtc()))}}
 @Test(expected=IllegalArgumentException::class) fun invalidLeapDayRejected(){SolarDate(1404,12,30).toUtc()}
 @Test fun persianDigits(){assertEquals("۱۴۰۵/۰۸/۲۵",SolarDate(1405,8,25).display())}
}
