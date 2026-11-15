package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val statusMessage: String = "",
    val photoBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatGroup(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    val description: String = "",
    val memberUids: List<String> = emptyList(),
    val lastMessagePreview: String = "",
    val lastSenderName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GroupMessage(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderStatus: String = "",
    val senderPhotoBase64: String? = null,
    val content: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
