package com.example.monthlysms

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.monthlysms.databinding.ItemScheduleBinding

class ScheduleAdapter(
    private val onItemClick: (ScheduleEntity) -> Unit,
    private val onToggle: (ScheduleEntity, Boolean) -> Unit,
    private val onLongClick: (ScheduleEntity) -> Unit
) : ListAdapter<ScheduleEntity, ScheduleAdapter.ScheduleViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val binding = ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ScheduleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ScheduleViewHolder(private val binding: ItemScheduleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(schedule: ScheduleEntity) {
            binding.textRecipient.text = "${schedule.recipientName} (${schedule.phoneNumber})"
            val hourText = String.format("%02d:%02d", schedule.hour, schedule.minute)
            binding.textSchedule.text = "매월 ${schedule.dayOfMonth}일 $hourText"
            binding.textMessage.text = schedule.message

            // setOnCheckedChangeListener가 bind 중 재호출되지 않도록 리스너를 먼저 해제
            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = schedule.enabled
            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(schedule, isChecked)
            }

            binding.root.setOnClickListener { onItemClick(schedule) }
            binding.root.setOnLongClickListener {
                onLongClick(schedule)
                true
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ScheduleEntity>() {
            override fun areItemsTheSame(oldItem: ScheduleEntity, newItem: ScheduleEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ScheduleEntity, newItem: ScheduleEntity) =
                oldItem == newItem
        }
    }
}
