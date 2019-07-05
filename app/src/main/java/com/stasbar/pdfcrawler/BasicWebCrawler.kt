package com.stasbar.pdfcrawler

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.response.HttpResponse
import io.ktor.util.cio.writeChannel
import io.ktor.util.collections.ConcurrentSet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.io.ByteWriteChannel
import kotlinx.coroutines.io.copyAndClose
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections.newSetFromMap


val links = newSetFromMap(ConcurrentHashMap<String, Boolean>())
val pdfsFound = newSetFromMap(ConcurrentHashMap<String, Boolean>())

fun CoroutineScope.launchScraper(url: String, sendChannel: SendChannel<URL>): Job = launch(Dispatchers.IO) {
    val urlHost = URI(url).host
    //4. Check if you have already crawled the urls
    //(we are intentionally not checking for duplicate content in this example)
    if (!links.contains(url)) {
        //4. (i) If not add it to the index
        if (links.add(url)) {

            println(url)
        }
        try {
            //2. Fetch the HTML code
            val response = Jsoup.connect(url).followRedirects(true).ignoreContentType(true).execute()

            if (response.url().toString().endsWith(".pdf")) {
                println("PDF Found: ${response.url()}")
                pdfsFound.add(response.url().toString())
                sendChannel.send(response.url())
            } else {
                val document = response.parse()
                //3. Parse the HTML to extract links to other urls
                val linksOnPage = document.select("a[href]")

                //5. For each extracted url... go back to Step 4.
                for (page in linksOnPage) {
                    try {
                        val host = URI(page.attr("abs:href")).host
                        if (host == urlHost)
                            launchScraper(page.attr("abs:href"), sendChannel)
                    } catch (e: URISyntaxException) {
                        println(e.message)
                    }
                }
            }
        } catch (e: IOException) {
            System.err.println("For '" + url + "': " + e.message)
        }

    }
}

fun CoroutineScope.launchDownloader(outputDir: File, receiveChannel: ReceiveChannel<URL>) = launch(Dispatchers.IO) {
    val client = HttpClient()
    for (url in receiveChannel) {
        val response = client.get<HttpResponse>(url.toString())
        println("Downloading filr ${url.file}")
        val fileName = url.toString().substring(url.toString().lastIndexOf('/') + 1, url.toString().length)
        val outputFile = File(outputDir, fileName)
        response.content.copyAndClose(outputFile.writeChannel())
    }
    client.close()
}