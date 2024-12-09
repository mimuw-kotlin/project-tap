package server

import common.Message
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json

const val TIMEOUTMS = 5000L

suspend fun serveNewClient(clientSocket: Socket, allPlayers: AllPlayers, gameMasterChannel: Channel<Unit>) {
    val input = clientSocket.openReadChannel()
    val output = clientSocket.openWriteChannel(autoFlush = true)
    try {
        withTimeout(TIMEOUTMS) {
            val clientMsgJson: String = input.readUTF8Line() ?: return@withTimeout
            when (val clientMsg: Message = Json.decodeFromString<Message>(clientMsgJson)) {
                is Message.DeclarePlace -> {
                    val place = clientMsg.place
                    if (!allPlayers.addPlayer(clientSocket, input, output, place)) {
                        // Reject, a player is already at this place
                        output.writeStringUtf8(Message.RejectPlace(place).toJson())
                    } else {
                        output.writeStringUtf8(Message.AcceptPlace(place).toJson())
                        if (allPlayers.canStartGame()) {
                            // Start game
                            gameMasterChannel.send(Unit)
                        }
                    }
                }

                else -> {
                    withContext(Dispatchers.IO) {
                        clientSocket.close()
                    }
                }
            }
        }
    }
    catch (e: Exception) {
        withContext(Dispatchers.IO) {
            clientSocket.close()
        }
    }
}

suspend fun serverListen(host: String, port: Int, allPlayers: AllPlayers, gameMasterChannel: Channel<Unit>) = coroutineScope {
    val serverListenSocket = aSocket(SelectorManager(Dispatchers.IO)).tcp().bind(host, port)
    try {
        while (true) {
            val newClient = serverListenSocket.accept()
            launch {
                serveNewClient(newClient, allPlayers, gameMasterChannel)
            }
        }
    } finally {
        serverListenSocket.close()
    }
}
