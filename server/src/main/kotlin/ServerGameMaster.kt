package server

import kotlinx.coroutines.channels.Channel
import kotlin.random.Random

import common.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Zasady
// 1. Nie ma bomby
// 2. Powyżej 800 pkt zdobywa się punkty tylko dzięki licytacji

class ServerGameState(private val allPlayers: AllPlayers) : GameState() {

    suspend fun sendMsg(place: Place, message: Message) {
        allPlayers.getPlayer(place).sendMsg(message)
    }

    suspend fun receiveMsg(place: Place): Message {
        return allPlayers.getPlayer(place).receiveMsg()
    }

    fun continueGame(): Boolean {
        return allPlayers.continueGame()
    }

    fun addPoints(place: Place, points: Int) {
        allPlayers.addPoints(place, points)
    }

    fun allPoints(): List<Int> {
        return allPlayers.allPoints()
    }
}

val talia: Set<Card> = CardSuit.entries.flatMap { suit ->
    CardRank.entries.map { rank ->
        Card(rank, suit)
    }
}.toSet()

val allPlayerPlaces = setOf(Place.First, Place.Second, Place.Third)

fun rozdanie(): Array<Set<Card>> {
    val pomieszanaTalia = talia.shuffled(Random)
    val firstSet = pomieszanaTalia.take(7).toSet()
    val secondSet = pomieszanaTalia.drop(7).take(7).toSet()
    val thirdSet = pomieszanaTalia.drop(2 * 7).take(7).toSet()
    val musik = pomieszanaTalia.drop(3 * 7).take(3).toSet()
    return arrayOf(firstSet, secondSet, thirdSet, musik)
}



suspend fun gameMaster(allPlayers: AllPlayers, gameMasterChannel: Channel<Unit>) {
    // Waiting for game to start
    gameMasterChannel.receive()
    val gameState = ServerGameState(allPlayers)

    while (true) {
        val rozdanie = rozdanie()

        sendRozdaniaToPlayers(gameState, rozdanie)

        licytacja(gameState)

        // Show musik to everyone if licytacjaPoints > 100, or just the licytacja winner
        showMusik(gameState, rozdanie)

        // Give each player 1 card from musik
        give2CardsFromMusik(gameState)

        // Get final point from licytacja winner
        getFinalPoints(gameState)

        // Game loop
        gameloop(gameState)

        // Check if the game should continue
        if (!gameState.continueGame()) {
            break
        }
    }
}

suspend fun sendRozdaniaToPlayers(gameState: ServerGameState, rozdanie: Array<Set<Card>>) {
    coroutineScope {
        for (player in allPlayerPlaces) {
            launch {
                gameState.sendMsg(player, Message.Rozdanie(rozdanie[player.ordinal]))
            }
        }
    }
}

suspend fun showMusik(gameState: ServerGameState, rozdanie: Array<Set<Card>>) {
    if (gameState.licytacjaPoints > 100) {
        broadcastEveryoneElse(gameState, Message.Musik(rozdanie[3]), null)
    }
    else {
        coroutineScope {
            gameState.sendMsg(gameState.licytacjaWinner, Message.Musik(rozdanie[3]))
        }
    }
}

suspend fun give2CardsFromMusik(gameState: ServerGameState) {
    repeat(2) {
        coroutineScope {
            when (val giveCard = gameState.receiveMsg(gameState.licytacjaWinner)) {
                is Message.GiveCard -> {
                    gameState.sendMsg(giveCard.destination, giveCard)
                }
                else -> throw Exception("Invalid message, expected GiveCard from ${gameState.licytacjaWinner}")
            }
        }
    }
}

suspend fun getFinalPoints(gameState: ServerGameState) {
    if (gameState.licytacjaPoints < 360) {
        when (val aktualnaLicytacja = gameState.receiveMsg(gameState.licytacjaWinner)) {
            is Message.LicytacjaAktualna -> {
                gameState.licytacjaPoints = aktualnaLicytacja.punkty
                broadcastEveryoneElse(gameState, aktualnaLicytacja, gameState.licytacjaWinner)
            }
            else -> throw Exception("Invalid message, expected final LicytacjaAktualna from ${gameState.licytacjaWinner}")
        }
    }
}

