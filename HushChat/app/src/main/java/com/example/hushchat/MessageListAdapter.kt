package com.example.hushchat

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * Class which deals with the display of our message view model. This class displays and updates
 * when changes are made in the view model. It displays messages within the ChatWindow.kt class and
 */
class MessageListAdapter : ListAdapter<ChatMessage, MessageListAdapter.MessageViewHolder>(MessageComparator()) {
    /**
     * init function
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return MessageViewHolder.create(parent)
    }

    /**
     * init function for when view holder is bound.
     */
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current.message, current.timestamp, current.sender)
    }

    /**
     * Deals with the display of our messages in the chat window. Displays messages from others in
     * default formatting, and handles them differently if they are messages we send specifically.
     */
    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageTimeStampView: TextView = itemView.findViewById(R.id.timestamp)
        private val messageItemView: TextView = itemView.findViewById(R.id.textView1)
        private val messageLinearLayout: LinearLayout = itemView.findViewById(R.id.chatLinearLayout)
        fun bind(text: String?, timestamp:String, sender:String) {
            messageItemView.text = text
            messageTimeStampView.text = timestamp

//            Handle case when we send a message and make it clear to the user that it has been
//            sent by us using formatting.
            if (sender=="me") {
                messageLinearLayout.setBackgroundColor(Color.parseColor("#005EB8"))
                messageItemView.setTextColor(Color.WHITE)
                messageTimeStampView.setTextColor(Color.WHITE)
                messageTimeStampView.setBackgroundColor(Color.parseColor("#005EB8"))
            }
        }

//        object to handle how the view is built
        companion object {
            fun create(parent:ViewGroup): MessageViewHolder {
                val view: View = LayoutInflater.from(parent.context)
                    .inflate(R.layout.card_view_design, parent, false)
                return MessageViewHolder(view)
            }
        }
    }

//    comparator functions
    class MessageComparator : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
           return oldItem.message == newItem.message
        }

    }
}