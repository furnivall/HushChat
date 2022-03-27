package com.example.hushchat
import android.widget.ImageView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter class which is used to show the contact list on the Contacts activity.
 * This class is redrawn every two seconds using the redrawUser daemon method within Contacts.kt
 */
class ContactsAdapter (private val mList: List<ItemsViewModel>) : RecyclerView.Adapter<ContactsAdapter.ViewHolder>() {
    var onItemClick: ((ItemsViewModel) -> Unit)? = null

    override fun onCreateViewHolder (parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_view_design, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder:ViewHolder, position: Int) {
        val ItemsViewModel = mList[position]
        holder.imageView.setImageResource(ItemsViewModel.image)
        holder.textView.text = ItemsViewModel.text
    }

    override fun getItemCount(): Int {
        return mList.size
    }

    inner class ViewHolder(ItemView: View) : RecyclerView.ViewHolder(ItemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageview)
        val textView: TextView = itemView.findViewById(R.id.textView1)
        init {
            itemView.setOnClickListener{
                onItemClick?.invoke(mList[adapterPosition])
            }
        }
    }

}