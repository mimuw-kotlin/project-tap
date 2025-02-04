package client

import common.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import server.AllPlayers
import server.ServerGameState
import server.serverListen
import kotlin.test.Test
import java.io.ByteArrayInputStream

const val host = "localhost"
const val port = 8080

class ClientTest {
    private fun licytacjaTestCase(stdin: String, expected: Pair<Int, Place>) = runBlocking {
        val inputStream = ByteArrayInputStream(stdin.toByteArray())
        System.setIn(inputStream)

        suspend fun player(place: Place): Pair<Int, Place> {
            val gameState = ClientGameState()
            val (input, output, toServer) = connectToServer(host, port, place)
            licytacja(gameState, input, output, place)
            return gameState.licytacjaPoints to gameState.licytacjaWinner
        }

        val allPlayers = AllPlayers()
        val listener = launch {
            serverListen(host, port, allPlayers, Channel(1))
        }
        delay(100)

        val res = Array(3) {Pair(0, Place.First)}

        val player1 = launch {
            res[0] = player(Place.First)
        }
        val player2 = launch {
            res[1] = player(Place.Second)
        }
        val player3 = launch {
            res[2] = player(Place.Third)
        }
        delay(300)

        launch {
            server.licytacja(ServerGameState(allPlayers))
        }
        listener.cancelAndJoin()
        player1.join()
        player2.join()
        player3.join()

        assert(res.all {it == expected})
    }

    @Test
    fun licytacjaTest() {
        licytacjaTestCase("110\n120\n0\n0\n", Pair(120, Place.Third))
        licytacjaTestCase("360\n", Pair(360, Place.Second))
    }

    fun seeMusikTestCase(licytacjaPoint: Int) = runBlocking {
        val allPlayers = AllPlayers()
        val listener = launch {
            serverListen(host, port, allPlayers, Channel(1))
        }
        delay(100)

        suspend fun player(place: Place): Set<Card> {
            val gameState = ClientGameState()
            gameState.licytacjaWinner = Place.First
            gameState.licytacjaPoints = licytacjaPoint
            val (input, _, _) = connectToServer(host, port, place)
            return seeMusik(gameState, place, input)
        }

        val rozdanie = server.rozdanie()
        val res = Array(3) {emptySet<Card>()}
        val player1 = launch {
            res[0] = player(Place.First)
        }
        val player2 = launch {
            res[1] = player(Place.Second)
        }
        val player3 = launch {
            res[2] = player(Place.Third)
        }
        delay(300)

        val gameState = ServerGameState(allPlayers)
        gameState.licytacjaWinner = Place.First
        gameState.licytacjaPoints = licytacjaPoint
        server.showMusik(gameState, rozdanie)

        listener.cancelAndJoin()
        player1.join()
        player2.join()
        player3.join()

        if (licytacjaPoint > 100) {
            assert(res.all {it == rozdanie[3]})
        } else {
            assert(res[0] == rozdanie[3])
            assert(res[1].isEmpty())
            assert(res[2].isEmpty())
        }
    }

    @Test
    fun seeMusikTest() {
        seeMusikTestCase(110)
        seeMusikTestCase(100)
    }

    @Test
    fun processMusikTest() = runBlocking {
        val stdin = "1\n2\n3\n0\n"
        val inputStream = ByteArrayInputStream(stdin.toByteArray())
        System.setIn(inputStream)

        val allPlayers = AllPlayers()
        val listener = launch {
            serverListen(host, port, allPlayers, Channel(1))
        }
        delay(100)

        val rozdanie = server.rozdanie()
        suspend fun player(place: Place): List<Card> {
            val gameState = ClientGameState()
            gameState.licytacjaWinner = Place.First
            val (input, output, _) = connectToServer(host, port, place)
            processMusik(gameState, input, output, place, rozdanie[3].toList())
            return gameState.myCards
        }

        val res = Array(3) {emptyList<Card>()}
        val player1 = launch {
            res[0] = player(Place.First)
        }
        val player2 = launch {
            res[1] = player(Place.Second)
        }
        val player3 = launch {
            res[2] = player(Place.Third)
        }
        delay(300)

        val gameState = ServerGameState(allPlayers)
        gameState.licytacjaWinner = Place.First
        gameState.licytacjaPoints = 110
        server.give2CardsFromMusik(gameState)

        listener.cancelAndJoin()
        player1.join()
        player2.join()
        player3.join()

        for (i in 0..2) {
            assert(res[i].contains(rozdanie[3].toList()[i]))
        }
    }

