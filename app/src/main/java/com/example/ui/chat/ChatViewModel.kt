package com.example.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatGroup
import com.example.data.model.GroupMessage
import com.example.data.model.UserProfile
import com.example.data.repository.ChatRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class IncomingNotificationEvent(
    val groupId: String,
    val groupName: String,
    val senderName: String,
    val content: String,
    val messageId: String
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository,
    private val currentUserId: String,
    private val defaultDisplayName: String,
    private val defaultRoomName: String,
    private val defaultRoomDescription: String
) : ViewModel() {

    private val _profile = MutableStateFlow(
        UserProfile(
            id = currentUserId,
            userId = currentUserId,
            displayName = defaultDisplayName,
            statusMessage = ""
        )
    )
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _senderNameInput = MutableStateFlow(defaultDisplayName)
    val senderNameInput: StateFlow<String> = _senderNameInput.asStateFlow()

    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    private val _activeGroupId = MutableStateFlow<String?>(null)
    val activeGroupId: StateFlow<String?> = _activeGroupId.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _notifyOnSelfSend = MutableStateFlow(false)
    val notifyOnSelfSend: StateFlow<Boolean> = _notifyOnSelfSend.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(true)

    private val _uiFeedback = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiFeedback: SharedFlow<String> = _uiFeedback.asSharedFlow()

    private val _notificationEvents = MutableSharedFlow<IncomingNotificationEvent>(extraBufferCapacity = 16)
    val notificationEvents: SharedFlow<IncomingNotificationEvent> = _notificationEvents.asSharedFlow()

    private val groupWatcherJobs = mutableMapOf<String, Job>()
    private val seenMessageIdsByGroup = mutableMapOf<String, MutableSet<String>>()
    private val initializedGroups = mutableSetOf<String>()

    val groupsState: StateFlow<UiState<List<ChatGroup>>> = repository.observeGroups(currentUserId)
        .map<List<ChatGroup>, UiState<List<ChatGroup>>> { groups ->
            syncGroupWatchers(groups)
            if (_activeGroupId.value == null || groups.none { it.id == _activeGroupId.value }) {
                _activeGroupId.value = groups.firstOrNull()?.id
            }
            UiState.Success(groups)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing groups", rootError)
            emit(UiState.Error(rootError.message ?: "그룹 목록을 불러오지 못했습니다."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val messagesState: StateFlow<UiState<List<GroupMessage>>> = _activeGroupId
        .flatMapLatest { groupId ->
            if (groupId == null) {
                flowOf(UiState.Loading)
            } else {
                repository.observeMessages(currentUserId, groupId)
                    .map<List<GroupMessage>, UiState<List<GroupMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing messages for $groupId", rootError)
                        emit(UiState.Error(rootError.message ?: "대화 내역을 불러오지 못했습니다."))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    init {
        bootstrapUserSession()
    }

    private fun bootstrapUserSession() {
        viewModelScope.launch {
            repository.getUserProfile().onSuccess { loaded ->
                if (loaded != null) {
                    _profile.value = loaded
                    if (_senderNameInput.value.isBlank() || _senderNameInput.value == defaultDisplayName) {
                        _senderNameInput.value = loaded.displayName
                    }
                } else {
                    val initialName = defaultDisplayName.trim().ifEmpty { "사용자" }
                    repository.saveUserProfile(
                        displayName = initialName,
                        statusMessage = "",
                        photoBase64 = null
                    )
                    _profile.value = UserProfile(
                        id = currentUserId,
                        userId = currentUserId,
                        displayName = initialName,
                        statusMessage = ""
                    )
                }
            }
            repository.ensureDefaultGroup(defaultRoomName, defaultRoomDescription)
                .onSuccess { roomId ->
                    if (_activeGroupId.value == null) {
                        _activeGroupId.value = roomId
                    }
                }
        }
    }

    private fun syncGroupWatchers(groups: List<ChatGroup>) {
        val activeIds = groups.map { it.id }.toSet()
        val removed = groupWatcherJobs.keys.filter { it !in activeIds }
        removed.forEach { id ->
            groupWatcherJobs.remove(id)?.cancel()
            seenMessageIdsByGroup.remove(id)
            initializedGroups.remove(id)
        }

        groups.forEach { group ->
            if (!groupWatcherJobs.containsKey(group.id)) {
                groupWatcherJobs[group.id] = viewModelScope.launch {
                    repository.observeMessages(currentUserId, group.id)
                        .catch { error ->
                            if (error is CancellationException) throw error
                            Log.w(TAG, "Watcher stopped for ${group.id}", error)
                        }
                        .collect { messages ->
                            val seen = seenMessageIdsByGroup.getOrPut(group.id) { mutableSetOf() }
                            if (group.id !in initializedGroups) {
                                messages.forEach { seen.add(it.id) }
                                initializedGroups.add(group.id)
                            } else {
                                val newMessages = messages.filter { it.id.isNotBlank() && seen.add(it.id) }
                                newMessages.forEach { msg ->
                                    val shouldNotify = _notificationsEnabled.value && (
                                        msg.senderId != currentUserId ||
                                            msg.groupId != _activeGroupId.value ||
                                            !_isAppInForeground.value ||
                                            _notifyOnSelfSend.value
                                        )
                                    if (shouldNotify) {
                                        val currentGroupName = (groupsState.value as? UiState.Success)
                                            ?.data?.firstOrNull { it.id == group.id }?.name ?: group.name
                                        _notificationEvents.tryEmit(
                                            IncomingNotificationEvent(
                                                groupId = group.id,
                                                groupName = currentGroupName,
                                                senderName = msg.senderName,
                                                content = msg.content,
                                                messageId = msg.id
                                            )
                                        )
                                    }
                                }
                            }
                        }
                }
            }
        }
    }

    fun setAppForeground(isForeground: Boolean) {
        _isAppInForeground.value = isForeground
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
    }

    fun setNotifyOnSelfSend(enabled: Boolean) {
        _notifyOnSelfSend.value = enabled
    }

    fun onSenderNameChange(value: String) {
        _senderNameInput.value = value.take(40)
    }

    fun onMessageInputChange(value: String) {
        _messageInput.value = value.take(1000)
    }

    fun selectGroup(groupId: String) {
        _activeGroupId.value = groupId
    }

    fun sendMessage(onValidationError: (String) -> Unit = {}) {
        val name = _senderNameInput.value.trim()
        val content = _messageInput.value.trim()
        if (name.isEmpty()) {
            onValidationError("이름을 입력해 주세요.")
            return
        }
        if (content.isEmpty()) {
            onValidationError("대화 내용을 입력해 주세요.")
            return
        }
        val currentGroups = (groupsState.value as? UiState.Success)?.data.orEmpty()
        val targetGroup = currentGroups.firstOrNull { it.id == _activeGroupId.value } ?: return
        if (_isSending.value) return

        viewModelScope.launch {
            _isSending.value = true
            val currentProfile = _profile.value
            val result = repository.sendMessage(
                groupId = targetGroup.id,
                memberUids = targetGroup.memberUids,
                senderName = name,
                senderStatus = currentProfile.statusMessage,
                senderPhotoBase64 = currentProfile.photoBase64,
                content = content
            )
            _isSending.value = false
            result.onSuccess {
                _messageInput.value = ""
            }.onFailure { error ->
                _uiFeedback.tryEmit(error.localizedMessage ?: "메시지 전송에 실패했습니다.")
            }
        }
    }

    fun saveProfile(
        displayName: String,
        statusMessage: String,
        photoBase64: String?,
        onComplete: (Boolean) -> Unit
    ) {
        val trimmedName = displayName.trim().take(40)
        if (trimmedName.isEmpty()) {
            _uiFeedback.tryEmit("프로필 이름을 입력해 주세요.")
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val result = repository.saveUserProfile(
                displayName = trimmedName,
                statusMessage = statusMessage,
                photoBase64 = photoBase64
            )
            result.onSuccess {
                _profile.value = _profile.value.copy(
                    displayName = trimmedName,
                    statusMessage = statusMessage.trim().take(100),
                    photoBase64 = photoBase64
                )
                _senderNameInput.value = trimmedName
                _uiFeedback.tryEmit("프로필이 저장되었습니다.")
                onComplete(true)
            }.onFailure { error ->
                _uiFeedback.tryEmit(error.localizedMessage ?: "프로필 저장에 실패했습니다.")
                onComplete(false)
            }
        }
    }

    fun createGroup(name: String, description: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repository.createGroup(name, description)
            result.onSuccess { newGroupId ->
                _activeGroupId.value = newGroupId
                _uiFeedback.tryEmit("새 그룹이 생성되었습니다. 초대 코드: $newGroupId")
                onComplete(true)
            }.onFailure { error ->
                _uiFeedback.tryEmit(error.localizedMessage ?: "그룹 생성에 실패했습니다.")
                onComplete(false)
            }
        }
    }

    fun joinGroupByCode(code: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repository.joinGroupByCode(code)
            result.onSuccess { joinedId ->
                _activeGroupId.value = joinedId
                _uiFeedback.tryEmit("그룹($joinedId)에 참여했습니다.")
                onComplete(true)
            }.onFailure { error ->
                val message = if (error.message?.contains("NOT_FOUND", ignoreCase = true) == true) {
                    "해당 초대 코드의 그룹을 찾을 수 없습니다."
                } else {
                    error.localizedMessage ?: "그룹 참여에 실패했습니다."
                }
                _uiFeedback.tryEmit(message)
                onComplete(false)
            }
        }
    }

    fun leaveGroup(groupId: String) {
        viewModelScope.launch {
            repository.leaveGroup(groupId).onSuccess {
                _uiFeedback.tryEmit("그룹에서 나갔습니다.")
            }.onFailure { error ->
                _uiFeedback.tryEmit(error.localizedMessage ?: "그룹 나가기에 실패했습니다.")
            }
        }
    }

    fun updateMessage(message: GroupMessage, newSenderName: String, newContent: String) {
        val groupId = _activeGroupId.value ?: return
        viewModelScope.launch {
            repository.updateMessage(groupId, message, newSenderName, newContent)
                .onFailure { error ->
                    _uiFeedback.tryEmit(error.localizedMessage ?: "메시지 수정에 실패했습니다.")
                }
        }
    }

    fun deleteMessage(messageId: String) {
        val groupId = _activeGroupId.value ?: return
        viewModelScope.launch {
            repository.deleteMessage(groupId, messageId)
                .onFailure { error ->
                    _uiFeedback.tryEmit(error.localizedMessage ?: "메시지 삭제에 실패했습니다.")
                }
        }
    }

    fun sendTestNotification() {
        val currentGroups = (groupsState.value as? UiState.Success)?.data.orEmpty()
        val activeGroup = currentGroups.firstOrNull { it.id == _activeGroupId.value }
        _notificationEvents.tryEmit(
            IncomingNotificationEvent(
                groupId = activeGroup?.id ?: "test",
                groupName = activeGroup?.name ?: defaultRoomName,
                senderName = _senderNameInput.value.trim().ifEmpty { _profile.value.displayName },
                content = "실시간 알림이 정상적으로 작동하고 있습니다!",
                messageId = "test_${System.currentTimeMillis()}"
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        groupWatcherJobs.values.forEach { it.cancel() }
        groupWatcherJobs.clear()
    }

    private companion object {
        const val TAG = "ChatVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
