package server

import io.ktor.network.sockets.*
import common.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json

class Player(private val socket: Socket,
             private val readChannel: ByteReadChannel,
             private val writeChannel: ByteWriteChannel) {
    var point: Int = 0

    suspend fun sendMsg(msg: Message) {
        writeChannel.writeStringUtf8(msg.toJson())
    }

    suspend fun receiveMsg(): Message {
        val msgJson = readChannel.readUTF8Line() ?: throw Exception("No message received")
        return Json.decodeFromString<Message>(msgJson)
    }

}

class AllPlayers {
    private val players: Array<Player?> = Array(3) { null }
    private var gameStarted = false

    @Synchronized
    fun addPlayer(socket: Socket, readChannel: ByteReadChannel, writeChannel: ByteWriteChannel, place: Place): Boolean {
        if (players[place.ordinal] == null) {
            players[place.ordinal] = Player(socket, readChannel, writeChannel)
            return true
        }
        return false
    }

    @Synchronized
    fun canStartGame(): Boolean {
        val playerCount =  players.count { it != null }
        if (playerCount == 3 && !gameStarted) {
            gameStarted = true
            return true
        }
        return false
    }

    // Not synchronized because it is called for gameMaster only
    fun getPlayer(place: Place): Player {
        return players[place.ordinal] ?: throw IllegalArgumentException("No player at $place")
    }

    fun continueGame(): Boolean {
        return players.all { it!!.point < 1000 }
    }

    fun addPoints(place: Place, points: Int) {
        players[place.ordinal]!!.point += points
    }

    fun allPoints(): List<Int> {
        return players.map { it!!.point }
    }
}