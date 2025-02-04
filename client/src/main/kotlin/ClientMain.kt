package client

import androidx.compose.runtime.mutableStateOf
import common.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

//fun main(args: Array<String>) {
//    val host = args[0]
//    val port = args[1].toInt()
//    val place = Place.getPlace(args[2].toInt() - 1)
//
//    runBlocking {
//        client(host, port, place)
//    }
//
//}
fun main() =

    application {
//        val gameState = ClientGameState()
        Window(
            onCloseRequest = ::exitApplication,
            title = "GUI1000",
        ) {
            App()
        }
    }


private fun cardsToString(cards: Set<Card>): String {
    return cards.joinToString(", ") { it.toString() }
}

private fun cardsToString(cards: List<Card>): String {
    return cards.joinToString(", ") { it.toString() }
}

private fun nullableCardsToString(cards: List<Card?>): String {
    return cards.joinToString(", ") { it?.toString() ?: "null" }
}

suspend fun client(host: String, port: Int, place: Place, gameState: ClientGameState) {
    val (input, output, toServer) = connectToServer(host, port, place)
    while (true) {
        var serverMsgJson = input.readUTF8Line() ?: throw Exception("No message received")
        when (val serverMsg = Json.decodeFromString<Message>(serverMsgJson)) {
            is Message.Rozdanie -> {
                gameState.myCards = serverMsg.cards.toMutableList()
                gameState._cardsChanged.value += 1
                gameState._gameStarted.value = true
                gameState._gamePhase.value = GamePhase.Licytacja
//                println("Your cards: ${cardsToString(gameState.myCards.toList())}")
            }
            else -> {
                throw Exception("Unexpected message from server, expected cards")
            }
        }

        licytacja(gameState, input, output, place)

        gameState._musik.value = seeMusik(gameState, place, input).toList()

        processMusik(gameState, input, output, place, gameState.musik.value)

        getFinalPoints(gameState, input, output, place)

        gameloop(gameState, input, output, place)

        println("Points: ${gameState.points.joinToString(", ")}, you are $place player")

        if (!gameState.continueGame()) {
            break
        }
        else {
            gameState._gamePhase.value = GamePhase.End
        }
    }
}



