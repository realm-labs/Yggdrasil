package io.github.realmlabs.yggdrasil.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConnectionProfileDraftTest {
    @Test
    fun createsReadOnlyProfileFromValidDraft() {
        val result = ConnectionProfileDraft(
            name = " Local ",
            connectionString = " localhost:2181 ",
            chroot = " /app ",
        ).toProfile(ConnectionId("local"))

        val profile = assertIs<OperationResult.Success<ConnectionProfile>>(result).value
        assertEquals("Local", profile.name)
        assertEquals("localhost:2181", profile.connectionString)
        assertEquals("/app", profile.chroot?.value)
        assertEquals(ConnectionMode.ReadOnly, profile.mode)
    }

    @Test
    fun rejectsBlankConnectionString() {
        val result = ConnectionProfileDraft(
            name = "Local",
            connectionString = "",
        ).toProfile(ConnectionId("local"))

        assertIs<OperationResult.Failure>(result)
    }

    @Test
    fun rejectsInvalidChrootPath() {
        val result = ConnectionProfileDraft(
            name = "Local",
            connectionString = "localhost:2181",
            chroot = "app",
        ).toProfile(ConnectionId("local"))

        assertIs<OperationResult.Failure>(result)
    }
}
