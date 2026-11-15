package com.example.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.GroupMessage
import com.example.util.ImageEncodingHelper
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

private val AvatarPalette = listOf(
    Color(0xFF2B59FF),
    Color(0xFF0D9488),
    Color(0xFFD97706),
    Color(0xFF7C3AED),
    Color(0xFFDB2777),
    Color(0xFF059669)
)

@Composable
fun AvatarBadge(
    name: String,
    photoBase64: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(photoBase64) {
        ImageEncodingHelper.decodeBase64ToImageBitmap(photoBase64)
    }
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val bgColor = remember(name) {
        AvatarPalette[abs(name.hashCode()) % AvatarPalette.size]
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = stringResource(R.string.cd_profile_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initial,
                style = if (size >= 48.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                color = bgColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageBubble(
    message: GroupMessage,
    isCurrentUserMessage: Boolean,
    isAlignedRight: Boolean,
    onEditClick: (GroupMessage) -> Unit,
    onDeleteClick: (GroupMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val timeText = remember(message.createdAt) {
        val ts = message.createdAt ?: Timestamp.now()
        SimpleDateFormat("a h:mm", Locale.KOREA).format(ts.toDate())
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (isAlignedRight) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isAlignedRight) {
            AvatarBadge(
                name = message.senderName,
                photoBase64 = message.senderPhotoBase64,
                size = 36.dp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isAlignedRight) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isAlignedRight) Arrangement.End else Arrangement.Start,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (message.senderStatus.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "· ${message.senderStatus}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box {
                val bubbleShape = if (isAlignedRight) {
                    RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                } else {
                    RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                }

                val containerColor = if (isAlignedRight) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                }
                val textColor = if (isAlignedRight) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

                Surface(
                    shape = bubbleShape,
                    color = containerColor,
                    tonalElevation = if (isAlignedRight) 0.dp else 2.dp,
                    shadowElevation = if (isAlignedRight) 1.dp else 1.dp,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (isCurrentUserMessage) showMenu = true
                        },
                        onLongClick = {
                            if (isCurrentUserMessage) showMenu = true
                        }
                    )
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }

                if (isCurrentUserMessage) {
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEditClick(message)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDeleteClick(message)
                            }
                        )
                    }
                }
            }

            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        if (isAlignedRight) {
            Spacer(modifier = Modifier.width(8.dp))
            AvatarBadge(
                name = message.senderName,
                photoBase64 = message.senderPhotoBase64,
                size = 36.dp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

@Composable
fun MessageComposerBar(
    senderName: String,
    messageText: String,
    isSending: Boolean,
    nameError: String?,
    messageError: String?,
    onSenderNameChange: (String) -> Unit,
    onMessageTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 10.dp)
            )

            OutlinedTextField(
                value = senderName,
                onValueChange = onSenderNameChange,
                singleLine = true,
                isError = nameError != null,
                label = { Text(stringResource(R.string.sender_name_label)) },
                placeholder = { Text(stringResource(R.string.sender_name_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (senderName.isNotEmpty()) {
                        IconButton(onClick = { onSenderNameChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = stringResource(R.string.sender_name_clear_cd)
                            )
                        }
                    }
                },
                supportingText = nameError?.let { err -> { Text(err) } },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sender_name_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = onMessageTextChange,
                    maxLines = 4,
                    isError = messageError != null,
                    placeholder = { Text(stringResource(R.string.message_input_placeholder)) },
                    supportingText = messageError?.let { err -> { Text(err) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendClick() }),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("message_input")
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onSendClick,
                    enabled = !isSending,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .heightIn(min = 54.dp)
                        .testTag("send_button")
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.send_button_cd),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.send_button_label),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IncomingAlertBanner(
    event: IncomingNotificationEvent?,
    onTap: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = event != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        if (event != null) {
            Surface(
                onClick = { onTap(event.groupId) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${event.groupName} · ${event.senderName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = event.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
