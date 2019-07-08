package com.stasbar.pdfscraper

import io.ktor.client.HttpClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

class WebScraperTest {
    val outputDir =
        Files.createTempDirectory(Paths.get("/", "Users", "stasbar", "Sandbox", "downloadedPdfs"), "webscraper")
    val serverAddress = "http://0.0.0.0:8080"
    val timeFormatter = SimpleDateFormat("mm:ss.SSS")
    @Before
    fun setUp() {
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                println("${if (tag != null) "[$tag]" else ""} $message")
            }
        })
    }

    @After
    fun tearDown() {
        //deleteDir(cacheDir.toFile())
    }

    private fun deleteDir(file: File) {
        val contents = file.listFiles()
        if (contents != null) {
            for (f in contents)
                if (!Files.isSymbolicLink(f.toPath())) deleteDir(f)

        }
        file.delete()
    }


    @Test
    fun `test launchScraper with whole updater`() = runBlocking<Unit> {
        val time = measureTimeMillis {
            val webScraper = WebScraper()
            val downloadPdfChannel = Channel<URL>(UNLIMITED)
            val visitedUpdateChannel = Channel<String>(UNLIMITED)
            val visitedList = mutableListOf<String>()
            val httpClient = HttpClient {
                followRedirects = true
                expectSuccess = false
            }

            val updateCounter = AtomicInteger(0)
            val updateList = Collections.synchronizedList(ArrayList<String>())
            launch {
                for (update in visitedUpdateChannel) {
                    Timber.tag("${updateCounter.incrementAndGet()} Updater").d("Update visited website $update")
                    updateList.add(update)
                }
            }
            val scraperJob = with(webScraper) {
                launchScraper(
                    URL(serverAddress),
                    visitedList,
                    httpClient,
                    downloadPdfChannel,
                    visitedUpdateChannel
                )
            }


            val downloadCounter = AtomicInteger(0)
            val downloadList = Collections.synchronizedList(ArrayList<URL>())
            val downloaderJob = launch {
                for (pdfUrl in downloadPdfChannel) {
                    Timber.tag("${downloadCounter.getAndIncrement()} Downloader")
                        .d(" Should download pdf: ${pdfUrl.file}")
                    downloadList.add(pdfUrl)
                }
            }


            scraperJob.join()
            visitedUpdateChannel.close()
            downloadPdfChannel.close()
            downloaderJob.join()

            Timber.d("Download List: $downloadList")
            assertEquals(downloadCounter.get(), 5)

            Timber.d("Update List: $updateList")
            Timber.d("Visited List: $visitedList")
            assertEquals(visitedList.size, 12)
            assertEquals(updateCounter.get(), 12)
        }
        Timber.d("Scraped in ${timeFormatter.format(time)}")
    }


    @Test
    fun `launchScraper should not block on update consumer absence`() = runBlocking<Unit> {
        withTimeout(Duration.ofSeconds(30)) {

            val webScraper = WebScraper()
            val downloadPdfChannel = Channel<URL>(UNLIMITED)
            val visitedUpdateChannel = Channel<String>()
            val visitedList = mutableListOf<String>()
            val httpClient = HttpClient {
                followRedirects = true
                expectSuccess = false
            }
            val scraperJob = with(webScraper) {
                launchScraper(
                    URL(serverAddress),
                    visitedList,
                    httpClient,
                    downloadPdfChannel,
                    visitedUpdateChannel
                )
            }
            val downloaderJob = launch {
                for (pdfUrl in downloadPdfChannel);
            }


            scraperJob.join()
            visitedUpdateChannel.close()
            downloadPdfChannel.close()
            downloaderJob.join()

            Timber.d("Visited List: $visitedList")
            assertEquals(visitedList.size, 12)
        }
    }

    @Test
    fun `test launchDownloader`() = runBlocking<Unit> {
        val webScraper = WebScraper()
        val downloadedList = mutableListOf<String>()
        val downloadPdfChannel = Channel<URL>()
        val downloadUpdateChannel = Channel<String>(UNLIMITED)
        val downloader = with(webScraper) {
            launchDownloader(
                HttpClient(),
                outputDir.toFile(),
                downloadedList,
                downloadPdfChannel,
                downloadUpdateChannel
            )
        }
        val updater = launch {
            for (update in downloadUpdateChannel) Timber.d("Update: $update")
        }
        repeat(5) {
            downloadPdfChannel.send(URL("$serverAddress/static/pdf/f${it + 1}.pdf"))
        }
        downloadPdfChannel.close()

        downloader.join()
        downloadUpdateChannel.close()
        updater.join()

        Timber.d("Done downloading ${outputDir.toFile().listFiles()?.size} files")
    }

}