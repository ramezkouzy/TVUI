package com.ramez.shasha.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramez.shasha.AppState
import com.ramez.shasha.data.Account
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(state: AppState) {
    val saved = state.prefs.account
    var server by remember { mutableStateOf(saved?.server ?: "") }
    var user by remember { mutableStateOf(saved?.username ?: "") }
    var pass by remember { mutableStateOf(saved?.password ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) { first.tryFocus() }

    fun submit() {
        if (busy) return
        if (server.isBlank() || user.isBlank()) { error = "Fill in the server, username and password."; return }
        busy = true; error = null
        scope.launch {
            error = state.signIn(Account(server, user, pass))
            busy = false
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            T("Shasha", size = 44.sp, weight = FontWeight.Bold)
            T("Sign in with the details from your IPTV provider (Xtream Codes).", size = 19.sp, color = C.muted)
            InputField("Server URL", server, { server = it }, keyboard = KeyboardType.Uri, focusRequester = first)
            InputField("Username", user, { user = it })
            InputField("Password", pass, { pass = it }, password = true, onDone = { submit() })
            if (error != null) T(error!!, size = 17.sp, color = androidx.compose.ui.graphics.Color(0xFFFCA5A5))
            AppButton(if (busy) "Signing in…" else "Sign in", onClick = { submit() }, primary = true)
        }
    }
}
