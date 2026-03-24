package com.tunion.reader.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tunion.reader.R
import com.tunion.reader.model.TransactionRecord
import com.tunion.reader.model.TripRecord

/**
 * 交易记录适配器
 */
class TransactionAdapter(
    private val items: List<TransactionRecord>
) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tv_item_type)
        val tvAmount: TextView = view.findViewById(R.id.tv_item_amount)
        val tvTime: TextView = view.findViewById(R.id.tv_item_time)
        val tvDetail: TextView = view.findViewById(R.id.tv_item_detail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvType.text = item.typeName
        holder.tvAmount.text = if (item.type == 0x02) {
            "+${item.amountDisplay}"
        } else {
            "-${item.amountDisplay}"
        }
        holder.tvAmount.setTextColor(
            if (item.type == 0x02) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()
        )
        holder.tvTime.text = item.timeDisplay
        holder.tvDetail.text = "序号: ${item.sequence}  终端: ${item.terminalId}"
    }

    override fun getItemCount() = items.size
}

/**
 * 行程记录适配器
 */
class TripAdapter(
    private val items: List<TripRecord>
) : RecyclerView.Adapter<TripAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tv_item_type)
        val tvAmount: TextView = view.findViewById(R.id.tv_item_amount)
        val tvTime: TextView = view.findViewById(R.id.tv_item_time)
        val tvDetail: TextView = view.findViewById(R.id.tv_item_detail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val sr = item.stationResult

        // 第一行：类型 + 站台/线路名
        holder.tvType.text = buildString {
            // 优先用数据库查到的线路类型，否则用卡片辅助类型
            append(sr.lineType ?: item.auxTypeName)
            append(" ${item.typeName}")
        }

        // 第二行：站台名 或 金额
        if (sr.stationName != null) {
            holder.tvAmount.text = sr.stationName
            holder.tvAmount.setTextColor(0xFF1976D2.toInt())
        } else {
            holder.tvAmount.text = String.format("¥%.2f", item.amountYuan)
            holder.tvAmount.setTextColor(0xFFF44336.toInt())
        }

        holder.tvTime.text = item.timeDisplay
        holder.tvDetail.text = buildString {
            append(sr.cityName ?: item.cityName)
            if (sr.lineName != null) {
                append("  ${sr.lineName}")
            }
            if (sr.stationName != null) {
                append("  ¥${String.format("%.2f", item.amountYuan)}")
            }
            append("  余额: ¥${String.format("%.2f", item.balanceYuan)}")
        }
    }

    override fun getItemCount() = items.size
}
