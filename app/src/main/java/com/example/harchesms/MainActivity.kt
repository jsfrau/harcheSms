package com.example.harchesms

import com.google.firebase.FirebaseApp
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import android.text.Editable
import android.text.TextWatcher

class MainActivity : AppCompatActivity() {

    // UI элементы
    private lateinit var textViewTitle: TextView
    private lateinit var editTextNickname: EditText
    private lateinit var editTextEmailOrNickname: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var editTextConfirmPassword: EditText
    private lateinit var buttonAuth: Button
    private lateinit var textViewToggle: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var textViewNicknameAvailability: TextView

    // Firebase Auth и Firestore
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // Текущий режим: true - вход, false - регистрация
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            FirebaseApp.initializeApp(this)
            // Инициализация Firebase Auth и Firestore
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "Ошибка инициализации: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // Проверка, авторизован ли пользователь
        if (auth.currentUser != null) {
            navigateToHome()
            return
        }

        setContentView(R.layout.activity_main)

        // Инициализация UI элементов
        textViewTitle = findViewById(R.id.textViewTitle)
        editTextNickname = findViewById(R.id.editTextNickname)
        editTextEmailOrNickname = findViewById(R.id.editTextEmailOrNickname)
        editTextPassword = findViewById(R.id.editTextPassword)
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword)
        buttonAuth = findViewById(R.id.buttonAuth)
        textViewToggle = findViewById(R.id.textViewToggle)
        progressBar = findViewById(R.id.progressBar)
        textViewNicknameAvailability = findViewById(R.id.textViewNicknameAvailability)

        // Установка начального режима
        setMode(isLogin = true)

        // Обработка нажатия на кнопку авторизации/регистрации
        buttonAuth.setOnClickListener {
            val emailOrNickname = editTextEmailOrNickname.text.toString().trim()
            val password = editTextPassword.text.toString().trim()
            val confirmPassword = editTextConfirmPassword.text.toString().trim()
            val nickname = editTextNickname.text.toString().trim()

            if (validateInput(emailOrNickname, password, confirmPassword, nickname)) {
                if (isLoginMode) {
                    loginUser(emailOrNickname, password)
                } else {
                    registerUser(emailOrNickname, password, nickname)
                }
            }
        }

        // Обработка нажатия на текст переключения режима
        textViewToggle.setOnClickListener {
            isLoginMode = !isLoginMode
            setMode(isLoginMode)
        }

        // Добавляем TextWatcher для динамической проверки доступности никнейма
        editTextNickname.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val nickname = s.toString().trim()
                if (nickname.length >= 3) {
                    checkNicknameAvailability(nickname)
                } else {
                    textViewNicknameAvailability.visibility = View.GONE
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    /**
     * Устанавливает режим формы: вход или регистрация
     */
    private fun setMode(isLogin: Boolean) {
        if (isLogin) {
            textViewTitle.text = "Авторизация"
            buttonAuth.text = "Вход"
            textViewToggle.text = "Нет аккаунта? Зарегистрируйтесь"
            editTextConfirmPassword.visibility = View.GONE
            editTextNickname.visibility = View.GONE
            textViewNicknameAvailability.visibility = View.GONE
            editTextEmailOrNickname.hint = "Email или Никнейм"
        } else {
            textViewTitle.text = "Регистрация"
            buttonAuth.text = "Регистрация"
            textViewToggle.text = "Уже есть аккаунт? Войдите"
            editTextConfirmPassword.visibility = View.VISIBLE
            editTextNickname.visibility = View.VISIBLE
            editTextEmailOrNickname.hint = "Email"
        }

        // Очистка полей ввода
        editTextEmailOrNickname.text.clear()
        editTextPassword.text.clear()
        editTextConfirmPassword.text.clear()
        editTextNickname.text.clear()
        textViewNicknameAvailability.visibility = View.GONE
    }

    /**
     * Валидирует ввод пользователя
     */
    private fun validateInput(
        emailOrNickname: String,
        password: String,
        confirmPassword: String,
        nickname: String
    ): Boolean {
        if (isLoginMode) {
            if (emailOrNickname.isEmpty()) {
                editTextEmailOrNickname.error = "Введите email или никнейм"
                editTextEmailOrNickname.requestFocus()
                return false
            }
        } else {
            if (emailOrNickname.isEmpty()) {
                editTextEmailOrNickname.error = "Введите email"
                editTextEmailOrNickname.requestFocus()
                return false
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(emailOrNickname).matches()) {
                editTextEmailOrNickname.error = "Введите корректный email"
                editTextEmailOrNickname.requestFocus()
                return false
            }

            if (nickname.isEmpty()) {
                editTextNickname.error = "Введите никнейм"
                editTextNickname.requestFocus()
                return false
            }

            if (nickname.length < 3) {
                editTextNickname.error = "Никнейм должен быть не менее 3 символов"
                editTextNickname.requestFocus()
                return false
            }

            if (textViewNicknameAvailability.text == "Никнейм уже занят") {
                editTextNickname.error = "Выберите другой никнейм"
                editTextNickname.requestFocus()
                return false
            }

            // Можно добавить дополнительные проверки для никнейма (например, допустимые символы)
        }

        if (password.isEmpty()) {
            editTextPassword.error = "Введите пароль"
            editTextPassword.requestFocus()
            return false
        }

        if (password.length < 6) {
            editTextPassword.error = "Пароль должен быть не менее 6 символов"
            editTextPassword.requestFocus()
            return false
        }

        if (!isLoginMode) {
            if (confirmPassword.isEmpty()) {
                editTextConfirmPassword.error = "Подтвердите пароль"
                editTextConfirmPassword.requestFocus()
                return false
            }

            if (password != confirmPassword) {
                editTextConfirmPassword.error = "Пароли не совпадают"
                editTextConfirmPassword.requestFocus()
                return false
            }
        }

        return true
    }

    /**
     * Проверяет доступность никнейма
     */
    private fun checkNicknameAvailability(nickname: String) {
        firestore.collection("users")
            .whereEqualTo("nickname", nickname)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    textViewNicknameAvailability.text = "Никнейм доступен"
                    textViewNicknameAvailability.setTextColor(getColor(android.R.color.holo_green_dark))
                } else {
                    textViewNicknameAvailability.text = "Никнейм уже занят"
                    textViewNicknameAvailability.setTextColor(getColor(android.R.color.holo_red_dark))
                }
                textViewNicknameAvailability.visibility = View.VISIBLE
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error checking nickname availability: ${e.message}")
            }
    }

    /**
     * Выполняет вход пользователя через Firebase Authentication
     */
    private fun loginUser(emailOrNickname: String, password: String) {
        try {
            buttonAuth.isEnabled = false
            progressBar.visibility = View.VISIBLE

            // Определяем, это email или никнейм
            if (Patterns.EMAIL_ADDRESS.matcher(emailOrNickname).matches()) {
                // Это email
                signInWithEmail(emailOrNickname, password)
            } else {
                // Это никнейм, нужно получить email по никнейму
                firestore.collection("users")
                    .whereEqualTo("nickname", emailOrNickname)
                    .get()
                    .addOnSuccessListener { documents ->
                        if (!documents.isEmpty) {
                            val email = documents.documents[0].getString("email")
                            if (email != null) {
                                signInWithEmail(email, password)
                            } else {
                                buttonAuth.isEnabled = true
                                progressBar.visibility = View.GONE
                                Toast.makeText(this, "Ошибка входа: email не найден", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            buttonAuth.isEnabled = true
                            progressBar.visibility = View.GONE
                            Toast.makeText(this, "Пользователь не найден", Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        buttonAuth.isEnabled = true
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Ошибка входа: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in loginUser: ${e.message}")
        }
    }

    /**
     * Вспомогательный метод для входа по email
     */
    private fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                buttonAuth.isEnabled = true
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    Toast.makeText(this, "Успешный вход", Toast.LENGTH_SHORT).show()
                    navigateToHome()
                } else {
                    Toast.makeText(this, "Ошибка входа: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    /**
     * Регистрирует нового пользователя через Firebase Authentication и сохраняет данные в Firestore
     */
    private fun registerUser(email: String, password: String, nickname: String) {
        try {
            buttonAuth.isEnabled = false
            progressBar.visibility = View.VISIBLE

            // Проверяем уникальность никнейма и почты
            firestore.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { emailDocs ->
                    if (emailDocs.isEmpty) {
                        firestore.collection("users")
                            .whereEqualTo("nickname", nickname)
                            .get()
                            .addOnSuccessListener { nicknameDocs ->
                                if (nicknameDocs.isEmpty) {
                                    // Почта и никнейм уникальны, можно регистрировать
                                    auth.createUserWithEmailAndPassword(email, password)
                                        .addOnCompleteListener(this) { task ->
                                            if (task.isSuccessful) {
                                                val user = auth.currentUser
                                                val userData = hashMapOf(
                                                    "email" to email,
                                                    "nickname" to nickname,
                                                    "wins" to 0,
                                                    "losses" to 0,
                                                    "friends" to listOf<String>(), // Инициализация пустого списка друзей
                                                    "friendRequests" to listOf<String>(), // Инициализация пустого списка запросов в друзья
                                                    "isOnline" to false, // Добавлено поле для онлайн статуса
                                                    "createdAt" to FieldValue.serverTimestamp()
                                                )
                                                firestore.collection("users")
                                                    .document(user?.uid ?: "")
                                                    .set(userData, SetOptions.merge())
                                                    .addOnSuccessListener {
                                                        buttonAuth.isEnabled = true
                                                        progressBar.visibility = View.GONE
                                                        Toast.makeText(this, "Регистрация прошла успешно", Toast.LENGTH_SHORT).show()
                                                        navigateToHome()
                                                    }
                                                    .addOnFailureListener { e ->
                                                        buttonAuth.isEnabled = true
                                                        progressBar.visibility = View.GONE
                                                        Toast.makeText(this, "Ошибка сохранения данных: ${e.message}", Toast.LENGTH_LONG).show()
                                                    }
                                            } else {
                                                buttonAuth.isEnabled = true
                                                progressBar.visibility = View.GONE
                                                Toast.makeText(this, "Ошибка регистрации: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                } else {
                                    buttonAuth.isEnabled = true
                                    progressBar.visibility = View.GONE
                                    editTextNickname.error = "Никнейм уже занят"
                                    editTextNickname.requestFocus()
                                }
                            }
                    } else {
                        buttonAuth.isEnabled = true
                        progressBar.visibility = View.GONE
                        editTextEmailOrNickname.error = "Почта уже используется"
                        editTextEmailOrNickname.requestFocus()
                    }
                }
                .addOnFailureListener { e ->
                    buttonAuth.isEnabled = true
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Ошибка проверки уникальности: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in registerUser: ${e.message}")
        }
    }

    /**
     * Переход на домашнюю активность
     */
    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}