internal suspend fun play1round(gameState: ClientGameState, myPlace: Place, input: ByteReadChannel, output: ByteWriteChannel): Pair<Place, Int> {
    gameState._playerPlaying.value = gameState.lewStarter.value
    gameState._cardsPlayed.value = Array(3) { null }// dummy cards
    gameState._newLicytacjaWinnerPoints.value = gameState.licytacjaWinnerPoints.value
    gameState._firstSuit.value = null
    repeat(3) {
        println()
        when(gameState.playerPlaying.value) {
            myPlace -> {// My turn
//                println("Your turn to play, choose a card, your cards:\n${cardsToString(gameState.myCards)}")
//                if (firstSuit != null) {
//                    println("First suit: $firstSuit")
//                }
//                if (gameState.atut.value != null) {
//                    println("Atut: ${gameState.atut}")
//                }
//                var myCardInd: Int
//                while (true) {// Verify card
//                    try {
//                        myCardInd = readln().toInt() - 1
//                    }
//                    catch (e: NumberFormatException) {
//                        println("Invalid number")
//                        continue
//                    }
//                    if (myCardInd < 0 || myCardInd >= gameState.myCards.size) {
//                        println("Invalid card index")
//                    }
//                    else if (firstSuit != null) {
//                        if (gameState.myCards[myCardInd].suit != firstSuit && gameState.checkSuit(firstSuit!!)) {
//                            println("You must play card of the same suit")
//                        }
//                        else {
//                            if (gameState.myCards[myCardInd].suit == firstSuit) {
//                                if (gameState.checkHaveGreaterCardSameSuit(cardsPlayed, firstSuit!!, gameState.atut.value)
//                                    && !checkGreatestCard(cardsPlayed, firstSuit!!, null, gameState.myCards[myCardInd])) {
//                                    println("You have greater card of the same suit")
//                                }
//                                else {
//                                    break
//                                }
//                            }
//                            else {
//                                if (gameState.checkHaveGreaterCard(cardsPlayed, firstSuit!!, gameState.atut.value) &&
//                                    !checkGreatestCard(cardsPlayed, firstSuit!!, gameState.atut.value, gameState.myCards[myCardInd])) {
//                                    println("You have greater card")
//                                }
//                                else {
//                                    break
//                                }
//                            }
//                        }
//                    }
//                    else {
//                        firstSuit = gameState.myCards[myCardInd].suit
//                        break
//                    }
//                }
//
//                // Check meldunek
//                var meldunek = false
//                val playedCard = gameState.myCards[myCardInd]
//                if (lewStarter == myPlace) {
//                    val canMeldunek = (playedCard.rank == CardRank.King && gameState.myCards.contains(Card(CardRank.Queen, playedCard.suit))) ||
//                            (playedCard.rank == CardRank.Queen && gameState.myCards.contains(Card(CardRank.King, playedCard.suit)))
//
//                    if (canMeldunek) {
//                        println("Do you want meldunek? (y/n)")
//                        var newline = readln()
//                        while (newline != "y" && newline != "n") {
//                            println("Invalid input, write y or n")
//                            newline = readln()
//                        }
//                        meldunek = newline == "y"
//                        if (meldunek) {
//                            gameState._atut.value = gameState.myCards[myCardInd].suit
//                            if (myPlace != gameState.licytacjaWinner.value) {
//                                gameState._points[myPlace.ordinal].value += gameState.myCards[myCardInd].suit.value
//                            }
//                            else {
//                                newLicytacjaWinnerPoints += gameState.myCards[myCardInd].suit.value
//                            }
//                        }
//                    }
//                }

                // Send decision
                val myCardMsg = gameState.toServerMsgChannel.receive()
                if (myCardMsg is Message.PlayACard) {
                    output.writeStringUtf8(myCardMsg.toJson())
                }
                else {
                    throw Exception("Unexpected message to server, expected PlayCard")
                }
//                cardsPlayed[myPlace.ordinal] = gameState.myCards[myCardInd]
//                gameState.myCards.removeAt(myCardInd)
//                gameState._cardsChanged.value += 1
//                println("Cards played: ${nullableCardsToString(cardsPlayed.toList())}")
            }
            else -> {// Other players
                println("Player $gameState.playerPlaying.value turn to play")
                val serverMsgJson = input.readUTF8Line() ?: throw Exception("No message received")
                val serverMsg = Json.decodeFromString<Message>(serverMsgJson)
                if (serverMsg is Message.PlayACard) {
                    gameState.cardsPlayed.value[gameState.playerPlaying.value.ordinal] = serverMsg.card
                    if (gameState.firstSuit.value == null) {
                        gameState._firstSuit.value = serverMsg.card.suit
                        if (serverMsg.meldunek) {
                            gameState._atut.value = serverMsg.card.suit
                            if (gameState.playerPlaying.value != gameState.licytacjaWinner.value) {
                                gameState._points[gameState.playerPlaying.value.ordinal].value += serverMsg.card.suit.value
                            }
                            else {
                                gameState._newLicytacjaWinnerPoints.value += serverMsg.card.suit.value
                            }
                        }
                    }
//                    println("Cards played: ${nullableCardsToString(cardsPlayed.toList())}")
                }
                else {
                    throw Exception("Unexpected message from server, expected PlayCard")
                }
            }
        }
        gameState._playerPlaying.value = gameState.playerPlaying.value.next()
    }

    // Calculate points
    val cards = arrayOf(gameState.cardsPlayed.value[0]!!, gameState.cardsPlayed.value[1]!!, gameState.cardsPlayed.value[2]!!)
    val nextLewStarter = compareCards(cards, gameState.lewStarter.value, gameState.atut.value)
    if (nextLewStarter != gameState.licytacjaWinner.value) {
        gameState._points[nextLewStarter.ordinal].value += calculatePoints(cards)
    }
    else {
        gameState._newLicytacjaWinnerPoints.value = gameState.licytacjaWinnerPoints.value + calculatePoints(cards)
    }
    return nextLewStarter to gameState.newLicytacjaWinnerPoints.value
}

internal suspend fun gameloop(gameState: ClientGameState, input: ByteReadChannel, output: ByteWriteChannel, myPlace: Place) {
    gameState._lewStarter.value = gameState.licytacjaWinner.value
    gameState._licytacjaWinnerPoints.value = 0
    repeat(8) {
        val (nextLewStarter, newLicytacjaWinnerPoints) = play1round(gameState, myPlace, input, output)
        gameState._lewStarter.value = nextLewStarter
        gameState._licytacjaWinnerPoints.value = newLicytacjaWinnerPoints
        delay(3000)
    }

    if (gameState.licytacjaWinnerPoints.value < gameState.licytacjaPoints.value) {
        gameState._points[gameState.licytacjaWinner.value.ordinal].value -= gameState.licytacjaPoints.value
    }
    else {
        gameState._points[gameState.licytacjaWinner.value.ordinal].value += gameState.licytacjaPoints.value
    }
}

