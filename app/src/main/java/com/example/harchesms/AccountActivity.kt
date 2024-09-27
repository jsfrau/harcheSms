package com.example.harchesms

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AccountActivity : AppCompatActivity() {

    private lateinit var textViewNickname: TextView
    private lateinit var textViewEmail: TextView
    private lateinit var textViewWins: TextView
    private lateinit var textViewLosses: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        // Инициализация Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Инициализация UI элементов
        textViewNickname = findViewById(R.id.textViewNickname)
        textViewEmail = findViewById(R.id.textViewEmail)
        textViewWins = findViewById(R.id.textViewWins)
        textViewLosses = findViewById(R.id.textViewLosses)

        // Получение и отображение информации об аккаунте
        fetchAccountInfo()
    }

    private fun fetchAccountInfo() {
        val currentUser = auth.currentUser ?: return

        firestore.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val nickname = document.getString("nickname") ?: "Неизвестный"
                    val email = document.getString("email") ?: "Нет Email"
                    val wins = document.getLong("wins") ?: 0
                    val losses = document.getLong("losses") ?: 0

                    textViewNickname.text = "Никнейм: $nickname"
                    textViewEmail.text = "Email: $email"
                    textViewWins.text = "Победы: $wins"
                    textViewLosses.text = "Поражения: $losses"
                } else {
                    Toast.makeText(this, "Не удалось получить данные аккаунта.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
