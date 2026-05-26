package com.example.finalproject

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore // 👇 匯入 Firestore

@Composable
fun SignUpScreen(onSignUpSuccess: () -> Unit, onBackToLogin: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance() // 👇 取得資料庫實例
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") } // 👇 新增暱稱變數
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(R.string.create_account), style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(48.dp))

        // 👇 新增暱稱輸入框
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.nickname)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password_hint)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text(stringResource(R.string.confirm_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    errorMessage = context.getString(R.string.fill_all)
                    return@Button
                }
                if (password != confirmPassword) {
                    errorMessage = context.getString(R.string.password_mismatch)
                    return@Button
                }
                if (password.length < 6) {
                    errorMessage = context.getString(R.string.password_too_short)
                    return@Button
                }

                // 1. 先在 Auth 建立帳號
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            if (user != null) {
                                // 2. 準備要存入資料庫的資料 (包含上次討論的陣列)
                                val userProfile = hashMapOf(
                                    "uid" to user.uid,
                                    "username" to username,
                                    "email" to email,
                                    "totalFocusTime" to 0,
                                    "joinedGroups" to emptyList<String>() // 初始化為空陣列
                                )

                                // 3. 寫入 Firestore 的 users 集合
                                db.collection("users").document(user.uid)
                                    .set(userProfile)
                                    .addOnSuccessListener {
                                        onSignUpSuccess() // 資料庫寫入成功才跳轉畫面
                                    }
                                    .addOnFailureListener { e ->
                                        errorMessage = "${context.getString(R.string.db_failed)}: ${e.message}"
                                    }
                            }
                        } else {
                            errorMessage = task.exception?.localizedMessage
                        }
                    }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.signup_button))
        }

        TextButton(onClick = onBackToLogin) {
            Text(stringResource(R.string.back_to_login))
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
        }
    }
}