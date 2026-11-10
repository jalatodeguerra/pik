package com.pik.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    prefs: PikPreferences,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)

    /** null while loading. Read once: later changes must not rebuild the nav graph. */
    val startDestination: StateFlow<String?> = _startDestination

    init {
        viewModelScope.launch {
            val done = prefs.onboardingComplete.first()
            _startDestination.value = if (done) Routes.HOME else Routes.FIRST_RUN
        }
    }
}
