package client

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import common.*

import k1000.client.generated.resources.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import server.rozdanie


fun String.isDigitsOnly() = all(Char::isDigit) || isEmpty()

@Composable
fun App() {
    val gameState = remember { ClientGameState() }

    var gotHostAndPort by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("")}
    var port by remember { mutableIntStateOf(0) }
    var place by remember { mutableStateOf(Place.First) }
    var connected by remember { mutableStateOf(false) }

    LaunchedEffect(gotHostAndPort) {
        if (gotHostAndPort) {
            connected = true
            gameState.myPlace = place
            client(host, port, place, gameState)
        }
    }

//    LaunchedEffect(Unit) {
//        gameState.myCards = rozdanie()[0].toMutableList()
//        gameState._cardsChanged.value += 1
//        repeat(7) {
//            gameState.myCards.removeLast()
//            gameState._cardsChanged.value += 1
//            delay(2000)
//            gameState._musik.value = listOf(Card(CardRank.Nine, CardSuit.Clubs), Card(CardRank.Nine, CardSuit.Spades), Card(CardRank.Nine, CardSuit.Diamonds))
//
//        }
//    }

    val gameStarted = gameState.gameStarted.collectAsState().value

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(!gotHostAndPort) {
            GetOptions(gameState) { h, po, pl ->
                gotHostAndPort = true
                host = h
                port = po
                place = pl
            }
        }

        AnimatedVisibility(gotHostAndPort && !gameStarted) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Waiting for other players...")
            }
        }

        AnimatedVisibility(gotHostAndPort && gameStarted) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                GameUI(gameState)
            }
        }
    }

}

@Composable
fun ColumnScope.GameUI(gameState: ClientGameState) {
    Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameInfo(gameState)
    }

    Row(
        modifier = Modifier.weight(4f).padding(20.dp, 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MidBoard(gameState)
    }

    Row(
        modifier = Modifier.weight(4f, fill = false),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        CardsUI(gameState)
    }
}

@Composable
fun MidBoard(gameState: ClientGameState) {
    val gamePhase = gameState.gamePhase.collectAsState().value
    when (gamePhase) {
        GamePhase.Licytacja -> Licytacja(gameState)
        GamePhase.Processing -> Text("Processing last request, waiting for response from server...")
        GamePhase.Musik -> Musik(gameState)
        GamePhase.GetFinalLicytacjaPoints -> GetFinalLicytacjaPoints(gameState)
        GamePhase.Game -> Game(gameState)
        GamePhase.End -> Text("Game ended")
    }
}

@Composable
fun Game(gameState: ClientGameState) {
    val cardsPlayed = gameState.cardsPlayed.collectAsState().value
    val atut = gameState.atut.collectAsState().value
    val firstSuit = gameState.firstSuit.collectAsState().value
    val licytacjaWinner = gameState.licytacjaWinner.collectAsState().value
    val licytacjaPoints = gameState.licytacjaPoints.collectAsState().value
    val licytacjaWinnerPoints = gameState.licytacjaWinnerPoints.collectAsState().value
    val playerPlaying = gameState.playerPlaying.collectAsState().value

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(10.dp, 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("First suit: $atut")
            Text("Atut: $firstSuit")
            Text("Licytacja winner: $licytacjaWinner")
            Text("Licytacja points: $licytacjaPoints")
            Text("Current licytacja winner's points: ${licytacjaWinnerPoints}")
            Text("Player's turn: $playerPlaying")
        }

        cardsPlayed.forEachIndexed { ind, card ->
            Column(
                modifier = Modifier.weight(1f).padding(10.dp, 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (ind == 0) "Player 1" else if (ind == 1) "Player 2" else "Player 3",
                )
                if (card != null) {
                    Image(
                        painter = painterResource(cardImgs[card]!!),
                        contentDescription = "",
                        contentScale = ContentScale.FillBounds,
                    )
                }
            }
        }
    }
}

