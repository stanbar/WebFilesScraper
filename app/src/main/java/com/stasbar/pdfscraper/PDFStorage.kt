package com.stasbar.pdfscraper

import android.content.SharedPreferences
import android.os.Environment
import timber.log.Timber
import java.io.File

class PDFStorage(val sharedPreferences: SharedPreferences) {
    fun getOutputPath(): String {
        val defaultOutputDir = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS
            ), OUTPUT_DIR_NAME
        )
        if (!defaultOutputDir.mkdirs()) {
            Timber.e("Directory ${defaultOutputDir.absolutePath} not created")
        }
        return sharedPreferences.getString(OUTPUT_PATH_KEY, defaultOutputDir.absolutePath)!!
    }

    fun setOutputPath(newOutputPath: String) {
        sharedPreferences.edit().putString(OUTPUT_PATH_KEY, newOutputPath).apply()
    }

    companion object {
        const val OUTPUT_PATH_KEY = "output_dir"
        const val OUTPUT_DIR_NAME = "PDFScraper"
    }
}