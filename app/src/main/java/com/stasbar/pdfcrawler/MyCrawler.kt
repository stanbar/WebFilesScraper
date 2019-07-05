package com.stasbar.pdfcrawler

import android.content.Context
import android.util.Log
import edu.uci.ics.crawler4j.crawler.Page
import edu.uci.ics.crawler4j.crawler.WebCrawler
import edu.uci.ics.crawler4j.parser.HtmlParseData
import edu.uci.ics.crawler4j.url.WebURL
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import java.net.URL
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.regex.Pattern

class MyCrawler(val context: Context, val crawlDomains: List<String>) : WebCrawler() {
    val tag = "MyCrawler"
    /**
     * This method receives two parameters. The first parameter is the page
     * in which we have discovered this new url and the second parameter is
     * the new url. You should implement this function to specify whether
     * the given url should be crawled or not (based on your crawling logic).
     * In this example, we are instructing the crawler to ignore urls that
     * have css, js, git, ... extensions and to only accept urls that start
     * with "https://www.ics.uci.edu/". In this case, we didn't need the
     * referringPage parameter to make the decision.
     */
    override fun shouldVisit(referringPage: Page?, url: WebURL): Boolean {
        val href = url.url.toLowerCase()
        for (domain in crawlDomains) {
            if (href.startsWith(domain)) {
                return true
            }
        }
        return false
    }

    /**
     * This function is called when a page is fetched and ready
     * to be processed by your program.
     */
    override fun visit(page: Page?) {
        val urlString = page!!.webURL.url
        Log.d(tag, "URL: $urlString")

        if (urlString.endsWith(".pdf")) {
            Log.d(tag, "opening connection")
            val url = URL(urlString)
            val inputChannel = Channels.newChannel(url.openStream())
            val outputDir = context.filesDir.absolutePath
            val outputFile = File(outputDir, UUID.randomUUID().toString() + ".pdf")
            val fos = FileOutputStream(outputFile)

            Log.d(tag, "reading from resource and writing to file ...")
            try {
                fos.channel.transferFrom(inputChannel, 0, Long.MAX_VALUE)
                Log.d(tag, "Stored: ${outputFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(tag, "Failed to write file:  ${outputFile.name}")
            }
        }
    }

    companion object {

        private val FILTERS = Pattern.compile(".*(\\.(.pdf))$")
    }
}