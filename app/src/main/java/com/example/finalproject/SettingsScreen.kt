package com.example.finalproject

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import coil.compose.AsyncImage
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun SettingsTabContent(onLogout: () -> Unit, themeViewModel: ThemeViewModel, onNavigateToAvatar: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val user = auth.currentUser

    var username by remember { mutableStateOf("...") }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showPwdDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(user?.uid) {
        if (user != null) {
            db.collection("users").document(user.uid).addSnapshotListener { doc, _ ->
                if (doc != null) {
                    username = doc.getString("username") ?: "使用者"
                    photoUrl = doc.getString("photoUrl")
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // 使用者資訊與頭貼
        Box(contentAlignment = Alignment.Center) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "頭貼",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = user?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

        Spacer(modifier = Modifier.height(32.dp))

        // 修改頭貼功能
        OutlinedButton(
            onClick = onNavigateToAvatar,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("修改頭貼")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 修改暱稱按鈕
        OutlinedButton(
            onClick = { showNameDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.edit_nickname))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 修改密碼按鈕
        OutlinedButton(
            onClick = { showPwdDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.change_password))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 語言設定按鈕
        OutlinedButton(
            onClick = { showLanguageDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Language, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.language_settings))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 深色模式切換
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Brightness4, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.dark_mode))
            }
            Switch(
                checked = themeViewModel.isDarkMode,
                onCheckedChange = { themeViewModel.toggleDarkMode() }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 登出按鈕
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (themeViewModel.isDarkMode) Color(0xFF333333) else Color(0xFFF5F5F5),
                contentColor = Color.Red
            ),
            elevation = null
        ) {
            Text(stringResource(R.string.logout))
        }
    }

    // 語言選擇彈窗
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.select_language)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags("zh-TW")
                                AppCompatDelegate.setApplicationLocales(appLocale)
                                showLanguageDialog = false
                                val intent = Intent(context, MainActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                context.startActivity(intent)
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.lang_zh))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags("en")
                                AppCompatDelegate.setApplicationLocales(appLocale)
                                showLanguageDialog = false
                                val intent = Intent(context, MainActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                context.startActivity(intent)
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.lang_en))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 修改暱稱彈窗
    if (showNameDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(stringResource(R.string.edit_nickname)) },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text(stringResource(R.string.new_nickname)) })
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank() && user != null) {
                        db.collection("users").document(user.uid).update("username", newName)
                            .addOnSuccessListener {
                                Toast.makeText(context, context.getString(R.string.update_success), Toast.LENGTH_SHORT).show()
                                showNameDialog = false
                            }
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // 修改密碼彈窗
    if (showPwdDialog) {
        var oldPwd by remember { mutableStateOf("") }
        var newPwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPwdDialog = false },
            title = { Text(stringResource(R.string.change_password)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = oldPwd, onValueChange = { oldPwd = it },
                        label = { Text(stringResource(R.string.old_password)) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPwd, onValueChange = { newPwd = it },
                        label = { Text(stringResource(R.string.new_password_hint)) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPwd.length >= 6 && user?.email != null) {
                        val credential = EmailAuthProvider.getCredential(user.email!!, oldPwd)
                        user.reauthenticate(credential).addOnCompleteListener { reAuthTask ->
                            if (reAuthTask.isSuccessful) {
                                user.updatePassword(newPwd).addOnSuccessListener {
                                    Toast.makeText(context, context.getString(R.string.update_success), Toast.LENGTH_SHORT).show()
                                    showPwdDialog = false
                                }.addOnFailureListener {
                                    Toast.makeText(context, "${context.getString(R.string.update_failed)}: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, context.getString(R.string.wrong_password), Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.invalid_password), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { showPwdDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
