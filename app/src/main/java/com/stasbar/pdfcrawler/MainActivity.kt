package com.stasbar.pdfcrawler

import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import edu.uci.ics.crawler4j.crawler.CrawlConfig
import edu.uci.ics.crawler4j.crawler.CrawlController
import edu.uci.ics.crawler4j.fetcher.PageFetcher
import edu.uci.ics.crawler4j.robotstxt.RobotstxtServer
import edu.uci.ics.crawler4j.robotstxt.RobotstxtConfig


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStartCrawler.setOnClickListener {
            val config = CrawlConfig()
            config.crawlStorageFolder = filesDir.absolutePath
            config.isIncludeBinaryContentInCrawling = true
            config.isIncludeHttpsPages = true
            val pageFetcher = PageFetcher(config)
            val robotstxtConfig = RobotstxtConfig()
            val robotstxtServer = RobotstxtServer(robotstxtConfig, pageFetcher)
            val controller = CrawlController(config, pageFetcher, robotstxtServer)
            val crawlDomains = listOf(etUrl.text.toString())
            crawlDomains.forEach {
                controller.addSeed(it)
            }

            val factory = CrawlController.WebCrawlerFactory { MyCrawler(applicationContext, crawlDomains) }
            AsyncTask.execute {
                controller.start(factory, 8)
            }
        }
    }
}
