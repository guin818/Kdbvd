package com.example.ui.chat

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.GroupMessage
import com.example.ui.auth.signOutUser
import com.example.util.ChatNotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    currentUserId: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val groupsState by viewModel.groupsState.collectAsStateWithLifecycle()
    val activeGroupId by viewModel.activeGroupId.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val senderName by viewModel.senderNameInput.collectAsStateWithLifecycle()
    val messageInput by viewModel.messageInput.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val notifyOnSelfSend by viewModel.notifyOnSelfSend.collectAsStateWithLifecycle()

    var showProfileSheet by remember { mutableStateOf(false) }
    var showGroupsSheet by remember { mutableStateOf(false) }
    var showNotificationSheet by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<GroupMessage?>(null) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf<String?>(null) }
    var activeAlert by remember { mutableStateOf<IncomingNotificationEvent?>(null) }
    var hasNotificationPermission by remember {
        mutableStateOf(ChatNotificationHelper.hasNotificationPermission(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val groups = (groupsState as? UiState.Success)?.data.orEmpty()
    val activeGroup = groups.firstOrNull { it.id == activeGroupId }

    LaunchedEffect(Unit) {
        viewModel.uiFeedback.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.notificationEvents.collect { event ->
            ChatNotificationHelper.showMessageNotification(
                context = context,
                groupId = event.groupId,
                groupName = event.groupName,
                senderName = event.senderName,
                content = event.content,
                notificationKey = event.messageId
            )
            activeAlert = event
            delay(4000)
            if (activeAlert?.messageId == event.messageId) {
                activeAlert = null
            }
        }
    }

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    var lastScrolledGroupId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(messages.size, activeGroupId) {
        if (messages.isNotEmpty()) {
            if (lastScrolledGroupId != activeGroupId) {
                listState.scrollToItem(messages.lastIndex)
                lastScrolledGroupId = activeGroupId
            } else {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Surface(
                        onClick = { showGroupsSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.testTag("open_groups_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = stringResource(R.string.cd_open_groups),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.widthIn(max = 170.dp)) {
                                Text(
                                    text = activeGroup?.name ?: stringResource(R.string.default_room_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (activeGroup != null) {
                                    Text(
                                        text = "${stringResource(R.string.group_member_count_format, activeGroup.memberUids.size)} · ${activeGroup.id}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (activeGroup != null) {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(activeGroup.id))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.group_code_copied))
                                }
                            },
                            modifier = Modifier.testTag("copy_group_code_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.cd_copy_invite_code)
                            )
                        }
                    }
                    IconButton(
                        onClick = { showNotificationSheet = true },
                        modifier = Modifier.testTag("notification_settings_button")
                    ) {
                        Icon(
                            imageVector = if (notificationsEnabled) {
                                Icons.Rounded.NotificationsActive
                            } else {
                                Icons.Rounded.NotificationsNone
                            },
                            contentDescription = stringResource(R.string.cd_notification_settings),
                            tint = if (notificationsEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(
                        onClick = { showProfileSheet = true },
                        modifier = Modifier.testTag("profile_button")
                    ) {
                        AvatarBadge(
                            name = profile.displayName.ifBlank { senderName },
                            photoBase64 = profile.photoBase64,
                            size = 34.dp
                        )
                    }
                }
            )
        },
        bottomBar = {
            MessageComposerBar(
                senderName = senderName,
                messageText = messageInput,
                isSending = isSending,
                nameError = nameError,
                messageError = messageError,
                onSenderNameChange = {
                    nameError = null
                    viewModel.onSenderNameChange(it)
                },
                onMessageTextChange = {
                    messageError = null
                    viewModel.onMessageInputChange(it)
                },
                onSendClick = {
                    val trimmedName = senderName.trim()
                    val trimmedMsg = messageInput.trim()
                    if (trimmedName.isEmpty()) {
                        nameError = context.getString(R.string.sender_name_error_empty)
                        return@MessageComposerBar
                    }
                    if (trimmedMsg.isEmpty()) {
                        messageError = context.getString(R.string.message_error_empty)
                        return@MessageComposerBar
                    }
                    viewModel.sendMessage { err ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(err) }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
            ) {
                if (profile.statusMessage.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${profile.displayName} · ${profile.statusMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.notification_permission_needed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            ) {
                                Text(stringResource(R.string.notification_permission_allow))
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (val state = messagesState) {
                        is UiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.loading_chat),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        is UiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(R.string.error_load_chat),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(onClick = { activeGroupId?.let(viewModel::selectGroup) }) {
                                        Text(stringResource(R.string.retry_button))
                                    }
                                }
                            }
                        }

                        is UiState.Success -> {
                            if (state.data.isEmpty()) {
                                EmptyChatState(modifier = Modifier.fillMaxSize())
                            } else {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(vertical = 12.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("chat_message_list")
                                ) {
                                    items(state.data, key = { it.id }) { msg ->
                                        val isMine = msg.senderId == currentUserId
                                        val isCurrentSpeaker = isMine &&
                                            (senderName.isBlank() || msg.senderName == senderName.trim())
                                        ChatMessageBubble(
                                            message = msg,
                                            isCurrentUserMessage = isMine,
                                            isAlignedRight = isCurrentSpeaker,
                                            onEditClick = { editingMessage = it },
                                            onDeleteClick = { viewModel.deleteMessage(it.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    IncomingAlertBanner(
                        event = activeAlert,
                        onTap = { targetGroupId ->
                            viewModel.selectGroup(targetGroupId)
                            activeAlert = null
                        },
                        onDismiss = { activeAlert = null },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    }

    if (showProfileSheet) {
        ProfileSettingsSheet(
            profile = profile,
            userEmail = userEmail,
            onDismiss = { showProfileSheet = false },
            onSaveProfile = { newName, newStatus, newPhoto ->
                viewModel.saveProfile(newName, newStatus, newPhoto) { success ->
                    if (success) showProfileSheet = false
                }
            },
            onSignOut = {
                showProfileSheet = false
                signOutUser(credentialManager, coroutineScope)
            }
        )
    }

    if (showGroupsSheet) {
        GroupChatSheet(
            groups = groups,
            activeGroupId = activeGroupId,
            currentUserId = currentUserId,
            onDismiss = { showGroupsSheet = false },
            onSelectGroup = viewModel::selectGroup,
            onCreateGroup = { name, desc ->
                viewModel.createGroup(name, desc) { success ->
                    if (success) showGroupsSheet = false
                }
            },
            onJoinGroup = { code ->
                viewModel.joinGroupByCode(code) { success ->
                    if (success) showGroupsSheet = false
                }
            },
            onCopyCode = { code ->
                clipboardManager.setText(AnnotatedString(code))
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.group_code_copied))
                }
            },
            onLeaveGroup = viewModel::leaveGroup
        )
    }

    if (showNotificationSheet) {
        NotificationSettingsSheet(
            notificationsEnabled = notificationsEnabled,
            notifyOnSelfSend = notifyOnSelfSend,
            onToggleNotifications = viewModel::setNotificationsEnabled,
            onToggleNotifyOnSelf = viewModel::setNotifyOnSelfSend,
            onTestNotification = viewModel::sendTestNotification,
            onDismiss = { showNotificationSheet = false }
        )
    }

    editingMessage?.let { target ->
        EditMessageDialog(
            message = target,
            onDismiss = { editingMessage = null },
            onConfirm = { newName, newContent ->
                viewModel.updateMessage(target, newName, newContent)
                editingMessage = null
            }
        )
    }
}

@Composable
private fun EmptyChatState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_chat),
            contentDescription = stringResource(R.string.cd_empty_chat_illustration),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(28.dp))
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.empty_chat_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_chat_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
