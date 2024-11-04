package com.example.harchesms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore

// Определение типов адаптера
enum class AdapterType {
    FRIENDS,
    SEARCH_RESULTS,
    FRIEND_REQUESTS
}

class FriendsAdapter(
    private val userList: List<User>,
    private val currentUserId: String,
    private val adapterType: AdapterType,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<FriendsAdapter.FriendViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(user: User)
        fun onAccept(userId: String)
        fun onReject(userId: String)
        fun onRemoveFriend(userId: String)
        fun onInviteToGame(user: User)
    }


    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun getItemCount(): Int = userList.size

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val user = userList[position]
        holder.bind(user)

    }

    inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        private val imageViewAvatar: ImageView = itemView.findViewById(R.id.imageViewAvatar)
        private val textViewNickname: TextView = itemView.findViewById(R.id.textViewNickname)
        private val buttonAction: Button = itemView.findViewById(R.id.buttonAction)
        private val buttonReject: Button = itemView.findViewById(R.id.buttonReject)
        private val imageViewOnlineStatus: ImageView = itemView.findViewById(R.id.imageViewOnlineStatus)
        private var currentUser: User? = null

        init {
            buttonAction.setOnClickListener(this)
            buttonReject.setOnClickListener(this)
            itemView.setOnClickListener {
                currentUser?.let { listener.onItemClick(it) }
            }
        }

        fun bind(user: User) {
            currentUser = user
            textViewNickname.text = user.nickname

            // Установка аватарки пользователя
            if (!user.avatarUrl.isNullOrEmpty()) {
                // Используем Glide или Picasso для загрузки изображения по URL
                Glide.with(itemView.context)
                    .load(user.avatarUrl)
                    .placeholder(R.drawable.ic_account)
                    .into(imageViewAvatar)
            } else {
                imageViewAvatar.setImageResource(R.drawable.ic_account)
            }

            // Отображение онлайн статуса
            if (user.isOnline) {
                imageViewOnlineStatus.setImageResource(R.drawable.ic_online)
            } else {
                imageViewOnlineStatus.setImageResource(R.drawable.ic_offline)
            }

            when (adapterType) {
                AdapterType.FRIENDS -> {
                    // Кнопка для приглашения в игру
                    buttonAction.text = "Пригласить"
                    buttonAction.visibility = View.VISIBLE
                    buttonReject.visibility = View.GONE
                }
                AdapterType.SEARCH_RESULTS -> {
                    // Кнопка для добавления в друзья
                    buttonAction.text = "Добавить"
                    buttonAction.visibility = View.VISIBLE
                    buttonReject.visibility = View.GONE
                }
                AdapterType.FRIEND_REQUESTS -> {
                    // Кнопки для принятия или отклонения запроса
                    buttonAction.text = "Принять"
                    buttonAction.visibility = View.VISIBLE
                    buttonReject.visibility = View.VISIBLE
                }
            }
        }

        override fun onClick(v: View?) {
            currentUser?.let { user ->
                when (adapterType) {
                    AdapterType.FRIENDS -> {
                        if (v?.id == R.id.buttonAction) {
                            listener.onInviteToGame(user)
                        }
                    }
                    AdapterType.FRIEND_REQUESTS -> {
                        when (v?.id) {
                            R.id.buttonAction -> listener.onAccept(user.userId)
                            R.id.buttonReject -> listener.onReject(user.userId)
                        }
                    }
                    AdapterType.SEARCH_RESULTS -> {
                        if (v?.id == R.id.buttonAction) {
                            listener.onItemClick(user)
                        }
                    }
                }
            }
        }
    }
}
