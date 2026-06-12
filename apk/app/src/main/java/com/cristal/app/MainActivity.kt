package com.cristal.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.max

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderReceiver.ensureChannel(this)
        requestNotificationPermission()

        val store = CristalStore(this)
        val scheduler = ReminderScheduler(this)
        setContent {
            CristalApp(store = store, scheduler = scheduler)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }
}

data class Debt(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val total: Double,
    val paid: Double = 0.0,
    val dueDate: String = "",
    val note: String = "",
    val archived: Boolean = false
) {
    val remaining: Double
        get() = max(total - paid, 0.0)

    val isPaid: Boolean
        get() = remaining <= 0.0
}

data class PaymentRecord(
    val id: String = UUID.randomUUID().toString(),
    val debtId: String,
    val amount: Double,
    val date: String,
    val note: String = ""
)

data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val debtId: String?,
    val message: String,
    val timestamp: Long,
    val repeatDays: Int? = null
)

class CristalStore(context: Context) {
    private val prefs = context.getSharedPreferences("cristal_store", Context.MODE_PRIVATE)

    fun hasPin(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hashPin(salt, pin))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return expected == hashPin(salt, pin)
    }

    fun loadDebts(): List<Debt> = readArray(KEY_DEBTS).mapNotNull { json ->
        runCatching {
            Debt(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name"),
                total = json.optDouble("total", 0.0),
                paid = json.optDouble("paid", 0.0),
                dueDate = json.optString("dueDate"),
                note = json.optString("note"),
                archived = json.optBoolean("archived", false)
            )
        }.getOrNull()
    }

    fun saveDebts(debts: List<Debt>) {
        val array = JSONArray()
        debts.forEach { debt ->
            array.put(
                JSONObject()
                    .put("id", debt.id)
                    .put("name", debt.name)
                    .put("total", debt.total)
                    .put("paid", debt.paid)
                    .put("dueDate", debt.dueDate)
                    .put("note", debt.note)
                    .put("archived", debt.archived)
            )
        }
        prefs.edit().putString(KEY_DEBTS, array.toString()).apply()
    }

    fun loadPayments(): List<PaymentRecord> = readArray(KEY_PAYMENTS).mapNotNull { json ->
        runCatching {
            PaymentRecord(
                id = json.optString("id", UUID.randomUUID().toString()),
                debtId = json.optString("debtId"),
                amount = json.optDouble("amount", 0.0),
                date = json.optString("date"),
                note = json.optString("note")
            )
        }.getOrNull()
    }

    fun savePayments(payments: List<PaymentRecord>) {
        val array = JSONArray()
        payments.forEach { payment ->
            array.put(
                JSONObject()
                    .put("id", payment.id)
                    .put("debtId", payment.debtId)
                    .put("amount", payment.amount)
                    .put("date", payment.date)
                    .put("note", payment.note)
            )
        }
        prefs.edit().putString(KEY_PAYMENTS, array.toString()).apply()
    }

    fun loadReminders(): List<Reminder> = readArray(KEY_REMINDERS).mapNotNull { json ->
        runCatching {
            val debtId = json.optString("debtId").ifBlank { null }
            Reminder(
                id = json.optString("id", UUID.randomUUID().toString()),
                debtId = debtId,
                message = json.optString("message"),
                timestamp = json.optLong("timestamp", 0L),
                repeatDays = json.optInt("repeatDays", 0).takeIf { it > 0 }
            )
        }.getOrNull()
    }

    fun saveReminders(reminders: List<Reminder>) {
        val array = JSONArray()
        reminders.forEach { reminder ->
            array.put(
                JSONObject()
                    .put("id", reminder.id)
                    .put("debtId", reminder.debtId ?: "")
                    .put("message", reminder.message)
                    .put("timestamp", reminder.timestamp)
                    .put("repeatDays", reminder.repeatDays ?: 0)
            )
        }
        prefs.edit().putString(KEY_REMINDERS, array.toString()).apply()
    }

    private fun readArray(key: String): List<JSONObject> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }.getOrElse { emptyList() }
    }

    private fun hashPin(salt: String, pin: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest("$salt:$pin".toByteArray())
            .toHex()
    }

    private companion object {
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_DEBTS = "debts"
        const val KEY_PAYMENTS = "payments"
        const val KEY_REMINDERS = "reminders"
    }
}

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(reminder: Reminder) {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_MESSAGE, reminder.message)
            .putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, reminder.id.hashCode())

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val repeatDays = reminder.repeatDays
        if (repeatDays != null && repeatDays > 0) {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                reminder.timestamp,
                repeatDays * 24L * 60L * 60L * 1000L,
                pendingIntent
            )
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, reminder.timestamp, pendingIntent)
        }
    }

    fun cancel(reminder: Reminder) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val allowed = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!allowed) return
        }

        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Tienes un pago pendiente."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Cristal")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, message.hashCode())
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    companion object {
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val CHANNEL_ID = "cristal_reminders"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Recordatorios de Cristal",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Avisos de pagos y deudas"
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }
        }
    }
}

