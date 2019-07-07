package com.stasbar.pdfscraper

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.files.folderChooser
import com.karumi.dexter.Dexter
import kotlinx.android.synthetic.main.activity_scraper.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.io.File
import java.lang.ref.WeakReference
import java.net.MalformedURLException
import java.net.URL

class ScraperActivity : AppCompatActivity() {

    private val serviceIntent by lazy { Intent(this, ScraperService::class.java) }
    private val pdfStorage: PDFStorage by inject()
    lateinit var visitedAdapter: UriAdapter
    lateinit var downloadedAdapter: UriAdapter
    lateinit var service: ScraperService
    lateinit var connection: ScraperConnection
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connection = ScraperConnection()
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        setContentView(R.layout.activity_scraper)
        updateOutputPath(etUrl.text.toString())
        tvOutputPath.setOnClickListener {
            checkingPermissions {
                openFolderChooser()
            }
        }
        etUrl.addOnTextChangeListener {
            updateOutputPath(it)
        }

        btnStartCrawler.setOnClickListener { startCrawler() }
        btnStop.setOnClickListener { stopCrawler() }

        visitedAdapter = UriAdapter { uri -> Intent(Intent.ACTION_VIEW, Uri.parse(uri)).also { startActivity(it) } }
        rvVisitedPages.adapter = visitedAdapter

        downloadedAdapter = UriAdapter { uri ->
            Intent(Intent.ACTION_VIEW).also { intent ->
                intent.setDataAndType(Uri.parse(uri), "application/pdf")
                val chooser = Intent.createChooser(intent, "Open PDF in")
                startActivity(chooser)
            }
        }
        rvDownloadedPdfs.adapter = downloadedAdapter
    }

    private fun updateOutputPath(path: String) {
        try {
            val url = URL(path)
            val outputDir = File(pdfStorage.getOutputPath(), url.host.replace(".", "_"))
            tvOutputPath.text = outputDir.absolutePath
            Timber.d("host: ${url.host}")
            etUrl.error = null
        } catch (e: MalformedURLException) {
            etUrl.error = e.message
        }
    }

    private fun openFolderChooser() {
        MaterialDialog(this@ScraperActivity).show {
            folderChooser(
                initialDirectory = File(pdfStorage.getOutputPath()),
                allowFolderCreation = true
            ) { _, file ->
                pdfStorage.setOutputPath(file.absolutePath)
            }
        }
    }

    private fun startCrawler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val urlString = etUrl.text.toString()
        try {
            val url = URL(urlString)
            service.scrapeWebsite(url)
        } catch (e: MalformedURLException) {
            etUrl.error = e.message
        }
    }

    private fun stopCrawler() {
        connection.updater.cancel()
        try {
            unbindService(connection)
        } catch (e: IllegalArgumentException) {
            Timber.e("filed unbindService", e)
        }
        val stopResult = stopService(serviceIntent)
        if (stopResult) {
            Timber.d("stopped service")
        } else {
            Timber.d("service is already stopped")
        }
    }

    override fun onDestroy() {
        try {
            unbindService(connection)
        } catch (e: IllegalArgumentException) {
            Timber.e("filed unbindService in onDestroy", e)
        }
        connection.updater.cancel()
        super.onDestroy()
    }

    private fun checkingPermissions(function: () -> Unit) {
        Dexter.withActivity(this)
            .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .withListener({ function() }, { toast("This permission is required") })
            .check()
    }

    private fun toast(text: String) {
        Toast.makeText(this@ScraperActivity, text, Toast.LENGTH_SHORT).show()
    }


    inner class ScraperConnection : ServiceConnection {
        lateinit var updater: Job
        @SuppressLint("SetTextI18n")
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            Timber.d("onServiceConnected")
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val service = (binder as ScraperService.ScraperBinder).getService()
            this@ScraperActivity.service = service
            updater = launchUpdater(WeakReference(service))
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Timber.d("onServiceDisconnected")
            updater.cancel()
        }

        fun launchUpdater(ref: WeakReference<ScraperService>) =
            lifecycleScope.launch {
                ref.get()?.visitedLiveData?.observe(this@ScraperActivity, Observer {
                    launch(Dispatchers.Main) {
                        visitedAdapter.replaceData(it)
                        tvVisitedPages.text = "${getString(R.string.visited_pages)} ${it.size}"
                    }
                })

                launch {
                    ref.get()?.visitedUpdateChannel?.let {
                        for (newItem in it) {
                            withContext(Dispatchers.Main) {
                                visitedAdapter.add(newItem)
                                tvVisitedPages.text = "${getString(R.string.visited_pages)} ${visitedAdapter.itemCount}"
                            }
                        }
                    }
                }
                ref.get()?.downloadLiveData?.observe(this@ScraperActivity, Observer {
                    launch(Dispatchers.Main) {
                        downloadedAdapter.replaceData(it)
                        tvDownloadedPdfs.text = "${getString(R.string.downloaded_pdfs)} ${it.size}"
                    }
                })
                launch {
                    ref.get()?.downloadUpdateChannel?.let {
                        for (newItem in it) {
                            withContext(Dispatchers.Main) {
                                downloadedAdapter.add(newItem)
                                tvDownloadedPdfs.text =
                                    "${getString(R.string.downloaded_pdfs)} ${downloadedAdapter.itemCount}"
                            }
                        }
                    }
                }
            }
    }

}
