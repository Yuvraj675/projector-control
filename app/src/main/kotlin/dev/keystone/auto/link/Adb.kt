package dev.keystone.auto.link

import dadb.AdbKeyPair
import dadb.Dadb
import okio.Buffer
import java.io.File

data class ShellResult(val out: String, val err: String, val code: Int) {
    val ok get() = code == 0
    val text get() = (out + err).trim()
}

/** Anything that can run a shell command on the projector (a real ADB connection, or a fake in tests). */
interface Shell {
    fun run(cmd: String): ShellResult
}

class ProjectorException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A live ADB-over-TCP connection (dadb speaks the ADB protocol itself: no adb binary is needed). */
class AdbConnection private constructor(private val dadb: Dadb, val host: String) : Shell, AutoCloseable {
    override fun run(cmd: String): ShellResult {
        val r = dadb.shell(cmd)
        return ShellResult(r.output, r.errorOutput, r.exitCode)
    }

    fun push(bytes: ByteArray, remotePath: String, mode: Int = 420 /* 0644 */) {
        dadb.push(Buffer().write(bytes), remotePath, mode, System.currentTimeMillis())
    }

    override fun close() {
        try { dadb.close() } catch (_: Exception) { }
    }

    companion object {
        const val DEFAULT_PORT = 5555

        fun connect(
            host: String,
            port: Int = DEFAULT_PORT,
            keyPair: AdbKeyPair? = null,
            connectTimeoutMs: Int = 4000,
            socketTimeoutMs: Int = 20000,
        ): AdbConnection {
            val d = try {
                if (keyPair != null) Dadb.create(host, port, keyPair, connectTimeoutMs, socketTimeoutMs)
                else Dadb.create(host, port, null, connectTimeoutMs, socketTimeoutMs)
            } catch (e: Exception) {
                throw ProjectorException("Could not connect to $host:$port (${e.message})", e)
            }
            // dadb connects lazily: run a trivial command so connection problems surface here.
            val conn = AdbConnection(d, host)
            try {
                val r = conn.run("echo ks-ok")
                if (!r.out.contains("ks-ok")) throw ProjectorException("Unexpected reply from $host: ${r.text}")
            } catch (e: ProjectorException) {
                conn.close(); throw e
            } catch (e: Exception) {
                conn.close(); throw ProjectorException("Could not talk to $host:$port (${e.message})", e)
            }
            return conn
        }
    }
}

/** Keeps an RSA key pair in the app's private storage so projectors that ask "Allow USB debugging?" can remember us. */
fun loadOrCreateKeyPair(dir: File): AdbKeyPair {
    dir.mkdirs()
    val priv = File(dir, "adbkey")
    val pub = File(dir, "adbkey.pub")
    if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
    return AdbKeyPair.read(priv, pub)
}
