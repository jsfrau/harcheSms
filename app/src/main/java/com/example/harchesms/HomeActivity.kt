package com.example.harchesms

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : AppCompatActivity() {

    private lateinit var textViewWelcome: TextView
    private lateinit var buttonLogout: Button
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Проверка, авторизован ли пользователь
        if (auth.currentUser == null) {
            navigateToLogin()
            return
        }

        setContentView(R.layout.activity_home)

        // Инициализация UI элементов
        textViewWelcome = findViewById(R.id.textViewWelcome)
        buttonLogout = findViewById(R.id.buttonLogout)

        // Установка приветственного сообщения
        val currentUser = auth.currentUser
        textViewWelcome.text = "Добро пожаловать, ${currentUser?.email}"

        // Обработка нажатия на кнопку выхода
        buttonLogout.setOnClickListener {
            auth.signOut()
            navigateToLogin()
        }
    }

    /**
     * Переход на экран авторизации
     */
    private fun navigateToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
