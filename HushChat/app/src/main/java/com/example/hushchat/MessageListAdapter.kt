package com.example.hushchat

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MessageListAdapter : ListAdapter<ChatMessage, MessageListAdapter.MessageViewHolder>(MessageComparator()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return MessageViewHolder.create(parent)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current.message, current.timestamp, current.sender)
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageTimeStampView: TextView = itemView.findViewById(R.id.timestamp)
        private val messageItemView: TextView = itemView.findViewById(R.id.textView1)
        fun bind(text: String?, timestamp:String, sender:String) {
            messageItemView.text = text
            messageTimeStampView.text = timestamp
            if (sender=="me") {
                messageItemView.setBackgroundColor(Color.parseColor("#005EB8"))
                messageItemView.setTextColor(Color.WHITE)
                messageTimeStampView.setTextColor(Color.WHITE)
                messageTimeStampView.setBackgroundColor(Color.parseColor("#005EB8"))
            }
        }

        companion object {
            fun create(parent:ViewGroup): MessageViewHolder {
                val view: View = LayoutInflater.from(parent.context)
                    .inflate(R.layout.card_view_design, parent, false)
                return MessageViewHolder(view)
            }
        }
    }

    class MessageComparator : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
           return oldItem.message == newItem.message
        }

    }
}