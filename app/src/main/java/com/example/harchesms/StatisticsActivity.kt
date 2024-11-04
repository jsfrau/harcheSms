package com.example.harchesms

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StatisticsActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var textViewWins: TextView
    private lateinit var textViewLosses: TextView
    private lateinit var textViewWinRate: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var currentUserId: String

    // Элементы для бокового меню
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navHeaderUsername: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        // Инициализация Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        val currentUser = auth.currentUser // Объявление переменной currentUser
        currentUserId = currentUser?.uid ?: ""

        // Проверка, авторизован ли пользователь
        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Инициализация UI элементов
        textViewWins = findViewById(R.id.textViewWins)
        textViewLosses = findViewById(R.id.textViewLosses)
        textViewWinRate = findViewById(R.id.textViewWinRate)

        // Инициализация Toolbar
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbarStatistics)
        setSupportActionBar(toolbar)

        // Инициализация бокового меню
        drawerLayout = findViewById(R.id.drawerLayoutStatistics)
        navView = findViewById(R.id.navViewStatistics)
        toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener(this)

        // Получаем ссылку на TextView в заголовке бокового меню
        val headerView = navView.getHeaderView(0)
        navHeaderUsername = headerView.findViewById(R.id.navHeaderUsername)

        // Установка никнейма в заголовке бокового меню
        fetchAndDisplayNickname(currentUser?.uid)

        // Загрузка статистики пользователя
        loadUserStatistics()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_account -> {
                openAccountInfo()
            }
            R.id.nav_statistics -> {
                // Уже на странице статистики
                Toast.makeText(this, "Вы уже на странице статистики", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_logout -> {
                auth.signOut()
                navigateToLogin()
            }
            R.id.nav_game_list -> {
                val intent = Intent(this, GameListActivity::class.java)
                startActivity(intent)
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun fetchAndDisplayNickname(userId: String?) {
        if (userId == null) {
            navHeaderUsername.text = "Гость"
            return
        }

        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val nickname = document.getString("nickname") ?: "Неизвестный"
                    navHeaderUsername.text = nickname
                } else {
                    navHeaderUsername.text = "Неизвестный"
                }
            }
            .addOnFailureListener { e ->
                navHeaderUsername.text = "Ошибка загрузки"
                Toast.makeText(this, "Не удалось загрузить никнейм: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun loadUserStatistics() {
        firestore.collection("users").document(currentUserId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val wins = document.getLong("wins") ?: 0
                    val losses = document.getLong("losses") ?: 0

                    textViewWins.text = "Победы: $wins"
                    textViewLosses.text = "Поражения: $losses"

                    val totalGames = wins + losses
                    val winRate = if (totalGames > 0) {
                        String.format("%.2f%%", (wins.toDouble() / totalGames) * 100)
                    } else {
                        "0.00%"
                    }
                    textViewWinRate.text = "Процент побед: $winRate"
                } else {
                    Toast.makeText(this, "Не удалось загрузить статистику.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка загрузки статистики: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun openAccountInfo() {
        val intent = Intent(this, AccountActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