internal suspend fun getFinalPoints(gameState: ClientGameState, input: ByteReadChannel, output: ByteWriteChannel, place: Place) {
    if (place != gameState.licytacjaWinner.value) {
        val serverMsgJson = input.readUTF8Line() ?: throw Exception("No message received")
        when (val serverMsg = Json.decodeFromString<Message>(serverMsgJson)) {
            is Message.LicytacjaAktualna -> {
                gameState._licytacjaPoints.value = serverMsg.punkty
            }
            else -> {
                throw Exception("Unexpected message from server, expected final points")
            }
        }
    }
    else {
//        while (true) {
//            println("Do you want to change your licytacja, current licytacja points: ${gameState.licytacjaPoints}\nWrite your licytacja, 0 to not change")
//            try {
//                myLicytacja = readln().toInt()
//            }
//            catch (e: NumberFormatException) {
//                println("Invalid number")
//                continue
//            }
//            if (myLicytacja != 0 && myLicytacja <= gameState.licytacjaPoints.value) {
//                println("Your licytacja must be higher")
//            } else {
//                break
//            }
//        }
        val msg = gameState.toServerMsgChannel.receive()
        when (msg) {
            is Message.LicytacjaAktualna -> {
                gameState._licytacjaPoints.value = msg.punkty
                output.writeStringUtf8(msg.toJson())
            }
            else -> {
                throw Exception("Unexpected message to server, expected final points")
            }
        }
    }

    gameState._gamePhase.value = GamePhase.Game
    println("Final licytacja points: ${gameState.licytacjaPoints}")
}

internal suspend fun processMusik(gameState: ClientGameState, input: ByteReadChannel, output: ByteWriteChannel, place: Place, musik: List<Card>) {
    if (place == gameState.licytacjaWinner.value) {
        println("Choose a card from musik for yourself")
//        var myCardInd: Int
//        while (true) {
//            try {
//                myCardInd = readln().toInt() - 1
//            }
//            catch (e: NumberFormatException) {
//                println("Invalid number")
//                continue
//            }
//            if (myCardInd < 0 || myCardInd >= musik.size) {
//                println("Invalid card index")
//            } else {
//                break
//            }
//        }
//        gameState.myCards.add(musik[myCardInd])
//        gameState._cardsChanged.value += 1

//        for (i in 0..2) {
//            if (i != myCardInd) {
//                println("Choose whom to give card ${musik[i]} (1-3), you are $place")
//                var destination: Int
//                while (true) {
//                    try {
//                        destination = readln().toInt() - 1
//                    }
//                    catch (e: NumberFormatException) {
//                        println("Invalid number")
//                        continue
//                    }
//                    if (destination == place.ordinal) {
//                        println("You cannot give card to yourself")
//                    } else if (destination < 0 || destination > 2) {
//                        println("Invalid destination")
//                    } else {
//                        break
//                    }
//                }
//                output.writeStringUtf8(Message.GiveCard(musik[i], Place.getPlace(destination)).toJson())
//            }
//        }
        repeat(2) {
            val msg = gameState.toServerMsgChannel.receive()
            if (msg is Message.GiveCard) {
                output.writeStringUtf8(msg.toJson())
            }
            else {
                throw Exception("Unexpected message to server, expected give card")
            }
        }
    }
    else {
        val serverMsgJson = input.readUTF8Line() ?: throw Exception("No message received")
        when (val serverMsg = Json.decodeFromString<Message>(serverMsgJson)) {
            is Message.GiveCard -> {
                gameState.myCards.add(serverMsg.card)
                gameState._cardsChanged.value += 1
                gameState._gamePhase.value = GamePhase.GetFinalLicytacjaPoints
                println("Received card ${serverMsg.card} from musik")
            }
            else -> {
                throw Exception("Unexpected message from server, expected give card")
            }
        }
    }
}

