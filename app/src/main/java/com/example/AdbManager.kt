package com.example

import android.content.Context
import android.util.Base64
import android.util.Log
import com.cgutman.adblib.AdbBase64
import com.cgutman.adblib.AdbConnection
import com.cgutman.adblib.AdbCrypto
import com.cgutman.adblib.AdbStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class AdbManager(private val context: Context) {

    private val TAG = "AdbManager"

    // Connection state
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var socket: Socket? = null
    private var adbConnection: AdbConnection? = null
    private var crypto: AdbCrypto? = null

    private val base64Impl = object : AdbBase64 {
        override fun encodeToString(arg0: ByteArray): String {
            return Base64.encodeToString(arg0, Base64.NO_WRAP)
        }
    }

    init {
        // Run setup in IO context to avoid blocking the main thread
        Log.d(TAG, "Initializing AdbManager")
    }

    /**
     * Initializes keys and crypto.
     */
    private suspend fun initCrypto(): AdbCrypto = withContext(Dispatchers.IO) {
        if (crypto != null) return@withContext crypto!!

        val privKeyFile = File(context.filesDir, "adb_priv.key")
        val pubKeyFile = File(context.filesDir, "adb_pub.key")

        try {
            if (privKeyFile.exists() && pubKeyFile.exists()) {
                Log.d(TAG, "Loading existing ADB keys")
                crypto = AdbCrypto.loadAdbKeyPair(base64Impl, privKeyFile, pubKeyFile)
            } else {
                Log.d(TAG, "Generating new ADB keys")
                val newCrypto = AdbCrypto.generateAdbKeyPair(base64Impl)
                newCrypto.saveAdbKeyPair(privKeyFile, pubKeyFile)
                crypto = newCrypto
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing crypto: ${e.message}", e)
            privKeyFile.delete()
            pubKeyFile.delete()
            val newCrypto = AdbCrypto.generateAdbKeyPair(base64Impl)
            newCrypto.saveAdbKeyPair(privKeyFile, pubKeyFile)
            crypto = newCrypto
        }

        crypto!!
    }

    /**
     * Attempts to connect to localhost public key auth port of wireless debugging.
     */
    suspend fun connect(port: Int): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.Connecting
        closeAll()

        try {
            val adbCrypto = initCrypto()
            Log.d(TAG, "Connecting to local port: $port")
            
            val currentSocket = Socket()
            socket = currentSocket
            currentSocket.connect(InetSocketAddress("127.0.0.1", port), 5000)
            
            val conn = AdbConnection.create(currentSocket, adbCrypto)
            adbConnection = conn
            
            Log.d(TAG, "Starting ADB Handshake")
            conn.connect()
            
            _connectionState.value = ConnectionState.Connected
            Log.i(TAG, "Successfully connected to local ADB")
            true
        } catch (e: Exception) {
            Log.e(TAG, "ADB Connection failed: ${e.message}", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Conexión rechazada o cancelada")
            closeAll()
            false
        }
    }

    /**
     * Disconnects current session
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        closeAll()
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun closeAll() {
        try {
            adbConnection?.close()
        } catch (e: Exception) { /* ignore */ }
        try {
            socket?.close()
        } catch (e: Exception) { /* ignore */ }
        adbConnection = null
        socket = null
    }

    /**
     * Executes a shell command and returns its output.
     */
    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        val conn = adbConnection
        if (conn == null) {
            return@withContext "Error: No conectado a ADB local"
        }

        Log.d(TAG, "Executing ADB command: $command")
        try {
            val stream: AdbStream = conn.open("shell:$command")
            val outputBuilder = java.lang.StringBuilder()
            
            // Read response loop
            while (!stream.isClosed) {
                val data = stream.read()
                if (data != null && data.isNotEmpty()) {
                    outputBuilder.append(String(data, Charsets.UTF_8))
                }
            }
            outputBuilder.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing command '$command': ${e.message}", e)
            "Error al ejecutar comando: ${e.message}"
        }
    }

    /**
     * Returns the public key fingerprint for user validation reference
     */
    suspend fun getPublicKeyString(): String = withContext(Dispatchers.IO) {
        try {
            val pubKeyFile = File(context.filesDir, "adb_pub.key")
            if (pubKeyFile.exists()) {
                pubKeyFile.readText().trim()
            } else {
                "Llave no generada aún"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
