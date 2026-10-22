package com.example.ui.screens.scan_sms

import com.example.util.DetectedTransfer

object ScanSmsDataHolder {
    var selectedItem: ScannedSmsItem? = null
    var detectedTransfer: DetectedTransfer? = null
    var openAsTransfer: Boolean = false

    fun clear() {
        selectedItem = null
        detectedTransfer = null
        openAsTransfer = false
    }
}
