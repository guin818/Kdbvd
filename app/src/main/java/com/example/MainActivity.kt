package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.ChatRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.theme.ChattingTalkTheme
import com.example.util.ChatNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {

    private val pendingNotificationGroupId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingNotificationGroupId.value = intent?.getStringExtra(ChatNotificationHelper.EXTRA_GROUP_ID)

        setContent {
            ChattingTalkTheme {
                AppContent(
                    pendingGroupId = pendingNotificationGroupId.value,
                    onPendingGroupHandled = { pendingNotificationGroupId.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationGroupId.value = intent.getStringExtra(ChatNotificationHelper.EXTRA_GROUP_ID)
    }
}

@Composable
fun AppContent(
    pendingGroupId: String?,
    onPendingGroupHandled: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var initializedDeps by remember { mutableStateOf<Pair<FirebaseAuth, FirebaseFirestore>?>(null) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        val deps = withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            ChatNotificationHelper.ensureChannel(appContext)
            val firebaseAuth = Firebase.auth
            val databaseId = appContext.getString(R.string.firestore_database_id)
            val firestore = FirebaseFirestore.getInstance(databaseId)
            firebaseAuth to firestore
        }
        initializedDeps = deps

        if (deps.first.currentUser == null) {
            delay(350)
            val credentialManager = CredentialManager.create(context)
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = coroutineScope
            )
        }
    }

    val deps = initializedDeps
    if (deps == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val (auth, firestore) = deps
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        val uid = user.uid
        val initialDisplayName = user.displayName?.takeIf { it.isNotBlank() } ?: "사용자"
        key(uid) {
            val viewModel: ChatViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        ChatViewModel(
                            repository = ChatRepository(firestore, auth),
                            currentUserId = uid,
                            defaultDisplayName = initialDisplayName,
                            defaultRoomName = app.getString(R.string.default_room_name),
                            defaultRoomDescription = app.getString(R.string.default_room_desc)
                        )
                    }
                }
            )

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, viewModel) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> viewModel.setAppForeground(true)
                        Lifecycle.Event.ON_STOP -> viewModel.setAppForeground(false)
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(pendingGroupId) {
                if (!pendingGroupId.isNullOrBlank()) {
                    viewModel.selectGroup(pendingGroupId)
                    onPendingGroupHandled()
                }
            }

            ChatScreen(
                viewModel = viewModel,
                currentUserId = uid,
                userEmail = user.email
            )
        }
    }
}
