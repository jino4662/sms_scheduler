package com.example.monthlysms

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 하나의 "매월 자동 문자" 예약을 표현하는 데이터 클래스.
 * 예: 수신자 010-1234-5678 에게 매월 25일 09:00 "급여 확인 부탁드립니다" 전송
 */
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val recipientName: String,   // 화면 표시용 이름 (예: "엄마")
    val phoneNumber: String,     // 실제 발송 대상 번호
    val message: String,         // 매번 앱에서 직접 수정 가능한 메시지 내용
    val dayOfMonth: Int,         // 1~31. 해당 월에 없는 날짜면 말일에 발송
    val hour: Int,                // 0~23
    val minute: Int,              // 0~59
    val enabled: Boolean = true   // 끄고 켤 수 있는 토글
)
