package com.stasbar.pdfcrawler

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

import java.io.IOException
import java.util.HashSet

class BasicWebCrawler {

    val links: HashSet<String> = HashSet()

    fun getPageLinks(URL: String) {
        //4. Check if you have already crawled the URLs
        //(we are intentionally not checking for duplicate content in this example)
        if (!links.contains(URL)) {
            try {
                //4. (i) If not add it to the index
                if (links.add(URL)) {
                    println(URL)
                }

                //2. Fetch the HTML code
                val response = Jsoup.connect(URL).followRedirects(true).execute()

                if (response.url().toString().endsWith(".pdf"))
                    println("PDF Found: ${response.url()}")
                else {
                    val document = response.parse()
                    //3. Parse the HTML to extract links to other URLs
                    val linksOnPage = document.select("a[href]")

                    //5. For each extracted URL... go back to Step 4.
                    for (page in linksOnPage) {
                        if (page.attr("abs:href").startsWith(URL))
                            getPageLinks(page.attr("abs:href"))
                    }
                }
            } catch (e: IOException) {
                System.err.println("For '" + URL + "': " + e.message)
            }

        }
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            //1. Pick a URL from the frontier
            BasicWebCrawler().getPageLinks("http://www.mkyong.com/")
        }
    }

}