package com.mfp.filemanager.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    // Current Tab Index (0=Home, 1=Music, 2=Settings)
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Events to request Tab Change (from Bottom Nav click)
    private val _targetTab = MutableSharedFlow<Int>()
    val targetTab: SharedFlow<Int> = _targetTab.asSharedFlow()

    // Bottom search bar state (shared with HomeFragment so Back can close it)
    private val _searchExpanded = MutableStateFlow(false)
    val searchExpanded: StateFlow<Boolean> = _searchExpanded.asStateFlow()

    fun setSearchExpanded(expanded: Boolean) {
        _searchExpanded.value = expanded
    }

    // Text typed in the bottom search bar while a folder (FileBrowserFragment) is open.
    // The folder screen reads it and searches only inside that folder.
    private val _browserSearchQuery = MutableStateFlow("")
    val browserSearchQuery: StateFlow<String> = _browserSearchQuery.asStateFlow()

    fun setBrowserSearchQuery(query: String) {
        _browserSearchQuery.value = query
    }

    fun setCurrentTab(index: Int) {
        _currentTab.value = index
    }

    fun requestTabChange(index: Int) {
        viewModelScope.launch {
            _targetTab.emit(index)
        }
    }
}
