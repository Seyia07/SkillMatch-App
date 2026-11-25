package com.skillmatch.app.ui.online

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.skillmatch.app.ui.components.LabelStyle
import com.skillmatch.app.ui.components.MessageBanner
import com.skillmatch.app.ui.components.MessageKind
import com.skillmatch.app.ui.components.PrimaryButton
import com.skillmatch.app.ui.components.SmCard
import com.skillmatch.app.ui.theme.Sm
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SmTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    imeAction: ImeAction = ImeAction.Next,
    onAction: (() -> Unit)? = null,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = modifier.then(Modifier.heightIn(min = 56.dp)),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onDone = { onAction?.invoke() },
            onSearch = { onAction?.invoke() },
            onGo = { onAction?.invoke() },
        ),
        supportingText = if (supporting != null) { { Text(supporting) } } else null,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Sm.Plum,
            focusedLabelColor = Sm.Plum,
            cursorColor = Sm.Plum,
            unfocusedBorderColor = Sm.BlueLine,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
        ),
    )
}

@Composable
fun SelectableChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) Sm.Plum else Color.White)
            .border(BorderStroke(2.dp, if (selected) Sm.Plum else Sm.BlueLine), shape)
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Color.White else Sm.Plum,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Colour, icon and words together, so colour is never the only signal. */
fun fitStyle(label: String): LabelStyle = when (label) {
    "Strong fit" -> LabelStyle("Strong fit", Sm.GreenTint, Sm.GreenDark, Sm.Green, Icons.Filled.Check)
    "Possible fit" -> LabelStyle("Possible fit", Sm.BrownTint, Sm.BrownDark, Sm.Brown, Icons.Filled.Remove)
    "Low fit" -> LabelStyle("Low fit", Sm.BerryTint, Sm.BerryDark, Sm.Berry, Icons.Filled.Close)
    else -> LabelStyle(label, Sm.BlueBg, Sm.BlueDeep, Sm.BlueLine, Icons.Filled.Info)
}

fun statusStyle(status: String?): LabelStyle = when (status?.lowercase()) {
    "met" -> LabelStyle("Met", Sm.GreenTint, Sm.GreenDark, Sm.Green, Icons.Filled.Check)
    "partly met" -> LabelStyle("Partly met", Sm.BrownTint, Sm.BrownDark, Sm.Brown, Icons.Filled.Remove)
    "missing" -> LabelStyle("Missing", Sm.BerryTint, Sm.BerryDark, Sm.Berry, Icons.Filled.Close)
    else -> LabelStyle("Not checked", Sm.BlueBg, Sm.BlueDeep, Sm.BlueLine, Icons.Filled.Info)
}

@Composable
fun StyleChip(style: LabelStyle, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(style.bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(style.icon, contentDescription = null, tint = style.fg, modifier = Modifier.size(16.dp))
        Text(style.text, color = style.fg, style = MaterialTheme.typography.labelMedium)
    }
}

fun prettyDate(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
    } catch (e: Exception) {
        iso
    }
}

@Composable
fun NoticeBanner(notice: String?, onDismiss: () -> Unit) {
    if (notice == null) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { MessageBanner(notice, MessageKind.Info) }
        TextButton(onClick = onDismiss) {
            Text("OK", color = Sm.Plum, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun ConnectionCard(serverUrl: String, error: String?, onSave: (String) -> Unit) {
    var text by remember(serverUrl) { mutableStateOf(serverUrl) }
    SmCard {
        Text("Server address", style = MaterialTheme.typography.titleMedium)
        Text(
            "The emulator reaches your computer at http://10.0.2.2:8000. A phone on the same Wi-Fi uses your computer's address, for example http://192.168.1.20:8000.",
            style = MaterialTheme.typography.bodySmall, color = Sm.Muted,
        )
        SmTextField(
            text, { text = it }, "Server address", keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done, onAction = { onSave(text) },
        )
        if (error != null) MessageBanner(error, MessageKind.Error)
        PrimaryButton("Save address", onClick = { onSave(text) })
    }
}
