package com.example.twirlly

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun durText(secs: Long): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** Watch time for the last 7 days. */
@Composable
fun ScreenTimeDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val log = remember { vm.watchLogMap() }
    val f = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val days = remember {
        (6 downTo 0).map { off ->
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -off)
            f.format(c.time) to "SMTWTFS"[c.get(Calendar.DAY_OF_WEEK) - 1].toString()
        }
    }
    val secs = days.map { log[it.first] ?: 0L }
    val maxSecs = maxOf(secs.maxOrNull() ?: 0L, 60L)
    val brand = Brush.verticalGradient(listOf(Color(0xFFFF2D87), Color(0xFF7B2FF7)))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Screen time") },
        text = {
            Column {
                Text("Today: " + durText(secs.last()), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Daily average: " + durText(secs.sum() / 7), color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().height(150.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom
                ) {
                    days.forEachIndexed { i, d ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (secs[i] >= 60) durText(secs[i]) else "", fontSize = 9.sp, color = Color.Gray)
                            Box(
                                Modifier.width(28.dp).height((100f * secs[i] / maxSecs).coerceAtLeast(4f).dp)
                                    .clip(RoundedCornerShape(8.dp)).background(brand)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(d.second, fontSize = 12.sp, fontWeight = if (i == 6) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** Asks for the 4-digit PIN. onSubmit returns true when the PIN was right. */
@Composable
fun PinDialog(title: String, onDismiss: () -> Unit, onSubmit: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    pin, { pin = it.filter { c -> c.isDigit() }.take(4); wrong = false }, singleLine = true,
                    label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (wrong) Text("Wrong PIN. Try again.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { if (!onSubmit(pin)) { wrong = true; pin = "" } }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SetPinDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Turn on kids mode") },
        text = {
            Column {
                Text("Create, LIVE, settings and profile editing will need this PIN.", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    a, { a = it.filter { c -> c.isDigit() }.take(4); err = null }, singleLine = true, label = { Text("New 4-digit PIN") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    b, { b = it.filter { c -> c.isDigit() }.take(4); err = null }, singleLine = true, label = { Text("Repeat PIN") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (a.length != 4) err = "The PIN needs 4 digits."
                else if (a != b) err = "The two PINs are different."
                else onSet(a)
            }) { Text("Turn on") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A row with - and + buttons for choosing an hour. */
@Composable
fun HourRow(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp)
        IconButton(onClick = { onChange((hour + 23) % 24) }) { Icon(Icons.Default.Remove, "Earlier") }
        Text("%02d:00".format(hour), fontWeight = FontWeight.Bold)
        IconButton(onClick = { onChange((hour + 1) % 24) }) { Icon(Icons.Default.Add, "Later") }
    }
}