@Composable
fun CristalApp(store: CristalStore, scheduler: ReminderScheduler) {
    var hasPin by remember { mutableStateOf(store.hasPin()) }
    var unlocked by remember { mutableStateOf(!hasPin) }
    var debts by remember { mutableStateOf(store.loadDebts()) }
    var payments by remember { mutableStateOf(store.loadPayments()) }
    var reminders by remember { mutableStateOf(store.loadReminders()) }

    val colors = lightColorScheme(
        primary = Color(0xFF006C67),
        secondary = Color(0xFF7A5C00),
        tertiary = Color(0xFF5B5B7D),
        background = Color(0xFFF8FAF8),
        surface = Color.White,
        error = Color(0xFFB3261E)
    )

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                !hasPin -> SetupPinScreen { pin ->
                    store.setPin(pin)
                    hasPin = true
                    unlocked = true
                }

                !unlocked -> UnlockScreen(
                    onUnlock = { pin -> store.verifyPin(pin).also { unlocked = it } }
                )

                else -> MainScreen(
                    debts = debts,
                    payments = payments,
                    reminders = reminders,
                    onSaveDebt = { debt ->
                        val next = if (debts.any { it.id == debt.id }) {
                            debts.map { if (it.id == debt.id) debt else it }
                        } else {
                            debts + debt
                        }
                        debts = next
                        store.saveDebts(next)
                    },
                    onArchiveDebt = { debt ->
                        val next = debts.map {
                            if (it.id == debt.id) it.copy(archived = !it.archived) else it
                        }
                        debts = next
                        store.saveDebts(next)
                    },
                    onAddPayment = { debt, amount, note ->
                        val payment = PaymentRecord(
                            debtId = debt.id,
                            amount = amount,
                            date = LocalDate.now().format(dateFormat),
                            note = note
                        )
                        val nextPayments = payments + payment
                        val nextDebts = debts.map {
                            if (it.id == debt.id) {
                                it.copy(paid = (it.paid + amount).coerceAtMost(it.total))
                            } else {
                                it
                            }
                        }
                        payments = nextPayments
                        debts = nextDebts
                        store.savePayments(nextPayments)
                        store.saveDebts(nextDebts)
                    },
                    onSaveReminder = { reminder ->
                        val next = reminders + reminder
                        reminders = next
                        store.saveReminders(next)
                        scheduler.schedule(reminder)
                    },
                    onDeleteReminder = { reminder ->
                        val next = reminders.filterNot { it.id == reminder.id }
                        reminders = next
                        store.saveReminders(next)
                        scheduler.cancel(reminder)
                    },
                    onChangePin = { currentPin, nextPin ->
                        if (store.verifyPin(currentPin)) {
                            store.setPin(nextPin)
                            true
                        } else {
                            false
                        }
                    },
                    onLock = { unlocked = false }
                )
            }
        }
    }
}

