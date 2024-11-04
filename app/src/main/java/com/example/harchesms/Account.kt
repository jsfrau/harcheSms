package com.example.harchesms

data class User(
    var userId: String = "",
    var email: String? = null,
    var nickname: String? = null,
    var friends: List<String> = listOf(),
    var friendRequests: List<String> = listOf(),
    var wins: Long = 0,
    var losses: Long = 0,
    var avatarUrl: String? = null,
    var isOnline: Boolean = false// Если используете аватары
)
