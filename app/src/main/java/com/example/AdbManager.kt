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
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

class AdbManager(private val context: Context) {

    private val TAG = "AdbManager"

    // Working modes
    enum class WorkingMode {
        ADB_WIRELESS,
        ROOT_SU,
        SIMULATED
    }

    // Connection state
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _workingMode = MutableStateFlow<WorkingMode>(WorkingMode.SIMULATED) // Default to simulated for preview ease
    val workingMode: StateFlow<WorkingMode> = _workingMode.asStateFlow()

    private var socket: Socket? = null
    private var adbConnection: AdbConnection? = null
    private var crypto: AdbCrypto? = null

    // Simulated resolution states
    var simPhysicalWidth = 1080
    var simPhysicalHeight = 2400
    var simPhysicalDensity = 440

    var simCurrentWidth = 1080
    var simCurrentHeight = 2400
    var simCurrentDensity = 440

    private val base64Impl = object : AdbBase64 {
        override fun encodeToString(arg0: ByteArray): String {
            return Base64.encodeToString(arg0, Base64.NO_WRAP)
        }
    }

    init {
        Log.d(TAG, "Initializing AdbManager")
        // Initialize simulated fields with real display metrics if possible
        try {
            val dm = context.resources.displayMetrics
            simPhysicalWidth = dm.widthPixels
            simPhysicalHeight = dm.heightPixels
            simPhysicalDensity = dm.densityDpi
            simCurrentWidth = dm.widthPixels
            simCurrentHeight = dm.heightPixels
            simCurrentDensity = dm.densityDpi
        } catch (e: Exception) {
            Log.e(TAG, "Failed using local metrics", e)
        }
    }

    fun setWorkingMode(mode: WorkingMode) {
        _workingMode.value = mode
        closeAll()
        if (mode == WorkingMode.SIMULATED || mode == WorkingMode.ROOT_SU) {
            _connectionState.value = ConnectionState.Connected
        } else {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    /**
     * Instantly checks if SU binary is available for Root connection.
     */
    fun isRootAvailable(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
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
     * Attempts to connect in current working mode.
     */
    suspend fun connect(port: Int): Boolean = withContext(Dispatchers.IO) {
        val currentMode = _workingMode.value
        Log.d(TAG, "Connecting in mode: $currentMode")

        if (currentMode == WorkingMode.SIMULATED) {
            _connectionState.value = ConnectionState.Connected
            return@withContext true
        }

        if (currentMode == WorkingMode.ROOT_SU) {
            if (isRootAvailable()) {
                _connectionState.value = ConnectionState.Connected
                return@withContext true
            } else {
                _connectionState.value = ConnectionState.Error("Acceso Root (SU) no detectado en el dispositivo.")
                return@withContext false
            }
        }

        // Otherwise, standard Local Wireless ADB connection
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
            _connectionState.value = ConnectionState.Error("Conexión rechazada o cancelada. Asegúrate de tener activa la Depuración Inalámbrica en el puerto indicado.")
            closeAll()
            false
        }
    }

    /**
     * Disconnects current session
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        closeAll()
        if (_workingMode.value == WorkingMode.ADB_WIRELESS) {
            _connectionState.value = ConnectionState.Disconnected
        } else {
            // Keep simulated or root connected, as they don't use real persistent socket
            _connectionState.value = ConnectionState.Connected
        }
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
        val currentMode = _workingMode.value
        Log.d(TAG, "Executing cmd [$currentMode]: $command")

        when (currentMode) {
            WorkingMode.SIMULATED -> {
                executeSimulatedCommand(command)
            }
            WorkingMode.ROOT_SU -> {
                executeRootCommand(command)
            }
            WorkingMode.ADB_WIRELESS -> {
                executeAdbCommandDirectly(command)
            }
        }
    }

    private fun executeRootCommand(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            var hasErr = false
            while (errReader.readLine().also { line = it } != null) {
                if (!hasErr) {
                    output.append("\n[Errores/Información]:\n")
                    hasErr = true
                }
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            "Error al ejecutar como Root: ${e.message}\nAsegúrate de otorgar permisos de superusuario."
        }
    }

    private fun executeAdbCommandDirectly(command: String): String {
        val conn = adbConnection ?: return "Error: No conectado a ADB local"
        try {
            val stream: AdbStream = conn.open("shell:$command")
            val outputBuilder = java.lang.StringBuilder()
            
            while (!stream.isClosed) {
                val data = stream.read()
                if (data != null && data.isNotEmpty()) {
                    outputBuilder.append(String(data, Charsets.UTF_8))
                }
            }
            return outputBuilder.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing command '$command': ${e.message}", e)
            return "Error al ejecutar comando: ${e.message}"
        }
    }

    private fun executeSimulatedCommand(command: String): String {
        val cleanCmd = command.trim()
        
        if (cleanCmd.startsWith("wm size")) {
            val args = cleanCmd.removePrefix("wm size").trim()
            if (args == "reset") {
                simCurrentWidth = simPhysicalWidth
                simCurrentHeight = simPhysicalHeight
                return "Resetting screen size..."
            } else if (args.isEmpty()) {
                return if (simCurrentWidth == simPhysicalWidth && simCurrentHeight == simPhysicalHeight) {
                    "Physical size: ${simPhysicalWidth}x${simPhysicalHeight}"
                } else {
                    "Physical size: ${simPhysicalWidth}x${simPhysicalHeight}\nOverride size: ${simCurrentWidth}x${simCurrentHeight}"
                }
            } else {
                val parts = args.split("x")
                if (parts.size == 2) {
                    val w = parts[0].toIntOrNull()
                    val h = parts[1].toIntOrNull()
                    if (w != null && h != null) {
                        simCurrentWidth = w
                        simCurrentHeight = h
                        return "Override size set to: ${w}x${h}"
                    }
                }
                return "Error: Invalid resolution parameters"
            }
        }
        
        if (cleanCmd.startsWith("wm density")) {
            val args = cleanCmd.removePrefix("wm density").trim()
            if (args == "reset") {
                simCurrentDensity = simPhysicalDensity
                return "Resetting density..."
            } else if (args.isEmpty()) {
                return if (simCurrentDensity == simPhysicalDensity) {
                    "Physical density: ${simPhysicalDensity}"
                } else {
                    "Physical density: ${simPhysicalDensity}\nOverride density: ${simCurrentDensity}"
                }
            } else {
                val d = args.toIntOrNull()
                if (d != null) {
                    simCurrentDensity = d
                    return "Override density set to: $d"
                }
                return "Error: Invalid density parameters"
            }
        }
        
        if (cleanCmd.contains("dumpsys window")) {
            return "Display [0] info:\n" +
                    "  mDisplayInfos: [Built-in Screen, actual ${simCurrentWidth}x${simCurrentHeight}, simulated density ${simCurrentDensity}dpi]\n" +
                    "  mDisplayWidth=${simCurrentWidth} mDisplayHeight=${simCurrentHeight}\n" +
                    "  mPhysicalWidth=${simPhysicalWidth} mPhysicalHeight=${simPhysicalHeight}"
        }
        
        return "Simulación: Comando '$command' ejecutado con éxito."
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
