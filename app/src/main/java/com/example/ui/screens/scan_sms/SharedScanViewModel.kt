package com.example.ui.screens.scan_sms

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedScanViewModel : ViewModel() {
    private val _pendingScanItem = MutableStateFlow<ScannedSmsItem?>(null)
    val pendingScanItem: StateFlow<ScannedSmsItem?> = _pendingScanItem.asStateFlow()

    private val _recentlySavedSmsHash = MutableStateFlow<String?>(null)
    val recentlySavedSmsHash: StateFlow<String?> = _recentlySavedSmsHash.asStateFlow()

    private val _savedHashes = MutableStateFlow<Set<String>>(emptySet())
    val savedHashes: StateFlow<Set<String>> = _savedHashes.asStateFlow()

    fun setPendingScanItem(item: ScannedSmsItem?) {
        _pendingScanItem.value = item
        // Keep ScanSmsDataHolder in sync as secondary safety
        ScanSmsDataHolder.selectedItem = item
    }

    fun clearPendingScanItem() {
        _pendingScanItem.value = null
        ScanSmsDataHolder.selectedItem = null
    }

    fun markSmsSaved(hash: String) {
        if (hash.isNotBlank()) {
            _recentlySavedSmsHash.value = hash
            _savedHashes.value = _savedHashes.value + hash
        }
    }

    fun clearRecentlySavedSmsHash() {
        _recentlySavedSmsHash.value = null
    }
}
