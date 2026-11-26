package com.example.data.repository

import com.example.util.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AppLockStore {
    var lockEnabled: Boolean
    var secureScreenEnabled: Boolean
    var lockGraceMs: Long
    var lastBackgroundAt: Long
}

class PreferencesAppLockStore(
    private val preferences: Preferences
) : AppLockStore {
    override var lockEnabled: Boolean
        get() = preferences.isBiometricEnabled
        set(value) { preferences.isBiometricEnabled = value }

    override var secureScreenEnabled: Boolean
        get() = preferences.isSecureScreenEnabled
        set(value) { preferences.isSecureScreenEnabled = value }

    override var lockGraceMs: Long
        get() = preferences.lockGraceMs
        set(value) { preferences.lockGraceMs = value }

    override var lastBackgroundAt: Long
        get() = preferences.lastBackgroundAt
        set(value) { preferences.lastBackgroundAt = value }
}

/**
 * Manages application lock state, grace period evaluation, and screen security flag policy.
 */
class AppLockRepository(
    private val store: AppLockStore,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    constructor(
        preferences: Preferences,
        clock: () -> Long = { System.currentTimeMillis() }
    ) : this(PreferencesAppLockStore(preferences), clock)

    private val _lockEnabled = MutableStateFlow(store.lockEnabled)
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()

    private val _secureScreenEnabled = MutableStateFlow(store.secureScreenEnabled)
    val secureScreenEnabled: StateFlow<Boolean> = _secureScreenEnabled.asStateFlow()

    private val _lockGraceMs = MutableStateFlow(store.lockGraceMs)
    val lockGraceMs: StateFlow<Long> = _lockGraceMs.asStateFlow()

    private val _lastBackgroundAt = MutableStateFlow(store.lastBackgroundAt)
    val lastBackgroundAt: StateFlow<Long> = _lastBackgroundAt.asStateFlow()

    // On cold start, if app lock is enabled, start in locked state immediately.
    private val _locked = MutableStateFlow(store.lockEnabled)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    fun setLockEnabled(enabled: Boolean) {
        store.lockEnabled = enabled
        _lockEnabled.value = enabled
        if (!enabled) {
            _locked.value = false
            clearLastBackgroundAt()
        } else {
            // User is actively in Settings enabling the lock; clear stale background timestamp
            // so the lock engages cleanly on next background exit or cold launch.
            clearLastBackgroundAt()
        }
    }

    fun setSecureScreenEnabled(enabled: Boolean) {
        store.secureScreenEnabled = enabled
        _secureScreenEnabled.value = enabled
    }

    fun setLockGraceMs(graceMs: Long) {
        val sanitized = graceMs.coerceAtLeast(0L)
        store.lockGraceMs = sanitized
        _lockGraceMs.value = sanitized
    }

    fun onAppBackgrounded(nowMs: Long = clock()) {
        if (!_lockEnabled.value) return
        store.lastBackgroundAt = nowMs
        _lastBackgroundAt.value = nowMs
    }

    fun onAppForegrounded(nowMs: Long = clock()) {
        if (!_lockEnabled.value) {
            _locked.value = false
            return
        }

        if (_locked.value) {
            // Already locked; require explicit authentication before unlocking.
            return
        }

        val lastBg = _lastBackgroundAt.value
        if (lastBg <= 0L) {
            return
        }

        val elapsed = nowMs - lastBg
        val graceMs = _lockGraceMs.value.coerceAtLeast(0L)
        clearLastBackgroundAt()

        // Fail-closed if system clock rolled backwards (elapsed < 0) or grace duration elapsed.
        if (elapsed < 0L || elapsed >= graceMs) {
            _locked.value = true
        }
    }

    fun unlock() {
        _locked.value = false
        clearLastBackgroundAt()
    }

    fun lockNow() {
        if (_lockEnabled.value) {
            _locked.value = true
        }
    }

    fun shouldApplySecureFlag(): Boolean {
        return _lockEnabled.value || _secureScreenEnabled.value || _locked.value
    }

    private fun clearLastBackgroundAt() {
        store.lastBackgroundAt = 0L
        _lastBackgroundAt.value = 0L
    }
}
