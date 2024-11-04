package com.example.harchesms

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

abstract class AccountActivity : AppCompatActivity(), FriendsAdapter.OnItemClickListener {

    private lateinit var editTextSearch: EditText
    private lateinit var buttonSearch: Button
    private lateinit var recyclerViewFriends: RecyclerView
    private lateinit var recyclerViewSearchResults: RecyclerView
    private lateinit var recyclerViewFriendRequests: RecyclerView

    private lateinit var friendsAdapter: FriendsAdapter
    private lateinit var searchResultsAdapter: FriendsAdapter
    private lateinit var friendRequestsAdapter: FriendsAdapter
    private lateinit var imageViewAvatar: ImageView

    private var friendsList: MutableList<User> = mutableListOf()
    private var searchResultsList: MutableList<User> = mutableListOf()
    private var friendRequestsList: MutableList<User> = mutableListOf()

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var currentUserId: String

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends)
        imageViewAvatar = findViewById(R.id.imageViewAvatar)
        // Инициализация Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""

        // Инициализация UI элементов
        editTextSearch = findViewById(R.id.editTextSearch)
        buttonSearch = findViewById(R.id.buttonSearch)
        recyclerViewFriends = findViewById(R.id.recyclerViewFriends)
        recyclerViewSearchResults = findViewById(R.id.recyclerViewSearchResults)
        recyclerViewFriendRequests = findViewById(R.id.recyclerViewFriendRequests)

        // Настройка RecyclerView для друзей
        friendsAdapter = FriendsAdapter(friendsList, currentUserId, AdapterType.FRIENDS, this)
        recyclerViewFriends.layoutManager = LinearLayoutManager(this)
        recyclerViewFriends.adapter = friendsAdapter

        // Настройка RecyclerView для результатов поиска
        searchResultsAdapter = FriendsAdapter(searchResultsList, currentUserId, AdapterType.SEARCH_RESULTS, this)
        recyclerViewSearchResults.layoutManager = LinearLayoutManager(this)
        recyclerViewSearchResults.adapter = searchResultsAdapter

        // Настройка RecyclerView для запросов в друзья
        friendRequestsAdapter = FriendsAdapter(friendRequestsList, currentUserId, AdapterType.FRIEND_REQUESTS, this)
        recyclerViewFriendRequests.layoutManager = LinearLayoutManager(this)
        recyclerViewFriendRequests.adapter = friendRequestsAdapter

        // Обработчик поиска
        buttonSearch.setOnClickListener {
            val nickname = editTextSearch.text.toString().trim()
            if (nickname.isNotEmpty()) {
                searchUsersByNickname(nickname)
            } else {
                Toast.makeText(this, "Введите никнейм для поиска", Toast.LENGTH_SHORT).show()
            }
        }

        //buttonEditProfile.setOnClickListener {
         //   val intent = Intent(this, EditProfileActivity::class.java)
         //   startActivity(intent)
       // }


        // Загрузка списка друзей и запросов
        loadUserInfo()
        loadFriends()
        loadFriendRequests()
    }
    private fun loadUserInfo() {
        val currentUser = auth.currentUser ?: return

        firestore.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val nickname = document.getString("nickname") ?: "Неизвестный"
                    val email = document.getString("email") ?: "Неизвестный"
                    val avatarUrl = document.getString("avatarUrl")



                    // Загрузка аватарки
                    if (!avatarUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_account)
                            .into(imageViewAvatar)
                    } else {
                        imageViewAvatar.setImageResource(R.drawable.ic_account)
                    }

                    // Остальная информация...
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка загрузки информации: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    private fun loadFriends() {
        firestore.collection("users").document(currentUserId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val friendsIds = document.get("friends") as? List<String> ?: listOf()
                    if (friendsIds.isNotEmpty()) {
                        firestore.collection("users")
                            .whereIn(FieldPath.documentId(), friendsIds)
                            .get()
                            .addOnSuccessListener { friendsDocs ->
                                friendsList.clear()
                                for (doc in friendsDocs.documents) {
                                    val user = doc.toObject(User::class.java)
                                    if (user != null) {
                                        user.userId = doc.id
                                        friendsList.add(user)
                                    }
                                }
                                friendsAdapter.notifyDataSetChanged()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Ошибка загрузки друзей: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        friendsList.clear()
                        friendsAdapter.notifyDataSetChanged()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка загрузки друзей: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun loadFriendRequests() {
        firestore.collection("users").document(currentUserId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val requestsIds = document.get("friendRequests") as? List<String> ?: listOf()
                    if (requestsIds.isNotEmpty()) {
                        firestore.collection("users")
                            .whereIn(FieldPath.documentId(), requestsIds)
                            .get()
                            .addOnSuccessListener { requestsDocs ->
                                friendRequestsList.clear()
                                for (doc in requestsDocs.documents) {
                                    val user = doc.toObject(User::class.java)
                                    if (user != null) {
                                        user.userId = doc.id
                                        friendRequestsList.add(user)
                                    }
                                }
                                friendRequestsAdapter.notifyDataSetChanged()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Ошибка загрузки запросов: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        friendRequestsList.clear()
                        friendRequestsAdapter.notifyDataSetChanged()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка загрузки запросов: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun searchUsersByNickname(nickname: String) {
        firestore.collection("users")
            .whereEqualTo("nickname", nickname)
            .get()
            .addOnSuccessListener { documents ->
                searchResultsList.clear()
                for (doc in documents.documents) {
                    val user = doc.toObject(User::class.java)
                    if (user != null && user.userId != currentUserId) { // Исключаем самого себя из результатов
                        user.userId = doc.id
                        searchResultsList.add(user)
                    }
                }
                searchResultsAdapter.notifyDataSetChanged()

                if (searchResultsList.isEmpty()) {
                    Toast.makeText(this, "Пользователь не найден", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка поиска: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onItemClick(user: User) {
        // Отправка запроса в друзья
        sendFriendRequest(user.userId)
    }

    override fun onAccept(userId: String) {
        // Принятие запроса в друзья
        val batch = firestore.batch()
        val currentUserRef = firestore.collection("users").document(currentUserId)
        val requesterRef = firestore.collection("users").document(userId)

        batch.update(currentUserRef, "friends", FieldValue.arrayUnion(userId))
        batch.update(requesterRef, "friends", FieldValue.arrayUnion(currentUserId))
        batch.update(currentUserRef, "friendRequests", FieldValue.arrayRemove(userId))

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Пользователь добавлен в друзья", Toast.LENGTH_SHORT).show()
                loadFriends()
                loadFriendRequests()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка добавления в друзья: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onReject(userId: String) {
        // Отклонение запроса в друзья
        firestore.collection("users").document(currentUserId)
            .update("friendRequests", FieldValue.arrayRemove(userId))
            .addOnSuccessListener {
                Toast.makeText(this, "Запрос отклонен", Toast.LENGTH_SHORT).show()
                loadFriendRequests()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка отклонения запроса: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onRemoveFriend(userId: String) {
        // Реализация удаления друга
        val batch = firestore.batch()
        val currentUserRef = firestore.collection("users").document(currentUserId)
        val friendUserRef = firestore.collection("users").document(userId)

        // Удаляем друг из списка друзей текущего пользователя
        batch.update(currentUserRef, "friends", FieldValue.arrayRemove(userId))
        // Удаляем текущего пользователя из списка друзей другого пользователя
        batch.update(friendUserRef, "friends", FieldValue.arrayRemove(currentUserId))

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Друг удалён", Toast.LENGTH_SHORT).show()
                loadFriends()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка удаления друга: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun sendFriendRequest(userId: String) {
        if (userId == currentUserId) {
            Toast.makeText(this, "Вы не можете добавить себя в друзья", Toast.LENGTH_SHORT).show()
            return
        }

        // Проверка, не является ли пользователь уже другом
        firestore.collection("users").document(currentUserId).get()
            .addOnSuccessListener { document ->
                val friends = document.get("friends") as? List<String> ?: listOf()
                if (friends.contains(userId)) {
                    Toast.makeText(this, "Пользователь уже в друзьях", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Проверка, не отправлен ли уже запрос
                val friendRequests = document.get("friendRequests") as? List<String> ?: listOf()
                if (friendRequests.contains(userId)) {
                    Toast.makeText(this, "Вы уже отправили запрос этому пользователю", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Добавление запроса в друзья
                firestore.collection("users").document(userId)
                    .update("friendRequests", FieldValue.arrayUnion(currentUserId))
                    .addOnSuccessListener {
                        Toast.makeText(this, "Запрос отправлен", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Ошибка отправки запроса: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка проверки друзей: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
