package com.stasbar

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.html.respondHtml
import io.ktor.http.ContentType
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.locations.Location
import io.ktor.locations.Locations
import io.ktor.locations.get
import io.ktor.request.path
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import kotlinx.css.CSSBuilder
import kotlinx.css.Color
import kotlinx.css.body
import kotlinx.css.em
import kotlinx.html.*
import org.slf4j.event.Level

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(Locations) {
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    routing {
        get("/") {
            call.respondHtml {
                head {
                    styleLink("/styles.css")
                }
                body {
                    ul {
                        li {
                            a("/static/pdf/f1.pdf") { +"F1" }
                        }
                        li {
                            a("/website1") { +"W1" }
                        }
                        li {
                            a("/website2") { +"W2" }
                        }
                        li {
                            a("/redirect-file4") { +"RF4" }
                        }
                        li {
                            a("/redirect-website4") { +"RW4" }
                        }
                    }

                }
            }
        }
        get("/website1") {
            call.respondHtml {
                body {
                    a("/static/pdf/f2.pdf") { +"F2" }
                }
            }
        }

        get("/website2") {
            call.respondHtml {
                body {
                    a("/website3") { +"W3" }
                }
            }
        }

        get("/website3") {
            call.respondHtml {
                body {
                    a("/static/pdf/f3.pdf") { +"F3" }
                }
            }
        }

        get("/redirect-file4") {
            call.respondRedirect("/static/pdf/f4.pdf")
        }
        get("/redirect-website4") {
            call.respondRedirect("/website4")
        }

        get("/website4") {
            call.respondHtml {
                body {
                    a("/static/pdf/f5.pdf") { +"F5" }
                }
            }
        }


        get("/styles.css") {
            call.respondCss {
                body {
                    backgroundColor = Color.white
                }
                rule("ul > li > a") {
                    fontSize = 2.em
                }
            }
        }

        // Static feature. Try to access `/static/ktor_logo.svg`
        static("/static") {
            resources("static")
        }

        get<MyLocation> {
            call.respondText("Location: name=${it.name}, arg1=${it.arg1}, arg2=${it.arg2}")
        }
        // Register nested routes
        get<Type.Edit> {
            call.respondText("Inside $it")
        }
        get<Type.List> {
            call.respondText("Inside $it")
        }
    }
}

@Location("/location/{name}")
class MyLocation(val name: String, val arg1: Int = 42, val arg2: String = "default")

@Location("/type/{name}")
data class Type(val name: String) {
    @Location("/edit")
    data class Edit(val type: Type)

    @Location("/list/{page}")
    data class List(val type: Type, val page: Int)
}

fun FlowOrMetaDataContent.styleCss(builder: CSSBuilder.() -> Unit) {
    style(type = ContentType.Text.CSS.toString()) {
        +CSSBuilder().apply(builder).toString()
    }
}

fun CommonAttributeGroupFacade.style(builder: CSSBuilder.() -> Unit) {
    this.style = CSSBuilder().apply(builder).toString().trim()
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
    this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}