suspend fun seeMusik(gameState: ClientGameState, place: Place, input: ByteReadChannel): Set<Card> {
    suspend fun receiveMusik(input: ByteReadChannel): Set<Card> {
        val serverMsgJson = input.readUTF8Line() ?: throw Exception("No message received")
        when (val serverMsg = Json.decodeFromString<Message>(serverMsgJson)) {
            is Message.Musik -> {
                return serverMsg.cards
            }
            else -> {
                throw Exception("Unexpected message from server, expected musik")
            }
        }
    }
    val musik: Set<Card> = if (gameState.licytacjaWinner.value == place) {
        receiveMusik(input)
    }
    else {
        if (gameState.licytacjaPoints.value > 100) {
            receiveMusik(input)
        } else {
            emptySet()
        }
    }

    gameState._gamePhase.value = GamePhase.Musik

    if (musik.isNotEmpty()) {
        println("Musik: ${cardsToString(musik)}")
    }
    else {
        println("No musik to shown, 100 points licytacja")
    }
    return musik
}

open class ClientGameState {
    val _points: Array<MutableStateFlow<Int>> = Array(3) { MutableStateFlow(0) }
    val points: Array<StateFlow<Int>> = Array(3) { _points[it].asStateFlow() }

    var myCards: MutableList<Card> = mutableListOf()
    val _cardsChanged = MutableStateFlow(0)
    val cardsChanged = _cardsChanged.asStateFlow()

    val _gameStarted = MutableStateFlow(false)
    val gameStarted = _gameStarted.asStateFlow()

    lateinit var myPlace: Place

    val _gamePhase = MutableStateFlow(GamePhase.Licytacja)
    val gamePhase = _gamePhase.asStateFlow()

    val _licytacjaPoints = MutableStateFlow(0)
    val licytacjaPoints = _licytacjaPoints.asStateFlow()

    val _licytacjaWinner = MutableStateFlow(Place.First)
    val licytacjaWinner = _licytacjaWinner.asStateFlow()

    var licytacjaStarter: Place = Place.First

    val _licytujacy = MutableStateFlow(Place.First)
    val licytujacy = _licytujacy.asStateFlow()

    val _atut = MutableStateFlow<CardSuit?>(null)
    val atut = _atut.asStateFlow()

    val _musik = MutableStateFlow(emptyList<Card>())
    val musik = _musik.asStateFlow()

    val _cardsPlayed: MutableStateFlow<Array<Card?>> = MutableStateFlow(Array(3) { null })
    val cardsPlayed = _cardsPlayed.asStateFlow()

    val _firstSuit = MutableStateFlow<CardSuit?>(null)
    val firstSuit = _firstSuit.asStateFlow()

    val _playerPlaying = MutableStateFlow(Place.First)
    val playerPlaying = _playerPlaying.asStateFlow()

    val _lewStarter = MutableStateFlow(Place.First)
    val lewStarter = _lewStarter.asStateFlow()

    val _newLicytacjaWinnerPoints = MutableStateFlow(0)
    val newLicytacjaWinnerPoints = _newLicytacjaWinnerPoints.asStateFlow()

    val _licytacjaWinnerPoints = MutableStateFlow(0)
    val licytacjaWinnerPoints = _licytacjaWinnerPoints.asStateFlow()

    val toServerMsgChannel: Channel<Message> = Channel(UNLIMITED)

    fun continueGame(): Boolean {
        return points.all { it.value < 1000 }
    }

    fun checkSuit(suit: CardSuit): Boolean {
        return myCards.any { it.suit == suit }
    }

    fun checkHaveGreaterCard(cards: Array<Card?>, starterSuit: CardSuit, atut: CardSuit?): Boolean {
        for (card in myCards) {
            if (checkGreatestCard(cards, starterSuit, atut, card)) {
                return true
            }
        }
        return false
    }

    fun checkHaveGreaterCardSameSuit(cards: Array<Card?>, starterSuit: CardSuit, atut: CardSuit?): Boolean {
        val cardsToCheck = myCards.filter { it.suit == starterSuit }
        for (card in cardsToCheck) {
            if (checkGreatestCard(cards, starterSuit, atut, card)) {
                return true
            }
        }
        return false
    }
}

internal fun checkGreatestCard(cards: Array<Card?>, starterSuit: CardSuit, atut: CardSuit?, card: Card): Boolean {
    return cards.all { it == null || compare2Cards(it, card, starterSuit, atut) < 0 }
}

