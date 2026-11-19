package ir.mbaghaee.bmi
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class PercentilesTest {
 private val engine=GrowthEngine(listOf("bmianthro","lenanthro","weianthro","wfhanthro","wflanthro","hcanthro","bmi2007","hfa2007").associateWith{GrowthEngine.parse(File("src/main/assets/$it.csv").readText())})
 @Test fun knownPercentiles(){assertEquals(50.0,Percentiles.value(0.0)!!,1e-8);assertEquals(84.1344746,Percentiles.value(1.0)!!,1e-5);assertEquals(2.2750132,Percentiles.value(-2.0)!!,1e-5);assertEquals(99.86501,Percentiles.value(3.0)!!,1e-5)}
 @Test fun tailsDoNotPretendToBeExactPercentiles(){assertNull(Percentiles.value(3.01));assertNull(Percentiles.value(-4.0));assertNull(Percentiles.value(Double.NaN))}
 @Test fun weightForLengthUsesSameLengthRatherThanSameAge(){val m=Metric("وزن نسبت به قد","طبیعی","",0.0,indicator="wflanthro");val text=Percentiles.summary(m)!!;assertTrue(text.contains("همین قد"));assertFalse(text.contains("هم‌سن"))}
 @Test fun ageComparisonsSaySameAge(){assertTrue(Percentiles.summary(Metric("قد","طبیعی","",1.0,indicator="lenanthro"))!!.contains("هم‌سن"))}
 @Test fun chartContainsActualPointAndOrderedCurves(){
  val p=Patient("",2,2200,22.0,120.0);val a=engine.assess(p)
  for(m in a.metrics){val near=engine.plotFor(p,m)!!;val full=engine.plotFor(p,m,true)!!
   assertTrue(near.point.x in near.xMin..near.xMax);assertTrue(full.xMin<=near.xMin&&full.xMax>=near.xMax)
   assertEquals(listOf(-3,-2,-1,0,1,2,3),near.curves.map{it.z});assertTrue(near.curves.all{it.points.size==81})
   for(i in 0..80){val ys=near.curves.map{it.points[i].y};assertEquals(ys.sorted(),ys)}
  }
  val h=engine.plotFor(p,a.metrics[1])!!;assertEquals(120.0,h.point.y,1e-9)
 }
 @Test fun infantPointUsesCorrectedLength(){val p=Patient("",1,300,9.0,70.0);val a=engine.assess(p);val plot=engine.plotFor(p,a.metrics[0])!!;assertEquals(70.7,plot.point.x,1e-9);assertEquals(9.0,plot.point.y,1e-9)}
 @Test fun invalidAndAdultResultsHaveNoChildChart(){val p=Patient("",1,2200,150.0,100.0);val a=engine.assess(p);assertNull(engine.plotFor(p,a.metrics[0]));val adult=p.copy(days=30*365,weight=70.0,height=170.0);for(m in engine.assess(adult).metrics)assertNull(engine.plotFor(adult,m))}
}
