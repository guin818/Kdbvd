package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createGroupJoinAndSendMessage_multiUserRealtimeFlow_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ChatRepository(firestore, auth)
        val groupCode = "GRP${UUID.randomUUID().toString().take(6).uppercase()}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createGroup(name = "팀 채팅방", description = "실시간 그룹 대화", explicitGroupId = groupCode)
        }
        assertTrue("Expected group creation to succeed: ${createResult.exceptionOrNull()}", createResult.isSuccess)

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = ChatRepository(firestore, auth)
        val joinResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.joinGroupByCode(groupCode)
        }
        assertTrue("Expected Bob to join group: ${joinResult.exceptionOrNull()}", joinResult.isSuccess)

        val bobProfileResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.saveUserProfile(displayName = "영희", statusMessage = "회의 중입니다", photoBase64 = "dGVzdA==")
        }
        assertTrue("Expected profile save to succeed: ${bobProfileResult.exceptionOrNull()}", bobProfileResult.isSuccess)

        val uniqueText = "안녕하세요 모두! ${UUID.randomUUID()}"
        val sendResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.sendMessage(
                groupId = groupCode,
                memberUids = listOf(aliceUid, bobUid),
                senderName = "영희",
                senderStatus = "회의 중입니다",
                senderPhotoBase64 = "dGVzdA==",
                content = uniqueText
            )
        }
        assertTrue("Expected sendMessage to succeed: ${sendResult.exceptionOrNull()}", sendResult.isSuccess)
        val messageId = sendResult.getOrThrow()

        // Alice observes Bob's message in real time
        signInTestUser(ALICE_EMAIL)
        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(aliceUid, groupCode).first { list -> list.any { it.id == messageId } }
        }
        val saved = messages.first { it.id == messageId }
        assertEquals("영희", saved.senderName)
        assertEquals("회의 중입니다", saved.senderStatus)
        assertEquals(uniqueText, saved.content)
    }

    @Test
    fun sendMessage_missingParentGroup_failsWithPermissionDenied() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = ChatRepository(firestore, auth)
        val missingGroupId = "MISSING_${UUID.randomUUID().toString().take(8)}"

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.sendMessage(
                groupId = missingGroupId,
                memberUids = listOf(uid),
                senderName = "민수",
                senderStatus = "",
                senderPhotoBase64 = null,
                content = "Orphan"
            )
        }
        assertTrue(result.isFailure)
        val firestoreEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got ${result.exceptionOrNull()}", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeMessages_nonMemberAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ChatRepository(firestore, auth)
        val groupCode = "PRIV${UUID.randomUUID().toString().take(6).uppercase()}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createGroup("비밀방", explicitGroupId = groupCode).getOrThrow()
            aliceRepo.sendMessage(groupCode, listOf(aliceUid), "Alice", "", null, "Private").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = ChatRepository(firestore, auth)
        val exception = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeMessages(aliceUid, groupCode).first()
            }
            null
        } catch (e: Exception) {
            e
        }
        assertNotNull("Expected PERMISSION_DENIED exception for non-member read", exception)
        val firestoreEx = exception?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $exception", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeGroups_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = ChatRepository(firestore, auth)

        val exception = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeGroups(aliceUid).first()
            }
            null
        } catch (e: Exception) {
            e
        }
        assertNotNull("Expected PERMISSION_DENIED exception for unauthenticated read", exception)
        val firestoreEx = exception?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $exception", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_group@test.com"
        const val BOB_EMAIL = "bob_group@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
