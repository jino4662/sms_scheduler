package com.example.monthlysms

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.monthlysms.databinding.ActivityEditScheduleBinding
import kotlinx.coroutines.launch

/**
 * 하나의 예약을 새로 만들거나(스케줄 id 없음) 기존 예약을 수정한다(id 있음).
 */
class EditScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditScheduleBinding
    private val db by lazy { AppDatabase.getInstance(this) }
    private var existingSchedule: ScheduleEntity? = null

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> loadContactFromUri(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDayPicker()

        val scheduleId = intent.getIntExtra(EXTRA_SCHEDULE_ID, -1)
        if (scheduleId != -1) {
            loadExistingSchedule(scheduleId)
            binding.btnDelete.visibility = android.view.View.VISIBLE
        }

        binding.btnPickContact.setOnClickListener { openContactPicker() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener { delete() }
    }

    private fun setupDayPicker() {
        binding.pickerDay.minValue = 1
        binding.pickerDay.maxValue = 31
        binding.pickerDay.value = 1
    }

    private fun loadExistingSchedule(id: Int) {
        lifecycleScope.launch {
            val schedule = db.scheduleDao().getById(id) ?: return@launch
            existingSchedule = schedule
            binding.editRecipientName.setText(schedule.recipientName)
            binding.editPhoneNumber.setText(schedule.phoneNumber)
            binding.editMessage.setText(schedule.message)
            binding.pickerDay.value = schedule.dayOfMonth
            binding.timePicker.hour = schedule.hour
            binding.timePicker.minute = schedule.minute
        }
    }

    private fun openContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    private fun loadContactFromUri(uri: Uri) {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (nameIndex >= 0) binding.editRecipientName.setText(cursor.getString(nameIndex))
                if (numberIndex >= 0) {
                    val rawNumber = cursor.getString(numberIndex)
                    binding.editPhoneNumber.setText(rawNumber.replace(" ", "").replace("-", ""))
                }
            }
        }
    }

    private fun save() {
        val name = binding.editRecipientName.text.toString().trim()
        val phone = binding.editPhoneNumber.text.toString().trim()
        val message = binding.editMessage.text.toString().trim()
        val day = binding.pickerDay.value
        val hour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) binding.timePicker.hour else 0
        val minute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) binding.timePicker.minute else 0

        if (name.isEmpty()) {
            Toast.makeText(this, "받는 사람 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (phone.isEmpty()) {
            Toast.makeText(this, "전화번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (message.isEmpty()) {
            Toast.makeText(this, "메시지 내용을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val current = existingSchedule
            if (current == null) {
                val newSchedule = ScheduleEntity(
                    recipientName = name,
                    phoneNumber = phone,
                    message = message,
                    dayOfMonth = day,
                    hour = hour,
                    minute = minute,
                    enabled = true
                )
                val newId = db.scheduleDao().insert(newSchedule)
                AlarmScheduler.scheduleNext(this@EditScheduleActivity, newSchedule.copy(id = newId.toInt()))
            } else {
                val updated = current.copy(
                    recipientName = name,
                    phoneNumber = phone,
                    message = message,
                    dayOfMonth = day,
                    hour = hour,
                    minute = minute
                )
                db.scheduleDao().update(updated)
                AlarmScheduler.scheduleNext(this@EditScheduleActivity, updated)
            }
            Toast.makeText(this@EditScheduleActivity, "저장되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun delete() {
        val current = existingSchedule ?: return
        AlertDialog.Builder(this)
            .setTitle("예약 삭제")
            .setMessage("이 예약을 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    db.scheduleDao().delete(current)
                    AlarmScheduler.cancel(this@EditScheduleActivity, current.id)
                    finish()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
    }
}