@Composable
fun SetupPinScreen(onSetPin: (String) -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val ready = pin.length >= 4 && pin == confirm

    CenteredPanel {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Crear PIN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Usaras este PIN para abrir Cristal.")
        Spacer(Modifier.height(20.dp))
        PinField(label = "PIN", value = pin, onValueChange = { pin = it })
        Spacer(Modifier.height(12.dp))
        PinField(label = "Confirmar PIN", value = confirm, onValueChange = { confirm = it })
        Spacer(Modifier.height(20.dp))
        Button(onClick = { onSetPin(pin) }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Guardar PIN")
        }
    }
}

@Composable
fun UnlockScreen(onUnlock: (String) -> Boolean) {
    var pin by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }

    CenteredPanel {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Cristal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Ingresa tu PIN para continuar.")
        Spacer(Modifier.height(20.dp))
        PinField(label = "PIN", value = pin, onValueChange = {
            pin = it
            error = false
        })
        if (error) {
            Spacer(Modifier.height(8.dp))
            Text("PIN incorrecto", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                val ok = onUnlock(pin)
                error = !ok
                if (!ok) pin = ""
            },
            enabled = pin.length >= 4,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Entrar")
        }
    }
}

@Composable
fun CenteredPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}

@Composable
fun PinField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(input.filter { it.isDigit() }.take(8))
        },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}

private enum class Section {
    Summary,
    Debts,
    Reminders,
    Settings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    debts: List<Debt>,
    payments: List<PaymentRecord>,
    reminders: List<Reminder>,
    onSaveDebt: (Debt) -> Unit,
    onArchiveDebt: (Debt) -> Unit,
    onAddPayment: (Debt, Double, String) -> Unit,
    onSaveReminder: (Reminder) -> Unit,
    onDeleteReminder: (Reminder) -> Unit,
    onChangePin: (String, String) -> Boolean,
    onLock: () -> Unit
) {
    var selected by rememberSaveable { mutableStateOf(Section.Summary.name) }
    val section = Section.valueOf(selected)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cristal") },
                actions = {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Default.Lock, contentDescription = "Bloquear")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = section == Section.Summary,
                    onClick = { selected = Section.Summary.name },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Resumen") }
                )
                NavigationBarItem(
                    selected = section == Section.Debts,
                    onClick = { selected = Section.Debts.name },
                    icon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                    label = { Text("Deudas") }
                )
                NavigationBarItem(
                    selected = section == Section.Reminders,
                    onClick = { selected = Section.Reminders.name },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    label = { Text("Avisos") }
                )
                NavigationBarItem(
                    selected = section == Section.Settings,
                    onClick = { selected = Section.Settings.name },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Ajustes") }
                )
            }
        }
    ) { padding ->
        when (section) {
            Section.Summary -> SummaryScreen(debts, payments, reminders, padding)
            Section.Debts -> DebtScreen(
                debts = debts,
                padding = padding,
                onSaveDebt = onSaveDebt,
                onArchiveDebt = onArchiveDebt,
                onAddPayment = onAddPayment,
                onSaveReminder = onSaveReminder
            )

            Section.Reminders -> ReminderScreen(
                debts = debts,
                reminders = reminders,
                padding = padding,
                onSaveReminder = onSaveReminder,
                onDeleteReminder = onDeleteReminder
            )

            Section.Settings -> SettingsScreen(
                debts = debts,
                payments = payments,
                reminders = reminders,
                padding = padding,
                onChangePin = onChangePin,
                onLock = onLock
            )
        }
    }
}

