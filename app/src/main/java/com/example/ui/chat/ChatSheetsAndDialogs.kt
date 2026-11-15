package com.example.ui.chat

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ChatGroup
import com.example.data.model.GroupMessage
import com.example.data.model.UserProfile
import com.example.util.ChatNotificationHelper
import com.example.util.ImageEncodingHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsSheet(
    profile: UserProfile,
    userEmail: String?,
    onDismiss: () -> Unit,
    onSaveProfile: (displayName: String, statusMessage: String, photoBase64: String?) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var displayName by remember(profile) { mutableStateOf(profile.displayName) }
    var statusMessage by remember(profile) { mutableStateOf(profile.statusMessage) }
    var photoBase64 by remember(profile) { mutableStateOf(profile.photoBase64) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = ImageEncodingHelper.encodeProfilePhotoToBase64(context, uri)
            if (encoded != null) {
                photoBase64 = encoded
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!userEmail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Google 계정 연동됨 · $userEmail",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AvatarBadge(
                    name = displayName.ifBlank { "?" },
                    photoBase64 = photoBase64,
                    size = 72.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("pick_profile_photo_button")
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.profile_photo_pick))
                    }
                    if (!photoBase64.isNullOrBlank()) {
                        TextButton(onClick = { photoBase64 = null }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.profile_photo_remove))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it.take(40) },
                singleLine = true,
                label = { Text(stringResource(R.string.profile_name_label)) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_name_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = statusMessage,
                onValueChange = { statusMessage = it.take(100) },
                singleLine = true,
                label = { Text(stringResource(R.string.profile_status_label)) },
                placeholder = { Text(stringResource(R.string.profile_status_placeholder)) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_status_input")
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onSaveProfile(displayName, statusMessage, photoBase64) },
                enabled = displayName.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("profile_save_button")
            ) {
                Text(stringResource(R.string.action_save))
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.auth_sign_out))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatSheet(
    groups: List<ChatGroup>,
    activeGroupId: String?,
    currentUserId: String,
    onDismiss: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onCreateGroup: (name: String, description: String) -> Unit,
    onJoinGroup: (code: String) -> Unit,
    onCopyCode: (String) -> Unit,
    onLeaveGroup: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.group_list_title),
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("create_group_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.group_create_button))
                }
                FilledTonalButton(
                    onClick = { showJoinDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("join_group_button")
                ) {
                    Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.group_join_button))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 360.dp)
            ) {
                items(groups, key = { it.id }) { group ->
                    val isSelected = group.id == activeGroupId
                    Card(
                        onClick = {
                            onSelectGroup(group.id)
                            onDismiss()
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = if (group.lastMessagePreview.isNotBlank()) {
                                    "${group.lastSenderName}: ${group.lastMessagePreview}"
                                } else {
                                    group.description.ifBlank { stringResource(R.string.group_member_count_format, group.memberUids.size) }
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${stringResource(R.string.group_invite_code_format, group.id)} · ${stringResource(R.string.group_member_count_format, group.memberUids.size)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onCopyCode(group.id) }) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(R.string.cd_copy_invite_code),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (group.ownerId != currentUserId) {
                                IconButton(onClick = { onLeaveGroup(group.id) }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                                        contentDescription = stringResource(R.string.group_leave_button),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.group_create_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(50) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.group_name_label)) },
                        placeholder = { Text(stringResource(R.string.group_name_placeholder)) },
                        modifier = Modifier.testTag("new_group_name_input")
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it.take(120) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.group_desc_label)) },
                        placeholder = { Text(stringResource(R.string.group_desc_placeholder)) }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateGroup(name, desc)
                        showCreateDialog = false
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag("confirm_create_group_button")
                ) {
                    Text(stringResource(R.string.group_create_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showJoinDialog) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text(stringResource(R.string.group_join_title)) },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(32) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.group_code_label)) },
                    placeholder = { Text(stringResource(R.string.group_code_placeholder)) },
                    modifier = Modifier.testTag("join_group_code_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onJoinGroup(code)
                        showJoinDialog = false
                    },
                    enabled = code.isNotBlank(),
                    modifier = Modifier.testTag("confirm_join_group_button")
                ) {
                    Text(stringResource(R.string.group_join_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsSheet(
    notificationsEnabled: Boolean,
    notifyOnSelfSend: Boolean,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleNotifyOnSelf: (Boolean) -> Unit,
    onTestNotification: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ChatNotificationHelper.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.notification_settings_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.notification_permission_needed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        ) {
                            Text(stringResource(R.string.notification_permission_allow))
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.notification_enable_label),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.notification_enable_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = onToggleNotifications
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.notification_include_self_label),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.notification_include_self_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notifyOnSelfSend,
                    onCheckedChange = onToggleNotifyOnSelf
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            FilledTonalButton(
                onClick = {
                    if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onTestNotification()
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("test_notification_button")
            ) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.notification_test_button))
            }
        }
    }
}

@Composable
fun EditMessageDialog(
    message: GroupMessage,
    onDismiss: () -> Unit,
    onConfirm: (newSenderName: String, newContent: String) -> Unit
) {
    var senderName by remember(message) { mutableStateOf(message.senderName) }
    var content by remember(message) { mutableStateOf(message.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_message_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = senderName,
                    onValueChange = { senderName = it.take(40) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.sender_name_label)) }
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it.take(1000) },
                    maxLines = 4,
                    label = { Text(stringResource(R.string.message_input_placeholder)) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(senderName, content) },
                enabled = senderName.isNotBlank() && content.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
