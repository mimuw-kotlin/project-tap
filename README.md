[![Review Assignment Due Date](https://classroom.github.com/assets/deadline-readme-button-22041afd0340ce965d47ae6ef1cefeee28c7c493a6346c4f15d667ab976d596c.svg)](https://classroom.github.com/a/M0kyOMLZ)
# 1000

# How to run
- Run server first with `./gradlew :server:run --args="localhost 8080"` (or your host/server)
- Run client (player) with `./gradlew :client:run --args="localhost 8080 1` (or your host, port, player index from 1-3)
- You can only play by command line at the moment

## Authors
- Tap

## Description
1000 - everyone knows how to play "tysiąc"

## Features
- server hosts a game, waiting for 3 players (clients)
- server rules the game, clients only send messages, but at any time (eg. client wants to play a card), server concurrently handles messages and replies (eg. the card played by player A was illegal)

## Plan
The first part is game logic

The second part will be GUI

## Libraries
- idk yet
