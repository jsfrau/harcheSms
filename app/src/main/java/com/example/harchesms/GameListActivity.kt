package com.example.harchesms

import Game
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*

class GameListActivity : AppCompatActivity(), GameAdapter.OnItemClickListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var gameAdapter: GameAdapter
    private lateinit var gameList: MutableList<Game>
    private lateinit var firestore: FirebaseFirestore
    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Устанавливаем макет
        setContentView(R.layout.activity_game_list)

        // Инициализируем Firestore
        firestore = FirebaseFirestore.getInstance()

        // Инициализируем список игр и адаптер
        gameList = mutableListOf()
        gameAdapter = GameAdapter(gameList, this)

        // Инициализируем RecyclerView
        recyclerView = findViewById(R.id.recyclerViewGames)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = gameAdapter

        // Загружаем игры из Firestore
        loadGames()
    }

    private fun loadGames() {
        // Запрос для получения только активных или ожидающих игр, исключая "canceled"
        listenerRegistration = firestore.collection("games")
            .whereIn("status", listOf("waiting", "playing"))
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("GameListActivity", "Listen failed.", e)
                    Toast.makeText(this, "Ошибка загрузки игр", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    gameList.clear()
                    for (doc in snapshots.documents) {
                        val game = doc.toObject(Game::class.java)
                        if (game != null && game.status != "canceled") { // Дополнительная проверка
                            game.gameId = doc.id // Сохраняем ID игры
                            gameList.add(game)
                        }
                    }
                    gameAdapter.notifyDataSetChanged()
                }
            }
    }


    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove()
    }

    override fun onItemClick(game: Game) {
        // Обработка клика по игре
        Toast.makeText(this, "Вы выбрали игру: ${game.gameId}", Toast.LENGTH_SHORT).show()

        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        if (game.status == "waiting" && game.player2Id == null) {
            // Присоединяемся к игре
            joinGame(game)
        } else {
            // Игра уже началась или завершена
            Toast.makeText(this, "Эта игра недоступна для присоединения.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun joinGame(game: Game) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val gameRef = firestore.collection("games").document(game.gameId)

        val player1Symbol = game.player1Symbol ?: "X"
        val player2Symbol = if (player1Symbol == "X") "O" else "X"

        val updates = hashMapOf<String, Any>(
            "player2Id" to currentUser.uid,
            "player2Symbol" to player2Symbol,
            "status" to "playing"
        )

        gameRef.update(updates)
            .addOnSuccessListener {
                // Успешно присоединились к игре
                val intent = Intent(this, GameActivity::class.java)
                intent.putExtra("gameId", game.gameId)
                intent.putExtra("playerSymbol", player2Symbol)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Не удалось присоединиться к игре: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
