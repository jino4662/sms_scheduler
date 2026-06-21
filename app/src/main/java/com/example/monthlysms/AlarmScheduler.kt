package com.example.monthlysms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * 예약(ScheduleEntity) 하나당 다음 발송 시각을 계산해서
 * AlarmManager에 정확한 1회성 알람으로 등록한다.
 *
 * 매월 반복은 AlarmManager의 반복 알람 기능을 쓰지 않는다 (그 기능은
 * 정확히 "30일/31일 간격"이 아니라 부정확하고, 매월 날짜 수가 달라서
 * 의도한 날짜에서 어긋나기 때문). 대신 알람이 울릴 때마다 "다음 달"
 * 알람을 다시 계산해서 새로 등록하는 방식(self-rescheduling)을 쓴다.
 */
object AlarmScheduler {

    private const val EXTRA_SCHEDULE_ID = "schedule_id"

    /** 이 예약의 다음 발송 시각을 계산해서 알람을 등록(또는 갱신)한다. */
    fun scheduleNext(context: Context, schedule: ScheduleEntity) {
        if (!schedule.enabled) {
            cancel(context, schedule.id)
            return
        }

        val triggerAtMillis = computeNextTriggerTime(schedule)

        val intent = Intent(context, SmsAlarmReceiver::class.java).apply {
            putExtra(EXTRA_SCHEDULE_ID, schedule.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            schedule.id, // requestCode: 예약 id로 구분해서 서로 덮어쓰지 않게 함
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 절전 모드(Doze)에서도 정확한 시각에 깨어나도록 setExactAndAllowWhileIdle 사용
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            } else {
                // 사용자가 "정확한 알람" 권한을 끈 경우의 대비책 (덜 정확하지만 동작은 함)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
            )
        }
    }

    fun cancel(context: Context, scheduleId: Int) {
        val intent = Intent(context, SmsAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    fun getExtraScheduleIdKey() = EXTRA_SCHEDULE_ID

    /**
     * 다음 발송 시각(밀리초)을 계산한다.
     * - dayOfMonth가 해당 월의 실제 일수보다 크면 그 달의 마지막 날로 보정한다.
     *   (예: 31일 예약 + 2월 -> 2월 28일 또는 29일)
     * - 이번 달 해당 날짜/시각이 이미 지났으면 다음 달로 계산한다.
     */
    fun computeNextTriggerTime(schedule: ScheduleEntity, from: Calendar = Calendar.getInstance()): Long {
        val now = from.clone() as Calendar

        val candidate = now.clone() as Calendar
        applyDayWithClamp(candidate, candidate.get(Calendar.YEAR), candidate.get(Calendar.MONTH), schedule.dayOfMonth)
        candidate.set(Calendar.HOUR_OF_DAY, schedule.hour)
        candidate.set(Calendar.MINUTE, schedule.minute)
        candidate.set(Calendar.SECOND, 0)
        candidate.set(Calendar.MILLISECOND, 0)

        if (candidate.timeInMillis <= now.timeInMillis) {
            // 이번 달 발송 시각이 이미 지났으므로 다음 달로 이동
            val nextMonthCal = now.clone() as Calendar
            nextMonthCal.add(Calendar.MONTH, 1)
            applyDayWithClamp(
                candidate,
                nextMonthCal.get(Calendar.YEAR),
                nextMonthCal.get(Calendar.MONTH),
                schedule.dayOfMonth
            )
            candidate.set(Calendar.HOUR_OF_DAY, schedule.hour)
            candidate.set(Calendar.MINUTE, schedule.minute)
            candidate.set(Calendar.SECOND, 0)
            candidate.set(Calendar.MILLISECOND, 0)
        }

        return candidate.timeInMillis
    }

    /** year/month(0-indexed)의 실제 마지막 날짜를 넘지 않도록 day를 보정해서 candidate에 설정 */
    private fun applyDayWithClamp(candidate: Calendar, year: Int, month: Int, requestedDay: Int) {
        candidate.set(Calendar.YEAR, year)
        candidate.set(Calendar.MONTH, month)
        val lastDayOfMonth = candidate.getActualMaximum(Calendar.DAY_OF_MONTH)
        val clampedDay = if (requestedDay > lastDayOfMonth) lastDayOfMonth else requestedDay
        candidate.set(Calendar.DAY_OF_MONTH, clampedDay)
    }
}
