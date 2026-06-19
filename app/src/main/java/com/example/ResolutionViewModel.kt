package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ResolutionViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ResolutionViewModel"
    val adbManager = AdbManager(application)

    // Current State
    private val _connectionState = MutableStateFlow<AdbManager.ConnectionState>(AdbManager.ConnectionState.Disconnected)
    val connectionState: StateFlow<AdbManager.ConnectionState> = _connectionState.asStateFlow()

    // Screen Dimensions State
    val physicalWidth = MutableStateFlow(1080)
    val physicalHeight = MutableStateFlow(2400)
    val physicalDensity = MutableStateFlow(440)

    val currentWidth = MutableStateFlow(1080)
    val currentHeight = MutableStateFlow(2400)
    val currentDensity = MutableStateFlow(440)

    // Input States
    val inputWidth = MutableStateFlow("1080")
    val inputHeight = MutableStateFlow("2400")
    val inputDensity = MutableStateFlow("440")
    val inputPort = MutableStateFlow("5555")

    // Console logs / terminal output
    private val _terminalOutput = MutableStateFlow("Bienvenido a Resolution Changer.\nConéctate por ADB local para iniciar.")
    val terminalOutput: StateFlow<String> = _terminalOutput.asStateFlow()

    // Safety Countdown State
    private val _safetyCountdownActive = MutableStateFlow(false)
    val safetyCountdownActive: StateFlow<Boolean> = _safetyCountdownActive.asStateFlow()

    private val _safetySecondsLeft = MutableStateFlow(15)
    val safetySecondsLeft: StateFlow<Int> = _safetySecondsLeft.asStateFlow()

    // Backup Safe States before applying test settings
    private var backupWidth = 1080
    private var backupHeight = 2400
    private var backupDensity = 440

    private var countdownJob: Job? = null

    init {
        // Collect ADB Manager states
        viewModelScope.launch {
            adbManager.connectionState.collectLatest { state ->
                _connectionState.value = state
                if (state == AdbManager.ConnectionState.Connected) {
                    appendLog("¡Conectado exitosamente al ADB Local!")
                    refreshScreenDetails()
                } else if (state is AdbManager.ConnectionState.Error) {
                    appendLog("Error de conexión: ${state.message}")
                }
            }
        }

        // Initialize display properties locally
        readLocalDisplayMetrics()
    }

    private fun readLocalDisplayMetrics() {
        val dm = getApplication<Application>().resources.displayMetrics
        currentWidth.value = dm.widthPixels
        currentHeight.value = dm.heightPixels
        currentDensity.value = dm.densityDpi

        // Set inputs initial default value if empty
        if (inputWidth.value.isEmpty()) inputWidth.value = dm.widthPixels.toString()
        if (inputHeight.value.isEmpty()) inputHeight.value = dm.heightPixels.toString()
        if (inputDensity.value.isEmpty()) inputDensity.value = dm.densityDpi.toString()
    }

    private fun appendLog(line: String) {
        val currentText = _terminalOutput.value
        _terminalOutput.value = "$currentText\n> $line"
    }

    fun clearLog() {
        _terminalOutput.value = "Logs de Consola Limpios."
    }

    /**
     * Executes single shell command from terminal UI.
     */
    fun runUserCommand(cmd: String) {
        viewModelScope.launch {
            if (_connectionState.value != AdbManager.ConnectionState.Connected) {
                appendLog("Error: Debes conectarte a un ADB local antes de enviar comandos.")
                return@launch
            }
            appendLog(cmd)
            val result = adbManager.executeCommand(cmd)
            appendLog(result.ifEmpty { "[Comando ejecutado sin respuesta visual]" })
            refreshScreenDetails()
        }
    }

    /**
     * Connects to Local ADB at specified port.
     */
    fun connectAdb() {
        val portInt = inputPort.value.toIntOrNull()
        if (portInt == null || portInt !in 1024..65535) {
            appendLog("Puerto inválido. Ingrese un puerto entre 1024 y 65535 (usualmente el de Depuración Inalámbrica).")
            return
        }

        viewModelScope.launch {
            appendLog("Intentando conectar a localhost:$portInt...")
            adbManager.connect(portInt)
        }
    }

    /**
     * Disconnects local ADB.
     */
    fun disconnectAdb() {
        viewModelScope.launch {
            adbManager.disconnect()
            appendLog("Desconectado de ADB.")
        }
    }

    /**
     * Refreshes active and physical display configuration from ADB `wm` commands.
     */
    fun refreshScreenDetails() {
        viewModelScope.launch {
            if (_connectionState.value != AdbManager.ConnectionState.Connected) {
                readLocalDisplayMetrics()
                return@launch
            }

            // Read sizing
            val sizeOutput = adbManager.executeCommand("wm size")
            // Parse sizing
            // Physical size: 1080x2400
            // Override size: 720x1600 (optional)
            var pWidth = physicalWidth.value
            var pHeight = physicalHeight.value
            var cWidth = currentWidth.value
            var cHeight = currentHeight.value

            val sizeLines = sizeOutput.lines()
            for (line in sizeLines) {
                if (line.contains("Physical size", ignoreCase = true)) {
                    val dimensions = line.substringAfter(":").trim().split("x")
                    if (dimensions.size == 2) {
                        pWidth = dimensions[0].toIntOrNull() ?: pWidth
                        pHeight = dimensions[1].toIntOrNull() ?: pHeight
                    }
                } else if (line.contains("Override size", ignoreCase = true)) {
                    val dimensions = line.substringAfter(":").trim().split("x")
                    if (dimensions.size == 2) {
                        cWidth = dimensions[0].toIntOrNull() ?: cWidth
                        cHeight = dimensions[1].toIntOrNull() ?: cHeight
                    }
                }
            }

            // If there's no override line, active = physical
            if (!sizeOutput.contains("Override size", ignoreCase = true)) {
                cWidth = pWidth
                cHeight = pHeight
            }

            physicalWidth.value = pWidth
            physicalHeight.value = pHeight
            currentWidth.value = cWidth
            currentHeight.value = cHeight

            // Read density
            val densityOutput = adbManager.executeCommand("wm density")
            var pDensity = physicalDensity.value
            var cDensity = currentDensity.value

            val densityLines = densityOutput.lines()
            for (line in densityLines) {
                if (line.contains("Physical density", ignoreCase = true)) {
                    pDensity = line.substringAfter(":").trim().toIntOrNull() ?: pDensity
                } else if (line.contains("Override density", ignoreCase = true)) {
                    cDensity = line.substringAfter(":").trim().toIntOrNull() ?: cDensity
                }
            }

            if (!densityOutput.contains("Override density", ignoreCase = true)) {
                cDensity = pDensity
            }

            physicalDensity.value = pDensity
            currentDensity.value = cDensity

            // Sync input selectors
            inputWidth.value = cWidth.toString()
            inputHeight.value = cHeight.toString()
            inputDensity.value = cDensity.toString()

            appendLog("Pantalla actualizada: ${cWidth}x${cHeight} @${cDensity}dpi (Física: ${pWidth}x${pHeight} @${pDensity}dpi)")
        }
    }

    /**
     * Safe Resolution Applier. Activates a 15-second restore safety mechanism.
     */
    fun applyResolutionAndDensitySafely() {
        val w = inputWidth.value.toIntOrNull()
        val h = inputHeight.value.toIntOrNull()
        val d = inputDensity.value.toIntOrNull()

        if (w == null || h == null || d == null || w < 240 || h < 240 || d < 72) {
            appendLog("Error: Valores de resolución o DPI demasiado pequeños o inválidos.")
            return
        }

        viewModelScope.launch {
            if (_connectionState.value != AdbManager.ConnectionState.Connected) {
                appendLog("Error: Debes estar conectado por ADB local para aplicar configuraciones.")
                return@launch
            }

            // Backup current configuration in case of failure
            backupWidth = currentWidth.value
            backupHeight = currentHeight.value
            backupDensity = currentDensity.value

            appendLog("Aplicando resolución temporal: ${w}x${h} @${d}dpi... (Seguridad de 15 segundos activa)")

            // Call wm commands
            adbManager.executeCommand("wm size ${w}x${h}")
            adbManager.executeCommand("wm density $d")

            // Wait a brief moment and refresh
            delay(500)
            refreshScreenDetails()

            // Start countdown
            startSafetyCountdown()
        }
    }

    /**
     * Resets Screen Sizing entirely to original manufacturer settings.
     */
    fun resetToNative() {
        viewModelScope.launch {
            cancelCountdown()
            if (_connectionState.value != AdbManager.ConnectionState.Connected) {
                appendLog("Error: Conéctate a ADB local primero")
                return@launch
            }
            appendLog("Restableciendo pantalla a valores de fábrica...")
            adbManager.executeCommand("wm size reset")
            adbManager.executeCommand("wm density reset")
            delay(500)
            refreshScreenDetails()
        }
    }

    /**
     * User confirms they want to keep the applied resolution changes.
     */
    fun confirmKeepChanges() {
        cancelCountdown()
        appendLog("¡Configuración confirmada y guardada!")
    }

    /**
     * User wants to revert applied changes immediately.
     */
    fun revertChanges() {
        cancelCountdown()
        viewModelScope.launch {
            if (_connectionState.value != AdbManager.ConnectionState.Connected) {
                return@launch
            }
            appendLog("Revirtiendo cambios aplicados a la configuración anterior segura...")
            adbManager.executeCommand("wm size ${backupWidth}x${backupHeight}")
            adbManager.executeCommand("wm density $backupDensity")
            delay(500)
            refreshScreenDetails()
        }
    }

    private fun startSafetyCountdown() {
        countdownJob?.cancel()
        _safetySecondsLeft.value = 15
        _safetyCountdownActive.value = true

        countdownJob = viewModelScope.launch(Dispatchers.Main) {
            while (_safetySecondsLeft.value > 0) {
                delay(1000)
                _safetySecondsLeft.value -= 1
            }
            // If timer runs out, revert automatically!
            _safetyCountdownActive.value = false
            revertChanges()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _safetyCountdownActive.value = false
    }

    override fun onCleared() {
        super.onCleared()
        cancelCountdown()
        closeAdbGracefully()
    }

    private fun closeAdbGracefully() {
        // Runs on application shutdown
        try {
            adbManager.connectionState.value.let {
                if (it == AdbManager.ConnectionState.Connected) {
                    // Try simple disconnect
                    viewModelScope.launch(Dispatchers.IO) {
                        adbManager.disconnect()
                    }
                }
            }
        } catch (e: Exception) {}
    }
}
