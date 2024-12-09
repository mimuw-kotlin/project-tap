package server

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import common.*

fun main(args: Array<String>) {
    val host = args[0]
    val port = args[1].toInt()
    val allPlayers = AllPlayers()
    val gameMasterChannel = Channel<Unit>(1)
    runBlocking {
        launch {
            serverListen(host, port, allPlayers, gameMasterChannel)
        }
        launch {
            gameMaster(allPlayers, gameMasterChannel)
        }
    }
}