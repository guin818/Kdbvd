package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatGroup
import com.example.data.model.GroupMessage
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun getUserProfile(): Result<UserProfile?> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid)
        return try {
            val snapshot = docRef.get().await()
            val profile = if (snapshot.exists()) {
                snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun saveUserProfile(
        displayName: String,
        statusMessage: String,
        photoBase64: String?
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val sanitizedName = displayName.trim().take(40)
        if (sanitizedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Display name must not be blank"))
        }
        val sanitizedStatus = statusMessage.trim().take(100)
        val sanitizedPhoto = photoBase64?.takeIf { it.isNotBlank() && it.length <= 80000 }
        val docRef = db.collection("users").document(uid)
        return try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "displayName" to sanitizedName,
                    "statusMessage" to sanitizedStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (sanitizedPhoto != null) {
                    updates["photoBase64"] = sanitizedPhoto
                } else {
                    updates["photoBase64"] = FieldValue.delete()
                }
                docRef.update(updates).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to sanitizedName,
                    "statusMessage" to sanitizedStatus,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (sanitizedPhoto != null) {
                    payload["photoBase64"] = sanitizedPhoto
                }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun ensureDefaultGroup(defaultName: String, defaultDescription: String): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val groupsPath = "groups"
        return try {
            val existing = db.collection(groupsPath)
                .whereArrayContains("memberUids", uid)
                .limit(1)
                .get()
                .await()
            if (!existing.isEmpty) {
                Result.success(existing.documents.first().id)
            } else {
                createGroup(defaultName, defaultDescription)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, groupsPath, auth)
            Result.failure(e)
        }
    }

    fun observeGroups(userId: String): Flow<List<ChatGroup>> {
        val path = "groups"
        return db.collection(path)
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChatGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                }
                throw error
            }
    }

    suspend fun createGroup(
        name: String,
        description: String = "",
        explicitGroupId: String? = null
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val sanitizedName = name.trim().take(50)
        if (sanitizedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Group name must not be blank"))
        }
        val sanitizedDesc = description.trim().take(120)
        val groupId = explicitGroupId?.trim()?.uppercase()?.takeIf { it.matches(VALID_ID_REGEX) }
            ?: generateInviteCode()
        val docRef = db.collection("groups").document(groupId)
        val payload = mapOf(
            "ownerId" to uid,
            "name" to sanitizedName,
            "description" to sanitizedDesc,
            "memberUids" to listOf(uid),
            "lastMessagePreview" to "",
            "lastSenderName" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(groupId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun joinGroupByCode(code: String): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val normalizedCode = code.trim().uppercase()
        if (!normalizedCode.matches(VALID_ID_REGEX)) {
            return Result.failure(IllegalArgumentException("유효하지 않은 그룹 코드 형식입니다."))
        }
        val docRef = db.collection("groups").document(normalizedCode)
        return try {
            docRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(normalizedCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(groupId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("groups").document(groupId)
        return try {
            docRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    fun observeMessages(userId: String, groupId: String): Flow<List<GroupMessage>> {
        val path = "groups/$groupId/messages"
        return db.collection("groups").document(groupId)
            .collection("messages")
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(GroupMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                }
                throw error
            }
    }

    suspend fun sendMessage(
        groupId: String,
        memberUids: List<String>,
        senderName: String,
        senderStatus: String,
        senderPhotoBase64: String?,
        content: String
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val trimmedName = senderName.trim().take(40)
        val trimmedContent = content.trim().take(1000)
        val trimmedStatus = senderStatus.trim().take(100)
        val sanitizedPhoto = senderPhotoBase64?.takeIf { it.isNotBlank() && it.length <= 80000 }
        if (trimmedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Sender name must not be blank"))
        }
        if (trimmedContent.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message content must not be blank"))
        }
        val effectiveMembers = if (uid in memberUids) memberUids else memberUids + uid
        val docRef = db.collection("groups").document(groupId)
            .collection("messages").document()
        val payload = mutableMapOf<String, Any>(
            "groupId" to groupId,
            "senderId" to uid,
            "senderName" to trimmedName,
            "senderStatus" to trimmedStatus,
            "content" to trimmedContent,
            "memberUids" to effectiveMembers.take(50),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (sanitizedPhoto != null) {
            payload["senderPhotoBase64"] = sanitizedPhoto
        }
        return try {
            docRef.set(payload).await()
            val groupRef = db.collection("groups").document(groupId)
            try {
                groupRef.update(
                    mapOf(
                        "lastMessagePreview" to trimmedContent.take(120),
                        "lastSenderName" to trimmedName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {
                // Non-fatal if group preview update fails after message commit
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateMessage(
        groupId: String,
        message: GroupMessage,
        newSenderName: String,
        newContent: String
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (message.id.isBlank()) {
            return Result.failure(IllegalArgumentException("Message ID must not be blank"))
        }
        val trimmedName = newSenderName.trim().take(40)
        val trimmedContent = newContent.trim().take(1000)
        if (trimmedName.isEmpty() || trimmedContent.isEmpty()) {
            return Result.failure(IllegalArgumentException("Name and content must not be blank"))
        }
        val docRef = db.collection("groups").document(groupId)
            .collection("messages").document(message.id)
        return try {
            docRef.update(
                mapOf(
                    "senderName" to trimmedName,
                    "content" to trimmedContent,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(groupId: String, messageId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (messageId.isBlank()) {
            return Result.failure(IllegalArgumentException("Message ID must not be blank"))
        }
        val docRef = db.collection("groups").document(groupId)
            .collection("messages").document(messageId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) {
            repeat(6) {
                append(chars[Random.nextInt(chars.length)])
            }
        }
    }

    companion object {
        private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")
    }
}
