package com.example.twirlly

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Hook for sending verification requests. The default only saves the request on this phone.
 * Replace `Verification.provider` later to send it to your own server. The badge itself
 * must be switched on by that server, never by the app.
 */
interface VerificationProvider {
    suspend fun submit(requestJson: String): String?
}

object LocalVerification : VerificationProvider {
    override suspend fun submit(requestJson: String): String? = null
}

object Verification {
    var provider: VerificationProvider = LocalVerification
}

/** Request screen for businesses and creators who want a verified badge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VerificationDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val profile by vm.profile.collectAsState()
    val scope = rememberCoroutineScope()
    val categories = listOf("Business", "Creator", "Brand", "Public figure")
    var business by remember { mutableStateOf("") }
    var category by remember { mutableIntStateOf(0) }
    var link by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verified badge") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    profile.verified -> Text("Your account is verified.", fontWeight = FontWeight.Bold)
                    profile.verifyRequest.isNotBlank() -> {
                        Text("Request saved", fontWeight = FontWeight.Bold)
                        Text(
                            "It will be reviewed when Twirlly is connected to its servers. Only the Twirlly team can give the badge.",
                            color = Color.Gray, fontSize = 13.sp
                        )
                    }
                    else -> {
                        Text("Running a business or a known brand? Tell us about it. A badge shows people your account is real.", fontSize = 13.sp, color = Color.Gray)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(business, { business = it.take(60) }, label = { Text("Business or brand name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            categories.forEachIndexed { i, n -> FilterChip(selected = category == i, onClick = { category = i }, label = { Text(n) }) }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(link, { link = it.take(120) }, label = { Text("Website or social link") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(reason, { reason = it.take(300) }, label = { Text("Why should it be verified?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                    }
                }
            }
        },
        confirmButton = {
            if (!profile.verified && profile.verifyRequest.isBlank()) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (business.isBlank() || link.isBlank()) { error = "Add the name and a link."; return@TextButton }
                        val json = JSONObject()
                            .put("username", profile.name).put("business", business.trim()).put("category", categories[category])
                            .put("link", link.trim()).put("reason", reason.trim()).put("at", System.currentTimeMillis()).toString()
                        scope.launch {
                            busy = true
                            val e = Verification.provider.submit(json)
                            if (e == null) { vm.markVerificationRequested(json); error = null } else error = e
                            busy = false
                        }
                    }
                ) { Text("Send request") }
            } else TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = { if (!profile.verified && profile.verifyRequest.isBlank()) TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
