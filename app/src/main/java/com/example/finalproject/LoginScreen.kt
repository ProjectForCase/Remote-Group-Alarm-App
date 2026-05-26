package com.example.finalproject

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

// 1. Google 登入客戶端設定
fun getGoogleSignInClient(context: Context): GoogleSignInClient {
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken("660397395254-425as7vbpmhn5nhra6he1eqroh85dpeb.apps.googleusercontent.com")
        .requestEmail()
        .build()
    return GoogleSignIn.getClient(context, gso)
}

// 2. 登入畫面 UI 邏輯
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit // 新增導航回呼
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)

            auth.signInWithCredential(credential).addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    val user = auth.currentUser
                    // 👇 Firebase 有提供一個屬性，可以判斷這是不是一個「全新建立」的帳號
                    val isNewUser = authTask.result?.additionalUserInfo?.isNewUser == true

                    if (isNewUser && user != null) {
                        // 如果是新帳號，就幫他在 Firestore 建立資料
                        val db = FirebaseFirestore.getInstance()
                        val userProfile = hashMapOf(
                            "uid" to user.uid,
                            // Google 帳號通常會自帶顯示名稱 (displayName)，如果沒有就給個預設值
                            "username" to (user.displayName ?: "Google 使用者"),
                            "email" to user.email,
                            "totalFocusTime" to 0,
                            "joinedGroups" to emptyList<String>()
                        )

                        db.collection("users").document(user.uid)
                            .set(userProfile)
                            .addOnSuccessListener {
                                onLoginSuccess()
                            }
                            .addOnFailureListener { e ->
                                errorMessage = "${context.getString(R.string.db_failed)}: ${e.message}"
                            }
                    } else {
                        // 如果是老手登入，資料庫早就有資料了，直接跳轉即可
                        onLoginSuccess()
                    }
                } else {
                    errorMessage = authTask.exception?.localizedMessage ?: context.getString(R.string.auth_failed)
                }
            }
        } catch (e: ApiException) {
            errorMessage = "${context.getString(R.string.google_failed)} (代碼: ${e.statusCode})"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(R.string.app_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(48.dp))

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
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) onLoginSuccess()
                            else errorMessage = task.exception?.localizedMessage
                        }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.login_button))
        }

        // 修改為純導航功能
        TextButton(onClick = onNavigateToSignUp) {
            Text(stringResource(R.string.no_account))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

        Button(
            onClick = {
                val signInClient = getGoogleSignInClient(context)
                googleSignInLauncher.launch(signInClient.signInIntent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text(stringResource(R.string.google_login))
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
        }
    }
}
