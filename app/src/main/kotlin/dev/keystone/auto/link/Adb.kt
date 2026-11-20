package dev.keystone.auto.link

import dadb.AdbKeyPair
import dadb.Dadb
import okio.Buffer
import okio.source
import java.io.File

data class ShellResult(val out: String, val err: String, val code: Int) {
    val ok get() = code == 0
    val text get() = (out + err).trim()
}

/** A long-running shell command whose standard input we keep writing to (used for the live input devices). */
interface ShellStream : AutoCloseable {
    fun write(text: String)
    val isOpen: Boolean
}

/** Anything that can run a shell command on the projector (a real ADB connection, or a fake in tests). */
interface Shell {
    fun run(cmd: String): ShellResult

    /** Starts `cmd` and keeps its stdin open for [ShellStream.write]. */
    fun openStream(cmd: String): ShellStream = throw ProjectorException("Streaming is not supported by this connection.")

    /** Copies a stream to a file on the projector. */
    fun push(input: java.io.InputStream, remotePath: String): Unit = throw ProjectorException("File transfer is not supported by this connection.")

    /** Reads a file from the projector. */
    fun pull(remotePath: String): ByteArray = throw ProjectorException("File transfer is not supported by this connection.")
}

class ProjectorException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A live ADB-over-TCP connection (dadb speaks the ADB protocol itself: no adb binary is needed). */
class AdbConnection private constructor(private val dadb: Dadb, val host: String) : Shell, AutoCloseable {
    override fun run(cmd: String): ShellResult {
        val r = dadb.shell(cmd)
        return ShellResult(r.output, r.errorOutput, r.exitCode)
    }

    override fun openStream(cmd: String): ShellStream {
        val sh = dadb.openShell(cmd)
        val open = java.util.concurrent.atomic.AtomicBoolean(true)
        // Nobody needs the command's output, but it must be drained or the stream stalls.
        Thread {
            try { while (open.get()) { if (sh.read() is dadb.AdbShellPacket.Exit) break } } catch (_: Exception) { }
            open.set(false)
        }.apply { isDaemon = true; name = "adb-stream-drain" }.start()
        return object : ShellStream {
            override val isOpen get() = open.get()
            override fun write(text: String) {
                try { sh.write(text) } catch (e: Exception) { open.set(false); throw ProjectorException("The connection to the projector dropped.", e) }
            }
            override fun close() { open.set(false); try { sh.close() } catch (_: Exception) { } }
        }
    }

    override fun push(input: java.io.InputStream, remotePath: String) {
        input.use { dadb.push(it.source(), remotePath, 420, System.currentTimeMillis()) }
    }

    override fun pull(remotePath: String): ByteArray {
        val buf = Buffer()
        dadb.pull(buf, remotePath)
        return buf.readByteArray()
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
