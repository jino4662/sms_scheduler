package com.example.monthlysms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 예약된 시각에 시스템이 호출하는 리시버.
 * 1. 해당 예약 정보를 DB에서 읽는다.
 * 2. SmsManager로 실제 문자를 발송한다.
 * 3. 다음 달 같은 날짜로 알람을 다시 등록한다 (매월 반복의 핵심).
 */
class SmsAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getIntExtra(AlarmScheduler.getExtraScheduleIdKey(), -1)
        if (scheduleId == -1) return

        // 시스템 브로드캐스트는 처리 시간이 짧게 제한되므로 goAsync()로 시간을 확보한다.
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(context).scheduleDao()
                val schedule = dao.getById(scheduleId)

                if (schedule != null && schedule.enabled) {
                    sendSms(context, schedule.phoneNumber, schedule.message)
                    // 다음 달 발송을 위해 알람을 다시 예약한다.
                    AlarmScheduler.scheduleNext(context, schedule)
                }
            } catch (e: Exception) {
                Log.e("SmsAlarmReceiver", "예약 문자 발송 중 오류", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun sendSms(context: Context, phoneNumber: String, message: String) {
        val smsManager = ContextCompat.getSystemService(context, SmsManager::class.java)
            ?: context.getSystemService(SmsManager::class.java)

        // 메시지가 길 경우 여러 파트로 자동 분할해서 전송
        val parts = smsManager?.divideMessage(message)
        if (smsManager != null && parts != null) {
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
        }
    }
}
