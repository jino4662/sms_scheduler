package com.example.monthlysms

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.monthlysms.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ScheduleAdapter
    private val db by lazy { AppDatabase.getInstance(this) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val smsGranted = results[Manifest.permission.SEND_SMS] == true
        if (!smsGranted) {
            AlertDialog.Builder(this)
                .setTitle("문자 발송 권한 필요")
                .setMessage("이 앱은 SMS 발송 권한이 없으면 자동으로 문자를 보낼 수 없습니다. 설정에서 권한을 허용해주세요.")
                .setPositiveButton("설정으로 이동") { _, _ -> openAppSettings() }
                .setNegativeButton("취소", null)
                .show()
        }
        checkExactAlarmPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ScheduleAdapter(
            onItemClick = { schedule -> openEdit(schedule.id) },
            onToggle = { schedule, isChecked -> toggleSchedule(schedule, isChecked) },
            onLongClick = { schedule -> confirmDelete(schedule) }
        )
        binding.recyclerSchedules.adapter = adapter
        binding.recyclerSchedules.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)

        db.scheduleDao().getAll().observe(this) { list ->
            adapter.submitList(list)
            binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fabAdd.setOnClickListener { openEdit(null) }

        requestNeededPermissions()
    }

    private fun openEdit(scheduleId: Int?) {
        val intent = Intent(this, EditScheduleActivity::class.java)
        if (scheduleId != null) {
            intent.putExtra(EditScheduleActivity.EXTRA_SCHEDULE_ID, scheduleId)
        }
        startActivity(intent)
    }

    private fun toggleSchedule(schedule: ScheduleEntity, isChecked: Boolean) {
        lifecycleScope.launch {
            val updated = schedule.copy(enabled = isChecked)
            db.scheduleDao().update(updated)
            if (isChecked) {
                AlarmScheduler.scheduleNext(this@MainActivity, updated)
            } else {
                AlarmScheduler.cancel(this@MainActivity, updated.id)
            }
        }
    }

    private fun confirmDelete(schedule: ScheduleEntity) {
        AlertDialog.Builder(this)
            .setTitle("예약 삭제")
            .setMessage("'${schedule.recipientName}' 예약을 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    db.scheduleDao().delete(schedule)
                    AlarmScheduler.cancel(this@MainActivity, schedule.id)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun requestNeededPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.SEND_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.READ_CONTACTS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            checkExactAlarmPermission()
        }
    }

    /** Android 12+ 에서는 "정확한 알람" 권한을 별도 설정 화면에서 허용해야 매월 정시 발송이 보장된다. */
    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("정확한 알람 권한 필요")
                    .setMessage("매월 정확한 날짜에 문자를 보내려면 '정확한 알람' 권한이 필요합니다.")
                    .setPositiveButton("설정으로 이동") { _, _ ->
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }
}