@Composable
fun SummaryScreen(
    debts: List<Debt>,
    payments: List<PaymentRecord>,
    reminders: List<Reminder>,
    padding: PaddingValues
) {
    val activeDebts = debts.filterNot { it.archived }
    val totalDebt = activeDebts.sumOf { it.total }
    val totalPaid = activeDebts.sumOf { it.paid }
    val totalRemaining = activeDebts.sumOf { it.remaining }
    val priorityDebt = activeDebts.filterNot { it.isPaid }.maxByOrNull { it.remaining }
    val nextReminder = reminders.filter { it.timestamp >= System.currentTimeMillis() }
        .minByOrNull { it.timestamp }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Resumen financiero", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Pendiente", money(totalRemaining), Modifier.weight(1f))
                MetricCard("Pagado", money(totalPaid), Modifier.weight(1f))
            }
        }
        item {
            MetricCard("Deuda original activa", money(totalDebt), Modifier.fillMaxWidth())
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Prioridad actual", fontWeight = FontWeight.Bold)
                    if (priorityDebt == null) {
                        Text("No hay deudas pendientes.")
                    } else {
                        Text(priorityDebt.name, style = MaterialTheme.typography.titleMedium)
                        Text("Pendiente: ${money(priorityDebt.remaining)}")
                        Text("Ordenada por monto pendiente de mayor a menor.")
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Proximo aviso", fontWeight = FontWeight.Bold)
                    if (nextReminder == null) {
                        Text("No tienes recordatorios futuros.")
                    } else {
                        Text(nextReminder.message)
                        Text(timestampToText(nextReminder.timestamp))
                    }
                }
            }
        }
        item {
            Text("Ultimos pagos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (payments.isEmpty()) {
            item { Text("Todavia no has registrado pagos.") }
        } else {
            items(payments.takeLast(5).reversed(), key = { it.id }) { payment ->
                val debtName = debts.firstOrNull { it.id == payment.debtId }?.name ?: "Deuda"
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(debtName, fontWeight = FontWeight.SemiBold)
                            Text(payment.date)
                        }
                        Text(money(payment.amount), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DebtScreen(
    debts: List<Debt>,
    padding: PaddingValues,
    onSaveDebt: (Debt) -> Unit,
    onArchiveDebt: (Debt) -> Unit,
    onAddPayment: (Debt, Double, String) -> Unit,
    onSaveReminder: (Reminder) -> Unit
) {
    var editingDebt by remember { mutableStateOf<Debt?>(null) }
    var showDebtDialog by remember { mutableStateOf(false) }
    var payingDebt by remember { mutableStateOf<Debt?>(null) }
    var reminderDebt by remember { mutableStateOf<Debt?>(null) }
    val sortedDebts = debts.sortedWith(compareBy<Debt> { it.archived }.thenByDescending { it.remaining })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Deudas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Prioridad por monto pendiente.")
            }
            Button(onClick = {
                editingDebt = null
                showDebtDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Nueva")
            }
        }

        if (sortedDebts.isEmpty()) {
            Text("Agrega tu primera deuda para empezar.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sortedDebts, key = { it.id }) { debt ->
                    DebtCard(
                        debt = debt,
                        onPay = { payingDebt = debt },
                        onReminder = { reminderDebt = debt },
                        onEdit = {
                            editingDebt = debt
                            showDebtDialog = true
                        },
                        onArchive = { onArchiveDebt(debt) }
                    )
                }
            }
        }
    }

    if (showDebtDialog) {
        DebtDialog(
            debt = editingDebt,
            onDismiss = { showDebtDialog = false },
            onSave = { debt ->
                onSaveDebt(debt)
                showDebtDialog = false
            }
        )
    }

    payingDebt?.let { debt ->
        PaymentDialog(
            debt = debt,
            onDismiss = { payingDebt = null },
            onSave = { amount, note ->
                onAddPayment(debt, amount, note)
                payingDebt = null
            }
        )
    }

    reminderDebt?.let { debt ->
        ReminderDialog(
            debt = debt,
            onDismiss = { reminderDebt = null },
            onSave = { reminder ->
                onSaveReminder(reminder)
                reminderDebt = null
            }
        )
    }
}