@Composable
fun GetFinalLicytacjaPoints(gameState: ClientGameState) {
    Column (
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (gameState.myPlace == gameState.licytacjaWinner.value) {
            var licytacja by remember { mutableStateOf("") }
            var lastInputValid by remember { mutableStateOf(true) }
            TextField(
                value = licytacja,
                onValueChange = { if (it.isDigitsOnly()) licytacja = it },
                label = { Text("Your new licytacja, capped at 360") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Button(
                onClick = {
                    if (licytacja.isNotEmpty()) {
                        val l = licytacja.toInt()
                        if (l > gameState.licytacjaPoints.value) {
                            gameState._gamePhase.value = GamePhase.Processing
                            runBlocking {
                                gameState.toServerMsgChannel.send(Message.LicytacjaAktualna(gameState.myPlace, l))
                            }
                            lastInputValid = true
                        } else {
                            lastInputValid = false
                        }
                    }
                }
            ) {
                Text("Change Licytacja")
            }

            Button(
                onClick = {
                    gameState._gamePhase.value = GamePhase.Processing
                    runBlocking {
                        gameState.toServerMsgChannel.send(Message.LicytacjaAktualna(gameState.myPlace, gameState.licytacjaPoints.value))
                    }
                }
            ) {
                Text("Pass")
            }

            if (!lastInputValid) {
                Text("Your licytacja must be higher", color = Color.Red)
            }
        } else {
            Text("Waiting for player ${gameState.licytacjaWinner.value} to set final licytacja points")
        }
    }
}

@Composable
fun Musik(gameState: ClientGameState) {
    val musikCards = gameState.musik.collectAsState().value
    val licytacjaWinner = gameState.licytacjaWinner.collectAsState().value

    if (musikCards.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var cardsProcessed by remember { mutableIntStateOf(0) }
            if (gameState.myPlace == licytacjaWinner) {
                Text(
                    text = when (cardsProcessed) {
                        0 -> "Musik\nChoose a card for yourself"
                        1 -> "Musik\nChoose a card for player " + gameState.myPlace.next()
                        else -> "Musik\nChoose a card for player 2" + gameState.myPlace.next().next()
                    },
                    fontStyle = FontStyle.Italic,
                )
            }
            else {
                Text("Musik")
            }

            val weight = 1f

            musikCards.forEach { card ->
                run {
                    val interactionSource = remember { MutableInteractionSource() }
                    val isHovered by interactionSource.collectIsHoveredAsState()

                    val modifier = Modifier.weight(weight).padding(10.dp, 10.dp).hoverable(interactionSource = interactionSource).clickable {
                        if (gameState.myPlace == licytacjaWinner) {
                            gameState._musik.value = gameState.musik.value.filter { musikCard -> musikCard != card }
                            when (cardsProcessed) {
                                0 -> {
                                    gameState.myCards.add(card)
                                    gameState._cardsChanged.value += 1
                                }

                                1 -> runBlocking {
                                    gameState.toServerMsgChannel.send(Message.GiveCard(card, gameState.myPlace.next()))
                                }

                                else -> runBlocking {
                                    gameState.toServerMsgChannel.send(
                                        Message.GiveCard(
                                            card,
                                            gameState.myPlace.next().next()
                                        )
                                    )
                                    gameState._gamePhase.value = GamePhase.GetFinalLicytacjaPoints
                                }
                            }
                            cardsProcessed = (cardsProcessed + 1) % 3
                        }
                    }
                    Column(
                        modifier = modifier,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Image(
                            painter = painterResource(cardImgs[card]!!),
                            contentDescription = "",
                            contentScale = ContentScale.FillBounds,
                            colorFilter = if (isHovered) ColorFilter.colorMatrix(ColorMatrix(invertColorMatrix)) else null,
                        )
                    }

                }
            }
        }
    }
    else {
        Text("No musik to show, waiting for licytacja winner's decision")
    }
}

@Composable
fun Licytacja(gameState: ClientGameState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val licytacjaPoints = gameState.licytacjaPoints.collectAsState().value
        val licytacjaWinner = gameState.licytacjaWinner.collectAsState().value
        val licytujacy = gameState.licytujacy.collectAsState().value
        Text("Licytacja", fontStyle = FontStyle.Italic)
        Text("Current points: ${licytacjaPoints}")
        Text("Current player: ${licytacjaWinner}")
        if (licytujacy == gameState.myPlace) {
            var licytacja by remember { mutableStateOf("") }
            var lastInputValid by remember { mutableStateOf(true) }
            TextField(value = licytacja, onValueChange = { if (it.isDigitsOnly()) licytacja = it }, label = { Text("Your licytacja, capped at 360")}, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),)
            Button(
                onClick = {
                    if (licytacja.isNotEmpty()) {
                        val l = licytacja.toInt()
                        if (l > gameState.licytacjaPoints.value) {
//                            gameState._gamePhase.value = GamePhase.Processing
                            runBlocking {
                                gameState.toServerMsgChannel.send(Message.LicytacjaAktualna(gameState.myPlace, l))
                            }
                            lastInputValid = true
                        } else {
                            lastInputValid = false
                        }
                    }
                }
            ) {
                Text("Licytacja")
            }

            Button(
                onClick = {
//                    gameState._gamePhase.value = GamePhase.Processing
                    runBlocking {
                        gameState.toServerMsgChannel.send(Message.LicytacjaPas(gameState.myPlace))
                    }
                }
            ) {
                Text("Pass")
            }

            if (!lastInputValid) {
                Text("Your licytacja must be higher", color = Color.Red)
            }
        }
    }
}

