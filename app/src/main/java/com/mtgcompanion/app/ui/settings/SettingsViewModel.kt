package com.mtgcompanion.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SettingsUiState(
    val clientId: String = "",
    val clientSecret: String = "",
    val savedMessage: String? = null
)

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.tcgPlayerClientId, repository.tcgPlayerClientSecret) { id, secret ->
                id to secret
            }.collect { (id, secret) ->
                _uiState.value = _uiState.value.copy(
                    clientId = id ?: "",
                    clientSecret = secret ?: ""
                )
            }
        }
    }

    fun onClientIdChange(value: String) {
        _uiState.value = _uiState.value.copy(clientId = value, savedMessage = null)
    }

    fun onClientSecretChange(value: String) {
        _uiState.value = _uiState.value.copy(clientSecret = value, savedMessage = null)
    }

    fun save() {
        viewModelScope.launch {
            repository.saveTcgPlayerCredentials(_uiState.value.clientId, _uiState.value.clientSecret)
            _uiState.value = _uiState.value.copy(savedMessage = "Saved.")
        }
    }

    fun clear() {
        viewModelScope.launch {
            repository.clearTcgPlayerCredentials()
            _uiState.value = SettingsUiState(savedMessage = "Cleared.")
        }
    }

    class Factory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(repository) as T
    }
}