@Composable
fun DebtCard(
    debt: Debt,
    onPay: () -> Unit,
    onReminder: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit
) {
    val progress = if (debt.total <= 0.0) 0f else (debt.paid / debt.total).toFloat().coerceIn(0f, 1f)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(debt.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            debt.archived -> "Archivada"
                            debt.isPaid -> "Pagada"
                            else -> "Pendiente: ${money(debt.remaining)}"
                        }
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                }
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Total ${money(debt.total)}")
                Text("Pagado ${money(debt.paid)}")
            }
            if (debt.dueDate.isNotBlank()) {
                Text("Fecha limite: ${debt.dueDate}")
            }
            if (debt.note.isNotBlank()) {
                Text(debt.note)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onPay,
                        enabled = !debt.archived && !debt.isPaid,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Abonar")
                    }
                    OutlinedButton(
                        onClick = onReminder,
                        enabled = !debt.archived,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Aviso")
                    }
                }
                TextButton(onClick = onArchive, modifier = Modifier.align(Alignment.End)) {
                    Text(if (debt.archived) "Restaurar" else "Archivar")
                }
            }
        }
    }
}

@Composable
fun DebtDialog(debt: Debt?, onDismiss: () -> Unit, onSave: (Debt) -> Unit) {
    var name by remember { mutableStateOf(debt?.name ?: "") }
    var total by remember { mutableStateOf(debt?.total?.takeIf { it > 0.0 }?.toString() ?: "") }
    var paid by remember { mutableStateOf(debt?.paid?.takeIf { it > 0.0 }?.toString() ?: "") }
    var dueDate by remember { mutableStateOf(debt?.dueDate ?: "") }
    var note by remember { mutableStateOf(debt?.note ?: "") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (debt == null) "Nueva deuda" else "Editar deuda") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true)
                MoneyField("Monto original", total) { total = it }
                MoneyField("Monto pagado", paid) { paid = it }
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Fecha limite yyyy-MM-dd") },
                    singleLine = true
                )
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Nota") })
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = saveDebt@{
                val totalValue = total.toDoubleOrNull() ?: 0.0
                val paidValue = paid.toDoubleOrNull() ?: 0.0
                if (name.isBlank() || totalValue <= 0.0) {
                    error = "Completa nombre y monto original."
                    return@saveDebt
                }
                onSave(
                    Debt(
                        id = debt?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        total = totalValue,
                        paid = paidValue.coerceAtMost(totalValue),
                        dueDate = dueDate.trim(),
                        note = note.trim(),
                        archived = debt?.archived ?: false
                    )
                )
            }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun PaymentDialog(debt: Debt, onDismiss: () -> Unit, onSave: (Double, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abonar a ${debt.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pendiente: ${money(debt.remaining)}")
                MoneyField("Monto", amount) { amount = it }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Nota") })
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = savePayment@{
                val value = amount.toDoubleOrNull() ?: 0.0
                if (value <= 0.0) {
                    error = "El monto debe ser mayor que cero."
                    return@savePayment
                }
                onSave(value.coerceAtMost(debt.remaining), note.trim())
            }) {
                Text("Registrar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun ReminderDialog(debt: Debt?, onDismiss: () -> Unit, onSave: (Reminder) -> Unit) {
    var message by remember { mutableStateOf(debt?.let { "Pagar ${it.name}" } ?: "") }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(1).format(dateFormat)) }
    var time by remember { mutableStateOf(LocalTime.now().plusHours(1).format(timeFormat)) }
    var repeatDays by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear aviso") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Mensaje") })
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Fecha yyyy-MM-dd") }, singleLine = true)
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Hora HH:mm") }, singleLine = true)
                OutlinedTextField(
                    value = repeatDays,
                    onValueChange = { repeatDays = it.filter(Char::isDigit).take(3) },
                    label = { Text("Repetir cada X dias") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = saveReminder@{
                val parsed = runCatching {
                    LocalDateTime.of(LocalDate.parse(date, dateFormat), LocalTime.parse(time, timeFormat))
                }.getOrNull()

                if (message.isBlank() || parsed == null) {
                    error = "Completa mensaje, fecha y hora."
                    return@saveReminder
                }

                val timestamp = parsed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (timestamp <= System.currentTimeMillis()) {
                    error = "El aviso debe ser futuro."
                    return@saveReminder
                }

                onSave(
                    Reminder(
                        debtId = debt?.id,
                        message = message.trim(),
                        timestamp = timestamp,
                        repeatDays = repeatDays.toIntOrNull()?.takeIf { it > 0 }
                    )
                )
            }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun MoneyField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(input.filter { it.isDigit() || it == '.' }.take(12))
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun ReminderScreen(
    debts: List<Debt>,
    reminders: List<Reminder>,
    padding: PaddingValues,
    onSaveReminder: (Reminder) -> Unit,
    onDeleteReminder: (Reminder) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val debtById = debts.associateBy { it.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Recordatorios", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Avisos locales para pagos.")
            }
            Button(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Nuevo")
            }
        }

        if (reminders.isEmpty()) {
            Text("No tienes recordatorios.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(reminders.sortedBy { it.timestamp }, key = { it.id }) { reminder ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(reminder.message, fontWeight = FontWeight.Bold)
                                Text(timestampToText(reminder.timestamp))
                                reminder.debtId?.let { id ->
                                    Text("Deuda: ${debtById[id]?.name ?: "No encontrada"}")
                                }
                                reminder.repeatDays?.let { Text("Repite cada $it dias") }
                            }
                            IconButton(onClick = { onDeleteReminder(reminder) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        ReminderDialog(
            debt = null,
            onDismiss = { showDialog = false },
            onSave = { reminder ->
                onSaveReminder(reminder)
                showDialog = false
            }
        )
    }
}

@Composable
fun SettingsScreen(
    debts: List<Debt>,
    payments: List<PaymentRecord>,
    reminders: List<Reminder>,
    padding: PaddingValues,
    onChangePin: (String, String) -> Boolean,
    onLock: () -> Unit
) {
    var showPinDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Datos guardados", fontWeight = FontWeight.Bold)
                Text("${debts.size} deudas")
                Text("${payments.size} pagos")
                Text("${reminders.size} recordatorios")
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Seguridad", fontWeight = FontWeight.Bold)
                Button(onClick = { showPinDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cambiar PIN")
                }
                OutlinedButton(onClick = onLock, modifier = Modifier.fillMaxWidth()) {
                    Text("Bloquear ahora")
                }
            }
        }
    }

    if (showPinDialog) {
        ChangePinDialog(
            onDismiss = { showPinDialog = false },
            onChangePin = { current, next ->
                if (onChangePin(current, next)) {
                    showPinDialog = false
                    true
                } else {
                    false
                }
            }
        )
    }
}

@Composable
fun ChangePinDialog(onDismiss: () -> Unit, onChangePin: (String, String) -> Boolean) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PinField("PIN actual", current) { current = it }
                PinField("PIN nuevo", next) { next = it }
                PinField("Confirmar PIN", confirm) { confirm = it }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = changePin@{
                if (next.length < 4 || next != confirm) {
                    error = "El PIN nuevo debe coincidir y tener 4 digitos o mas."
                    return@changePin
                }
                if (!onChangePin(current, next)) {
                    error = "El PIN actual no coincide."
                }
            }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun money(value: Double): String {
    return String.format(Locale.US, "$%,.2f", value)
}

private fun timestampToText(timestamp: Long): String {
    return LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(timestamp),
        ZoneId.systemDefault()
    ).format(dateTimeFormat)
}

private fun ByteArray.toHex(): String {
    return joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
