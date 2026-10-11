package com.lockerlift.mobile.ui.insights

import androidx.annotation.StringRes
import com.lockerlift.mobile.R

/**
 * Widget catalog of the insights dashboard. Users can reorder and
 * show/hide these widgets; the order is persisted via [DashboardConfigStore].
 */
enum class DashboardWidgetType(@StringRes val labelRes: Int) {
    VERDICT(R.string.insights_widget_verdict),
    REGULARITY(R.string.insights_widget_regularity),
    CALENDAR(R.string.insights_widget_calendar),
    VOLUME_CHART(R.string.insights_widget_volume_chart),
    PROGRESSION(R.string.insights_widget_progression),
    RECORDS(R.string.insights_widget_records);

    companion object {
        val DEFAULT_ORDER: List<DashboardWidgetType> = entries.toList()
    }
}
