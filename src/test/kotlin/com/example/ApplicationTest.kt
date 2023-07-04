package com.example

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.server.websocket.WebSockets
import io.ktor.util.collections.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class WSException : Exception()

class ApplicationTest : FunSpec() {
    init {
        test("repro") {
            testApplication {
                application {

                    install(StatusPages) {
                        exception<WSException> { call, _ ->
                            call.response.status(HttpStatusCode.TooManyRequests)
                        }
                    }

                    val counter = ConcurrentMap<String, Int>()
                    counter["this"] = 0

                    install(Authentication) {
                        bearer("auth") {
                            authSchemes("AccessKey")
                            authHeader { call -> HttpAuthHeader.Single("AccessKey", "pass") }
                            authenticate {
                                counter["this"] = counter["this"]!! + 1
                                if (counter["this"]!! >= 3) {
                                    throw WSException()
                                }
                                object : Principal {}
                            }
                        }
                    }

                    this.install(WebSockets) {
                        pingPeriod = java.time.Duration.ofSeconds(15)
                        timeout = java.time.Duration.ofSeconds(45)
                        maxFrameSize = Long.MAX_VALUE
                        masking = false
                    }

                    routing {
                        authenticate("auth") {
                            route("/") {
                                webSocket {
                                    incoming.consumeEach {}
                                }
                            }
                        }
                    }
                }

                val client = createClient {
                    install(io.ktor.client.plugins.websocket.WebSockets) {}
                }

                client.webSocketSession { }
                delay(1.seconds)
                client.webSocketSession { }
                delay(1.seconds)
                shouldThrow<CancellationException> {
                    client.webSocketSession { }
                }
            }
        }
    }
}
