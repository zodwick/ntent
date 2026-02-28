package com.scrnstr.ui

import android.net.Uri
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.scrnstr.R
import com.scrnstr.data.InterceptRecord

class RecentInterceptAdapter(
    private var items: List<InterceptRecord> = emptyList()
) : RecyclerView.Adapter<RecentInterceptAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.interceptThumbnail)
        val category: TextView = view.findViewById(R.id.interceptCategory)
        val title: TextView = view.findViewById(R.id.interceptTitle)
        val time: TextView = view.findViewById(R.id.interceptTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_intercept, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.category.text = when (item.category) {
            "food_bill" -> "BILL"
            "event" -> "EVENT"
            "tech_article" -> "ARTICLE"
            "movie" -> "FILM"
            else -> item.category.uppercase()
        }

        holder.title.text = item.title

        holder.time.text = DateUtils.getRelativeTimeSpanString(
            item.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        )

        if (item.thumbnailUri.isNotEmpty()) {
            Glide.with(holder.thumbnail.context)
                .load(Uri.parse(item.thumbnailUri))
                .centerCrop()
                .override(96, 96)
                .into(holder.thumbnail)
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<InterceptRecord>) {
        items = newItems
        notifyDataSetChanged()
    }
}
