package server

import common.Message
import common.Place
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

const val host = "localhost"
const val port = 8080

suspend fun declarePlaceAndGetMsg(host: String, port: Int, place: Place, sleepTime: Long): Message {
    val toServer = aSocket(SelectorManager(Dispatchers.IO)).tcp().connect(host, port)
    delay(sleepTime)
    val input = toServer.openReadChannel()
    val output = toServer.openWriteChannel(autoFlush = true)
    output.writeStringUtf8(Message.DeclarePlace(place).toJson())
    val serverMsgJson = input.readUTF8Line() ?: throw Exception("No response from server")
    return Json.decodeFromString<Message>(serverMsgJson)
}