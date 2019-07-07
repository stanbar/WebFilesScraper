package com.stasbar.pdfscraper

import android.app.Application
import android.preference.PreferenceManager
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import timber.log.Timber

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG)
            Timber.plant(Timber.DebugTree())

        startKoin {
            androidContext(this@MainApplication)
            modules(androidContextModules)
        }
    }

    private val androidContextModules = module {
        single { PreferenceManager.getDefaultSharedPreferences(androidContext()) }
        single { PDFStorage(get()) }
    }
}