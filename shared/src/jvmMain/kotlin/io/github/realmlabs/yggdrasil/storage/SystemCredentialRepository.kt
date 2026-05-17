package io.github.realmlabs.yggdrasil.storage

import io.github.realmlabs.yggdrasil.domain.model.AppError
import io.github.realmlabs.yggdrasil.domain.model.OperationResult
import io.github.realmlabs.yggdrasil.domain.repository.CredentialRepository
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class SystemCredentialRepository(
    private val serviceName: String = SystemCredentialServiceName,
) : CredentialRepository {
    override suspend fun saveCredential(ref: String, secret: String): OperationResult<Unit> =
        when (currentCredentialPlatform()) {
            CredentialPlatform.MacOs -> runCommand(
                command = listOf(
                    "/usr/bin/security",
                    "add-generic-password",
                    "-s",
                    serviceName,
                    "-a",
                    ref,
                    "-w",
                    secret,
                    "-U",
                ),
                failureMessage = "Could not save credential.",
            ).toUnitResult()

            CredentialPlatform.Windows -> runWindowsCredentialScript(
                script = windowsCredentialInteropScript("Write", readSecretFromStdin = true),
                arguments = listOf("-Ref", ref, "-ServiceName", serviceName),
                input = secret,
                failureMessage = "Could not save credential.",
            ).toUnitResult()

            CredentialPlatform.Linux -> runCommand(
                command = listOf(
                    "secret-tool",
                    "store",
                    "--label=Yggdrasil",
                    "service",
                    serviceName,
                    "ref",
                    ref,
                ),
                input = secret,
                failureMessage = LinuxSecretServiceUnavailable,
            ).toUnitResult()

            CredentialPlatform.Unsupported -> OperationResult.Failure(
                AppError.Storage(CredentialStorageUnsupported),
            )
        }

    override suspend fun readCredential(ref: String): OperationResult<String> =
        when (currentCredentialPlatform()) {
            CredentialPlatform.MacOs -> runCommand(
                command = macOsReadCredentialCommand(serviceName, ref),
                failureMessage = "Could not read credential.",
            ).toStringResult()

            CredentialPlatform.Windows -> runWindowsCredentialScript(
                script = windowsCredentialInteropScript("Read"),
                arguments = listOf("-Ref", ref, "-ServiceName", serviceName),
                failureMessage = "Could not read credential.",
            ).toStringResult()

            CredentialPlatform.Linux -> runCommand(
                command = linuxReadCredentialCommand(serviceName, ref),
                failureMessage = LinuxSecretServiceUnavailable,
            ).toStringResult()

            CredentialPlatform.Unsupported -> OperationResult.Failure(
                AppError.Storage(CredentialStorageUnsupported),
            )
        }

    override suspend fun deleteCredential(ref: String): OperationResult<Unit> =
        when (currentCredentialPlatform()) {
            CredentialPlatform.MacOs -> runCommand(
                command = listOf(
                    "/usr/bin/security",
                    "delete-generic-password",
                    "-s",
                    serviceName,
                    "-a",
                    ref,
                ),
                failureMessage = "Could not delete credential.",
            ).ignoreMissingCredential().toUnitResult()

            CredentialPlatform.Windows -> runWindowsCredentialScript(
                script = windowsCredentialInteropScript("Delete"),
                arguments = listOf("-Ref", ref, "-ServiceName", serviceName),
                failureMessage = "Could not delete credential.",
            ).toUnitResult()

            CredentialPlatform.Linux -> runCommand(
                command = listOf(
                    "secret-tool",
                    "clear",
                    "service",
                    serviceName,
                    "ref",
                    ref,
                ),
                failureMessage = LinuxSecretServiceUnavailable,
            ).ignoreMissingCredential().toUnitResult()

            CredentialPlatform.Unsupported -> OperationResult.Success(Unit)
        }

    private fun runWindowsCredentialScript(
        script: String,
        arguments: List<String>,
        input: String? = null,
        failureMessage: String,
    ): CommandResult {
        val scriptFile = Files.createTempFile("yggdrasil-credential", ".ps1")
        return try {
            Files.writeString(scriptFile, script)
            runCommand(
                command = listOf(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    scriptFile.toAbsolutePath().toString(),
                ) + arguments,
                input = input,
                failureMessage = failureMessage,
            )
        } finally {
            Files.deleteIfExists(scriptFile)
        }
    }
}