suspend fun gameloop(gameState: ServerGameState) {
    var lewStarter = gameState.licytacjaWinner
    var licytacjaWinnerPoints = 0
    repeat(8) {
        val (nextLewStarter, newLicytacjaWinnerPoints) = play1Round(lewStarter, gameState, licytacjaWinnerPoints)
        lewStarter = nextLewStarter
        licytacjaWinnerPoints = newLicytacjaWinnerPoints
    }

    if (licytacjaWinnerPoints < gameState.licytacjaPoints) {
        gameState.addPoints(gameState.licytacjaWinner, -gameState.licytacjaPoints)
    }
    else {
        gameState.addPoints(gameState.licytacjaWinner, gameState.licytacjaPoints)
    }
}

suspend fun play1Round(lewStarter: Place, gameState: ServerGameState, licytacjaWinnerPoints: Int): Pair<Place, Int> {
    var playing = lewStarter
    val cards: Array<Card> = Array(3) { Card(CardRank.Nine, CardSuit.Hearts) }// dummy cards
    var newLicytacjaWinnerPoints = licytacjaWinnerPoints
    // get cards from players, calculate winner, points, meldunek, itd
    repeat(3) {
        when (val playCard = gameState.receiveMsg(playing)) {
            is Message.PlayACard -> {
                cards[playing.ordinal] = playCard.card
                broadcastEveryoneElse(gameState, playCard, playing)
                if (playing == lewStarter && playCard.meldunek && (playCard.card.rank == CardRank.King || playCard.card.rank == CardRank.Queen)) {
                    gameState.atut = playCard.card.suit
                    if (playing != gameState.licytacjaWinner) {
                        gameState.addPoints(playing, playCard.card.suit.value)
                    }
                    else {
                        newLicytacjaWinnerPoints += playCard.card.suit.value
                    }
                }
            }
            else -> throw Exception("Invalid message")
        }
        playing = playing.next()
    }
    val nextLewStarter = compareCards(cards, lewStarter, gameState.atut)
    if (nextLewStarter != gameState.licytacjaWinner) {
        gameState.addPoints(nextLewStarter, calculatePoints(cards))
    }
    else {
        newLicytacjaWinnerPoints = licytacjaWinnerPoints + calculatePoints(cards)
    }
    return nextLewStarter to newLicytacjaWinnerPoints
}

private suspend fun broadcastEveryoneElse(gameState: ServerGameState, message: Message, sender: Place?) = coroutineScope {
    for(player in allPlayerPlaces) {
        if (player != sender) {
            launch {
                gameState.sendMsg(player, message)
            }
        }
    }
}

suspend fun licytacja(gameState: ServerGameState) {
    val wszyscyLicytujacy = mutableSetOf(Place.First, Place.Second, Place.Third)
    var licytujacy = gameState.licytacjaStarter
    gameState.licytacjaWinner = licytujacy
    gameState.licytacjaPoints = 100

    broadcastEveryoneElse(gameState, Message.LicytacjaAktualna(licytujacy, gameState.licytacjaPoints), null)

    while(wszyscyLicytujacy.size > 1) {
        licytujacy = licytujacy.next()
        while (!wszyscyLicytujacy.contains(licytujacy)) {
            licytujacy = licytujacy.next()
        }

        when (val nowaLicytacja = gameState.receiveMsg(licytujacy)) {
            is Message.LicytacjaAktualna -> {
                gameState.licytacjaWinner = licytujacy
                gameState.licytacjaPoints = nowaLicytacja.punkty
                broadcastEveryoneElse(gameState, Message.LicytacjaAktualna(licytujacy, gameState.licytacjaPoints), licytujacy)

                if (nowaLicytacja.punkty >= 360) {// Licytacja won
                    break
                }
            }
            is Message.LicytacjaPas -> {
                wszyscyLicytujacy.remove(licytujacy)
                broadcastEveryoneElse(gameState, Message.LicytacjaPas(licytujacy), licytujacy)
            }
            else -> throw Exception("Invalid message")
        }
    }

    gameState.licytacjaStarter = gameState.licytacjaStarter.next()
}

