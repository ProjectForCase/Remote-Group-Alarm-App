package com.example.finalproject

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.Date

class GroupAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID).orEmpty()
        val senderId = intent.getStringExtra(EXTRA_SENDER_ID).orEmpty()
        val senderName = intent.getStringExtra(EXTRA_SENDER_NAME).orEmpty()
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID).orEmpty()
        val groupName = intent.getStringExtra(EXTRA_GROUP_NAME).orEmpty()
        val targetUids = intent.getStringArrayListExtra(EXTRA_TARGET_UIDS).orEmpty()
        val repeatDays = intent.getIntegerArrayListExtra(EXTRA_REPEAT_DAYS).orEmpty()
        val hour = intent.getIntExtra(EXTRA_HOUR, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        val minute = intent.getIntExtra(EXTRA_MINUTE, Calendar.getInstance().get(Calendar.MINUTE))

        if (senderId.isBlank() || groupId.isBlank() || targetUids.isEmpty()) {
            pendingResult.finish()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val callData = hashMapOf(
            "senderId" to senderId,
            "senderName" to senderName.ifBlank { context.getString(R.string.group_member) },
            "groupId" to groupId,
            "groupName" to groupName,
            "targetUids" to targetUids,
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "pending",
            "source" to "groupAlarm",
            "alarmId" to alarmId
        )

        db.collection("calls").add(callData)
            .addOnSuccessListener {
                if (repeatDays.isEmpty()) {
                    markAlarmStatus(db, alarmId, "triggered")
                } else {
                    val nextTriggerAt = calculateNextRepeatingAlarm(hour, minute, repeatDays)
                    scheduleNextRepeat(
                        context = context,
                        alarmId = alarmId,
                        senderId = senderId,
                        senderName = senderName,
                        groupId = groupId,
                        groupName = groupName,
                        targetUids = targetUids,
                        repeatDays = repeatDays,
                        hour = hour,
                        minute = minute,
                        triggerAtMillis = nextTriggerAt
                    )
                    markAlarmStatus(db, alarmId, "scheduled", nextTriggerAt)
                }
                pendingResult.finish()
            }
            .addOnFailureListener { error ->
                markAlarmStatus(db, alarmId, "failed", errorMessage = error.message)
                pendingResult.finish()
            }
    }

    private fun markAlarmStatus(
        db: FirebaseFirestore,
        alarmId: String,
        status: String,
        nextScheduledAtMillis: Long? = null,
        errorMessage: String? = null
    ) {
        if (alarmId.isBlank()) return

        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "lastTriggeredAt" to FieldValue.serverTimestamp()
        )
        if (nextScheduledAtMillis != null) {
            updates["scheduledAtMillis"] = nextScheduledAtMillis
            updates["scheduledAt"] = Date(nextScheduledAtMillis)
        } else {
            updates["triggeredAt"] = FieldValue.serverTimestamp()
        }
        if (!errorMessage.isNullOrBlank()) {
            updates["errorMessage"] = errorMessage
        }
        db.collection("groupAlarms").document(alarmId).update(updates)
    }

    private fun calculateNextRepeatingAlarm(hour: Int, minute: Int, repeatDays: List<Int>): Long {
        val now = Calendar.getInstance()
        return repeatDays.map { day ->
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.DAY_OF_WEEK, day)
                if (!after(now)) {
                    add(Calendar.WEEK_OF_YEAR, 1)
                }
            }.timeInMillis
        }.minOrNull() ?: now.timeInMillis
    }

    private fun scheduleNextRepeat(
        context: Context,
        alarmId: String,
        senderId: String,
        senderName: String,
        groupId: String,
        groupName: String,
        targetUids: List<String>,
        repeatDays: List<Int>,
        hour: Int,
        minute: Int,
        triggerAtMillis: Long
    ) {
        val intent = Intent(context, GroupAlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_SENDER_ID, senderId)
            putExtra(EXTRA_SENDER_NAME, senderName)
            putExtra(EXTRA_GROUP_ID, groupId)
            putExtra(EXTRA_GROUP_NAME, groupName)
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
            putStringArrayListExtra(EXTRA_TARGET_UIDS, ArrayList(targetUids))
            putIntegerArrayListExtra(EXTRA_REPEAT_DAYS, ArrayList(repeatDays))
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_SENDER_ID = "extra_sender_id"
        const val EXTRA_SENDER_NAME = "extra_sender_name"
        const val EXTRA_GROUP_ID = "extra_group_id"
        const val EXTRA_GROUP_NAME = "extra_group_name"
        const val EXTRA_TARGET_UIDS = "extra_target_uids"
        const val EXTRA_REPEAT_DAYS = "extra_repeat_days"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_MINUTE = "extra_minute"
    }
}
