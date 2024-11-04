package com.example.harchesms
import Game
import android.graphics.Color

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class GameAdapter(
    private val gameList: List<Game>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(game: Game)
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val nicknameCache = mutableMapOf<String, String?>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_game, parent, false)
        return GameViewHolder(view)
    }

    override fun getItemCount(): Int = gameList.size

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        val game = gameList[position]
        holder.bind(game)

        if (game.status == "canceled") {
            holder.itemView.setBackgroundColor(Color.LTGRAY) // Изменяем цвет фона
            holder.textViewGameStatus.text = "Статус: Отменена"
        } else {
            holder.itemView.setBackgroundColor(Color.WHITE)
            holder.textViewGameStatus.text = "Статус: ${getStatusInRussian(game.status)}"
        }
    }

    private fun getStatusInRussian(status: String?): String {
        return when (status) {
            "waiting" -> "Ожидание"
            "playing" -> "Игра идет"
            "finished" -> "Завершена"
            "draw" -> "Ничья"
            "canceled" -> "Отменена"
            else -> "Неизвестный статус"
        }
    }


    inner class GameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        private val textViewGameInfo: TextView = itemView.findViewById(R.id.textViewGameInfo)
        val textViewGameStatus: TextView = itemView.findViewById(R.id.textViewGameStatus)

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(game: Game) {
            var player1Nickname = "Ожидание игрока"
            var player2Nickname = "Ожидание игрока"

            val player1Id = game.player1Id
            val player2Id = game.player2Id

            if (player1Id != null) {
                if (nicknameCache.containsKey(player1Id)) {
                    player1Nickname = nicknameCache[player1Id] ?: "Неизвестный"
                } else {
                    firestore.collection("users").document(player1Id).get()
                        .addOnSuccessListener { document ->
                            val nickname = document.getString("nickname") ?: "Неизвестный"
                            nicknameCache[player1Id] = nickname
                            player1Nickname = nickname
                            updateGameInfo(player1Nickname, player2Nickname, game)
                        }
                }
            }

            if (player2Id != null) {
                if (nicknameCache.containsKey(player2Id)) {
                    player2Nickname = nicknameCache[player2Id] ?: "Неизвестный"
                } else {
                    firestore.collection("users").document(player2Id).get()
                        .addOnSuccessListener { document ->
                            val nickname = document.getString("nickname") ?: "Неизвестный"
                            nicknameCache[player2Id] = nickname
                            player2Nickname = nickname
                            updateGameInfo(player1Nickname, player2Nickname, game)
                        }
                }
            }

            updateGameInfo(player1Nickname, player2Nickname, game)
        }

        private fun updateGameInfo(player1Nickname: String, player2Nickname: String, game: Game) {
            val gameInfo = "Игрок 1: $player1Nickname (${game.player1Symbol}) vs Игрок 2: $player2Nickname (${game.player2Symbol})"
            textViewGameInfo.text = gameInfo
            textViewGameStatus.text = "Статус: ${game.status}"
        }


        override fun onClick(v: View?) {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onItemClick(gameList[position])
            }
        }
    }
}
