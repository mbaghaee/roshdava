package ir.mbaghaee.bmi

import kotlin.math.ceil
import kotlin.math.roundToInt

internal object AssessmentAge {
 const val DAY_MS = 86_400_000L
 private val supportedYears = 19..120

 fun parseYears(text: String): Int? {
  val digits = text.trim().map { it.digitToIntOrNull() ?: return null }
  return digits.joinToString("").toIntOrNull()?.takeIf { it in supportedYears }
 }

 fun approximateDays(years: Int): Int = ceil(years * 365.25).toInt()
 fun approximateYears(days: Int): Int = (days / 365.25).roundToInt()
 fun validApproximateDays(days: Int): Boolean =
  approximateYears(days).let { it in supportedYears && approximateDays(it) == days }

 fun elapsedDays(from: Long, to: Long): Int = ((to - from) / DAY_MS).toInt()

 fun byVisitDate(plot: ReferencePlot): ReferencePlot {
  val day = plot.point.day
  fun datePoint(point: PlotPoint) = point.copy(x = (point.day - day).toDouble())
  val history = plot.history.map(::datePoint)
  return plot.copy(xLabel = "روز نسبت به این سنجش", xMin = minOf(0.0, history.minOfOrNull { it.x } ?: 0.0) - 1,
   xMax = maxOf(0.0, history.maxOfOrNull { it.x } ?: 0.0) + 1,
   point = datePoint(plot.point), history = history, dateBasedAge = true)
 }
}
