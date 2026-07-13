package dev.lorenzods.anonimizadorpdf.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lorenzods.anonimizadorpdf.data.preferences.AppPreferences
import dev.lorenzods.anonimizadorpdf.domain.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val preferences: AppPreferences,
) : ViewModel() {

    /** null = still loading, true/false = resolved onboarding flag. */
    val onboardingDone: StateFlow<Boolean?> = preferences.onboardingDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val dynamicColor: StateFlow<Boolean> = preferences.dynamicColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Initial value true: FLAG_SECURE stays set until the preference explicitly disables it. */
    val blockScreenshots: StateFlow<Boolean> = preferences.blockScreenshots
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun completeOnboarding() {
        viewModelScope.launch { preferences.setOnboardingDone(true) }
    }
}