    fun getFinalPointsTestCase(change: Boolean) = runBlocking {
        val stdin = if (change) "200\n" else "0\n"
        val inputStream = ByteArrayInputStream(stdin.toByteArray())
        System.setIn(inputStream)

        val allPlayers = AllPlayers()
        val listener = launch {
            serverListen(host, port, allPlayers, Channel(1))
        }
        delay(100)

        suspend fun player(place: Place): Int {
            val gameState = ClientGameState()
            gameState.licytacjaWinner = Place.First
            gameState.licytacjaPoints = 110
            val (input, output, _) = connectToServer(host, port, place)
            getFinalPoints(gameState, input, output, place)
            return gameState.licytacjaPoints
        }

        val res = Array(3) {0}
        val player1 = launch {
            res[0] = player(Place.First)
        }
        val player2 = launch {
            res[1] = player(Place.Second)
        }
        val player3 = launch {
            res[2] = player(Place.Third)
        }
        delay(300)

        val gameState = ServerGameState(allPlayers)
        gameState.licytacjaWinner = Place.First
        gameState.licytacjaPoints = 110
        server.getFinalPoints(gameState)

        listener.cancelAndJoin()
        player1.join()
        player2.join()
        player3.join()

        assert(res.all {it == if (change) 200 else 110})
    }

    @Test
    fun getFinalPointsTest() {
        getFinalPointsTestCase(false)
        getFinalPointsTestCase(true)
    }

    fun test1RoundCase(stdin: String, rozdanie: List<MutableList<Card>>, lewStarter: Place, atut: CardSuit?, expected: List<Int>) = runBlocking {
        val inputStream = ByteArrayInputStream(stdin.toByteArray())
        System.setIn(inputStream)

        val allPlayers = AllPlayers()
        val listener = launch {
            serverListen(host, port, allPlayers, Channel(1))
        }
        delay(100)

        suspend fun player(place: Place): List<Int> {
            val gameState = ClientGameState()
            gameState.licytacjaWinner = Place.First
            gameState.atut = atut
            gameState.myCards = rozdanie[place.ordinal]
            val cardsCount = gameState.myCards.size
            val (input, output, _) = connectToServer(host, port, place)
            val (_, newLicWinnerPoint) = play1round(lewStarter, gameState, 0, place, input, output)
            assert(gameState.myCards.size < cardsCount)
            val res = (gameState.points.map { it.value }).toMutableList()
            res.add(newLicWinnerPoint)
            return res
        }

        val res = Array(3) {emptyList<Int>()}
        val player1 = launch {
            res[0] = player(Place.First)
        }
        val player2 = launch {
            res[1] = player(Place.Second)
        }
        val player3 = launch {
            res[2] = player(Place.Third)
        }
        delay(300)

        val gameState = ServerGameState(allPlayers)
        gameState.licytacjaWinner = Place.First
        gameState.atut = atut
        server.play1Round(lewStarter, gameState, 0)

        listener.cancelAndJoin()
        player1.join()
        player2.join()
        player3.join()

        assert(res.all {it == expected})
    }


    @Test
    fun play1RoundTest() {
        // Run test by giving stdin for all players, their hands, lewStarter, atut, expected result {0, second, third, first}
        test1RoundCase(// Force to play greater card of the same suit
            "1\n2\n1\n1\n",
            listOf(
                mutableListOf(Card(CardRank.Nine, CardSuit.Diamonds), Card(CardRank.Ten, CardSuit.Diamonds),),
                mutableListOf(Card(CardRank.King, CardSuit.Clubs), Card(CardRank.Ten, CardSuit.Clubs),),
                mutableListOf(Card(CardRank.Ace, CardSuit.Clubs), Card(CardRank.Nine, CardSuit.Clubs),)
            ),
            Place.Second,
            null,
            listOf(0,0,15,0)
        )
        test1RoundCase(// Force to play greater card of the same suit, not atut
            "1\n2\n1\n1\n",
            listOf(
                mutableListOf(Card(CardRank.Nine, CardSuit.Diamonds), Card(CardRank.Ten, CardSuit.Diamonds),),
                mutableListOf(Card(CardRank.King, CardSuit.Clubs), Card(CardRank.Ten, CardSuit.Clubs),),
                mutableListOf(Card(CardRank.Ace, CardSuit.Clubs), Card(CardRank.Ace, CardSuit.Diamonds),)
            ),
            Place.Second,
            CardSuit.Diamonds,
            listOf(0,0,0,15)
        )
        test1RoundCase(// play with suit, not greater card by 3rd player
            "1\n1\n1\n",
            listOf(
                mutableListOf(Card(CardRank.King, CardSuit.Diamonds), Card(CardRank.Ten, CardSuit.Diamonds),),
                mutableListOf(Card(CardRank.King, CardSuit.Clubs), Card(CardRank.Ten, CardSuit.Clubs),),
                mutableListOf(Card(CardRank.Nine, CardSuit.Diamonds), Card(CardRank.Ace, CardSuit.Diamonds),)
            ),
            Place.First,
            CardSuit.Clubs,
            listOf(0,8,0,0)
        )
        test1RoundCase(// meldunek
            "1\ny\n1\n1\n2\n",
            listOf(
                mutableListOf(Card(CardRank.King, CardSuit.Diamonds), Card(CardRank.Queen, CardSuit.Diamonds),),
                mutableListOf(Card(CardRank.King, CardSuit.Clubs), Card(CardRank.Ten, CardSuit.Clubs),),
                mutableListOf(Card(CardRank.Nine, CardSuit.Diamonds), Card(CardRank.Ace, CardSuit.Diamonds),)
            ),
            Place.First,
            CardSuit.Clubs,
            listOf(0,0,19,80)
        )
    }
}

