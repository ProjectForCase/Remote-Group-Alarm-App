package com.example.finalproject

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import com.google.firebase.firestore.FieldValue
class FocusViewModel : ViewModel() {
    var timerSeconds by mutableStateOf(0)
        private set

    var isRunning by mutableStateOf(false)
        private set

    var selectedType by mutableStateOf("工作")
        private set

    private var timerJob: Job? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun onTypeChange(newType: String) {
        selectedType = newType
    }

    fun toggleTimer() {
        if (isRunning) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    fun completeTimer() {
        saveRecordToFirebase()
        // 即使存檔失敗或時間太短，點擊完成也應該重置計時器
        if (timerSeconds <= 1) {
            timerSeconds = 0
        }
    }

    fun startTimer() {
        if (isRunning) return
        isRunning = true
        timerJob = viewModelScope.launch {
            while (isRunning) {
                delay(1000)
                timerSeconds++
            }
        }
    }

    fun pauseTimer() {
        isRunning = false
        timerJob?.cancel()
    }

    private fun saveRecordToFirebase() {
        val userId = auth.currentUser?.uid ?: return
        val duration = timerSeconds
        if (duration <= 1) return // 太短不存

        val record = hashMapOf(
            "type" to selectedType,
            "durationSeconds" to duration,
            "timestamp" to Date(),
            "userId" to userId
        )

        val batch = db.batch()

        // 1. 存入單次歷史紀錄 (focus_records)
        val recordRef = db.collection("focus_records").document()
        batch.set(recordRef, record)

        // 2. 更新使用者的總計數據 (users)
        val userRef = db.collection("users").document(userId)

        // A: 累加所有專注的「總時間」 (給排行榜用的)
        batch.update(userRef, "totalFocusTime", FieldValue.increment(duration.toLong()))

        // B: 【新增】累加「特定分類」的時間 (給未來的圓餅圖用的)
        // 使用點記法 "categoryTotals.工作" 來精準更新 Map 裡面的值
        batch.update(userRef, "categoryTotals.$selectedType", FieldValue.increment(duration.toLong()))

        // 提交寫入
        batch.commit()
            .addOnSuccessListener {
                timerSeconds = 0
                // 這裡可以加上成功後的處理，例如 Toast 或 Log
            }
            .addOnFailureListener { e ->
                // 這裡可以處理寫入失敗的狀況
                println("存檔失敗: ${e.message}")
            }
    }

    fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }
}
