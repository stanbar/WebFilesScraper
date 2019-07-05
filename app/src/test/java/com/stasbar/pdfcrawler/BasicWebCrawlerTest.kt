package com.stasbar.pdfcrawler

import org.junit.Test

class BasicWebCrawlerTest {

    @Test
    fun testCrawler() {
        val basicWebCrawler = BasicWebCrawler()
        basicWebCrawler.getPageLinks("https://bip.gdansk.pl")
        println(basicWebCrawler.links.toString())
    }
}