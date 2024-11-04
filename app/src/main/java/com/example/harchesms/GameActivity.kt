package com.example.harchesms

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.firestore.EventListener

class GameActivity : AppCompatActivity() {

    private lateinit var gridLayout: GridLayout
    private lateinit var textViewInfo: TextView
    private lateinit var textViewOpponentInfo: TextView
    private lateinit var buttons: Array<Button>
    private lateinit var currentUserId: String
    lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var gameId: String
    private lateinit var playerSymbol: String
    private var opponentSymbol: String? = null

    private var board: MutableList<String> = MutableList(9) { "" }
    private var currentTurn: String = "X"
    private var gameStatus: String = "playing"

    private var gameListenerRegistration: ListenerRegistration? = null
    private var opponentNickname: String = "Оппонент"

    // Добавленные переменные для чата
    private lateinit var listViewMessages: ListView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSendMessage: Button

    private lateinit var messageAdapter: MessageAdapter
    private var messageList: MutableList<Message> = mutableListOf()

    // Добавленная переменная для хранения ID оппонента
    private var opponentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_game)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""
        gameId = intent.getStringExtra("gameId") ?: ""
        playerSymbol = intent.getStringExtra("playerSymbol") ?: "X"
        opponentSymbol = if (playerSymbol == "X") "O" else "X"

        gridLayout = findViewById(R.id.gridLayout)
        textViewInfo = findViewById(R.id.textViewInfo)
        textViewOpponentInfo = findViewById(R.id.textViewOpponentInfo)

        // Инициализация элементов игрового поля
        buttons = Array(9) { i ->
            val button = gridLayout.getChildAt(i) as Button
            button.setOnClickListener { onCellClicked(i) }
            button
        }

        // Инициализация элементов чата
        listViewMessages = findViewById(R.id.listViewMessages)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSendMessage = findViewById(R.id.buttonSendMessage)

        // Инициализация адаптера для сообщений
        messageAdapter = MessageAdapter(this, messageList)
        listViewMessages.adapter = messageAdapter

        // Обработчик отправки сообщения
        buttonSendMessage.setOnClickListener {
            val messageText = editTextMessage.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
                editTextMessage.text.clear()
            }
        }

        // Загрузка информации об оппоненте
        loadOpponentInfo()

        // Начинаем слушать обновления игры
        listenToGameUpdates()

        // Начинаем слушать сообщения
        listenForMessages()
    }

    private fun loadOpponentInfo() {
        val currentUser = auth.currentUser ?: return

        firestore.collection("games").document(gameId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val opponent = if (document.getString("player1Id") == currentUser.uid) {
                        document.getString("player2Id")
                    } else {
                        document.getString("player1Id")
                    }

                    opponent?.let { id ->
                        opponentId = id // Устанавливаем opponentId
                        firestore.collection("users").document(id).get()
                            .addOnSuccessListener { userDoc ->
                                opponentNickname = userDoc.getString("nickname") ?: "Оппонент"
                                textViewOpponentInfo.text = "Вы играете с $opponentNickname"
                            }
                    }
                }
            }
    }

    private fun listenToGameUpdates() {
        val gameRef = firestore.collection("games").document(gameId)
        gameListenerRegistration = gameRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Toast.makeText(this, "Ошибка обновления игры: ${e.message}", Toast.LENGTH_LONG).show()
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                gameStatus = snapshot.getString("status") ?: "playing"
                board = (snapshot.get("board") as? List<String>)?.toMutableList() ?: MutableList(9) { "" }
                currentTurn = snapshot.getString("currentTurn") ?: "X"

                updateUI()

                if (gameStatus == "finished") {
                    val winner = snapshot.getString("winner")
                    if (winner == playerSymbol) {
                        Toast.makeText(this, "Вы выиграли!", Toast.LENGTH_LONG).show()
                    } else if (winner == opponentSymbol) {
                        Toast.makeText(this, "Вы проиграли.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Ничья.", Toast.LENGTH_LONG).show()
                    }
                    disableBoard()
                }
            }
        }
    }

    private fun updateUI() {
        for (i in buttons.indices) {
            buttons[i].text = board[i]
            buttons[i].isEnabled = board[i].isEmpty() && gameStatus == "playing" && currentTurn == playerSymbol
        }

        textViewInfo.text = if (currentTurn == playerSymbol) {
            "Ваш ход ($playerSymbol)"
        } else {
            "Ход оппонента ($opponentSymbol)"
        }
    }

    private fun onCellClicked(index: Int) {
        if (board[index].isNotEmpty()) return

        board[index] = playerSymbol
        buttons[index].text = playerSymbol
        buttons[index].isEnabled = false

        val gameRef = firestore.collection("games").document(gameId)
        val updates = hashMapOf<String, Any>(
            "board" to board,
            "currentTurn" to opponentSymbol!!
        )

        // Проверяем победу
        val winner = checkWinner()
        if (winner != null) {
            updates["status"] = "finished"
            updates["winner"] = winner
        } else if (!board.contains("")) {
            updates["status"] = "finished"
            updates["winner"] = "draw"
        }

        gameRef.update(updates)
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка обновления хода: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun checkWinner(): String? {
        val winCombinations = listOf(
            listOf(0,1,2), listOf(3,4,5), listOf(6,7,8), // горизонтали
            listOf(0,3,6), listOf(1,4,7), listOf(2,5,8), // вертикали
            listOf(0,4,8), listOf(2,4,6) // диагонали
        )

        for (combo in winCombinations) {
            val (a, b, c) = combo
            if (board[a] == playerSymbol && board[b] == playerSymbol && board[c] == playerSymbol) {
                return playerSymbol
            } else if (board[a] == opponentSymbol && board[b] == opponentSymbol && board[c] == opponentSymbol) {
                return opponentSymbol
            }
        }
        return null
    }

    private fun disableBoard() {
        for (button in buttons) {
            button.isEnabled = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gameListenerRegistration?.remove()
        messagesListenerRegistration?.remove()

        // Добавленная проверка на выход из игры
        if (gameStatus == "playing") {
            endGameDueToExit()
        }
    }

    // ----- Добавленные методы для чата -----

    private var messagesListenerRegistration: ListenerRegistration? = null

    // Метод для отправки сообщения
    private fun sendMessage(text: String) {
        val currentUser = auth.currentUser ?: return

        val messageData = hashMapOf(
            "senderId" to currentUser.uid,
            "text" to text,
            "timestamp" to FieldValue.serverTimestamp()
        )

        firestore.collection("games").document(gameId)
            .collection("messages")
            .add(messageData)
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка отправки сообщения: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Метод для прослушивания новых сообщений
    private fun listenForMessages() {
        messagesListenerRegistration = firestore.collection("games").document(gameId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(this, "Ошибка получения сообщений: ${e.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    messageList.clear()
                    for (doc in snapshots.documents) {
                        val message = doc.toObject(Message::class.java)
                        if (message != null) {
                            messageList.add(message)
                        }
                    }
                    messageAdapter.notifyDataSetChanged()
                    // Прокручиваем список сообщений вниз при добавлении нового сообщения
                    listViewMessages.post {
                        listViewMessages.setSelection(messageAdapter.count - 1)
                    }
                }
            }
    }

    // ----- Добавленные методы для завершения игры при выходе -----

    /**
     * Завершает игру, устанавливая статус "finished" и определяя победителя как оппонента
     */
    private fun endGameDueToExit() {
        val gameRef = firestore.collection("games").document(gameId)

        if (opponentId == null) {
            // Если не удалось определить оппонента, устанавливаем ничью
            gameRef.update(
                "status", "finished",
                "winner", "draw"
            )
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Ошибка завершения игры: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            // Устанавливаем победителя как оппонента
            gameRef.update(
                "status", "finished",
                "winner", opponentSymbol ?: "O"
            )
                .addOnSuccessListener {
                    // Обновление статистики
                    updateStatistics(winnerSymbol = opponentSymbol ?: "O")
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Ошибка завершения игры: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun updateStatistics(winnerSymbol: String) {
        val gameRef = firestore.collection("games").document(gameId)
        gameRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val player1Id = document.getString("player1Id")
                    val player2Id = document.getString("player2Id")

                    when (winnerSymbol) {
                        playerSymbol -> {
                            // Текущий пользователь победил
                            incrementWins(currentUserId)
                            incrementLosses(player2Id)
                        }
                        opponentSymbol -> {
                            // Оппонент победил
                            incrementWins(player2Id)
                            incrementLosses(currentUserId)
                        }
                        "draw" -> {
                            // Ничья, можно добавить поле "draws" если необходимо
                            // В данном примере просто не обновляем wins/losses
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка обновления статистики: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun incrementWins(userId: String?) {
        if (userId == null) return
        firestore.collection("users").document(userId)
            .update("wins", FieldValue.increment(1))
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка обновления побед: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun incrementLosses(userId: String?) {
        if (userId == null) return
        firestore.collection("users").document(userId)
            .update("losses", FieldValue.increment(1))
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка обновления поражений: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

}
