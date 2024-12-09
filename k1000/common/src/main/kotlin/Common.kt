package common

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class Place(val value: Int) {
    First(0), Second(1), Third(2);

    fun next(): Place {
        return when(this) {
            First -> Second
            Second -> Third
            Third -> First
        }
    }

    companion object {
        fun getPlace(value: Int): Place {
            return when (value) {
                0 -> First
                1 -> Second
                2 -> Third
                else -> throw Exception("Invalid place value")
            }
        }
    }
}

enum class CardSuit(val value: Int) {
    Hearts(100), Diamonds(80), Clubs(60), Spades(40)
}

enum class CardRank(val value: Int) {
    Nine(0), Jack(2), Queen(3), King(4), Ten(10), Ace(11)
}

@Serializable
data class Card(val rank: CardRank, val suit: CardSuit) {
    override fun toString(): String {
        return "${rank.name}-${suit.name}"
    }
}

@Serializable
sealed class Message {
    fun toJson(): String = Json.encodeToString<Message>(this) + "\n"
    @Serializable
    data class DeclarePlace(val place: Place) : Message()

    @Serializable
    data class AcceptPlace(val place: Place) : Message()

    @Serializable
    data class RejectPlace(val place: Place) : Message()

    @Serializable
    data class LicytacjaPas(val place: Place) : Message()

    @Serializable
    data class LicytacjaAktualna(val place: Place, val punkty: Int) : Message()

    @Serializable
    data class Rozdanie(val cards: Set<Card>) : Message()

    @Serializable
    data class Musik(val cards: Set<Card>) : Message()

    @Serializable
    data class GiveCard(val card: Card, val destination: Place) : Message()

    @Serializable
    data class PlayACard(val card: Card, val place: Place, val meldunek: Boolean) : Message()
}

open class GameState {
    var licytacjaStarter: Place = Place.First
    var licytacjaPoints: Int = 0
    var licytacjaWinner: Place = Place.First
    var atut: CardSuit? = null
}

fun compare2Cards(card1: Card, card2: Card, starterSuit: CardSuit, atut: CardSuit?): Int {
    fun assignVal(card: Card, starterSuit: CardSuit, atut: CardSuit?): Int {
        return when (card.suit) {
            atut -> 2
            starterSuit -> 1
            else -> 0
        }
    }

    val card1Val = assignVal(card1, starterSuit, atut)
    val card2Val = assignVal(card2, starterSuit, atut)

    return if (card1Val == card2Val) {
        card1.rank.value - card2.rank.value
    } else {
        card1Val - card2Val
    }
}

fun compareCards(cards: Array<Card>, starter: Place, atut: CardSuit?): Place {
    val starterSuit = cards[starter.ordinal].suit
    var maxCard = cards[starter.ordinal]
    var maxPlace = starter
    var nextPlayer = starter.next()
    repeat(2) {
        val card = cards[nextPlayer.ordinal]
        if (compare2Cards(card, maxCard, starterSuit, atut) > 0) {
            maxCard = card
            maxPlace = nextPlayer
        }
        nextPlayer = nextPlayer.next()
    }

    return maxPlace
}

fun calculatePoints(cards: Array<Card>): Int {
    return cards.sumOf { it.rank.value }
}