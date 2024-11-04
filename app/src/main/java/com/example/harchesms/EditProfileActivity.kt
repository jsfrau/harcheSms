package com.example.harchesms

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class EditProfileActivity : AppCompatActivity() {

    private lateinit var imageViewAvatar: ImageView
    private lateinit var buttonChangeAvatar: Button
    private lateinit var buttonSave: Button
    private lateinit var editTextNickname: EditText

    private val PICK_IMAGE_REQUEST = 1

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private var avatarUri: Uri? = null // Хранит выбранный URI аватарки

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        // Инициализация Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Инициализация UI элементов
        imageViewAvatar = findViewById(R.id.imageViewAvatar)
        buttonChangeAvatar = findViewById(R.id.buttonChangeAvatar)
        buttonSave = findViewById(R.id.buttonSave)
        editTextNickname = findViewById(R.id.editTextNickname)

        // Загрузка текущих данных пользователя
        loadUserData()

        // Обработчик нажатия на кнопку смены аватарки
        buttonChangeAvatar.setOnClickListener {
            openFileChooser()
        }

        // Обработчик нажатия на кнопку сохранения
        buttonSave.setOnClickListener {
            saveProfileChanges()
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser ?: return

        firestore.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val nickname = document.getString("nickname") ?: ""
                    val avatarUrl = document.getString("avatarUrl")

                    editTextNickname.setText(nickname)

                    if (!avatarUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_account)
                            .into(imageViewAvatar)
                    } else {
                        imageViewAvatar.setImageResource(R.drawable.ic_account)
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка загрузки данных: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun openFileChooser() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Выберите изображение"), PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.data != null) {
            avatarUri = data.data
            imageViewAvatar.setImageURI(avatarUri)
        }
    }

    private fun saveProfileChanges() {
        val nickname = editTextNickname.text.toString().trim()
        val currentUser = auth.currentUser ?: return

        if (nickname.isEmpty()) {
            editTextNickname.error = "Введите никнейм"
            editTextNickname.requestFocus()
            return
        }

        val userUpdates = hashMapOf<String, Any>(
            "nickname" to nickname
        )

        if (avatarUri != null) {
            // Загрузка аватарки в Firebase Storage
            val avatarRef = storage.reference.child("avatars/${currentUser.uid}.jpg")
            avatarRef.putFile(avatarUri!!)
                .addOnSuccessListener {
                    avatarRef.downloadUrl.addOnSuccessListener { uri ->
                        userUpdates["avatarUrl"] = uri.toString()
                        updateUserInFirestore(userUpdates)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Ошибка загрузки аватарки: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            updateUserInFirestore(userUpdates)
        }
    }

    private fun updateUserInFirestore(userUpdates: Map<String, Any>) {
        val currentUser = auth.currentUser ?: return

        firestore.collection("users").document(currentUser.uid)
            .update(userUpdates)
            .addOnSuccessListener {
                Toast.makeText(this, "Профиль обновлен", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка обновления профиля: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}

