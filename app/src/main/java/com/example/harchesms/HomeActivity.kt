package com.example.harchesms

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*

class HomeActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var textViewWelcome: TextView
    private lateinit var buttonCreateGame: Button
    private lateinit var buttonFindGame: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var textViewOnlineInfo: TextView
    private var gameInvitationListener: ListenerRegistration? = null
    private var gameListener: ListenerRegistration? = null // Для отслеживания изменений в игре
    private val handledInvitations = mutableSetOf<String>()
    // Для отображения сообщения и прогрессбара
    private lateinit var layoutSearching: LinearLayout
    private lateinit var textViewSearching: TextView
    private lateinit var progressBarSearching: ProgressBar

    // Элементы для бокового меню
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navHeaderUsername: TextView

    private var selectedSymbol = "X" // По умолчанию крестики

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        // Инициализация Firebase Auth и Firestore
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Проверка, авторизован ли пользователь
        if (auth.currentUser == null) {
            navigateToLogin()
            return
        }

        setContentView(R.layout.activity_home)

        // Инициализация UI элементов
        textViewWelcome = findViewById(R.id.textViewWelcome)
        buttonCreateGame = findViewById(R.id.buttonCreateGame)
        buttonFindGame = findViewById(R.id.buttonFindGame)
        textViewOnlineInfo = findViewById(R.id.textViewOnlineInfo)

        // Элементы для отображения сообщения и прогрессбара
        layoutSearching = findViewById(R.id.layoutSearching)
        textViewSearching = findViewById(R.id.textViewSearching)
        progressBarSearching = findViewById(R.id.progressBarSearching)
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbarHome)
        setSupportActionBar(toolbar)
        // Инициализация бокового меню
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        navView.setNavigationItemSelectedListener(this)



        // Получаем ссылку на TextView в заголовке бокового меню
        val headerView = navView.getHeaderView(0)
        navHeaderUsername = headerView.findViewById(R.id.navHeaderUsername)

        // Установка приветственного сообщения
        val currentUser = auth.currentUser
        textViewWelcome.text = "Добро пожаловать, ${currentUser?.email}"

        // Получение и отображение никнейма пользователя
        fetchAndDisplayNickname(currentUser?.uid)

        // Обработка нажатий на кнопки
        buttonCreateGame.setOnClickListener {
            showSymbolChoiceDialog()
        }

        buttonFindGame.setOnClickListener {
            findAndJoinGame()
        }

        // Обновляем информацию об онлайне
        updateOnlineInfo()
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            firestore.collection("users").document(currentUser.uid)
                .update("isOnline", true)
        }
        listenForGameInvitations()
    }

    override fun onStop() {
        super.onStop()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            firestore.collection("users").document(currentUser.uid)
                .update("isOnline", false)
        }
    }

    private fun listenForGameInvitations() {
        val currentUser = auth.currentUser ?: return

        gameInvitationListener = firestore.collection("gameInvitations")
            .whereEqualTo("receiverId", currentUser.uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    // Обработка ошибки
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    for (doc in snapshots.documents) {
                        val invitationId = doc.id
                        val senderId = doc.getString("senderId") ?: continue

                        // Проверяем, чтобы не показывать одно и то же приглашение несколько раз
                        if (!handledInvitations.contains(invitationId)) {
                            handledInvitations.add(invitationId)
                            showGameInvitationDialog(invitationId, senderId)
                        }
                    }
                }
            }
    }
    private fun showGameInvitationDialog(invitationId: String, senderId: String) {
        firestore.collection("users").document(senderId).get()
            .addOnSuccessListener { document ->
                val senderNickname = document.getString("nickname") ?: "Пользователь"

                AlertDialog.Builder(this)
                    .setTitle("Приглашение в игру")
                    .setMessage("$senderNickname приглашает вас в игру")
                    .setPositiveButton("Принять") { _, _ ->
                        acceptGameInvitation(invitationId, senderId)
                    }
                    .setNegativeButton("Отклонить") { _, _ ->
                        declineGameInvitation(invitationId)
                    }
                    .setCancelable(false)
                    .show()
            }
    }

    private fun acceptGameInvitation(invitationId: String, senderId: String) {
        val currentUser = auth.currentUser ?: return

        firestore.collection("gameInvitations").document(invitationId)
            .update("status", "accepted")
            .addOnSuccessListener {
                // Создаем новую игру
                val gameData = hashMapOf(
                    "player1Id" to senderId,
                    "player2Id" to currentUser.uid,
                    "player1Symbol" to "X",
                    "player2Symbol" to "O",
                    "status" to "playing",
                    "board" to List(9) { "" },
                    "currentTurn" to "X",
                    "createdAt" to FieldValue.serverTimestamp()
                )

                firestore.collection("games")
                    .add(gameData)
                    .addOnSuccessListener { gameRef ->
                        // Переходим к игре
                        val intent = Intent(this, GameActivity::class.java)
                        intent.putExtra("gameId", gameRef.id)
                        intent.putExtra("playerSymbol", "O")
                        startActivity(intent)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Ошибка создания игры: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка обновления приглашения: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun declineGameInvitation(invitationId: String) {
        firestore.collection("gameInvitations").document(invitationId)
            .update("status", "declined")
            .addOnSuccessListener {
                Toast.makeText(this, "Вы отклонили приглашение", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка обновления приглашения: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    /**
     * Получает и отображает никнейм пользователя
     */
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

    /**
     * Обновляет информацию о текущем онлайне
     */
    private fun updateOnlineInfo() {
        firestore.collection("users")
            .whereEqualTo("isOnline", true)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    return@addSnapshotListener
                }
                val onlineCount = snapshots?.size() ?: 0

                firestore.collection("games")
                    .whereIn("status", listOf("playing", "waiting"))
                    .addSnapshotListener { gameSnapshots, e2 ->
                        if (e2 != null) {
                            return@addSnapshotListener
                        }
                        val searchingCount = gameSnapshots?.size() ?: 0
                        textViewOnlineInfo.text = "Онлайн: $onlineCount, Ищут игру: $searchingCount"
                    }
            }
    }

    /**
     * Отображает диалог выбора символа для первого игрока
     */
    private fun showSymbolChoiceDialog() {
        val symbols = arrayOf("Крестики (X)", "Нолики (O)")
        selectedSymbol = "X" // По умолчанию крестики

        AlertDialog.Builder(this)
            .setTitle("Выберите символ")
            .setSingleChoiceItems(symbols, 0) { _, which ->
                selectedSymbol = if (which == 0) "X" else "O"
            }
            .setPositiveButton("Создать игру") { _, _ ->
                createGame()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /**
     * Создаёт новую игру и ожидает противника
     */
    private fun createGame() {
        val currentUser = auth.currentUser ?: return
        val gameData = hashMapOf(
            "player1Id" to currentUser.uid,
            "player1Symbol" to selectedSymbol,
            "player2Id" to null,
            "player2Symbol" to if (selectedSymbol == "X") "O" else "X",
            "status" to "waiting",
            "board" to listOf("", "", "", "", "", "", "", "", ""),
            "currentTurn" to "X",
            "createdAt" to FieldValue.serverTimestamp() // Добавлено поле даты создания
        )
        // Отображаем сообщение и прогрессбар
        layoutSearching.visibility = View.VISIBLE
        buttonCreateGame.isEnabled = false
        buttonFindGame.isEnabled = false

        val gamesRef = firestore.collection("games")

        // Создаем новую игру со статусом "waiting"


        gamesRef.add(gameData)
            .addOnSuccessListener { documentReference ->
                listenForOpponent(documentReference.id)
            }
            .addOnFailureListener { e ->
                layoutSearching.visibility = View.GONE
                buttonCreateGame.isEnabled = true
                buttonFindGame.isEnabled = true
                Toast.makeText(this, "Ошибка создания игры: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Ожидает второго игрока
     */
    private fun listenForOpponent(gameId: String) {
        val gamesRef = firestore.collection("games").document(gameId)
        gameListener = gamesRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                // Обработка ошибки
                gameListener?.remove()
                deleteGame(gameId)
                Toast.makeText(this, "Произошла ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                returnToHome()
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val status = snapshot.getString("status")
                if (status == "playing") {
                    // Второй игрок присоединился
                    gameListener?.remove()
                    layoutSearching.visibility = View.GONE
                    buttonCreateGame.isEnabled = true
                    buttonFindGame.isEnabled = true
                    val playerSymbol = snapshot.getString("player1Symbol")
                    val intent = Intent(this, GameActivity::class.java)
                    intent.putExtra("gameId", gameId)
                    intent.putExtra("playerSymbol", playerSymbol)
                    startActivity(intent)
                }
            } else {
                // Игра была удалена
                gameListener?.remove()
                Toast.makeText(this, "Игра была отменена.", Toast.LENGTH_LONG).show()
                returnToHome()
            }
        }
    }

    /**
     * Подключается к существующей игре
     */
    private fun findAndJoinGame() {
        val currentUser = auth.currentUser ?: return

        // Отображаем сообщение и прогрессбар
        layoutSearching.visibility = View.VISIBLE
        buttonFindGame.isEnabled = false
        buttonCreateGame.isEnabled = false

        val gamesRef = firestore.collection("games")

        // Ищем существующую игру в статусе "waiting"
        gamesRef.whereEqualTo("status", "waiting")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Нет доступных игр
                    layoutSearching.visibility = View.GONE
                    buttonFindGame.isEnabled = true
                    buttonCreateGame.isEnabled = true
                    Toast.makeText(this, "Нет доступных игр для подключения.", Toast.LENGTH_LONG).show()
                } else {
                    // Присоединяемся к первой найденной игре
                    val gameDoc = documents.documents[0]
                    val gameId = gameDoc.id
                    val player1Symbol = gameDoc.getString("player1Symbol") ?: "X"
                    val player2Symbol = if (player1Symbol == "X") "O" else "X"

                    // Обновляем данные игры
                    val updates = hashMapOf<String, Any>(
                        "player2Id" to currentUser.uid,
                        "player2Symbol" to player2Symbol,
                        "status" to "playing"
                    )

                    gamesRef.document(gameId).update(updates)
                        .addOnSuccessListener {
                            layoutSearching.visibility = View.GONE
                            buttonFindGame.isEnabled = true
                            buttonCreateGame.isEnabled = true
                            // Переходим к игре
                            val intent = Intent(this, GameActivity::class.java)
                            intent.putExtra("gameId", gameId)
                            intent.putExtra("playerSymbol", player2Symbol)
                            startActivity(intent)
                        }
                        .addOnFailureListener { e ->
                            layoutSearching.visibility = View.GONE
                            buttonFindGame.isEnabled = true
                            buttonCreateGame.isEnabled = true
                            Toast.makeText(this, "Ошибка присоединения к игре: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                layoutSearching.visibility = View.GONE
                buttonFindGame.isEnabled = true
                buttonCreateGame.isEnabled = true
                Toast.makeText(this, "Ошибка поиска игры: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Удаляет игру
     */
    private fun deleteGame(gameId: String) {
        firestore.collection("games").document(gameId).delete()
    }

    /**
     * Возвращает пользователя на главный экран
     */
    private fun returnToHome() {
        layoutSearching.visibility = View.GONE
        buttonCreateGame.isEnabled = true
        buttonFindGame.isEnabled = true
        // Дополнительные действия по возвращению на главный экран
    }

    /**
     * Переход на экран авторизации
     */
    private fun navigateToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Удаляем слушателя при уничтожении активности
        gameListener?.remove()
    }

    // Методы для бокового меню
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (toggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_friends -> {
                openFriends()
            }
            R.id.nav_profile -> {
                openAccountInfo()
            }
            R.id.nav_statistics -> {
                openStatistics()
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

    private fun openFriends() {
        val intent = Intent(this, FriendsActivity::class.java)
        startActivity(intent)
    }


    private fun openStatistics() {
        val intent = Intent(this, StatisticsActivity::class.java)
        startActivity(intent)
    }

    /**
     * Открывает окно с информацией об аккаунте
     */
    private fun openAccountInfo() {
        val intent = Intent(this, AccountActivity::class.java)
        startActivity(intent)
    }
}
