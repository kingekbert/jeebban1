package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockRepositoryTest {

    private class FakeAppLockStore(
        override var lockEnabled: Boolean = false,
        override var secureScreenEnabled: Boolean = false,
        override var lockGraceMs: Long = 30_000L,
        override var lastBackgroundAt: Long = 0L
    ) : AppLockStore

    @Test
    fun `cold start stays unlocked when lockEnabled is false`() {
        val store = FakeAppLockStore(lockEnabled = false)
        val repo = AppLockRepository(store)

        assertFalse(repo.lockEnabled.value)
        assertFalse(repo.locked.value)
        assertFalse(repo.shouldApplySecureFlag())
    }

    @Test
    fun `cold start starts locked when lockEnabled is true`() {
        val store = FakeAppLockStore(lockEnabled = true)
        val repo = AppLockRepository(store)

        assertTrue(repo.lockEnabled.value)
        assertTrue(repo.locked.value)
        assertTrue(repo.shouldApplySecureFlag())
    }

    @Test
    fun `returning within grace period keeps app unlocked`() {
        var now = 1_000_000L
        val store = FakeAppLockStore(lockEnabled = true, lockGraceMs = 30_000L)
        val repo = AppLockRepository(store, clock = { now })

        // Authenticate initial cold lock
        repo.unlock()
        assertFalse(repo.locked.value)

        // Move to background
        repo.onAppBackgrounded()
        assertEquals(1_000_000L, repo.lastBackgroundAt.value)

        // Return after 20 seconds (< 30s grace)
        now += 20_000L
        repo.onAppForegrounded()
        assertFalse(repo.locked.value)
        assertEquals(0L, repo.lastBackgroundAt.value)
    }

    @Test
    fun `returning after grace period locks app`() {
        var now = 1_000_000L
        val store = FakeAppLockStore(lockEnabled = true, lockGraceMs = 30_000L)
        val repo = AppLockRepository(store, clock = { now })

        repo.unlock()
        assertFalse(repo.locked.value)

        repo.onAppBackgrounded()

        // Return after 30 seconds (>= 30s grace)
        now += 30_000L
        repo.onAppForegrounded()
        assertTrue(repo.locked.value)

        // Unlock after biometric success
        repo.unlock()
        assertFalse(repo.locked.value)
    }

    @Test
    fun `zero grace period locks immediately on return from background`() {
        var now = 5_000_000L
        val store = FakeAppLockStore(lockEnabled = true, lockGraceMs = 0L)
        val repo = AppLockRepository(store, clock = { now })

        repo.unlock()
        repo.onAppBackgrounded()
        now += 100L
        repo.onAppForegrounded()

        assertTrue(repo.locked.value)
    }

    @Test
    fun `clock rollback fails closed and locks app`() {
        var now = 5_000_000L
        val store = FakeAppLockStore(lockEnabled = true, lockGraceMs = 30_000L)
        val repo = AppLockRepository(store, clock = { now })

        repo.unlock()
        repo.onAppBackgrounded()

        // System clock moved backwards
        now -= 5_000L
        repo.onAppForegrounded()

        assertTrue(repo.locked.value)
    }

    @Test
    fun `disabling lock immediately unlocks and clears FLAG_SECURE when secure screen is off`() {
        val store = FakeAppLockStore(lockEnabled = true, secureScreenEnabled = false)
        val repo = AppLockRepository(store)

        assertTrue(repo.locked.value)
        assertTrue(repo.shouldApplySecureFlag())

        repo.setLockEnabled(false)
        assertFalse(repo.locked.value)
        assertFalse(repo.shouldApplySecureFlag())
    }

    @Test
    fun `secure screen independently enables FLAG_SECURE even when app lock is off`() {
        val store = FakeAppLockStore(lockEnabled = false, secureScreenEnabled = false)
        val repo = AppLockRepository(store)

        assertFalse(repo.shouldApplySecureFlag())
        repo.setSecureScreenEnabled(true)
        assertTrue(repo.shouldApplySecureFlag())
        assertFalse(repo.locked.value)
    }
}
