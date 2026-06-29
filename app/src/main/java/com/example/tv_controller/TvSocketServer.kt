package com.example.tv_controller

import android.content.Context
import android.util.Log
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

    private val TAG = "H7_DIAGNOSTICS_SERVER"
    private var serverSocket: ServerSocket? = null
    private var isServerRunning = false
    private var activeClientSocket: Socket? = null
    private var dataOutputStream: DataOutputStream? = null

//    called in TvMediaProjectionService.kt . startCoreHosterEngine
    fun startServer() {
        if (isServerRunning) return
        isServerRunning = true
        Log.d(TAG, "Starting TvSocketServer on port $port...")

        thread {
            try {
                serverSocket = ServerSocket(port)
                while (isServerRunning) {
                    val client = serverSocket?.accept() ?: break
                    Log.d(TAG, "Incoming connection from: ${client.remoteSocketAddress}")

                    if (runH7AuthenticationHandshake(client)) {
                        activeClientSocket = client
                        dataOutputStream = DataOutputStream(client.getOutputStream())

                        // Blocks until client disconnects or minimizes
                        listenForIncomingClientCommands(client)
                    } else {
                        client.close()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server Exception: ${e.message}")
            }
        }
    }

//    called in startServer
    private fun runH7AuthenticationHandshake(client: Socket): Boolean {
        try {
            val reader = client.getInputStream().bufferedReader()
            val out = client.getOutputStream()

            val random = SecureRandom()
            val nonceBytes = ByteArray(16)
            random.nextBytes(nonceBytes)
            val generatedNonce = nonceBytes.joinToString("") { "%02x".format(it) }

            out.write(("AUTH_CHALLENGE:$generatedNonce\n").toByteArray())
            out.flush()

            val incomingClientResponse = reader.readLine()?.trim() ?: ""
            val localStoredMasterHash = SecurityVault.getPrefs(context).getString("master_hash", "") ?: ""

            val messageDigest = MessageDigest.getInstance("SHA-256")
            var combinedPayload = (localStoredMasterHash + generatedNonce).toByteArray()

            for (i in 1..700000) {
                combinedPayload = messageDigest.digest(combinedPayload)
            }
            val calculatedExpectedH7 = combinedPayload.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

            if (incomingClientResponse == calculatedExpectedH7) {
                out.write("AUTH_SUCCESS\n".toByteArray())
                out.write("DEVICE:PA\n".toByteArray())
                out.flush()
                return true
            }
        } catch (e: Exception) { e.printStackTrace() }
        return false
    }

//     called in startServer
    private fun listenForIncomingClientCommands(client: Socket) {
        try {
            val reader = client.getInputStream().bufferedReader()
            while (isServerRunning && !client.isClosed) {
                val commandLine = reader.readLine() ?: break // Hits break if client loses focus/closes!
                while (isServerRunning && !client.isClosed) {
                    val commandLine = reader.readLine() ?: break

                    val parts = commandLine.split(":")
                    if (parts.size == 3) {
                        val action = parts[0] // Contains "DOWN", "MOVE", or "UP"
                        val percentX = parts[1].toFloatOrNull() ?: 0f
                        val percentY = parts[2].toFloatOrNull() ?: 0f

                        // Route directly to your new structural gesture engine
                        TvAccessibilityService.handleRemoteTouch(action, percentX, percentY)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection interrupted: ${e.message}")
        } finally {
            // --- THE CRITICAL FIX ---
            // If the client disconnects or leaves, instantly free up the connection channel!
            disconnectActiveClient()
        }
    }

//    called in TvMediaProjectionService.kt . startImageProcessingLoop
    fun sendFrameToClient(frameBytes: ByteArray) {
        val outputStream = dataOutputStream
        if (outputStream != null && activeClientSocket?.isClosed == false) {
            thread {
                try {
                    outputStream.writeInt(frameBytes.size)
                    outputStream.write(frameBytes)
                    outputStream.flush()
                } catch (e: IOException) {
                    disconnectActiveClient()
                }
            }
        }
    }

//    called in listenForIncomingClientCommands , sendFrameToClient , stopServer
    fun disconnectActiveClient() {
        try { dataOutputStream?.close() } catch (e: Exception) {}
        try { activeClientSocket?.close() } catch (e: Exception) {}
        activeClientSocket = null
        dataOutputStream = null
        Log.d(TAG, "Active client link recycled cleanly. Port 9999 ready for fresh reconnection loops.")
    }

//    called in TvMediaProjectionService.kt . onDestroy
    fun stopServer() {
        isServerRunning = false
        disconnectActiveClient()
        try { serverSocket?.close() } catch (e: Exception) {}
        serverSocket = null
    }
}