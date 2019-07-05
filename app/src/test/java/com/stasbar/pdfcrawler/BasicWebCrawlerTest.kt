package com.stasbar.pdfcrawler

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Files.delete


class BasicWebCrawlerTest {
    val cacheDir = Files.createTempDirectory("webcrawler")

    @After
    fun tearDown() {
        deleteDir(cacheDir.toFile())
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
    fun `test URL`() {
        println(Paths.get(URL("https://www.gdansk.pl/download/2019-03/123793.pdf").path).fileName)
    }

    @Test
    fun `test downloader`() = runBlocking<Unit> {
        val channel = Channel<URL>()
        val job = launchDownloader(cacheDir.toFile(), channel)
        channel.send(URL("https://www.gdansk.pl/download/2019-03/123793.pdf"))
        delay(1000)
        job.cancel()
    }

    @Test
    fun `test integration`() = runBlocking<Unit> {
        val sendDownloadChannel = Channel<URL>(Channel.UNLIMITED)
        val scraper = launchScraper(
            "https://bip.gdansk.pl/zamowienia-publiczne/reklama-gdanska-jako-produktu-turystycznego-w-formie-lokowania-produktu-w-serialu-tvn,a,145522",
            sendDownloadChannel
        )
        val downloader = launchDownloader(cacheDir.toFile(), sendDownloadChannel)

        delay(60000)
        for (file in cacheDir) {
            println(file.toString())
        }
        scraper.cancel()
        downloader.cancel()
    }
}