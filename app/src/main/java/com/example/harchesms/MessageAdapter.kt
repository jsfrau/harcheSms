package com.example.harchesms

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class MessageAdapter(
    private val context: Context,
    private val messageList: List<Message>
) : BaseAdapter() {

    override fun getCount(): Int = messageList.size

    override fun getItem(position: Int): Any = messageList[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val currentUserId = (context as GameActivity).auth.currentUser?.uid

        val message = messageList[position]
        val inflater = LayoutInflater.from(context)

        val layoutResource = if (message.senderId == currentUserId) {
            R.layout.item_message_outgoing
        } else {
            R.layout.item_message_incoming
        }

        val view = convertView ?: inflater.inflate(layoutResource, parent, false)
        val textViewMessageText = view.findViewById<TextView>(R.id.textViewMessageText)

        textViewMessageText.text = message.text

        return view
    }
}