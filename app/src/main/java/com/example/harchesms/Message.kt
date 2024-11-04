package com.example.harchesms

import com.google.firebase.Timestamp

data class Message(
    var senderId: String = "",
    var text: String = "",
    var timestamp: Timestamp? = null
)