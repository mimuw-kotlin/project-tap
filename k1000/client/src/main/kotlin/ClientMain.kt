package client

import common.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    val host = args[0]
    val port = args[1].toInt()
    val place = Place.getPlace(args[2].toInt() - 1)

    runBlocking {
        client(host, port, place)
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

suspend fun client(host: String, port: Int, place: Place) {
    val (input, output, toServer) = connectToServer(host, port, place)
    val gameState = ClientGameState()
    while (true) {
        var serverMsgJson = input.readUTF8Line() ?: throw Exception("No message received")
        when (val serverMsg = Json.decodeFromString<Message>(serverMsgJson)) {
            is Message.Rozdanie -> {
                gameState.myCards = serverMsg.cards.toMutableList()
                println("Your cards: ${cardsToString(gameState.myCards.toList())}")
            }
            else -> {
                throw Exception("Unexpected message from server, expected cards")
            }
        }

        licytacja(gameState, input, output, place)

        val musik = seeMusik(gameState, place, input).toList()

        processMusik(gameState, input, output, place, musik)

        getFinalPoints(gameState, input, output, place)

        gameloop(gameState, input, output, place)

        println("Points: ${gameState.points.joinToString(", ")}, you are $place player")

        if (!gameState.continueGame()) {
            break
        }
    }
}



internal suspend fun play1round(lewStarter: Place, gameState: ClientGameState, licytacjaWinnerPoints: Int, myPlace: Place, input: ByteReadChannel, output: ByteWriteChannel): Pair<Place, Int> {
    // TODO("VALIDATE CARD")
    var playing = lewStarter
    val cardsPlayed: Array<Card?> = Array(3) { null }// dummy cards
    var newLicytacjaWinnerPoints = licytacjaWinnerPoints
    var firstSuit: CardSuit? = null
    repeat(3) {
        println()
        when(playing) {
            myPlace -> {// My turn
                println("Your turn to play, choose a card, your cards:\n${cardsToString(gameState.myCards)}")
                if (firstSuit != null) {
                    println("First suit: $firstSuit")
                }
                if (gameState.atut != null) {
                    println("Atut: ${gameState.atut}")
                }
                var myCardInd: Int
                while (true) {// Verify card
                    try {
                        myCardInd = readln().toInt() - 1
                    }
                    catch (e: NumberFormatException) {
                        println("Invalid number")
                        continue
                    }
                    if (myCardInd < 0 || myCardInd >= gameState.myCards.size) {
                        println("Invalid card index")
                    }
                    else if (firstSuit != null) {
                        if (gameState.myCards[myCardInd].suit != firstSuit && gameState.checkSuit(firstSuit!!)) {
                            println("You must play card of the same suit")
                        }
                        else {
                            if (gameState.myCards[myCardInd].suit == firstSuit) {
                                if (gameState.checkHaveGreaterCardSameSuit(cardsPlayed, firstSuit!!, gameState.atut)
                                    && !checkGreatestCard(cardsPlayed, firstSuit!!, null, gameState.myCards[myCardInd])) {
                                    println("You have greater card of the same suit")
                                }
                                else {
                                    break
                                }
                            }
                            else {
                                if (gameState.checkHaveGreaterCard(cardsPlayed, firstSuit!!, gameState.atut) &&
                                    !checkGreatestCard(cardsPlayed, firstSuit!!, gameState.atut, gameState.myCards[myCardInd])) {
                                    println("You have greater card")
                                }
                                else {
                                    break
                                }
                            }
                        }
                    }
                    else {
                        firstSuit = gameState.myCards[myCardInd].suit
                        break
                    }
                }

                // Check meldunek
                var meldunek = false
                val playedCard = gameState.myCards[myCardInd]
                if (lewStarter == myPlace) {
                    val canMeldunek = (playedCard.rank == CardRank.King && gameState.myCards.contains(Card(CardRank.Queen, playedCard.suit))) ||
                            (playedCard.rank == CardRank.Queen && gameState.myCards.contains(Card(CardRank.King, playedCard.suit)))
                    
                    if (canMeldunek) {
                        println("Do you want meldunek? (y/n)")
                        var newline = readln()
                        while (newline != "y" && newline != "n") {
                            println("Invalid input, write y or n")
                            newline = readln()
                        }
                        meldunek = newline == "y"
                        if (meldunek) {
                            gameState.atut = gameState.myCards[myCardInd].suit
                            if (myPlace != gameState.licytacjaWinner) {
                                gameState.points[myPlace.ordinal] += gameState.myCards[myCardInd].suit.value
                            }
                            else {
                                newLicytacjaWinnerPoints += gameState.myCards[myCardInd].suit.value
                            }
                        }
                    }
                }

                // Send decision
                output.writeStringUtf8(Message.PlayACard(gameState.myCards[myCardInd], myPlace, meldunek).toJson())
                cardsPlayed[myPlace.ordinal] = gameState.myCards[myCardInd]
                gameState.myCards.removeAt(myCardInd)
                println("Cards played: ${nullableCardsToString(cardsPlayed.toList())}")
            }
            else -> {// Other players
                println("Player $playing turn to play")
                val serverMsgJson = input.readUTF8Line() ?: throw Exception("No message received")
                val serverMsg = Json.decodeFromString<Message>(serverMsgJson)
                if (serverMsg is Message.PlayACard) {
                    cardsPlayed[playing.ordinal] = serverMsg.card
                    if (firstSuit == null) {
                        firstSuit = serverMsg.card.suit
                        if (serverMsg.meldunek) {
                            gameState.atut = serverMsg.card.suit
                            if (playing != gameState.licytacjaWinner) {
                                gameState.points[playing.ordinal] += serverMsg.card.suit.value
                            }
                            else {
                                newLicytacjaWinnerPoints += serverMsg.card.suit.value
                            }
                        }
                    }
                    println("Cards played: ${nullableCardsToString(cardsPlayed.toList())}")
                }
                else {
                    throw Exception("Unexpected message from server, expected PlayCard")
                }
            }
        }
        playing = playing.next()
    }

    // Calculate points
    val cards = arrayOf(cardsPlayed[0]!!, cardsPlayed[1]!!, cardsPlayed[2]!!)
    val nextLewStarter = compareCards(cards, lewStarter, gameState.atut)
    if (nextLewStarter != gameState.licytacjaWinner) {
        gameState.points[nextLewStarter.ordinal] += calculatePoints(cards)
    }
    else {
        newLicytacjaWinnerPoints = licytacjaWinnerPoints + calculatePoints(cards)
    }
    return nextLewStarter to newLicytacjaWinnerPoints
}

internal suspend fun gameloop(gameState: ClientGameState, input: ByteReadChannel, output: ByteWriteChannel, myPlace: Place) {
    var lewStarter = gameState.licytacjaWinner
    var licytacjaWinnerPoints = 0
    repeat(8) {
        val (nextLewStarter, newLicytacjaWinnerPoints) = play1round(lewStarter, gameState, licytacjaWinnerPoints, myPlace, input, output)
        lewStarter = nextLewStarter
        licytacjaWinnerPoints = newLicytacjaWinnerPoints
    }

    if (licytacjaWinnerPoints < gameState.licytacjaPoints) {
        gameState.points[gameState.licytacjaWinner.ordinal] -= gameState.licytacjaPoints
    }
    else {
        gameState.points[gameState.licytacjaWinner.ordinal] += gameState.licytacjaPoints
    }
}

internal suspend fun getFinalPoints(gameState: ClientGameState, input: ByteReadChannel, output: ByteWriteChannel, place: Place) {
    if (place != gameState.licytacjaWinner) {
        val serverMsgJson = input.readUTF8Line() ?: throw Exception("No message received")
        when (val serverMsg = Json.decodeFromString<Message>(serverMsgJson)) {
            is Message.LicytacjaAktualna -> {
                gameState.licytacjaPoints = serverMsg.punkty
            }
            else -> {
                throw Exception("Unexpected message from server, expected final points")
            }
        }
    }
    else {
        var myLicytacja: Int
        while (true) {
            println("Do you want to change your licytacja, current licytacja points: ${gameState.licytacjaPoints}\nWrite your licytacja, 0 to not change")
            try {
                myLicytacja = readln().toInt()
            }
            catch (e: NumberFormatException) {
                println("Invalid number")
                continue
            }
            if (myLicytacja != 0 && myLicytacja <= gameState.licytacjaPoints) {
                println("Your licytacja must be higher")
            } else {
                break
            }
        }
        if (myLicytacja != 0) {
            gameState.licytacjaPoints = myLicytacja
        }
        output.writeStringUtf8(Message.LicytacjaAktualna(place, gameState.licytacjaPoints).toJson())
    }

    println("Final licytacja points: ${gameState.licytacjaPoints}")
}

internal suspend fun processMusik(gameState: ClientGameState, input: ByteReadChannel, output: ByteWriteChannel, place: Place, musik: List<Card>) {
    if (place == gameState.licytacjaWinner) {
        println("Choose a card from musik for yourself")
        var myCardInd: Int
        while (true) {
            try {
                myCardInd = readln().toInt() - 1
            }
            catch (e: NumberFormatException) {
                println("Invalid number")
                continue
            }
            if (myCardInd < 0 || myCardInd >= musik.size) {
                println("Invalid card index")
            } else {
                break
            }
        }
        gameState.myCards.add(musik[myCardInd])

        for (i in 0..2) {
            if (i != myCardInd) {
                println("Choose whom to give card ${musik[i]} (1-3), you are $place")
                var destination: Int
                while (true) {
                    try {
                        destination = readln().toInt() - 1
                    }
                    catch (e: NumberFormatException) {
                        println("Invalid number")
                        continue
                    }
                    if (destination == place.ordinal) {
                        println("You cannot give card to yourself")
                    } else if (destination < 0 || destination > 2) {
                        println("Invalid destination")
                    } else {
                        break
                    }
                }
                output.writeStringUtf8(Message.GiveCard(musik[i], Place.getPlace(destination)).toJson())
            }
        }
    }
    else {
        val serverMsgJson = input.readUTF8Line() ?: throw Exception("No message received")
        when (val serverMsg = Json.decodeFromString<Message>(serverMsgJson)) {
            is Message.GiveCard -> {
                gameState.myCards.add(serverMsg.card)
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

    val musik: Set<Card> = if (gameState.licytacjaWinner == place) {
        receiveMusik(input)
    }
    else {
        if (gameState.licytacjaPoints > 100) {
            receiveMusik(input)
        } else {
            emptySet()
        }
    }

    if (musik.isNotEmpty()) {
        println("Musik: ${cardsToString(musik)}")
    }
    else {
        println("No musik to shown, 100 points licytacja")
    }
    return musik
}

class ClientGameState: GameState() {
    val points: Array<Int> = Array(3) { 0 }
    var myCards = mutableListOf<Card>()

    fun continueGame(): Boolean {
        return points.all { it < 1000 }
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
            gameState.licytacjaPoints = serverMsg.punkty
            gameState.licytacjaWinner = serverMsg.place
            gameState.licytacjaStarter = serverMsg.place
        }
        else -> {
            throw Exception("Unexpected message from server, expected licytacja")
        }
    }

    var licytujacy = gameState.licytacjaStarter.next()
    val allLicytujacy = mutableSetOf(Place.First, Place.Second, Place.Third)
    while (allLicytujacy.size > 1) {
        if (licytujacy == place) {
            var myLicytacja: Int
            while (true) {
                println("Your turn to licytacja, current licytacja points: ${gameState.licytacjaPoints}, by Player ${gameState.licytacjaWinner}\nWrite your licytacja, 0 to pass")
                try {
                    myLicytacja = readln().toInt()
                }
                catch (e: NumberFormatException) {
                    println("Invalid number")
                    continue
                }

                if (myLicytacja != 0 && myLicytacja <= gameState.licytacjaPoints) {
                    println("Your licytacja must be higher")
                } else {
                    break
                }
            }
            if (myLicytacja == 0) {
                output.writeStringUtf8(Message.LicytacjaPas(place).toJson())
                allLicytujacy.remove(place)
            } else {
                gameState.licytacjaWinner = place
                gameState.licytacjaPoints = myLicytacja
                output.writeStringUtf8(Message.LicytacjaAktualna(place, myLicytacja).toJson())
                if (myLicytacja >= 360) {
                    break
                }
            }
        }
        else {
            serverMsgJson = input.readUTF8Line() ?: throw Exception("No message received")
            when (val serverMsg = Json.decodeFromString<Message>(serverMsgJson)) {
                is Message.LicytacjaAktualna -> {
                    gameState.licytacjaPoints = serverMsg.punkty
                    gameState.licytacjaWinner = serverMsg.place
                    if (gameState.licytacjaPoints >= 360) {
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
        licytujacy = licytujacy.next()
        while (!allLicytujacy.contains(licytujacy)) {
            licytujacy = licytujacy.next()
        }
    }

    println("Licytacja won by Player ${gameState.licytacjaWinner} with ${gameState.licytacjaPoints}")
}





