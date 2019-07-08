package com.stasbar.pdfscraper

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.MutableLiveData
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import timber.log.Timber
import java.io.File
import java.net.URL
import java.util.*
import kotlin.coroutines.CoroutineContext

class ScraperService : Service(), CoroutineScope {
    private val parent = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = parent
    private val binder = ScraperBinder()

    val visitedLiveData = MutableLiveData<List<String>>()
    val downloadLiveData = MutableLiveData<List<String>>()

    val visitedUpdateChannel = Channel<String>(10)
    val downloadUpdateChannel = Channel<String>(10)

    var workingScraper = false
    var workingDownloader = false

    fun scrapeWebsite(url: URL) {
        val visitedList = Collections.synchronizedList(ArrayList<String>())
        visitedLiveData.value = visitedList
        val downloadList = Collections.synchronizedList(ArrayList<String>())
        downloadLiveData.value = downloadList

        val filesStorage: FilesStorage = get()
        val webScraper: WebScraper = get()
        val httpClient: HttpClient = get()

        val outputDirFile = File(filesStorage.getOutputPath(), url.host.replace(".", "_"))
        if (!outputDirFile.mkdirs()) {
            Timber.i("Directory $outputDirFile not created")
        }
        val foundPdfChannel = Channel<URL>(UNLIMITED)

        val scraperJob = with(webScraper) {
            launchScraper(url, visitedList, httpClient, foundPdfChannel, visitedUpdateChannel, depth = 0)
        }

        val downloadingJob = with(webScraper) {
            launchDownloader(
                HttpClient(),
                outputDirFile,
                downloadList,
                foundPdfChannel,
                downloadUpdateChannel
            )
        }
        launch {
            workingScraper = true
            workingDownloader = true
            scraperJob.join()
            workingScraper = false
            httpClient.close()
            foundPdfChannel.close()
            downloadingJob.join()
            workingDownloader = false
            stopSelf()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Timber.d("onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Timber.d("onUnbind")
        return super.onUnbind(intent)
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("onCreate")
        showForegroundNotification()
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        parent.cancelChildren()
        super.onDestroy()
    }

    private fun showForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
            val pendingIntent: PendingIntent =
                Intent(this, ScraperActivity::class.java).let { notificationIntent ->
                    PendingIntent.getActivity(this, 0, notificationIntent, 0)
                }

            val notification: Notification =
                Notification.Builder(this, getString(R.string.scraper_channel_id))
                    .setContentTitle(getText(R.string.scraping))
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(pendingIntent)
                    .setTicker(getText(R.string.ticker_text))
                    .build()
            startForeground(SCRAPING_NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = applicationContext.getString(R.string.scraping)
            val descriptionText =
                applicationContext.getString(R.string.scraping_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel =
                NotificationChannel(applicationContext.getString(R.string.scraper_channel_id), name, importance).apply {
                    description = descriptionText
                }
            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    inner class ScraperBinder : Binder() {
        fun getService(): ScraperService = this@ScraperService
    }

    companion object {
        const val SCRAPING_NOTIFICATION_ID = 100
    }
}
