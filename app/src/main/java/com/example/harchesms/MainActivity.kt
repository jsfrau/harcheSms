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

class MainActivity : AppCompatActivity() {

    // UI элементы
    private lateinit var textViewTitle: TextView
    private lateinit var editTextEmail: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var editTextConfirmPassword: EditText
    private lateinit var buttonAuth: Button
    private lateinit var textViewToggle: TextView
    private lateinit var progressBar: ProgressBar // Добавьте ProgressBar для индикации

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
            // Дополнительные действия, например, отображение сообщения об ошибке пользователю
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
        editTextEmail = findViewById(R.id.editTextEmail)
        editTextPassword = findViewById(R.id.editTextPassword)
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword)
        buttonAuth = findViewById(R.id.buttonAuth)
        textViewToggle = findViewById(R.id.textViewToggle)
        progressBar = findViewById(R.id.progressBar) // Инициализация ProgressBar

        // Установка начального режима
        setMode(isLogin = true)

        // Обработка нажатия на кнопку авторизации/регистрации
        buttonAuth.setOnClickListener {
            val email = editTextEmail.text.toString().trim()
            val password = editTextPassword.text.toString().trim()
            val confirmPassword = editTextConfirmPassword.text.toString().trim()

            if (validateInput(email, password, confirmPassword)) {
                if (isLoginMode) {
                    loginUser(email, password)
                } else {
                    // Предполагаем, что вы хотите также получать имя пользователя при регистрации
                    val name = "Default Name" // Замените на ввод имени, если необходимо
                    registerUser(email, password, name)
                }
            }

        }

        // Обработка нажатия на текст переключения режима
        textViewToggle.setOnClickListener {
            isLoginMode = !isLoginMode
            setMode(isLoginMode)
        }
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
        } else {
            textViewTitle.text = "Регистрация"
            buttonAuth.text = "Регистрация"
            textViewToggle.text = "Уже есть аккаунт? Войдите"
            editTextConfirmPassword.visibility = View.VISIBLE
        }

        // Очистка полей ввода
        editTextEmail.text.clear()
        editTextPassword.text.clear()
        editTextConfirmPassword.text.clear()
    }

    /**
     * Валидирует ввод пользователя
     */
    private fun validateInput(email: String, password: String, confirmPassword: String): Boolean {
        if (email.isEmpty()) {
            editTextEmail.error = "Введите email"
            editTextEmail.requestFocus()
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.error = "Введите корректный email"
            editTextEmail.requestFocus()
            return false
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
     * Выполняет вход пользователя через Firebase Authentication
     */
    private fun loginUser(email: String, password: String) {
        try {
        buttonAuth.isEnabled = false
        progressBar.visibility = View.VISIBLE
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
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in loginUser: ${e.message}")
            // Обработка ошибки
        }
    }

    /**
     * Регистрирует нового пользователя через Firebase Authentication и сохраняет данные в Firestore
     */
    private fun registerUser(email: String, password: String, name: String) {
        try {
        buttonAuth.isEnabled = false
        progressBar.visibility = View.VISIBLE
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val userData = hashMapOf(
                        "email" to email,
                        "name" to name,
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
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in registerUser: ${e.message}")
            // Обработка ошибки
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
