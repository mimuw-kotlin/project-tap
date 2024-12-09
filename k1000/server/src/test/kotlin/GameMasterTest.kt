package server

import common.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Nested
import kotlin.test.Test

private suspend fun connectToServer(host: String, port: Int, place: Place, sleepTime: Long): Triple<ByteReadChannel, ByteWriteChannel, Socket> {
    val toServer = aSocket(SelectorManager(Dispatchers.IO)).tcp().connect(host, port)
    delay(sleepTime)
    val input = toServer.openReadChannel()
    val output = toServer.openWriteChannel(autoFlush = true)
    output.writeStringUtf8(Message.DeclarePlace(place).toJson())
    val serverMsgJson = input.readUTF8Line() ?: throw Exception("No response from server")
    assert(Json.decodeFromString<Message>(serverMsgJson) is Message.AcceptPlace)
    return Triple(input, output, toServer)
}

class GameMasterTest {
    @Test
    fun rozdanieRoznychKart() {
        val rozdanie1 = rozdanie()
        rozdanie1.forEachIndexed { index, set ->
            assert(if (index == 3) set.size == 3 else set.size == 7) { "Set $index has ${set.size} cards" }
        }

        assert(rozdanie1[0] != rozdanie1[1] && rozdanie1[0] != rozdanie1[2] && rozdanie1[1] != rozdanie1[2]) { "Sets are not different" }

        val rozdanie2 = rozdanie()
        assert(!rozdanie1.contentEquals(rozdanie2)) { "Unlikely for 2 different rozdanie to be the same" }
    }

    @Nested
    inner class LicytacjaTest {
        @Test
        fun licytacjaTest() {
            runBlocking {
                val allPlayers = AllPlayers()
                val gameState = ServerGameState(allPlayers)
                val listener = launch {
                    serverListen(host, port, allPlayers, Channel(1))
                }
                delay(100)  // Wait for server to start

                val startLic = 100
                val player2_1Lic = 110
                val player3_1Lic = 200
                val player1_2Lic = 210
                val player3_2Lic = 220

                val player1 = launch {
                    val (input, output, socket) = connectToServer(host, port, Place.First, 0)
                    var msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                    assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(Place.First, startLic))

                    msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                    assert(
                        Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(
                            Place.Second,
                            player2_1Lic
                        )
                    )

                    msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                    assert(
                        Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(
                            Place.Third,
                            player3_1Lic
                        )
                    )

                    output.writeStringUtf8(Message.LicytacjaAktualna(Place.First, player1_2Lic).toJson())

                    msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                    assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaPas(Place.Second))

                    msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                    assert(
                        Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(
                            Place.Third,
                            player3_2Lic
                        )
                    )

                    output.writeStringUtf8(Message.LicytacjaPas(Place.First).toJson())
                    socket.close()
                }

                val player2 = launch {
                    val (input, output, socket) = connectToServer(host, port, Place.Second, 0)
                    var msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                    assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(Place.First, startLic))

                    output.writeStringUtf8(Message.LicytacjaAktualna(Place.Second, player2_1Lic).toJson())

                    msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                    assert(
                        Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(
                            Place.Third,
                            player3_1Lic
                        )
                    )

                    msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                    assert(
                        Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(
                            Place.First,
                            player1_2Lic
                        )
                    )

                    output.writeStringUtf8(Message.LicytacjaPas(Place.Second).toJson())

                    msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                    assert(
                        Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(
                            Place.Third,
                            player3_2Lic
                        )
                    )

                    socket.close()
                }

                val player3 = launch {
                    val (input, output, socket) = connectToServer(host, port, Place.Third, 0)

                    var msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                    assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(Place.First, startLic))

                    msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                    assert(
                        Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(
                            Place.Second,
                            player2_1Lic
                        )
                    )

                    output.writeStringUtf8(Message.LicytacjaAktualna(Place.Third, player3_1Lic).toJson())

                    msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                    assert(
                        Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(
                            Place.First,
                            player1_2Lic
                        )
                    )

