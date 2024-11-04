package com.example.harchesms

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.harchesms.databinding.ActivityFriendsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

abstract class FriendsActivity : AppCompatActivity(), FriendsAdapter.OnItemClickListener {

    private lateinit var binding: ActivityFriendsBinding

    private lateinit var friendsAdapter: FriendsAdapter
    private lateinit var searchResultsAdapter: FriendsAdapter
    private lateinit var friendRequestsAdapter: FriendsAdapter

    private var friendsList: MutableList<User> = mutableListOf()
    private var searchResultsList: MutableList<User> = mutableListOf()
    private var friendRequestsList: MutableList<User> = mutableListOf()

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var currentUserId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""

        // Настройка RecyclerView для друзей
        friendsAdapter = FriendsAdapter(friendsList, currentUserId, AdapterType.FRIENDS, this)
        binding.recyclerViewFriends.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewFriends.adapter = friendsAdapter

        // Настройка RecyclerView для результатов поиска
        searchResultsAdapter = FriendsAdapter(searchResultsList, currentUserId, AdapterType.SEARCH_RESULTS, this)
        binding.recyclerViewSearchResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSearchResults.adapter = searchResultsAdapter

        // Настройка RecyclerView для запросов в друзья
        friendRequestsAdapter = FriendsAdapter(friendRequestsList, currentUserId, AdapterType.FRIEND_REQUESTS, this)
        binding.recyclerViewFriendRequests.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewFriendRequests.adapter = friendRequestsAdapter

        // Обработчик поиска
        binding.buttonSearch.setOnClickListener {
            val nickname = binding.editTextSearch.text.toString().trim()
            if (nickname.isNotEmpty()) {
                searchUsersByNickname(nickname)
            } else {
                Toast.makeText(this, "Введите никнейм для поиска", Toast.LENGTH_SHORT).show()
            }
        }

        // Загрузка списка друзей и запросов
        loadFriends()
        loadFriendRequests()
    }

    override fun onInviteToGame(user: User) {
        sendGameInvitation(user.userId)
    }

    private fun sendGameInvitation(userId: String) {
        val currentUser = auth.currentUser ?: return

        val invitationData = hashMapOf(
            "senderId" to currentUser.uid,
            "receiverId" to userId,
            "status" to "pending",
            "timestamp" to FieldValue.serverTimestamp()
        )

        firestore.collection("gameInvitations")
            .add(invitationData)
            .addOnSuccessListener {
                Toast.makeText(this, "Приглашение отправлено", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка отправки приглашения: ${e.message}", Toast.LENGTH_LONG).show()
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
