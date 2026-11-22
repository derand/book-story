/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.permission

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.acclorite.book_story.domain.repository.PermissionRepository

/**
 * The settings screen refreshes Browse only after a grant that changed, and
 * says so when it did not: the use cases have to tell the two apart.
 */
@RunWith(RobolectricTestRunner::class)
class PersistableUriPermissionUseCaseTest {

    private class FakePermissions(private val fails: Boolean) : PermissionRepository {
        val calls = mutableListOf<String>()

        private fun answer(call: String): Result<Unit> {
            calls += call
            return if (fails) Result.failure(SecurityException("No persistable grant offered."))
            else Result.success(Unit)
        }

        override suspend fun grantPersistableUriPermission(uri: String) = answer("grant $uri")
        override suspend fun releasePersistableUriPermission(uri: String) = answer("release $uri")
    }

    private val uri = "content://test/tree/books"

    @Test
    fun aGrantThatWorkedSaysSo() = runBlocking {
        val permissions = FakePermissions(fails = false)

        assertEquals(true, GrantPersistableUriPermissionUseCase(permissions)(uri))
        assertEquals(listOf("grant $uri"), permissions.calls)
    }

    @Test
    fun aGrantTheProviderRefusedSaysSo() = runBlocking {
        assertEquals(false, GrantPersistableUriPermissionUseCase(FakePermissions(fails = true))(uri))
    }

    @Test
    fun aReleaseReportsItsResultToo() = runBlocking {
        assertEquals(true, ReleasePersistableUriPermissionUseCase(FakePermissions(fails = false))(uri))
        assertEquals(false, ReleasePersistableUriPermissionUseCase(FakePermissions(fails = true))(uri))
    }
}