                    msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                    assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaPas(Place.Second))

                    output.writeStringUtf8(Message.LicytacjaAktualna(Place.Third, player3_2Lic).toJson())

                    msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                    assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaPas(Place.First))

                    socket.close()
                }

                delay(300)// Wait for players to connect

                val licytacja = launch {
                    licytacja(gameState)
                }

                player1.join()
                player2.join()
                player3.join()
                licytacja.join()
                listener.cancelAndJoin()
                assert(gameState.licytacjaWinner == Place.Third) { "Player 3 should win the licytacja" }
                assert(gameState.licytacjaPoints == player3_2Lic) { "Player 3 should win with $player3_2Lic points" }
                assert(gameState.licytacjaStarter == Place.Second) { "Player 2 should start the next licytacja" }
            }
        }

        @Test
        fun licytacja360Test() = runBlocking {
            val allPlayers = AllPlayers()
            val gameState = ServerGameState(allPlayers)
            val listener = launch {
                serverListen(host, port, allPlayers, Channel(1))
            }
            delay(100)  // Wait for server to start

            val startLic = 100

            val player1 = launch {
                val (input, output, socket) = connectToServer(host, port, Place.First, 0)
                var msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(Place.First, startLic))

                msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(Place.Second, 360))

                socket.close()
            }

            val player2 = launch {
                val (input, output, socket) = connectToServer(host, port, Place.Second, 0)
                val msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(Place.First, startLic))

                output.writeStringUtf8(Message.LicytacjaAktualna(Place.Second, 360).toJson())

                socket.close()
            }

            val player3 = launch {
                val (input, output, socket) = connectToServer(host, port, Place.Third, 0)
                var msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(Place.First, startLic))

                msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(Place.Second, 360))

                socket.close()
            }

            delay(300)// Wait for players to connect

            val licytacja = launch {
                licytacja(gameState)
            }

            player1.join()
            player2.join()
            player3.join()
            licytacja.join()
            listener.cancelAndJoin()
            assert(gameState.licytacjaWinner == Place.Second) { "Player 2 should win the licytacja" }
            assert(gameState.licytacjaPoints == 360) { "Player 2 should win with 360 points" }
            assert(gameState.licytacjaStarter == Place.Second) { "Player 2 should start the next licytacja" }
        }

        @Test
        fun licytacja100Test() = runBlocking {
            val allPlayers = AllPlayers()
            val gameState = ServerGameState(allPlayers)
            gameState.licytacjaStarter = Place.First
            gameState.licytacjaWinner = Place.Second
            val listener = launch {
                serverListen(host, port, allPlayers, Channel(1))
            }
            delay(100)  // Wait for server to start

            val startLic = 100

            val player1 = launch {
                val (input, output, socket) = connectToServer(host, port, Place.First, 0)
                var msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(Place.First, startLic))

                msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaPas(Place.Second))

                msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaPas(Place.Third))

                socket.close()
            }

            val player2 = launch {
                val (input, output, socket) = connectToServer(host, port, Place.Second, 0)
                var msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(Place.First, startLic))

                output.writeStringUtf8(Message.LicytacjaPas(Place.Second).toJson())

                msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaPas(Place.Third))

                socket.close()
            }

            val player3 = launch {
                val (input, output, socket) = connectToServer(host, port, Place.Third, 0)
                var msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaAktualna(Place.First, startLic))

                msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                assert(Json.decodeFromString<Message>(msgStr) == Message.LicytacjaPas(Place.Second))

                output.writeStringUtf8(Message.LicytacjaPas(Place.Third).toJson())

                socket.close()
            }

            delay(300)// Wait for players to connect

            val licytacja = launch {
                licytacja(gameState)
            }

            player1.join()
            player2.join()
            player3.join()
            licytacja.join()
            listener.cancelAndJoin()
            assert(gameState.licytacjaWinner == Place.First) { "Player 1 should win the licytacja" }
            assert(gameState.licytacjaPoints == 100) { "Player 2 should win with 360 points" }
            assert(gameState.licytacjaStarter == Place.Second) { "Player 2 should start the next licytacja" }
        }
    }

    @Test
    fun sendRozdaniaToPlayersTest() = runBlocking {
        val allPlayers = AllPlayers()
        val gameState = ServerGameState(allPlayers)
        val listener = launch {
            serverListen(host, port, allPlayers, Channel(1))
        }
        delay(100)  // Wait for server to start

        val rozdanie = rozdanie()

        suspend fun player(place:Place) {
            val (input, output, socket) = connectToServer(host, port, place, 0)
            var msgStr = input.readUTF8Line() ?: throw Exception("No message received")
            when (val msg = Json.decodeFromString<Message>(msgStr)) {
                is Message.Rozdanie -> {
                    assert(msg.cards == rozdanie[place.ordinal])
                }
                else -> throw Exception("Invalid message")
            }

            socket.close()
        }

        val player1 = launch {
            player(Place.First)
        }

        val player2 = launch {
            player(Place.Second)
        }

        val player3 = launch {
            player(Place.Third)
        }

        delay(300)// Wait for players to connect

        val rozdaj = launch {
            sendRozdaniaToPlayers(gameState, rozdanie)
        }

        rozdaj.join()
        player1.join()
        player2.join()
        player3.join()
        listener.cancelAndJoin()
    }

    private fun showMusikTest(licytacjaPoint100: Boolean) = runBlocking {
        val allPlayers = AllPlayers()
        val gameState = ServerGameState(allPlayers)
        val listener = launch {
            serverListen(host, port, allPlayers, Channel(1))
        }
        delay(100)  // Wait for server to start

        val rozdanie = rozdanie()
        when {
            licytacjaPoint100 -> gameState.licytacjaPoints = 100
            else -> gameState.licytacjaPoints = 200
        }
        gameState.licytacjaWinner = Place.Second

        suspend fun player(place:Place) {
            val (input, output, socket) = connectToServer(host, port, place, 0)

            if (place == gameState.licytacjaWinner) {
                var msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                assert(Json.decodeFromString<Message>(msgStr) == Message.Musik(rozdanie[3]))
            }
            else if (!licytacjaPoint100) {
                var msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                assert(Json.decodeFromString<Message>(msgStr) == Message.Musik(rozdanie[3]))
            }

            socket.close()
        }

        val player1 = launch {
            player(Place.First)
        }

        val player2 = launch {
            player(Place.Second)
        }

        val player3 = launch {
            player(Place.Third)
        }

        delay(300)// Wait for players to connect

        val showMusik = launch {
            showMusik(gameState, rozdanie)
        }

        showMusik.join()
        player1.join()
        player2.join()
        player3.join()
        listener.cancelAndJoin()
    }

    @Test
    fun showMusikAllTest() {
        showMusikTest(true)
        showMusikTest(false)
    }


    @Test
    fun give2CardsFromMusikTest() = runBlocking {
        val allPlayers = AllPlayers()
        val gameState = ServerGameState(allPlayers)
        val listener = launch {
            serverListen(host, port, allPlayers, Channel(1))
        }
        delay(100)  // Wait for server to start

        val rozdanie = rozdanie()
        val musik = rozdanie[3].toList()
        gameState.licytacjaWinner = Place.Third

        suspend fun player(place:Place) {
            val (input, output, socket) = connectToServer(host, port, place, 0)

            if (place == Place.Third) {
                output.writeStringUtf8(Message.GiveCard(musik[0], Place.First).toJson())
                output.writeStringUtf8(Message.GiveCard(musik[1], Place.Second).toJson())
            }
            else {
                val msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                when (val msg = Json.decodeFromString<Message>(msgStr)) {
                    is Message.GiveCard -> {
                        assert(msg.card == musik[place.ordinal])
                        assert(msg.destination == place)
                    }
                    else -> throw Exception("Invalid message for licytacja losers")
                }
            }

            socket.close()
        }

        val player1 = launch {
            player(Place.First)
        }

        val player2 = launch {
            player(Place.Second)
        }

        val player3 = launch {
            player(Place.Third)
        }

        delay(300)// Wait for players to connect

        val giver = launch {
            give2CardsFromMusik(gameState)
        }

        giver.join()
        player1.join()
        player2.join()
        player3.join()
        listener.cancelAndJoin()
    }

    @Test
    fun play1RoundTest() = runBlocking {
        val allPlayers = AllPlayers()
        val gameState = ServerGameState(allPlayers)
        val listener = launch {
            serverListen(host, port, allPlayers, Channel(1))
        }
        delay(100)  // Wait for server to start
        gameState.licytacjaStarter = Place.First
        gameState.licytacjaWinner = Place.Third
        gameState.licytacjaPoints = 100

        val round1 = arrayOf(
        Message.PlayACard(Card(CardRank.Ace, CardSuit.Spades), Place.First, false),
        Message.PlayACard(Card(CardRank.King, CardSuit.Clubs), Place.Second, false),
        Message.PlayACard(Card(CardRank.Queen, CardSuit.Clubs), Place.Third, false)
        ) to Place.Second
        val round2 = arrayOf(
            Message.PlayACard(Card(CardRank.Ten, CardSuit.Hearts), Place.First, false),
            Message.PlayACard(Card(CardRank.King, CardSuit.Hearts), Place.Second, true),
            Message.PlayACard(Card(CardRank.Ace, CardSuit.Hearts), Place.Third, false)
        ) to Place.Third
        val round3 = arrayOf(
            Message.PlayACard(Card(CardRank.Ace, CardSuit.Diamonds), Place.First, true),
            Message.PlayACard(Card(CardRank.Queen, CardSuit.Hearts), Place.Second, true),
            Message.PlayACard(Card(CardRank.King, CardSuit.Diamonds), Place.Third, true)
        ) to Place.First
        val round4 = arrayOf(
            Message.PlayACard(Card(CardRank.Ace, CardSuit.Clubs), Place.First, false),
            Message.PlayACard(Card(CardRank.Ten, CardSuit.Clubs), Place.Second, true),
            Message.PlayACard(Card(CardRank.Queen, CardSuit.Diamonds), Place.Third, true)
        ) to Place.Third

        val allRound = arrayOf(round1, round2, round3, round4)

        suspend fun player(place:Place) {
            val (input, output, socket) = connectToServer(host, port, place, 0)

            for (round in allRound) {
                val (cards, winner) = round
                output.writeStringUtf8(cards[place.ordinal].toJson())
                repeat(2) {
                    val msgStr = input.readUTF8Line() ?: throw Exception("No message received")
                    when (val msg = Json.decodeFromString<Message>(msgStr)) {
                        is Message.PlayACard -> {}
                        else -> throw Exception("Invalid message for licytacja losers")
                    }
                }
            }
            socket.close()
        }

        val player1 = launch {
            player(Place.First)
        }

        val player2 = launch {
            player(Place.Second)
        }

        val player3 = launch {
            player(Place.Third)
        }

        delay(300)// Wait for players to connect

        var lewStarter = gameState.licytacjaWinner
        var licytacjaWinnerPoints = 11
        var roundConter = 0
        for (round in allRound) {
            val (nextLewStarter, newLicytacjaWinnerPoints) = play1Round(lewStarter, gameState, licytacjaWinnerPoints)
            lewStarter = nextLewStarter
            licytacjaWinnerPoints = newLicytacjaWinnerPoints
            assert(lewStarter == round.second) { "Lew starter should be ${round.second}, but got $lewStarter, $roundConter" }
            roundConter++
        }

        player1.join()
        player2.join()
        player3.join()
        listener.cancelAndJoin()
        assert(licytacjaWinnerPoints == 140) { "Licytacja winner should have 49 points, but got $licytacjaWinnerPoints" }
        assert(gameState.allPoints() == listOf(18, 118, 0)) { "${gameState.allPoints()}" }
    }


}