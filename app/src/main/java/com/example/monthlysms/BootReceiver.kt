package com.example.monthlysms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 폰이 재부팅되면 AlarmManager에 등록해뒀던 알람은 모두 사라진다.
 * 이 리시버가 BOOT_COMPLETED를 받아서 DB에 저장된 모든 예약을 다시 등록한다.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(context).scheduleDao()
                val schedules = dao.getAllOnce()
                schedules.filter { it.enabled }.forEach { schedule ->
                    AlarmScheduler.scheduleNext(context, schedule)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
