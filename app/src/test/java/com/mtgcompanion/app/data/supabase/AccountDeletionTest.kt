package com.mtgcompanion.app.data.supabase

import org.junit.Assert.assertEquals
import org.junit.Test

/** Delete my account: what the server's answer means, and which profile pictures go with it. */
class AccountDeletionTest {

    @Test
    fun aSuccessfulCallIsADeletion() {
        assertEquals(AccountDeletion.DELETED, accountDeletionOutcome(204, null))
        assertEquals(AccountDeletion.DELETED, accountDeletionOutcome(200, null))
    }

    @Test
    fun aServerWithoutTheFunctionDeletesNothing() {
        assertEquals(AccountDeletion.UNAVAILABLE, accountDeletionOutcome(404, "PGRST202"))
        assertEquals(AccountDeletion.UNAVAILABLE, accountDeletionOutcome(404, null))
    }

    @Test
    fun otherErrorsAreFailures() {
        assertEquals(AccountDeletion.FAILED, accountDeletionOutcome(400, "P0001"))
        assertEquals(AccountDeletion.FAILED, accountDeletionOutcome(401, "PGRST301"))
        assertEquals(AccountDeletion.FAILED, accountDeletionOutcome(500, null))
        assertEquals(AccountDeletion.FAILED, accountDeletionOutcome(404, "42P01"))
    }

    @Test
    fun avatarPathsAreTheFilesInTheUsersFolder() {
        val json = """[
            {"name": "a1.jpg", "id": "11111111-1111-1111-1111-111111111111"},
            {"name": "b2.png", "id": "22222222-2222-2222-2222-222222222222"},
            {"name": "sub", "id": null},
            {"name": "", "id": "33333333-3333-3333-3333-333333333333"}
        ]"""
        assertEquals(listOf("u1/a1.jpg", "u1/b2.png"), avatarPaths("u1", json))
    }

    @Test
    fun anUnreadableListRemovesNothing() {
        assertEquals(emptyList<String>(), avatarPaths("u1", ""))
        assertEquals(emptyList<String>(), avatarPaths("u1", "{\"error\":\"x\"}"))
        assertEquals(emptyList<String>(), avatarPaths("u1", "[]"))
    }
}
