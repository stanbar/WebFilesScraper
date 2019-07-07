package com.stasbar.pdfscraper

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.response.HttpResponse
import io.ktor.http.toURI
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.io.jvm.javaio.copyTo
import org.jsoup.Jsoup
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.io.File
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ScraperService : Service(), CoroutineScope {
    private val parent = SupervisorJob()
    override val coroutineContext = parent
    private val binder = ScraperBinder()


    private val pdfsFound = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    val visited = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    val downloaded = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    val pdfStorage: PDFStorage by inject()

    fun scrapeWebsite(url: URL) {
        val outputDirFile = File(pdfStorage.getOutputPath())
        if (!outputDirFile.mkdirs()) {
            Timber.i("Directory $outputDirFile not created")
        }
        val downloadPdfChannel = Channel<URL>(Channel.UNLIMITED)
        val scrapEverything = launchScraper(url, downloadPdfChannel)
        val downloadingJob = launchDownloader(outputDirFile, downloadPdfChannel)
        launch {
            scrapEverything.join()
            downloadingJob.join()
            stopSelf()
        }
    }

    val httpClient = HttpClient {
        followRedirects = true
        expectSuccess = false
    }

    private fun CoroutineScope.launchScraper(url: URL, downloadPdfChannel: SendChannel<URL>): Job =
        launch(Dispatchers.IO) {
            val urlHost = url.host
            if (visited.contains(url.toString()))
                return@launch

            if (visited.add(url.toString()))
                println(url)
            val res = try {
                httpClient.get<HttpResponse>(url)
            } catch (e: Exception) {
                Timber.e("GET on $url failed", e)
                return@launch
            }
            println("request call: ${res.call.request.url}")
            println("res contentType: ${res.headers["Content-Type"]}")

            if (res.headers["Content-Type"] == "application/pdf" || res.call.request.url.toString().endsWith(".pdf")) {
                println("PDF Found: ${res.call.request.url}")
                pdfsFound.add(res.call.request.url.toString())
                downloadPdfChannel.send(res.call.request.url.toURI().toURL())
            } else {
                val document = try {
                    Jsoup.parse(res.receive<String>())
                } catch (e: Exception) {
                    Timber.e("receive string on $url failed", e)
                    return@launch
                }
                //3. Parse the HTML to extract links to other urls
                val linksOnPage = document.select("a[href]")

                //5. For each extracted url... go back to Step 4.
                linksOnPage.mapNotNull { page ->
                    try {
                        val host = URI(page.attr("abs:href")).host
                        if (host == urlHost)
                            launchScraper(URL(page.attr("abs:href")), downloadPdfChannel)
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


    private fun CoroutineScope.launchDownloader(outputDir: File, receiveChannel: ReceiveChannel<URL>) =
        launch(Dispatchers.IO) {
            val downloadingClient = HttpClient()
            for (url in receiveChannel) {
                val response = downloadingClient.get<HttpResponse>(url.toString())
                println("Downloading file: ${url.file}")
                val fileName = url.toString().substring(url.toString().lastIndexOf('/') + 1, url.toString().length)
                val outputFile = File(outputDir, fileName)
                outputFile.outputStream().use { outputStream ->
                    val count = response.content.copyTo(outputStream)
                    println("Downloading finished with $count bytes copied")
                    downloaded.add(outputFile.absolutePath)
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
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = applicationContext.getString(R.string.scraping)
            val descriptionText =
                applicationContext.getString(R.string.scraping_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(applicationContext.getString(R.string.scraper_channel_id), name, importance).apply {
                    description = descriptionText
                }
            // Register the channel with the system
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
