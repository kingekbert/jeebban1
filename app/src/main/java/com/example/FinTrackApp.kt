package com.example

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.data.local.SeedData
import com.example.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class FinTrackApp : Application() {
    lateinit var appModule: AppModule
        private set

    override fun onCreate() {
        super.onCreate()
        appModule = AppModule(this)

        // Observe whole-process foreground/background lifecycle for App Lock
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    appModule.appLockRepository.onAppForegrounded()
                }

                override fun onStop(owner: LifecycleOwner) {
                    appModule.appLockRepository.onAppBackgrounded()
                }
            }
        )

        // Seed default categories and account on first launch if database is empty
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val existingCategories = appModule.database.categoryDao().getAll().firstOrNull()
                if (existingCategories.isNullOrEmpty()) {
                    SeedData.insertDefaults(
                        accountDao = appModule.database.accountDao(),
                        categoryDao = appModule.database.categoryDao()
                    )
                } else {
                    SeedData.ensureDebtCategories(appModule.database.categoryDao())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
