package com.stasbar.pdfscraper

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.MutableLiveData
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.response.HttpResponse
import io.ktor.http.toURI
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.io.jvm.javaio.copyTo
import org.jsoup.Jsoup
import org.koin.android.ext.android.get
import timber.log.Timber
import java.io.File
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
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

    val visitedUpdateChannel = Channel<String>()
    val downloadUpdateChannel = Channel<String>()

    fun scrapeWebsite(url: URL) {
        val visitedList = Collections.synchronizedList(ArrayList<String>())
        visitedLiveData.value = visitedList
        val downloadList = Collections.synchronizedList(ArrayList<String>())
        downloadLiveData.value = downloadList

        val pdfStorage: PDFStorage = get()

        val outputDirFile = File(pdfStorage.getOutputPath(), url.host.replace(".", "_"))
        if (!outputDirFile.mkdirs()) {
            Timber.i("Directory $outputDirFile not created")
        }
        val downloadPdfChannel = Channel<URL>(UNLIMITED)

        val httpClient = HttpClient {
            followRedirects = true
            expectSuccess = false
        }

        val scrapEverything = launchScraper(url, visitedList, httpClient, downloadPdfChannel)
        val downloadingJob = launchDownloader(outputDirFile, downloadList, downloadPdfChannel)
        launch {
            scrapEverything.join()
            downloadPdfChannel.close()
            downloadingJob.join()
            stopSelf()
            httpClient.close()
        }
    }

    private fun CoroutineScope.launchScraper(
        url: URL,
        visited: MutableList<String>,
        httpClient: HttpClient,
        downloadPdfChannel: SendChannel<URL>
    ): Job =
        launch(Dispatchers.IO) {
            if (visited.contains(url.toString()))
                return@launch

            visited.add(url.toString())
            visitedUpdateChannel.offer(url.toString())
            val res = try {
                httpClient.get<HttpResponse>(url)
            } catch (e: Exception) {
                Timber.e("GET on $url failed", e)
                return@launch
            }

            Timber.d("Visited ${res.call.request.url}")

            if (res.headers["Content-Type"] == "application/pdf" || res.call.request.url.toString().endsWith(".pdf")) {
                val fileUrl = res.call.request.url.toURI().toURL()
                downloadPdfChannel.send(fileUrl)
            } else {
                val document = try {
                    Jsoup.parse(res.receive<String>())
                } catch (e: Exception) {
                    Timber.e("receive string on $url failed", e)
                    return@launch
                }
                val linksOnPage = document.select("a[href]")

                linksOnPage.mapNotNull { page ->
                    try {
                        val host = URI(page.attr("abs:href")).host
                        if (host == url.host)
                            launchScraper(URL(page.attr("abs:href")), visited, httpClient, downloadPdfChannel)
                        else
                            null
                    } catch (e: URISyntaxException) {
                        println(e.message)
                        null
                    } catch (e: MalformedURLException) {
                        println(e.message)
                        null
                    }
                }
            }
        }


    private fun CoroutineScope.launchDownloader(
        outputDir: File,
        downloaded: MutableList<String>,
        receiveChannel: ReceiveChannel<URL>
    ) =
        launch(Dispatchers.IO) {
            val downloadingClient = HttpClient()
            for (url in receiveChannel) {
                val response = downloadingClient.get<HttpResponse>(url.toString())
                Timber.d("Downloading PDF file: ${url.file}")
                val fileName = url.toString().substring(url.toString().lastIndexOf('/') + 1, url.toString().length)
                val outputFile = File(outputDir, fileName)
                outputFile.outputStream().use { outputStream ->
                    val count = response.content.copyTo(outputStream)
                    Timber.d("Downloading finished with $count bytes copied")
                    downloaded.add(outputFile.absolutePath)
                    downloadUpdateChannel.offer(outputFile.absolutePath)
                }
            }
            downloadingClient.close()
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
            val importance = NotificationManager.IMPORTANCE_DEFAULT
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