internal fun createCredentialAskPassScripts(
    credentialRef: String,
    serviceName: String = SystemCredentialServiceName,
): List<Path> =
    when (currentCredentialPlatform()) {
        CredentialPlatform.MacOs,
        CredentialPlatform.Linux -> {
            val script = Files.createTempFile("yggdrasil-ssh-askpass", ".sh")
            val command = when (currentCredentialPlatform()) {
                CredentialPlatform.MacOs -> macOsReadCredentialCommand(serviceName, credentialRef)
                    .joinToString(" ") { it.shellQuote() }

                else -> linuxReadCredentialCommand(serviceName, credentialRef).joinToString(" ") { it.shellQuote() }
            }
            Files.writeString(
                script,
                """
                    |#!/bin/sh
                    |exec $command
                    |
                """.trimMargin(),
            )
            script.setOwnerExecutable()
            listOf(script)
        }

        CredentialPlatform.Windows -> {
            val ps1 = Files.createTempFile("yggdrasil-ssh-askpass", ".ps1")
            val cmd = Files.createTempFile("yggdrasil-ssh-askpass", ".cmd")
            Files.writeString(
                ps1,
                windowsCredentialInteropScript("Read", refLiteral = credentialRef, serviceNameLiteral = serviceName),
            )
            Files.writeString(
                cmd,
                """
                    |@echo off
                    |powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "${ps1.toAbsolutePath()}"
                    |
                """.trimMargin(),
            )
            listOf(cmd, ps1)
        }

        CredentialPlatform.Unsupported -> throw IOException(CredentialStorageUnsupported)
    }

internal const val SystemCredentialServiceName = "io.github.realmlabs.yggdrasil.credentials"
internal const val CredentialStorageUnsupported = "No supported secure credential store is available."
internal const val LinuxSecretServiceUnavailable = "Linux credential storage requires secret-tool and a running Secret Service."

private enum class CredentialPlatform {
    MacOs,
    Windows,
    Linux,
    Unsupported,
}

private fun currentCredentialPlatform(): CredentialPlatform {
    val osName = System.getProperty("os.name")
    return when {
        osName.contains("Mac", ignoreCase = true) -> CredentialPlatform.MacOs
        osName.contains("Windows", ignoreCase = true) -> CredentialPlatform.Windows
        osName.contains("Linux", ignoreCase = true) -> CredentialPlatform.Linux
        else -> CredentialPlatform.Unsupported
    }
}

private fun macOsReadCredentialCommand(
    serviceName: String,
    credentialRef: String,
): List<String> =
    listOf(
        "/usr/bin/security",
        "find-generic-password",
        "-s",
        serviceName,
        "-a",
        credentialRef,
        "-w",
    )

private fun linuxReadCredentialCommand(
    serviceName: String,
    credentialRef: String,
): List<String> =
    listOf(
        "secret-tool",
        "lookup",
        "service",
        serviceName,
        "ref",
        credentialRef,
    )

private data class CommandResult(
    val exitCode: Int,
    val output: String,
    val failureMessage: String,
)

private fun runCommand(
    command: List<String>,
    input: String? = null,
    failureMessage: String,
): CommandResult =
    try {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        if (input != null) {
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(input)
            }
        } else {
            process.outputStream.close()
        }
        val completed = process.waitFor(10, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText().trim()
        if (!completed) {
            process.destroyForcibly()
            CommandResult(exitCode = -1, output = "credential command timed out.", failureMessage = failureMessage)
        } else {
            CommandResult(exitCode = process.exitValue(), output = output, failureMessage = failureMessage)
        }
    } catch (exception: IOException) {
        CommandResult(exitCode = -1, output = exception.message.orEmpty(), failureMessage = failureMessage)
    } catch (exception: InterruptedException) {
        Thread.currentThread().interrupt()
        CommandResult(exitCode = -1, output = exception.message.orEmpty(), failureMessage = failureMessage)
    }

private fun CommandResult.toUnitResult(): OperationResult<Unit> =
    if (exitCode == 0) {
        OperationResult.Success(Unit)
    } else {
        OperationResult.Failure(AppError.Storage(failureMessage, output.ifBlank { null }))
    }

private fun CommandResult.toStringResult(): OperationResult<String> =
    if (exitCode == 0 && output.isNotEmpty()) {
        OperationResult.Success(output)
    } else {
        OperationResult.Failure(AppError.Storage(failureMessage, output.ifBlank { null }))
    }

private fun CommandResult.ignoreMissingCredential(): CommandResult =
    if (
        exitCode != 0 &&
        (
            output.contains("could not be found", ignoreCase = true) ||
                output.contains("No such secret", ignoreCase = true) ||
                output.contains("Element not found", ignoreCase = true)
            )
    ) {
        copy(exitCode = 0, output = "")
    } else {
        this
    }

private fun Path.setOwnerExecutable() {
    toFile().setReadable(false, false)
    toFile().setWritable(false, false)
    toFile().setExecutable(false, false)
    toFile().setReadable(true, true)
    toFile().setWritable(true, true)
    toFile().setExecutable(true, true)
}

