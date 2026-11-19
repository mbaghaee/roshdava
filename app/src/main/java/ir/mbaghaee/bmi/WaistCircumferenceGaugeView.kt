package ir.mbaghaee.bmi

import android.content.Context
import android.util.AttributeSet
import kotlin.math.ceil
import kotlin.math.floor

/** Local circumference reference used for tracking when the NICE ratio bands do not apply. */
class WaistCircumferenceGaugeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SegmentedGaugeView(context, attrs) {
    private fun frame(waist: Double): GaugePresentation {
        val threshold = WaistAssessment.IRANIAN_WAIST_THRESHOLD_CM
        val below = waist < threshold
        val low = minOf(70.0, floor((waist - 8) / 10) * 10).coerceAtLeast(0.0)
        val high = maxOf(110.0, ceil((waist + 8) / 10) * 10)
        return GaugePresentation(
            waist, listOf(low, threshold, high),
            listOf(colour(R.color.waist_gauge_normal), colour(R.color.waist_gauge_increased)),
            listOf("زیر آستانه", "در آستانه یا بالاتر"),
            colour(if (below) R.color.growth_normal_text else R.color.waist_gauge_increased_ink),
            "${SolarDate.persian(WaistAssessment.number(waist).replace('.', '٫'))} سانتی‌متر",
            selectedBand = if (below) 0 else 1
        )
    }

    fun setWaist(waist: Double) {
        if (!waist.isFinite() || waist <= 0) {
            showGauge(null)
            return
        }
        showGauge(frame(waist))
        contentDescription = "جایگاه دور کمر در مرجع بزرگسالان ایرانی. ${WaistAssessment.iranianWaistPosition(waist)}"
    }

    override fun previewGauge() = frame(112.0)
}
