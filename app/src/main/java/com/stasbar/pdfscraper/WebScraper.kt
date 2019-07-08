package com.stasbar.pdfscraper

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.toURI
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.io.jvm.javaio.copyTo
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.File
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL

class WebScraper {

    fun CoroutineScope.launchScraper(
        url: URL,
        visited: MutableList<String>,
        httpClient: HttpClient,
        downloadPdfChannel: SendChannel<URL>,
        visitedUpdateChannel: SendChannel<String>
    ): Job =
        launch(Dispatchers.IO) {
            if (visited.contains(url.toString()))
                return@launch

            visited.add(url.toString())
            visitedUpdateChannel.offer(url.toString())

            if (url.toString().endsWith(".pdf")) {
                downloadPdfChannel.send(url)
            } else {
                val res = try {
                    httpClient.get<HttpResponse>(url)
                } catch (e: Exception) {
                    Timber.e(e, "GET on $url failed")
                    return@launch
                }
                val reqUrl = res.call.request.url
                Timber.d("GET $reqUrl")

                // Prevent from following redirection as well
                if (!visited.contains(reqUrl.toString())) {
                    visited.add(reqUrl.toString())
                    visitedUpdateChannel.offer(url.toString())
                }

                val contentType = res.headers["Content-Type"]

                if (contentType != null && ContentType.parse(contentType).match(ContentType.Application.Pdf)) {
                    val fileUrl = reqUrl.toURI().toURL()
                    downloadPdfChannel.send(fileUrl)

                } else if (contentType == null || ContentType.parse(contentType).match(ContentType.Text.Html)) {
                    val document = try {
                        Jsoup.parse(res.receive<String>())
                    } catch (e: Exception) {
                        Timber.e("receive string on $url failed")
                        return@launch
                    }
                    val linksOnPage = document.select("a[href]")

                    Timber.d("Found ${linksOnPage.size} links on $url")

                    linksOnPage.mapNotNull { page ->
                        try {
                            val linkUrl = URL(url, page.attr("href"))
                            Timber.d("found link to $linkUrl")
                            if (linkUrl.host == url.host)
                                launchScraper(
                                    linkUrl,
                                    visited,
                                    httpClient,
                                    downloadPdfChannel,
                                    visitedUpdateChannel
                                )
                            else
                                null
                        } catch (e: URISyntaxException) {
                            Timber.e(e)
                            null
                        } catch (e: MalformedURLException) {
                            Timber.e(e)
                            null
                        }
                    }.joinAll()
                }
            }
        }


    fun CoroutineScope.launchDownloader(
        httpClient: HttpClient = HttpClient(),
        outputDir: File,
        downloaded: MutableList<String>,
        receiveChannel: ReceiveChannel<URL>,
        downloadUpdateChannel: SendChannel<String>
    ) =
        launch(Dispatchers.IO) {
            for (url in receiveChannel) {
                val response = httpClient.get<HttpResponse>(url.toString())
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
            httpClient.close()
        }
}