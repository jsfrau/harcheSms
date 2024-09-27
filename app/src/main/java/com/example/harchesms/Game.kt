package com.example.harchesms

data class Game(
    var gameId: String = "",
    var player1Id: String? = null,
    var player2Id: String? = null,
    var player1Symbol: String? = null,
    var player2Symbol: String? = null,
    var status: String? = null,
    var board: List<String>? = null,
    var currentTurn: String? = null
)