private fun String.shellQuote(): String =
    "'${replace("'", "'\"'\"'")}'"

private fun windowsCredentialInteropScript(
    operation: String,
    readSecretFromStdin: Boolean = false,
    refLiteral: String? = null,
    serviceNameLiteral: String? = null,
): String {
    val parameterBlock = if (refLiteral == null || serviceNameLiteral == null) {
        "param([string]\$Ref, [string]\$ServiceName)"
    } else {
        """
            |${'$'}Ref = ${refLiteral.powerShellSingleQuote()}
            |${'$'}ServiceName = ${serviceNameLiteral.powerShellSingleQuote()}
        """.trimMargin()
    }
    val targetAssignment = "\$target = \"\$ServiceName/\$Ref\""
    val secretAssignment = if (readSecretFromStdin) {
        "\$secret = [Console]::In.ReadToEnd()"
    } else {
        ""
    }
    val call = when (operation) {
        "Write" -> "[YggdrasilCredential]::Write(\$target, \$secret, 'Yggdrasil')"
        "Read" -> "[Console]::Out.Write([YggdrasilCredential]::Read(\$target))"
        "Delete" -> "[YggdrasilCredential]::Delete(\$target)"
        else -> error("Unsupported credential operation: $operation")
    }
    return """
        |$parameterBlock
        |Add-Type @"
        |using System;
        |using System.ComponentModel;
        |using System.Runtime.InteropServices;
        |using System.Text;
        |
        |public static class YggdrasilCredential {
        |    private const UInt32 CRED_TYPE_GENERIC = 1;
        |    private const UInt32 CRED_PERSIST_LOCAL_MACHINE = 2;
        |
        |    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        |    private struct CREDENTIAL {
        |        public UInt32 Flags;
        |        public UInt32 Type;
        |        public string TargetName;
        |        public string Comment;
        |        public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
        |        public UInt32 CredentialBlobSize;
        |        public IntPtr CredentialBlob;
        |        public UInt32 Persist;
        |        public UInt32 AttributeCount;
        |        public IntPtr Attributes;
        |        public string TargetAlias;
        |        public string UserName;
        |    }
        |
        |    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        |    private static extern bool CredWrite(ref CREDENTIAL credential, UInt32 flags);
        |
        |    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        |    private static extern bool CredRead(string target, UInt32 type, Int32 reservedFlag, out IntPtr credentialPtr);
        |
        |    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        |    private static extern bool CredDelete(string target, UInt32 type, Int32 flags);
        |
        |    [DllImport("advapi32.dll", SetLastError = false)]
        |    private static extern void CredFree(IntPtr buffer);
        |
        |    public static void Write(string target, string secret, string userName) {
        |        byte[] bytes = Encoding.UTF8.GetBytes(secret);
        |        var credential = new CREDENTIAL();
        |        credential.Type = CRED_TYPE_GENERIC;
        |        credential.TargetName = target;
        |        credential.UserName = userName;
        |        credential.Persist = CRED_PERSIST_LOCAL_MACHINE;
        |        credential.CredentialBlobSize = (UInt32)bytes.Length;
        |        credential.CredentialBlob = Marshal.AllocCoTaskMem(bytes.Length);
        |        try {
        |            Marshal.Copy(bytes, 0, credential.CredentialBlob, bytes.Length);
        |            if (!CredWrite(ref credential, 0)) throw LastError();
        |        } finally {
        |            Marshal.FreeCoTaskMem(credential.CredentialBlob);
        |        }
        |    }
        |
        |    public static string Read(string target) {
        |        IntPtr credentialPtr;
        |        if (!CredRead(target, CRED_TYPE_GENERIC, 0, out credentialPtr)) throw LastError();
        |        try {
        |            var credential = (CREDENTIAL)Marshal.PtrToStructure(credentialPtr, typeof(CREDENTIAL));
        |            byte[] bytes = new byte[(int)credential.CredentialBlobSize];
        |            Marshal.Copy(credential.CredentialBlob, bytes, 0, bytes.Length);
        |            return Encoding.UTF8.GetString(bytes);
        |        } finally {
        |            CredFree(credentialPtr);
        |        }
        |    }
        |
        |    public static void Delete(string target) {
        |        if (!CredDelete(target, CRED_TYPE_GENERIC, 0)) {
        |            int error = Marshal.GetLastWin32Error();
        |            if (error != 1168) throw new Win32Exception(error);
        |        }
        |    }
        |
        |    private static Exception LastError() {
        |        return new Win32Exception(Marshal.GetLastWin32Error());
        |    }
        |}
        |"@
        |$targetAssignment
        |$secretAssignment
        |$call
    """.trimMargin()
}

private fun String.powerShellSingleQuote(): String =
    "'${replace("'", "''")}'"
