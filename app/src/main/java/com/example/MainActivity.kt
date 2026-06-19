package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold")
                ) { innerPadding ->
                    ResolutionChangerApp(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResolutionChangerApp(
    modifier: Modifier = Modifier,
    viewModel: ResolutionViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val workingMode by viewModel.workingMode.collectAsState()
    
    val physicalW by viewModel.physicalWidth.collectAsState()
    val physicalH by viewModel.physicalHeight.collectAsState()
    val physicalDpi by viewModel.physicalDensity.collectAsState()

    val currentW by viewModel.currentWidth.collectAsState()
    val currentH by viewModel.currentHeight.collectAsState()
    val currentDpi by viewModel.currentDensity.collectAsState()

    val inputW by viewModel.inputWidth.collectAsState()
    val inputH by viewModel.inputHeight.collectAsState()
    val inputDpi by viewModel.inputDensity.collectAsState()
    val inputPort by viewModel.inputPort.collectAsState()

    val terminalOutput by viewModel.terminalOutput.collectAsState()
    val isCountdownActive by viewModel.safetyCountdownActive.collectAsState()
    val secondsLeft by viewModel.safetySecondsLeft.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var showGuide by remember { mutableStateOf(false) }
    var userCmdInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
        ) {
            // Header Hero Area
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Pantalla",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Resolution Changer Pro",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                        )
                        Text(
                            text = "Modifica la resolución y densidad sin root por ADB local inalámbrico, acceso Root directo, o modo de simulación segura.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Method Selector tabs
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Método de Conexión / Ejecución:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val modes = listOf(
                            Triple(AdbManager.WorkingMode.SIMULATED, "Simulado (Demo)", Icons.Default.Science),
                            Triple(AdbManager.WorkingMode.ADB_WIRELESS, "ADB Wifi", Icons.Default.SettingsInputAntenna),
                            Triple(AdbManager.WorkingMode.ROOT_SU, "Root Directo", Icons.Default.DirectionsRun)
                        )
                        
                        modes.forEach { (mode, label, icon) ->
                            val isSelected = workingMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        viewModel.setWorkingMode(mode)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Connection Details Card (Dynamic based on selected mode)
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (workingMode) {
                            AdbManager.WorkingMode.SIMULATED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            AdbManager.WorkingMode.ROOT_SU -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)
                            AdbManager.WorkingMode.ADB_WIRELESS -> {
                                when (connectionState) {
                                    is AdbManager.ConnectionState.Connected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    is AdbManager.ConnectionState.Connecting -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)
                                    is AdbManager.ConnectionState.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                }
                            }
                        }
                    ),
                    modifier = Modifier.testTag("connection_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = when (workingMode) {
                                    AdbManager.WorkingMode.SIMULATED -> Icons.Default.PlayArrow
                                    AdbManager.WorkingMode.ROOT_SU -> Icons.Default.Bolt
                                    AdbManager.WorkingMode.ADB_WIRELESS -> {
                                        when (connectionState) {
                                            is AdbManager.ConnectionState.Connected -> Icons.Default.Dns
                                            is AdbManager.ConnectionState.Connecting -> Icons.Default.Refresh
                                            is AdbManager.ConnectionState.Error -> Icons.Default.Warning
                                            else -> Icons.Default.DeveloperMode
                                        }
                                    }
                                },
                                contentDescription = "Conexión",
                                tint = when (workingMode) {
                                    AdbManager.WorkingMode.SIMULATED -> MaterialTheme.colorScheme.primary
                                    AdbManager.WorkingMode.ROOT_SU -> MaterialTheme.colorScheme.tertiary
                                    AdbManager.WorkingMode.ADB_WIRELESS -> {
                                        when (connectionState) {
                                            is AdbManager.ConnectionState.Connected -> MaterialTheme.colorScheme.primary
                                            is AdbManager.ConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary
                                            is AdbManager.ConnectionState.Error -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (workingMode) {
                                        AdbManager.WorkingMode.SIMULATED -> "Modo Simulación Activo"
                                        AdbManager.WorkingMode.ROOT_SU -> "Modo Root Directo Activo"
                                        AdbManager.WorkingMode.ADB_WIRELESS -> "Estado del ADB Inalámbrico"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when (workingMode) {
                                        AdbManager.WorkingMode.SIMULATED -> "Demostración segura para usar en vistas previas y pruebas."
                                        AdbManager.WorkingMode.ROOT_SU -> if (viewModel.adbManager.isRootAvailable()) "Dispositivo con acceso Root concedido." else "Intentando conexión por superusuario ('su')."
                                        AdbManager.WorkingMode.ADB_WIRELESS -> {
                                            when (connectionState) {
                                                is AdbManager.ConnectionState.Connected -> "Conectado a Wireless Debugging"
                                                is AdbManager.ConnectionState.Connecting -> "Intentando enlace local..."
                                                is AdbManager.ConnectionState.Error -> (connectionState as AdbManager.ConnectionState.Error).message
                                                else -> "Sin enlace local activo"
                                            }
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            if (workingMode == AdbManager.WorkingMode.ADB_WIRELESS) {
                                IconButton(onClick = { showGuide = !showGuide }) {
                                    Icon(
                                        imageVector = if (showGuide) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Mostrar Guía"
                                    )
                                }
                            }
                        }

                        // Guide Instructions
                        AnimatedVisibility(visible = showGuide && workingMode == AdbManager.WorkingMode.ADB_WIRELESS) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Guía de Activación (Paso a Paso):",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "1. Abre Configuración -> Opciones de desarrollador.\n" +
                                                "2. Activa la 'Depuración inalámbrica' (Wireless Debugging).\n" +
                                                "3. Entra en ella y copia la Dirección IP y el Puerto asignado.\n" +
                                                "4. Presiona abajo 'Buscar Puertos' para autodetectar el puerto local, ó introduce el puerto manualmente y presiona 'Enlazar'.",
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }

                        if (workingMode == AdbManager.WorkingMode.ADB_WIRELESS) {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Port Entry & Auto Scan Button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = inputPort,
                                    onValueChange = { viewModel.inputPort.value = it },
                                    label = { Text("Puerto local (ej: 39821)") },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Go
                                    ),
                                    keyboardActions = KeyboardActions(onGo = {
                                        keyboardController?.hide()
                                        viewModel.connectAdb()
                                    }),
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("port_input"),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Button(
                                    onClick = {
                                        keyboardController?.hide()
                                        viewModel.scanActiveAdbPorts()
                                    },
                                    enabled = !isScanning,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    if (isScanning) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    } else {
                                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Auto Buscar")
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    if (connectionState == AdbManager.ConnectionState.Connected) {
                                        viewModel.disconnectAdb()
                                    } else {
                                        viewModel.connectAdb()
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("connect_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (connectionState == AdbManager.ConnectionState.Connected)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = if (connectionState == AdbManager.ConnectionState.Connected) "Desconectar enlace Wireles ADB" else "Establecer enlace local ADB"
                                )
                            }
                        } else {
                            // Quick Action for Simulation / Root
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (workingMode == AdbManager.WorkingMode.SIMULATED) 
                                    "✨ El modo de simulación simula perfectamente las respuestas de la consola wm y refresca la UI en tiempo real." 
                                    else "⚡ Modo Superusuario: Se utilizará 'su' directo para cambiar el tamaño y densidad de pantalla sin configurar ADB.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Realtime metrics overview
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Modificación Activa",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${currentW} x ${currentH}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "Densidad: $currentDpi DPI",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Pantalla Física Nativa",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${physicalW} x ${physicalH}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            Text(
                                "Densidad original: $physicalDpi DPI",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Presets Header
            item {
                Text(
                    text = "Ajustes Rápidos de Relación de Aspecto",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Aspect ratio quick options flow grid
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Reset Option
                    SuggestionChip(
                        onClick = {
                            viewModel.inputWidth.value = physicalW.toString()
                            viewModel.inputHeight.value = physicalH.toString()
                            viewModel.inputDensity.value = physicalDpi.toString()
                        },
                        label = { Text("Original nativa") },
                        icon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("preset_native")
                    )

                    // 16:9 Preset
                    SuggestionChip(
                        onClick = {
                            viewModel.inputHeight.value = physicalH.toString()
                            val scaledW = (physicalH * 9) / 16
                            viewModel.inputWidth.value = scaledW.toString()
                            val scaledDensity = (physicalDpi * scaledW) / physicalW
                            viewModel.inputDensity.value = Math.max(72, scaledDensity).toString()
                        },
                        label = { Text("Pantallazo 16:9") },
                        icon = { Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("preset_16_9")
                    )

                    // 18:9 Preset
                    SuggestionChip(
                        onClick = {
                            viewModel.inputHeight.value = physicalH.toString()
                            val scaledW = (physicalH * 9) / 18
                            viewModel.inputWidth.value = scaledW.toString()
                            val scaledDensity = (physicalDpi * scaledW) / physicalW
                            viewModel.inputDensity.value = Math.max(72, scaledDensity).toString()
                        },
                        label = { Text("Doble 18:9 (2:1)") },
                        icon = { Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("preset_18_9")
                    )

                    // 19.5:9 Preset
                    SuggestionChip(
                        onClick = {
                            viewModel.inputHeight.value = physicalH.toString()
                            val scaledW = (physicalH * 9.0 / 19.5).toInt()
                            viewModel.inputWidth.value = scaledW.toString()
                            val scaledDensity = (physicalDpi * scaledW) / physicalW
                            viewModel.inputDensity.value = Math.max(72, scaledDensity).toString()
                        },
                        label = { Text("Cine Móvil 19.5:9") },
                        icon = { Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("preset_19_5_9")
                    )

                    // 21:9 UltraWide Preset
                    SuggestionChip(
                        onClick = {
                            viewModel.inputHeight.value = physicalH.toString()
                            val scaledW = (physicalH * 9) / 21
                            viewModel.inputWidth.value = scaledW.toString()
                            val scaledDensity = (physicalDpi * scaledW) / physicalW
                            viewModel.inputDensity.value = Math.max(72, scaledDensity).toString()
                        },
                        label = { Text("UltraWide 21:9") },
                        icon = { Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("preset_21_9")
                    )
                }
            }

            // Resolution inputs box
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Ajustar Dimensiones Manuales",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = inputW,
                                onValueChange = { viewModel.inputWidth.value = it },
                                label = { Text("Ancho (Px)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("width_input"),
                                shape = RoundedCornerShape(16.dp)
                            )

                            OutlinedTextField(
                                value = inputH,
                                onValueChange = { viewModel.inputHeight.value = it },
                                label = { Text("Alto (Px)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("height_input"),
                                shape = RoundedCornerShape(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = inputDpi,
                            onValueChange = { viewModel.inputDensity.value = it },
                            label = { Text("Densidad de Pantalla (DPI)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("density_input"),
                            shape = RoundedCornerShape(16.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.resetToNative() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("reset_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Volver a Nativo")
                            }

                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    viewModel.applyResolutionAndDensitySafely()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("apply_button"),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Aplicar")
                            }
                        }
                    }
                }
            }

            // Advanced Console Section
            item {
                var showConsole by remember { mutableStateOf(false) }
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showConsole = !showConsole }
                        ) {
                            Icon(imageVector = Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Consola Terminal de Diagnóstico",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (showConsole) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }

                        if (showConsole) {
                            Spacer(modifier = Modifier.height(12.dp))
                            // Command outputs terminal
                            Surface(
                                color = Color.Black,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            ) {
                                Box(modifier = Modifier.padding(12.dp)) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Text(
                                                text = terminalOutput,
                                                color = Color.Green,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.clearLog() },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Limpiar Logs",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Custom command enter
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = userCmdInput,
                                    onValueChange = { userCmdInput = it },
                                    label = { Text("Comando shell (Ej. 'wm size')") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = {
                                        if (userCmdInput.trim().isNotEmpty()) {
                                            viewModel.runUserCommand(userCmdInput)
                                            userCmdInput = ""
                                        }
                                    }),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("terminal_cmd_input"),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        if (userCmdInput.trim().isNotEmpty()) {
                                            viewModel.runUserCommand(userCmdInput)
                                            userCmdInput = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .testTag("terminal_send_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Enviar")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Common quick actions
                            Text("Diagnóstico Rápido:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { viewModel.runUserCommand("wm size") },
                                    colors = ButtonDefaults.filledTonalButtonColors(),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text("wm size", style = MaterialTheme.typography.bodySmall)
                                }
                                Button(
                                    onClick = { viewModel.runUserCommand("wm density") },
                                    colors = ButtonDefaults.filledTonalButtonColors(),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text("wm density", style = MaterialTheme.typography.bodySmall)
                                }
                                Button(
                                    onClick = { viewModel.runUserCommand("dumpsys window") },
                                    colors = ButtonDefaults.filledTonalButtonColors(),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text("Datos de Pantalla", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 15-Seconds Auto-Revert Safety Banner
        AnimatedVisibility(
            visible = isCountdownActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("safety_banner"),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "¿Mantener la nueva resolución de pantalla?",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Si no confirmas o la pantalla se queda en negro, los cambios se revertirán en $secondsLeft segundos.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.revertChanges() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("safety_revert_button")
                        ) {
                            Text("Revertir ($secondsLeft)")
                        }

                        Button(
                            onClick = { viewModel.confirmKeepChanges() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("safety_confirm_button")
                        ) {
                            Text("Confirmar")
                        }
                    }
                }
            }
        }
    }
}
