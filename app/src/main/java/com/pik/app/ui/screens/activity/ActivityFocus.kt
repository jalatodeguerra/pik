package com.pik.app.ui.screens.activity

import com.pik.app.domain.CategoryFilter
import com.pik.app.domain.Period
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** "Show these transactions": a category inside a payday period, asked for by the Insights screen. */
data class ActivityFocusRequest(val category: CategoryFilter, val period: Period)

/**
 * One-shot hand-over from Insights to Activity (Activity is a tab, so the request is not a route
 * argument). The Activity screen takes the request and clears it.
 */
@Singleton
class ActivityFocus @Inject constructor() {
    private val _pending = MutableStateFlow<ActivityFocusRequest?>(null)
    val pending: StateFlow<ActivityFocusRequest?> = _pending

    fun request(request: ActivityFocusRequest) {
        _pending.value = request
    }

    fun clear() {
        _pending.value = null
    }
}
