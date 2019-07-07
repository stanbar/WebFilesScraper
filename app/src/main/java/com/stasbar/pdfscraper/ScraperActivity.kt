package com.stasbar.pdfscraper

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.files.folderChooser
import com.google.android.material.snackbar.Snackbar
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import kotlinx.android.synthetic.main.activity_scraper.*
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber
import java.io.File
import java.lang.ref.WeakReference
import java.net.MalformedURLException
import java.net.URL
import kotlin.coroutines.CoroutineContext

class ScraperActivity : AppCompatActivity() {

    val serviceIntent by lazy { Intent(this, ScraperService::class.java) }
    val pdfStorage: PDFStorage by inject()
    var executeOnConnect: ((ScraperService) -> Unit)? = null
    var updater = Job()

    private val connection = object : ServiceConnection, CoroutineScope {
        val parent = SupervisorJob()
        override val coroutineContext = parent

        @SuppressLint("SetTextI18n")
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            Timber.d("onServiceConnected")
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val service = (binder as ScraperService.ScraperBinder).getService()

            executeOnConnect?.invoke(service)

            launchUpdater(WeakReference(service))
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Timber.d("onServiceDisconnected")
        }
    }

    fun launchUpdater(ref: WeakReference<ScraperService>) {
        lifecycleScope.launch(updater) {

            while (isActive && ref.get() != null) {
                delay(1000)
                ref.get()?.downloaded?.let {
                    val downloaded = it.toList()
                    withContext(Dispatchers.Main) {
                        downloadedAdapter.replaceData(downloaded)
                        tvDownloadedPdfs.text = "${getString(R.string.downloaded_pdfs)} ${downloaded.size}"
                    }
                }
                ref.get()?.visited?.let {
                    val visited = it.toList()
                    withContext(Dispatchers.Main) {
                        visitedAdapter.replaceData(visited)
                        tvVisitedPages.text = "${getString(R.string.visited_pages)} ${visited.size}"
                    }
                }
            }
        }
    }

    lateinit var visitedAdapter: UriAdapter
    lateinit var downloadedAdapter: UriAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scraper)
        tvOutputPath.text = pdfStorage.getOutputPath()
        tvOutputPath.setOnClickListener {
            checkingPermissions {
                openFolderChooser()
            }
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


    private fun startCrawler() {

        stopCrawler()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        executeOnConnect = {
            val urlString = etUrl.text.toString()
            try {
                val url = URL(urlString)
                it.scrapeWebsite(url)
            } catch (e: MalformedURLException) {
                etUrl.error = e.message
            }
        }
    }

    private fun stopCrawler() {
        updater.cancelChildren()
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

    fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService<ActivityManager>()!!
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
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

    override fun onDestroy() {
        unbindService(connection)
        super.onDestroy()
    }

    private fun checkingPermissions(function: () -> Unit) {
        Dexter.withActivity(this)
            .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(response: PermissionGrantedResponse?) {
                    function()
                }

                override fun onPermissionRationaleShouldBeShown(
                    permission: PermissionRequest?,
                    token: PermissionToken?
                ) {
                }

                override fun onPermissionDenied(response: PermissionDeniedResponse?) {
                    toast("This permission is required")
                }

            })
            .check()
    }

    fun toast(text: String) {
        Toast.makeText(this@ScraperActivity, text, Toast.LENGTH_SHORT).show()
    }
}
