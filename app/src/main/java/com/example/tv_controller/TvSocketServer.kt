package com.example.tv_controller

import android.content.Context
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread
import android.util.Log
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
    private val TAG = "H7_DIAGNOSTICS_SERVER"

    fun startServer() {
        if (isServerRunning) return
        isServerRunning = true
        Log.d(TAG, "Starting TvSocketServer on port $port...")

        thread {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "ServerSocket successfully bound to port $port. Waiting for client connections...")
                while (isServerRunning) {
                    val client = serverSocket?.accept() ?: break
                    Log.d(TAG, "Incoming socket connection accepted from remote address: ${client.remoteSocketAddress}")

                    if (runH7AuthenticationHandshake(client)) {
                        Log.i(TAG, "Handshake PASSED. Client authenticated successfully.")
                        activeClientSocket = client
                        dataOutputStream = DataOutputStream(client.getOutputStream())
                        listenForIncomingClientCommands(client)
                    } else {
                        Log.w(TAG, "Handshake FAILED. Terminating rogue client connection safely.")
                        client.close()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Critical error caught in server master execution loop: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun runH7AuthenticationHandshake(client: Socket): Boolean {
        try {
            val reader = client.getInputStream().bufferedReader()
            val out = client.getOutputStream()

            val random = SecureRandom()
            val nonceBytes = ByteArray(16)
            random.nextBytes(nonceBytes)
            val generatedNonce = nonceBytes.joinToString("") { "%02x".format(it) }
            Log.d(TAG, "Generated secure authentication challenge nonce: $generatedNonce")

            out.write(("AUTH_CHALLENGE:$generatedNonce\n").toByteArray())
            out.flush()
            Log.d(TAG, "AUTH_CHALLENGE line transmitted down the pipeline stream.")

            val incomingClientResponse = reader.readLine()?.trim() ?: ""
            Log.d(TAG, "Received response signature from client: '$incomingClientResponse'")

            val localStoredMasterHash = SecurityVault.getPrefs(context).getString("master_hash", "") ?: ""
            Log.d(TAG, "Local encrypted SharedPreferences base master hash: '$localStoredMasterHash'")

            if (localStoredMasterHash.isEmpty()) {
                Log.e(TAG, "ABORT: Stored master hash is completely EMPTY on this server! Connection cannot pass.")
                return false
            }

            Log.d(TAG, "Beginning 700,000 round crypto iteration block...")
            val messageDigest = MessageDigest.getInstance("SHA-256")
            var combinedPayload = (localStoredMasterHash + generatedNonce).toByteArray()

            for (i in 1..700000) {
                combinedPayload = messageDigest.digest(combinedPayload)
            }
            val calculatedExpectedH7 = combinedPayload.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            Log.d(TAG, "Server-side locally computed target verification hash: '$calculatedExpectedH7'")

            Log.i(TAG, "--- Handshake Validation Profile ---")
            Log.i(TAG, "Client payload: $incomingClientResponse")
            Log.i(TAG, "Server payload: $calculatedExpectedH7")
            Log.i(TAG, "Exact length match: ${incomingClientResponse.length == calculatedExpectedH7.length}")

            if (incomingClientResponse == calculatedExpectedH7) {
                Log.i(TAG, "Match confirmed! Sending AUTH_SUCCESS verification receipt.")
                out.write("AUTH_SUCCESS\n".toByteArray())
                out.write("DEVICE:PA\n".toByteArray())
                out.flush()
                try {
                    val dataOut = DataOutputStream(out)
                    dataOut.writeInt(-1) // Send a safe non-frame ping signaling active line
                    dataOut.flush()
                } catch (e: Exception) { e.printStackTrace() }

                return true
            } else {
                Log.w(TAG, "Mismatch detected! Key signatures do not match.")
                out.write("AUTH_FAILURE\n".toByteArray())
                out.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception encountered during processing gatekeeper handshake: ${e.message}")
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
                    if (commandLine.startsWith("TOUCH:")) {
                        val parts = commandLine.split(":")
                        val percentX = parts[1].toFloatOrNull() ?: 0f
                        val percentY = parts[2].toFloatOrNull() ?: 0f
                        onTouchCommandReceived(percentX, percentY)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception caught within command reader execution loop: ${e.message}")
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
                    outputStream.writeInt(frameBytes.size)
                    outputStream.write(frameBytes)
                    outputStream.flush()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write image frame payload down stream pipeline link: ${e.message}")
                disconnectActiveClient()
            }
        }
    }

    fun disconnectActiveClient() {
        Log.d(TAG, "Disconnecting active host client pipeline structures context connections.")
        try { dataOutputStream?.close() } catch (e: Exception) {}
        try { activeClientSocket?.close() } catch (e: Exception) {}
        activeClientSocket = null
        dataOutputStream = null
    }

    fun stopServer() {
        Log.i(TAG, "Shutting down target server pipeline framework operations.")
        isServerRunning = false
        disconnectActiveClient()
        try { serverSocket?.close() } catch (e: Exception) {}
        serverSocket = null
    }
}