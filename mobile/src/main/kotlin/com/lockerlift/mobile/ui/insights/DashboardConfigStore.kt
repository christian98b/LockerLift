package com.lockerlift.mobile.ui.insights

import android.content.Context

/**
 * Persists the user-configurable dashboard widget order and visibility
 * using SharedPreferences (pipe-separated enum names).
 */
class DashboardConfigStore(context: Context) {

    private val prefs =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadOrder(): List<DashboardWidgetType> {
        val stored = prefs.getString(PREF_ORDER_KEY, null) ?: return DashboardWidgetType.DEFAULT_ORDER
        return parseOrder(stored)
    }

    fun saveOrder(order: List<DashboardWidgetType>) {
        prefs.edit().putString(PREF_ORDER_KEY, serialize(order)).apply()
    }

    fun loadHidden(): Set<DashboardWidgetType> {
        val stored = prefs.getString(PREF_HIDDEN_KEY, null) ?: return emptySet()
        return parseHidden(stored)
    }

    fun saveHidden(hidden: Set<DashboardWidgetType>) {
        prefs.edit().putString(PREF_HIDDEN_KEY, serialize(hidden.toList())).apply()
    }

    companion object {
        private const val PREFS_NAME = "lockerlift_dashboard_prefs"
        private const val PREF_ORDER_KEY = "pref_widget_order"
        private const val PREF_HIDDEN_KEY = "pref_widget_hidden"
        private const val ORDER_SEPARATOR = "|"

        fun parseOrder(stored: String): List<DashboardWidgetType> {
            val parsed = stored.split(ORDER_SEPARATOR)
                .mapNotNull { name -> DashboardWidgetType.entries.firstOrNull { it.name == name } }
            return if (parsed.size == DashboardWidgetType.entries.size) parsed else DashboardWidgetType.DEFAULT_ORDER
        }

        fun parseHidden(stored: String): Set<DashboardWidgetType> {
            return stored.split(ORDER_SEPARATOR)
                .mapNotNull { name -> DashboardWidgetType.entries.firstOrNull { it.name == name } }
                .toSet()
        }

        fun serialize(widgets: List<DashboardWidgetType>): String =
            widgets.joinToString(ORDER_SEPARATOR) { it.name }
    }
}
