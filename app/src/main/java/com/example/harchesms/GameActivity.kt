package com.example.harchesms

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*

class GameActivity : AppCompatActivity() {

    private lateinit var gameId: String
    private lateinit var playerSymbol: String
    private lateinit var opponentSymbol: String
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var textViewOpponentInfo: TextView
    private lateinit var textViewInfo: TextView
    private lateinit var gridLayout: GridLayout

    private var gameListener: ListenerRegistration? = null
    private var board: MutableList<String> = MutableList(9) { "" }
    private var currentTurn: String = "X"
    private var isMyTurn: Boolean = false
    private var gameStatus: String = "playing"

    // Элементы чата
    private lateinit var listViewMessages: ListView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSendMessage: Button
    private lateinit var messagesAdapter: ArrayAdapter<String>
    private val messagesList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Получаем данные из Intent
        gameId = intent.getStringExtra("gameId") ?: ""
        playerSymbol = intent.getStringExtra("playerSymbol") ?: "X"
        opponentSymbol = if (playerSymbol == "X") "O" else "X"

        // Инициализация UI элементов
        textViewOpponentInfo = findViewById(R.id.textViewOpponentInfo)
        textViewInfo = findViewById(R.id.textViewInfo)
        gridLayout = findViewById(R.id.gridLayout)

        // Установка начальной информации
        textViewInfo.text = "Вы играете за: $playerSymbol"

        // Инициализация элементов чата
        listViewMessages = findViewById(R.id.listViewMessages)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSendMessage = findViewById(R.id.buttonSendMessage)

        messagesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, messagesList)
        listViewMessages.adapter = messagesAdapter

        buttonSendMessage.setOnClickListener {
            val message = editTextMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                editTextMessage.text.clear()
            }
        }

        // Получаем информацию о противнике
        fetchOpponentInfo()

        // Устанавливаем слушателей
        listenToGameUpdates()
        listenForMessages()

        // Обработка нажатий на клетки поля
        for (i in 0 until gridLayout.childCount) {
            val button = gridLayout.getChildAt(i) as Button
            button.setOnClickListener {
                makeMove(i)
            }
        }
    }

    /**
     * Получает информацию о противнике
     */
    private fun fetchOpponentInfo() {
        val gameRef = firestore.collection("games").document(gameId)
        gameRef.get().addOnSuccessListener { snapshot ->
            val opponentId = if (playerSymbol == "X") {
                snapshot.getString("player2Id")
            } else {
                snapshot.getString("player1Id")
            }

            if (opponentId != null) {
                firestore.collection("users").document(opponentId).get()
                    .addOnSuccessListener { document ->
                        val nickname = document.getString("nickname") ?: "Неизвестный"
                        val wins = document.getLong("wins") ?: 0
                        val losses = document.getLong("losses") ?: 0
                        textViewOpponentInfo.text = "Вы играете с $nickname, его победы: $wins, его поражения: $losses"
                    }
                    .addOnFailureListener { e ->
                        textViewOpponentInfo.text = "Не удалось получить данные противника"
                        Log.e("GameActivity", "Ошибка получения данных противника: ${e.message}")
                    }
            } else {
                textViewOpponentInfo.text = "Противник еще не подключился"
            }
        }.addOnFailureListener { e ->
            Log.e("GameActivity", "Ошибка получения данных игры: ${e.message}")
        }
    }

    /**
     * Слушает обновления игры из Firestore
     */
    private fun listenToGameUpdates() {
        val gameRef = firestore.collection("games").document(gameId)
        gameListener = gameRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w("GameActivity", "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                board = (snapshot.get("board") as List<String>).toMutableList()
                currentTurn = snapshot.getString("currentTurn") ?: "X"
                gameStatus = snapshot.getString("status") ?: "playing"

                updateBoard()
                checkTurn()
                checkForWinner()
            }
        }
    }

    /**
     * Обновляет отображение игрового поля
     */
    private fun updateBoard() {
        for (i in 0 until gridLayout.childCount) {
            val button = gridLayout.getChildAt(i) as Button
            button.text = board[i]
        }
    }

    /**
     * Проверяет, чей сейчас ход
     */
    private fun checkTurn() {
        isMyTurn = currentTurn == playerSymbol
        if (gameStatus != "playing") {
            isMyTurn = false
        }
        textViewInfo.text = when {
            gameStatus == "finished" -> "Игра завершена."
            gameStatus == "draw" -> "Ничья!"
            isMyTurn -> "Ваш ход ($playerSymbol)"
            else -> "Ход противника ($opponentSymbol)"
        }
    }

    /**
     * Обрабатывает ход игрока
     */
    private fun makeMove(position: Int) {
        if (isMyTurn && board[position] == "") {
            board[position] = playerSymbol
            currentTurn = opponentSymbol

            // Обновляем данные в Firestore
            val gameRef = firestore.collection("games").document(gameId)
            gameRef.update(
                "board", board,
                "currentTurn", currentTurn
            ).addOnFailureListener { e ->
                Log.e("GameActivity", "Ошибка обновления хода: ${e.message}")
            }
        }
    }

    /**
     * Проверяет наличие победителя или ничьей
     */
    private fun checkForWinner() {
        val winningPositions = arrayOf(
            intArrayOf(0, 1, 2),
            intArrayOf(3, 4, 5),
            intArrayOf(6, 7, 8),
            intArrayOf(0, 3, 6),
            intArrayOf(1, 4, 7),
            intArrayOf(2, 5, 8),
            intArrayOf(0, 4, 8),
            intArrayOf(2, 4, 6)
        )

        for (positions in winningPositions) {
            val (a, b, c) = positions
            if (board[a] != "" && board[a] == board[b] && board[b] == board[c]) {
                // У нас есть победитель
                gameStatus = "finished"
                val winner = if (board[a] == playerSymbol) "Вы победили!" else "Вы проиграли!"
                textViewInfo.text = winner

                // Обновляем статус игры в Firestore
                val gameRef = firestore.collection("games").document(gameId)
                gameRef.update("status", gameStatus)
                return
            }
        }

        // Проверяем на ничью
        if (!board.contains("") && gameStatus == "playing") {
            gameStatus = "draw"
            textViewInfo.text = "Ничья!"

            // Обновляем статус игры в Firestore
            val gameRef = firestore.collection("games").document(gameId)
            gameRef.update("status", gameStatus)
        }
    }

    /**
     * Отправляет сообщение в чат
     */
    private fun sendMessage(message: String) {
        val messageData = hashMapOf(
            "senderId" to auth.currentUser?.uid,
            "message" to message,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("games").document(gameId)
            .collection("messages")
            .add(messageData)
    }

    /**
     * Слушает сообщения чата
     */
    private fun listenForMessages() {
        firestore.collection("games").document(gameId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    messagesList.clear()
                    for (doc in snapshots.documents) {
                        val senderId = doc.getString("senderId")
                        val message = doc.getString("message")
                        val senderName = if (senderId == auth.currentUser?.uid) "Вы" else "Соперник"
                        messagesList.add("$senderName: $message")
                    }
                    messagesAdapter.notifyDataSetChanged()
                    listViewMessages.smoothScrollToPosition(messagesList.size - 1)
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        gameListener?.remove()
    }
}
