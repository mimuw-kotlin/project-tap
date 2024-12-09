package server

import common.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.test.Test
import kotlin.test.fail

class ServerNetworkTest {

    @Test
    fun serveClientAddNewPlayer() = runBlocking {
        val allPlayers = AllPlayers()
        val place = Place.First

        val serverJob = launch {// Server
            serverListen(host, port, allPlayers, Channel(1))
        }
        delay(100)  // Wait for server to start

        val clientJob = launch {// Client
            val serverMsg = declarePlaceAndGetMsg(host, port, place, 0)
            assert(serverMsg is Message.AcceptPlace)
        }

        clientJob.join()
        serverJob.cancelAndJoin()
    }

    @Test
    fun serveClientRejectPlace() = runBlocking {
        val allPlayers = AllPlayers()
        val place = Place.First

        val serverJob = launch {
            serverListen(host, port, allPlayers, Channel(1))
        }
        delay(100) // Wait for server to start

        val client1Job = launch {
            val serverMsg = declarePlaceAndGetMsg(host, port, place, 0)
            assert(serverMsg is Message.AcceptPlace)
        }
        client1Job.join()

        val client2Job = launch {
            val serverMsg = declarePlaceAndGetMsg(host, port, place, 0)
            assert(serverMsg is Message.RejectPlace)
        }
        client2Job.join()
        serverJob.cancelAndJoin()
    }

    @Test
    fun concurrentServingNewClients() {
        val timeout: Long = 1000
        val place = Place.First
        val allPlayers = AllPlayers()
        val clientCount = 4
        runBlocking {
            val serverJob = launch {
                serverListen(host, port, allPlayers, Channel(1))
            }
            delay(100) // Wait for server to start

            try {
                withTimeout(timeout * (clientCount - 1) + timeout / 2) {
                    val clientsJobs: Array<Job> = Array(clientCount) {
                        launch {
                            declarePlaceAndGetMsg(host, port, place, timeout)
                        }
                    }
                    for (job in clientsJobs) {
                        job.join()
                    }
                    serverJob.cancelAndJoin()
                }
            } catch (e: TimeoutCancellationException) {
                fail("Server did not respond in time")
            }
        }
    }


}