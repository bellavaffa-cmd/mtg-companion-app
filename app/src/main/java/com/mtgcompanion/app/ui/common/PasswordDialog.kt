package com.mtgcompanion.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.supabase.SupabaseAuth
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Choose a new password — shown after a password-reset link reopens the app (already signed in by
 * the link), and from Settings → Change password. [onDone] receives a confirmation message.
 */
@Composable
fun SetPasswordDialog(
    auth: SupabaseAuth,
    title: String,
    explanation: String,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    val app = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val mismatch = confirm.isNotEmpty() && confirm != password
    val canSave = !busy && password.length >= 6 && password == confirm

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = app.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            KeepSystemBarsHidden()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(explanation, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("New password", color = app.textMuted) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = { Text("Repeat new password", color = app.textMuted) },
                    singleLine = true,
                    isError = mismatch,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    when {
                        mismatch -> "The passwords don't match."
                        else -> "At least 6 characters."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (mismatch) app.warning else app.textDim
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = app.warning) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            auth.updatePassword(password)
                            onDone("Password changed. Use the new one next time you sign in.")
                        } catch (e: Exception) {
                            error = if (e is IOException) "Can't reach the server — check your connection." else e.message
                        }
                        busy = false
                    }
                },
                enabled = canSave,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = app.accent, contentColor = app.onAccent)
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = app.onAccent)
                else Text("Save password", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Not now", color = app.textMuted) }
        }
    )
}
