package ir.mbaghaee.bmi

/** Provisional whole-pregnancy context only; these are not validated weekly or BMI-specific targets. */
object HigherOrderPregnancyReference {
 const val VERSION="HOM_TOTAL_CONTEXT_20261117_V1"
 const val NOTICE="راهنمای حدودی با شواهد محدود؛ تعیین هدف نهایی و ارزیابی وزن‌گیری باید به تیم مراقبت بارداری واگذار شود. این بازه، هدف این هفته یا معیار تشخیص طبیعی بودن نیست."
 const val TRIPLET_URL="https://www.texashealth.org/baby-care/Pregnancy/expecting-twins-triplets-or-more"
 const val HIGHER_URL="https://pogsinc.org/wp-content/uploads/2022/09/CPG-on-Maternal-Nutrition-and-Supplementation-2018.pdf"
 // Triplets: Texas Health 50–60 lb. Four/five: POGS 2018 (low-quality evidence), 50–75 / 65–100 lb.
 fun totalRange(count:Int?):Pair<Double,Double>? {
  val pounds=when(count){3->50.0 to 60.0;4->50.0 to 75.0;5->65.0 to 100.0;else->return null}
  return pounds.first*.45359237 to pounds.second*.45359237
 }
 fun source(count:Int?)=if(count==3)"Texas Health؛ راهنمای آموزشی سه‌قلویی" else "POGS 2018؛ راهنمای قدیمی با کیفیت شواهد پایین"
 fun sourceUrl(count:Int?)=if(count==3)TRIPLET_URL else HIGHER_URL
}
