package com.example.finalproject

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarSelectionScreen(onBack: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    // Storage initialization might need a check if it's available via BOM
    val storage = FirebaseStorage.getInstance()
    val user = auth.currentUser
    val context = LocalContext.current

    val photos = remember { mutableStateListOf<String>() }
    var isLoading by remember { mutableStateOf(true) }
    var isUploading by remember { mutableStateOf(false) }

    // Fetch previously uploaded photos
    LaunchedEffect(user?.uid) {
        if (user != null) {
            db.collection("users").document(user.uid).collection("photos")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { snapshot ->
                    photos.clear()
                    for (doc in snapshot.documents) {
                        doc.getString("url")?.let { photos.add(it) }
                    }
                    isLoading = false
                }
                .addOnFailureListener {
                    isLoading = false
                }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isUploading = true
            val fileName = UUID.randomUUID().toString()
            val ref = storage.reference.child("avatars/${user?.uid}/$fileName")
            ref.putFile(it)
                .addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { downloadUri ->
                        val url = downloadUri.toString()
                        val photoData = hashMapOf(
                            "url" to url,
                            "timestamp" to FieldValue.serverTimestamp()
                        )
                        // Save to user's photo history
                        db.collection("users").document(user!!.uid).collection("photos")
                            .add(photoData)
                            .addOnSuccessListener {
                                photos.add(0, url)
                                // Automatically set as current avatar
                                db.collection("users").document(user.uid).update("photoUrl", url)
                                isUploading = false
                                Toast.makeText(context, "上傳成功", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener {
                    isUploading = false
                    Toast.makeText(context, "上傳失敗", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("選擇頭貼", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Upload button
                    item {
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { launcher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(
                                    Icons.Default.Add, 
                                    contentDescription = "上傳照片", 
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }

                    // Existing photos
                    items(photos) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    db.collection("users").document(user!!.uid).update("photoUrl", url)
                                        .addOnSuccessListener {
                                            Toast.makeText(context, "已更改頭貼", Toast.LENGTH_SHORT).show()
                                            onBack()
                                        }
                                },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}
