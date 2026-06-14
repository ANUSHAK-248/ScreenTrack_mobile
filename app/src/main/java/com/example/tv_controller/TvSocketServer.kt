package com.example.tv_controller

import android.content.Context
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread

class TvSocketServer(
    private val context: Context,
    private val port: Int = 9999,
    private val onTouchCommandReceived: (Float, Float) -> Unit
) {

    private var serverSocket: ServerSocket? = null
    private var isServerRunning = false
    private var activeClientSocket: Socket? = null
    private var dataOutputStream: DataOutputStream? = null

    fun startServer() {
        if (isServerRunning) return
        isServerRunning = true

        thread {
            try {
                serverSocket = ServerSocket(port)
                while (isServerRunning) {
                    val client = serverSocket?.accept() ?: break

                    // 1. Force the H7 cryptographic gatekeeper verification check
                    if (runH7AuthenticationHandshake(client)) {
                        activeClientSocket = client
                        dataOutputStream = DataOutputStream(client.getOutputStream())

                        // 2. Launch background command listener loop
                        listenForIncomingClientCommands(client)
                    } else {
                        client.close()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun runH7AuthenticationHandshake(client: Socket): Boolean {
        try {
            val reader = client.getInputStream().bufferedReader()
            val out = client.getOutputStream()

            // Generate 16-byte cryptographically secure random Nonce challenge
            val random = SecureRandom()
            val nonceBytes = ByteArray(16)
            random.nextBytes(nonceBytes)
            val generatedNonce = nonceBytes.joinToString("") { "%02x".format(it) }

            // Transmit challenge code down the stream
            out.write(("AUTH_CHALLENGE:$generatedNonce\n").toByteArray())
            out.flush()

            val incomingClientResponse = reader.readLine() ?: ""
            val localStoredMasterHash = SecurityVault.getPrefs(context).getString("master_hash", "") ?: ""

            // --- LOCAL H7 LOOP VERIFICATION WORKER ---
            val messageDigest = MessageDigest.getInstance("SHA-256")
            var combinedPayload = (localStoredMasterHash + generatedNonce).toByteArray()

            for (i in 1..700000) {
                combinedPayload = messageDigest.digest(combinedPayload)
            }
            val calculatedExpectedH7 = combinedPayload.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

            // Validate match profile parameters
            if (incomingClientResponse == calculatedExpectedH7) {
                out.write("AUTH_SUCCESS\n".toByteArray())

                // CRITICAL ROUTING IDENTIFIER: Tells your phone app this is a TV!
                out.write("DEVICE:PA\n".toByteArray())
                out.flush()
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun listenForIncomingClientCommands(client: Socket) {
        thread {
            try {
                val reader = client.getInputStream().bufferedReader()
                while (isServerRunning && !client.isClosed) {
                    val commandLine = reader.readLine() ?: break

                    // Intercept and parse incoming touch strings: "TOUCH:0.45:0.72"
                    if (commandLine.startsWith("TOUCH:")) {
                        val parts = commandLine.split(":")
                        val percentX = parts[1].toFloatOrNull() ?: 0f
                        val percentY = parts[2].toFloatOrNull() ?: 0f

                        // Pass percentages back to the callback up to the system service executor
                        onTouchCommandReceived(percentX, percentY)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                disconnectActiveClient()
            }
        }
    }

    // Call this method safely from your ImageReader listener inside TvMediaProjectionService
    fun sendFrameToClient(frameBytes: ByteArray) {
        thread {
            try {
                val outputStream = dataOutputStream
                if (outputStream != null && activeClientSocket?.isClosed == false) {
                    // Wrap payload with sizing metrics for safe stream framing decoding
                    outputStream.writeInt(frameBytes.size)
                    outputStream.write(frameBytes)
                    outputStream.flush()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                disconnectActiveClient()
            }
        }
    }

    fun disconnectActiveClient() {
        try {
            dataOutputStream?.close()
            activeClientSocket?.close()
        } catch (e: Exception) { e.printStackTrace() }
        activeClientSocket = null
        dataOutputStream = null
    }

    fun stopServer() {
        isServerRunning = false
        disconnectActiveClient()
        try {
            serverSocket?.close()
        } catch (e: Exception) { e.printStackTrace() }
        serverSocket = null
    }
}