package com.example.harchesms

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class UpdateUsersActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация Firestore
        firestore = FirebaseFirestore.getInstance()

        // Запуск обновления пользователей
        updateUsers()
    }

    private fun updateUsers() {
        firestore.collection("users").get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val wins = document.getLong("wins")
                    val losses = document.getLong("losses")

                    val updates = mutableMapOf<String, Any>()

                    if (wins == null) {
                        updates["wins"] = 0
                    }
                    if (losses == null) {
                        updates["losses"] = 0
                    }

                    if (updates.isNotEmpty()) {
                        firestore.collection("users").document(document.id).update(updates)
                            .addOnSuccessListener {
                                // Обновление успешно
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Ошибка обновления пользователя ${document.id}: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                }
                Toast.makeText(this, "Обновление пользователей завершено", Toast.LENGTH_LONG).show()
                finish() // Закрыть активность после завершения
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка получения пользователей: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
    }
}