@Composable
fun GameInfo(gameState: ClientGameState) {
    val points1 = gameState.points[0].collectAsState().value
    val points2 = gameState.points[1].collectAsState().value
    val points3 = gameState.points[2].collectAsState().value
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Points: ")
        val player1Text = if (gameState.myPlace == Place.First) "Player 1 (You): " else "Player 1: "
        val player2Text = if (gameState.myPlace == Place.Second) "Player 2 (You): " else "Player 2: "
        val player3Text = if (gameState.myPlace == Place.Third) "Player 3 (You): " else "Player 3: "
        val textPaddingModifier = Modifier.padding(end = 10.dp)
        Text(
            text = player1Text,
            fontWeight = FontWeight.Bold,
        )
        Text(
            points1.toString(),
            modifier = textPaddingModifier,
        )

        Text(
            text = player2Text,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = points2.toString(),
            modifier = textPaddingModifier,
        )

        Text(
            text = player3Text,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = points3.toString(),
            modifier = textPaddingModifier,
        )
    }
}

@Composable
fun CardsUI(gameState: ClientGameState) {
    val state = gameState.cardsChanged.collectAsState().value
    val playerPlaying = gameState.playerPlaying.collectAsState().value
    val firstSuit = gameState.firstSuit.collectAsState().value
    var alert by remember { mutableStateOf("") }
    var openMeldunekDialog by remember { mutableStateOf(false) }
    var meldunekCardIt by remember { mutableStateOf(0) }
    val cardWeight = 1f / state // dummy division by state, to let it be updated
    val myCards = gameState.myCards
    var cardIt = 0
    val cardItStart = 4 - myCards.size / 2 // To arrange cards in center

    if (openMeldunekDialog) {
        MeldunekAlert(
            onDismissRequest = { openMeldunekDialog = false },
            onConfirmation = {
                gameState._atut.value = gameState.myCards[meldunekCardIt].suit
                if (gameState.myPlace != gameState.licytacjaWinner.value) {
                    gameState._points[gameState.myPlace.ordinal].value += gameState.myCards[meldunekCardIt].suit.value
                }
                else {
                    gameState._newLicytacjaWinnerPoints.value += gameState.myCards[meldunekCardIt].suit.value
                }
                openMeldunekDialog = false
            },
            dialogText = "Do you want meldunek?"
        )
    }


    fun cardClickHandler(thisCardIt: Int) {
        var gotGoodCard = false
        if (firstSuit != null) {
            if (myCards[thisCardIt].suit != firstSuit && gameState.checkSuit(firstSuit)) {
                alert = "You must play card of the same suit"
            }
            else {
                if (myCards[thisCardIt].suit == firstSuit) {
                    if (gameState.checkHaveGreaterCardSameSuit(gameState.cardsPlayed.value, firstSuit, gameState.atut.value)
                        && !checkGreatestCard(gameState.cardsPlayed.value, firstSuit, null, myCards[thisCardIt])) {
                        alert = "You have greater card of the same suit"
                    }
                    else {
                        alert = ""
                        gotGoodCard = true
                    }
                }
                else {
                    if (gameState.checkHaveGreaterCard(gameState.cardsPlayed.value, firstSuit, gameState.atut.value) &&
                        !checkGreatestCard(gameState.cardsPlayed.value, firstSuit, gameState.atut.value, gameState.myCards[thisCardIt])) {
                        alert = "You have greater card"
                    }
                    else {
                        alert = ""
                        gotGoodCard = true
                    }
                }
            }

        }
        else {
            gameState._firstSuit.value = gameState.myCards[thisCardIt].suit
            alert = ""
            gotGoodCard = true
        }

        var meldunek = false
        if (gotGoodCard) {
            // Check meldunek
            val playedCard = gameState.myCards[thisCardIt]
            if (gameState.lewStarter.value == gameState.myPlace) {
                val canMeldunek = (playedCard.rank == CardRank.King && gameState.myCards.contains(Card(CardRank.Queen, playedCard.suit))) ||
                        (playedCard.rank == CardRank.Queen && gameState.myCards.contains(Card(CardRank.King, playedCard.suit)))

                if (canMeldunek) {
                    openMeldunekDialog = true
                    meldunek = true
                }
            }

            runBlocking {
                gameState.toServerMsgChannel.send(Message.PlayACard(playedCard, gameState.myPlace, meldunek))
                gameState._cardsPlayed.value[gameState.myPlace.ordinal] = gameState.myCards[thisCardIt]
                gameState.myCards.removeAt(thisCardIt)
                gameState._cardsChanged.value += 1
            }
        }
    }


    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(alert, color = Color.Red)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (i in 0..7) {
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()

                val cardCell = i >= cardItStart && cardIt < myCards.size
                val thisCardIt = cardIt
                Column(
                    modifier = Modifier.weight(cardWeight).hoverable(interactionSource = interactionSource).clickable {
                        if (playerPlaying == gameState.myPlace && gameState.gamePhase.value == GamePhase.Game && cardCell) {
                            cardClickHandler(thisCardIt)
                        }
                    },
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (cardCell) {
                        Image(
                            painter = painterResource(cardImgs[myCards[thisCardIt]]!!),
                            contentDescription = "",
                            contentScale = ContentScale.FillBounds,
                            colorFilter = if (isHovered) ColorFilter.colorMatrix(ColorMatrix(invertColorMatrix)) else null,
                        )
                        cardIt++
                    }

                }
            }
        }
    }
}

