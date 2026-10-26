package com.example.alarmaiot

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.alarmaiot.ui.theme.AlarmaIoTTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Un evento enviado por un sensor (simulado) y guardado en Firebase
data class Evento(val tipo: String, val dispositivo: String, val ts: Long)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlarmaIoTTheme {
                AlarmaApp()
            }
        }
    }
}

// ---------- RAÍZ: decide entre login y app principal ----------
@Composable
fun AlarmaApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    // Si ya había una sesión iniciada, Firebase la recuerda
    var correoActual by remember { mutableStateOf(auth.currentUser?.email) }

    val correo = correoActual
    if (correo == null) {
        LoginScreen(auth = auth, onLoginOk = { correoActual = it })
    } else {
        PrincipalScreen(
            correo = correo,
            onCerrarSesion = {
                auth.signOut()
                correoActual = null
            }
        )
    }
}

// ---------- LOGIN CON FIREBASE AUTHENTICATION ----------
@Composable
fun LoginScreen(auth: FirebaseAuth, onLoginOk: (String) -> Unit) {
    var correo by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Alarma IoT", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text("Inicia sesión para continuar")
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = correo,
                onValueChange = { correo = it },
                label = { Text("Correo") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = clave,
                onValueChange = { clave = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            if (error.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                enabled = !cargando,
                onClick = {
                    if (correo.isBlank() || clave.isBlank()) {
                        error = "Completa el correo y la contraseña"
                    } else {
                        cargando = true
                        error = ""
                        auth.signInWithEmailAndPassword(correo.trim(), clave)
                            .addOnCompleteListener { tarea ->
                                cargando = false
                                if (tarea.isSuccessful) {
                                    onLoginOk(auth.currentUser?.email ?: correo.trim())
                                } else {
                                    error = "No se pudo ingresar. Revisa tus datos y la conexión."
                                }
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (cargando) "Ingresando..." else "Ingresar")
            }
        }
    }
}

// ---------- PANTALLA PRINCIPAL: 3 destinos + datos en tiempo real ----------
@Composable
fun PrincipalScreen(correo: String, onCerrarSesion: () -> Unit) {
    val db = remember { FirebaseDatabase.getInstance() }
    var armada by remember { mutableStateOf(false) }
    var eventos by remember { mutableStateOf(listOf<Evento>()) }
    var pestana by remember { mutableIntStateOf(0) }

    // Escucha en tiempo real: estado de la alarma y lista de eventos
    DisposableEffect(Unit) {
        val armadaRef = db.getReference("estado/armada")
        val eventosQuery = db.getReference("eventos").limitToLast(50)

        val armadaListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                armada = snapshot.getValue(Boolean::class.java) ?: false
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        val eventosListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                eventos = snapshot.children.mapNotNull { hijo ->
                    val tipo = hijo.child("tipo").getValue(String::class.java)
                    val dispositivo =
                        hijo.child("dispositivo").getValue(String::class.java) ?: "?"
                    val ts = hijo.child("ts").getValue(Long::class.java) ?: 0L
                    if (tipo == null) null else Evento(tipo, dispositivo, ts)
                }.sortedByDescending { it.ts }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        armadaRef.addValueEventListener(armadaListener)
        eventosQuery.addValueEventListener(eventosListener)

        onDispose {
            armadaRef.removeEventListener(armadaListener)
            eventosQuery.removeEventListener(eventosListener)
        }
    }

    // Envía un evento a la base de datos (lo recibe el otro celular)
    val enviarEvento: (String) -> Unit = { tipo ->
        val dato = mapOf(
            "tipo" to tipo,
            "dispositivo" to Build.MODEL,
            "usuario" to correo,
            "ts" to System.currentTimeMillis()
        )
        db.getReference("eventos").push().setValue(dato)
    }

    // Arma o desarma la alarma para todos los dispositivos
    val cambiarArmada: (Boolean) -> Unit = { valor ->
        db.getReference("estado/armada").setValue(valor)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = pestana == 0,
                    onClick = { pestana = 0 },
                    icon = { Text("🎛") },
                    label = { Text("Activador") }
                )
                NavigationBarItem(
                    selected = pestana == 1,
                    onClick = { pestana = 1 },
                    icon = { Text("📋") },
                    label = { Text("Monitoreo") }
                )
                NavigationBarItem(
                    selected = pestana == 2,
                    onClick = { pestana = 2 },
                    icon = { Text("🔒") },
                    label = { Text("Cuenta") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (pestana) {
                0 -> ActivadorScreen(armada, cambiarArmada, enviarEvento)
                1 -> MonitoreoScreen(armada, eventos)
                else -> CuentaScreen(correo, onCerrarSesion)
            }
        }
    }
}

// ---------- ACTIVADOR: arma/desarma y simula sensores ----------
@Composable
fun ActivadorScreen(
    armada: Boolean,
    onArmadaChange: (Boolean) -> Unit,
    onSensor: (String) -> Unit
) {
    var mensaje by remember { mutableStateOf("") }

    val simular: (String) -> Unit = { tipo ->
        if (armada) {
            onSensor(tipo)
            mensaje = "Evento enviado: $tipo"
        } else {
            mensaje = "Primero arma la alarma"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Activador", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (armada) "Alarma ARMADA" else "Alarma DESARMADA",
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(checked = armada, onCheckedChange = onArmadaChange)
            }
        }

        Text("Simular sensores", style = MaterialTheme.typography.titleMedium)

        Button(
            onClick = { simular("Láser cortado") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Láser cortado") }

        Button(
            onClick = { simular("Puerta abierta") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Puerta abierta") }

        Button(
            onClick = { simular("Movimiento detectado") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Movimiento detectado") }

        if (mensaje.isNotEmpty()) {
            Text(mensaje, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------- MONITOREO: eventos recibidos en tiempo real ----------
@Composable
fun MonitoreoScreen(armada: Boolean, eventos: List<Evento>) {
    val formato = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text("Monitoreo", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (armada) "Estado: alarma ARMADA" else "Estado: alarma DESARMADA",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(16.dp))

        if (eventos.isEmpty()) {
            Text("Sin eventos todavía")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(eventos) { evento ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "ALERTA: ${evento.tipo}",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${formato.format(Date(evento.ts))} - desde ${evento.dispositivo}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- CUENTA Y SEGURIDAD ----------
@Composable
fun CuentaScreen(correo: String, onCerrarSesion: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Cuenta", style = MaterialTheme.typography.headlineMedium)
        Text("Sesión iniciada como:")
        Text(correo, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Medidas de seguridad", style = MaterialTheme.typography.titleMedium)
                Text("• Las contraseñas las gestiona Firebase Authentication; la app no las guarda.")
                Text("• La comunicación con Firebase va cifrada (HTTPS/TLS).")
                Text("• Reglas de la base de datos: solo usuarios autenticados pueden leer y escribir.")
            }
        }

        OutlinedButton(
            onClick = onCerrarSesion,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Cerrar sesión") }
    }
}