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

    @Test
    fun createsProfileWithSshTunnel() {
        val result = ConnectionProfileDraft(
            name = "Remote",
            connectionString = "zk.internal:2181",
            sshTunnelEnabled = true,
            sshHost = "bastion.example.com",
            sshUsername = "deploy",
            sshPort = "2222",
            sshIdentityFile = "~/.ssh/id_ed25519",
        ).toProfile(ConnectionId("remote"))

        val profile = assertIs<OperationResult.Success<ConnectionProfile>>(result).value
        assertEquals("bastion.example.com", profile.sshTunnel?.host)
        assertEquals("deploy", profile.sshTunnel?.username)
        assertEquals(2222, profile.sshTunnel?.port)
        assertEquals("~/.ssh/id_ed25519", profile.sshTunnel?.identityFile)
        assertEquals(SshAuthenticationMethod.PublicKey, profile.sshTunnel?.authenticationMethod)
        assertEquals("ssh:deploy@bastion.example.com:2222:publickey", profile.sshTunnel?.credentialRef)
    }

    @Test
    fun createsProfileWithSavedSshPasswordReference() {
        val result = ConnectionProfileDraft(
            name = "Remote",
            connectionString = "zk.internal:2181",
            sshTunnelEnabled = true,
            sshHost = "bastion.example.com",
            sshUsername = "deploy",
            sshAuthenticationMethod = SshAuthenticationMethod.Password,
            sshSecret = "secret",
        ).toProfile(ConnectionId("remote"))

        val profile = assertIs<OperationResult.Success<ConnectionProfile>>(result).value
        assertEquals(SshAuthenticationMethod.Password, profile.sshTunnel?.authenticationMethod)
        assertEquals("ssh:deploy@bastion.example.com:22:password", profile.sshTunnel?.credentialRef)
    }

    @Test
    fun rejectsIncompleteSshTunnel() {
        val result = ConnectionProfileDraft(
            name = "Remote",
            connectionString = "zk.internal:2181",
            sshTunnelEnabled = true,
            sshHost = "bastion.example.com",
        ).toProfile(ConnectionId("remote"))

        assertIs<OperationResult.Failure>(result)
    }

    @Test
    fun rejectsPublicKeySshTunnelWithoutIdentityFile() {
        val result = ConnectionProfileDraft(
            name = "Remote",
            connectionString = "zk.internal:2181",
            sshTunnelEnabled = true,
            sshHost = "bastion.example.com",
            sshUsername = "deploy",
        ).toProfile(ConnectionId("remote"))

        assertIs<OperationResult.Failure>(result)
    }
}