@Composable
fun GetOptions(gameState: ClientGameState, gotOptions: (String, Int, Place) -> Unit) {
    val placeOptions = listOf("1st", "2nd", "3rd")
    val (selectedPlace, onPlaceSelected) = remember { mutableStateOf(placeOptions[0]) }
    var port by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        TextField(value = host, onValueChange = { host = it }, label = { Text("Host") })
        TextField(value = port, onValueChange = { if (it.isDigitsOnly()) port = it }, label = { Text("Port")}, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),)

        placeOptions.forEach { place ->
            Row(Modifier
                .fillMaxWidth()
                .height(56.dp)
                .selectable(
                    selected = ( place == selectedPlace),
                    onClick = { onPlaceSelected(place) },
                    role = Role.RadioButton
                )
                .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                RadioButton(
                    selected = (place == selectedPlace),
                    onClick = null,
                )
                Text("$place place")
            }
        }

        Button(
            onClick = {
                if (host.length > 0 && port.length > 0) {
                    val place = when(selectedPlace) {
                        placeOptions[0] -> Place.First
                        placeOptions[1] -> Place.Second
                        else -> Place.Third
                    }
                    gotOptions(host, port.toInt(), place)
                }
            }
        ) {
            Text("Connect")
        }
    }
}


private val cardImgs = mapOf(
    Card(CardRank.Nine, CardSuit.Clubs) to Res.drawable._9C,
    Card(CardRank.Nine, CardSuit.Spades) to Res.drawable._9S,
    Card(CardRank.Nine, CardSuit.Diamonds) to Res.drawable._9D,
    Card(CardRank.Nine, CardSuit.Hearts) to Res.drawable._9H,

    Card(CardRank.Ten, CardSuit.Clubs) to Res.drawable._10C,
    Card(CardRank.Ten, CardSuit.Spades) to Res.drawable._10S,
    Card(CardRank.Ten, CardSuit.Diamonds) to Res.drawable._10D,
    Card(CardRank.Ten, CardSuit.Hearts) to Res.drawable._10H,

    Card(CardRank.Jack, CardSuit.Clubs) to Res.drawable.JC,
    Card(CardRank.Jack, CardSuit.Spades) to Res.drawable.JS,
    Card(CardRank.Jack, CardSuit.Diamonds) to Res.drawable.JD,
    Card(CardRank.Jack, CardSuit.Hearts) to Res.drawable.JH,

    Card(CardRank.Queen, CardSuit.Clubs) to Res.drawable.QC,
    Card(CardRank.Queen, CardSuit.Spades) to Res.drawable.QS,
    Card(CardRank.Queen, CardSuit.Diamonds) to Res.drawable.QD,
    Card(CardRank.Queen, CardSuit.Hearts) to Res.drawable.QH,

    Card(CardRank.King, CardSuit.Clubs) to Res.drawable.KC,
    Card(CardRank.King, CardSuit.Spades) to Res.drawable.KS,
    Card(CardRank.King, CardSuit.Diamonds) to Res.drawable.KD,
    Card(CardRank.King, CardSuit.Hearts) to Res.drawable.KH,

    Card(CardRank.Ace, CardSuit.Clubs) to Res.drawable.AC,
    Card(CardRank.Ace, CardSuit.Spades) to Res.drawable.AS,
    Card(CardRank.Ace, CardSuit.Diamonds) to Res.drawable.AD,
    Card(CardRank.Ace, CardSuit.Hearts) to Res.drawable.AH,
)

val invertColorMatrix = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f
)

@Composable
fun MeldunekAlert(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogText: String,
) {
    AlertDialog(
        text = {
            Text(text = dialogText)
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation()
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text("Dismiss")
            }
        }
    )
}