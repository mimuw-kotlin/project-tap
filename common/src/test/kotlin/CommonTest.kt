package common
import kotlin.test.Test

class CommonTest {
    @Test
    fun compareCardsTest() {
        // cards, starterPlace, atut, expected
        val testCases: Array<Pair<Triple<Array<Card>, Place, CardSuit?>, Place>> = arrayOf(
            Triple(
                arrayOf(// Normal case
                    Card(CardRank.Ace, CardSuit.Clubs),
                    Card(CardRank.Ten, CardSuit.Clubs),
                    Card(CardRank.King, CardSuit.Clubs)
                ),
                Place.Second,
                null
            ) to Place.First,
            Triple(
                arrayOf(// Starter suit wins
                    Card(CardRank.Ace, CardSuit.Hearts),
                    Card(CardRank.Ten, CardSuit.Clubs),
                    Card(CardRank.King, CardSuit.Clubs)
                ),
                Place.Third,
                null
            ) to Place.Second,
            Triple(
                arrayOf(// Atut wins
                    Card(CardRank.Ace, CardSuit.Clubs),
                    Card(CardRank.Ten, CardSuit.Clubs),
                    Card(CardRank.Nine, CardSuit.Spades)
                ),
                Place.First,
                CardSuit.Spades
            ) to Place.Third,
            Triple(
                arrayOf(// Higher atut wins
                    Card(CardRank.Ace, CardSuit.Clubs),
                    Card(CardRank.Ten, CardSuit.Spades),
                    Card(CardRank.King, CardSuit.Spades)
                ),
                Place.First,
                CardSuit.Spades
            ) to Place.Second,
            Triple(
                arrayOf(// Atut does not prevent
                    Card(CardRank.Ace, CardSuit.Clubs),
                    Card(CardRank.Ten, CardSuit.Clubs),
                    Card(CardRank.King, CardSuit.Clubs)
                ),
                Place.Second,
                CardSuit.Diamonds
            ) to Place.First,
        )

        for ((testcase, expected) in testCases) {
            val (cards, starterPlace, atut) = testcase
            assert(compareCards(cards, starterPlace, atut) == expected)
        }
    }
}