internal suspend fun connectToServer(host: String, port: Int, place: Place): Triple<ByteReadChannel, ByteWriteChannel, Socket> {
    val toServer = aSocket(SelectorManager(Dispatchers.IO)).tcp().connect(host, port)
    val input = toServer.openReadChannel()
    val output = toServer.openWriteChannel(autoFlush = true)
    output.writeStringUtf8(Message.DeclarePlace(place).toJson())
    val serverMsgJson = input.readUTF8Line() ?: throw Exception("No response from server")
    assert(Json.decodeFromString<Message>(serverMsgJson) is Message.AcceptPlace)
    return Triple(input, output, toServer)
}

internal suspend fun licytacja(gameState: ClientGameState, input: ByteReadChannel, output: ByteWriteChannel, place: Place) {
    // Licytacja begins
    var serverMsgJson = input.readUTF8Line() ?: throw Exception("No message received")
    when (val serverMsg = Json.decodeFromString<Message>(serverMsgJson)) {
        is Message.LicytacjaAktualna -> {
            gameState._licytacjaPoints.value = serverMsg.punkty
            gameState._licytacjaWinner.value = serverMsg.place
            gameState.licytacjaStarter = serverMsg.place
            gameState._gamePhase.value = GamePhase.Licytacja
        }
        else -> {
            throw Exception("Unexpected message from server, expected licytacja")
        }
    }
    println("got init")

    gameState._licytujacy.value = gameState.licytacjaStarter.next()
    val allLicytujacy = mutableSetOf(Place.First, Place.Second, Place.Third)
    while (allLicytujacy.size > 1) {
        if (gameState.licytujacy.value == place) {
//            var myLicytacja: Int
//            while (true) {
//                println("Your turn to licytacja, current licytacja points: ${gameState.licytacjaPoints}, by Player ${gameState.licytacjaWinner}\nWrite your licytacja, 0 to pass")
//                try {
//                    myLicytacja = readln().toInt()
//                }
//                catch (e: NumberFormatException) {
//                    println("Invalid number")
//                    continue
//                }
//
//                if (myLicytacja != 0 && myLicytacja <= gameState.licytacjaPoints.value) {
//                    println("Your licytacja must be higher")
//                } else {
//                    break
//                }
//            }
            val msg = gameState.toServerMsgChannel.receive()
            when (msg) {
                is Message.LicytacjaAktualna -> {
                    gameState._licytacjaPoints.value = msg.punkty
                    gameState._licytacjaWinner.value = msg.place
                    output.writeStringUtf8(msg.toJson())
                    if (gameState.licytacjaPoints.value >= 360) {
                        break
                    }
                    println("gotlicytacjamsg")

                }
                is Message.LicytacjaPas -> {
                    allLicytujacy.remove(msg.place)
                    output.writeStringUtf8(msg.toJson())
                    println("gotpasmsg")

                }
                else -> {
                    throw Exception("Unexpected message to server, expected licytacja")
                }
            }

//            if (myLicytacja == 0) {
//                output.writeStringUtf8(Message.LicytacjaPas(place).toJson())
//                allLicytujacy.remove(place)
//            } else {
//                gameState._licytacjaWinner.value = place
//                gameState._licytacjaPoints.value = myLicytacja
//                output.writeStringUtf8(Message.LicytacjaAktualna(place, myLicytacja).toJson())
//                if (myLicytacja >= 360) {
//                    break
//                }
//            }
        }
        else {
            serverMsgJson = input.readUTF8Line() ?: throw Exception("No message received")
            when (val serverMsg = Json.decodeFromString<Message>(serverMsgJson)) {
                is Message.LicytacjaAktualna -> {
                    gameState._licytacjaPoints.value = serverMsg.punkty
                    gameState._licytacjaWinner.value = serverMsg.place
                    if (gameState.licytacjaPoints.value >= 360) {
                        break
                    }
                }
                is Message.LicytacjaPas -> {
                    allLicytujacy.remove(serverMsg.place)
                }
                else -> {
                    throw Exception("Unexpected message from server, expected licytacja")
                }
            }
        }
        gameState._licytujacy.value = gameState.licytujacy.value.next()
        while (!allLicytujacy.contains(gameState.licytujacy.value)) {
            gameState._licytujacy.value = gameState.licytujacy.value.next()
        }
    }

    println("Licytacja won by Player ${gameState.licytacjaWinner} with ${gameState.licytacjaPoints}")
}

enum class GamePhase {
    Licytacja, Musik, Game, Processing, GetFinalLicytacjaPoints, End
}

