package com.skillmatch.app.ui.online

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.skillmatch.app.ui.components.MessageBanner
import com.skillmatch.app.ui.components.MessageKind
import com.skillmatch.app.ui.components.PrimaryButton
import com.skillmatch.app.ui.components.ScreenTitle
import com.skillmatch.app.ui.components.SecondaryButton
import com.skillmatch.app.ui.components.SmCard
import com.skillmatch.app.ui.theme.Sm
import java.io.ByteArrayOutputStream

private const val MAX_CV_BYTES = 2 * 1024 * 1024

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillsScreen(state: OnlineState, vm: OnlineViewModel, onSaved: () -> Unit) {
    val edit = state.edit
    val cv = state.cv
    val context = LocalContext.current
    var skillText by rememberSaveable { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) readCv(context, uri, vm)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("My skills", Sm.PlumDark)
        Text(
            "Your skills are compared with what each listing asks for. They stay on your account and you can change or delete them at any time.",
            style = MaterialTheme.typography.bodyMedium, color = Sm.Muted,
        )
        state.notice?.let { NoticeBanner(it, vm::clearNotice) }

        // ----- CV -----
        SmCard {
            Text("Start from your CV", style = MaterialTheme.typography.titleMedium, color = Sm.PlumDark)
            Text(
                "Upload a PDF, DOCX or TXT file (2 MB at most). The server reads it, suggests skills, and does not keep the file. You choose which skills to add.",
                style = MaterialTheme.typography.bodySmall, color = Sm.Muted,
            )
            cv.error?.let { MessageBanner(it, MessageKind.Error) }
            if (cv.busy) {
                Text("Reading your CV…", style = MaterialTheme.typography.bodyMedium, color = Sm.BlueDeep)
            } else {
                SecondaryButton(
                    "Choose a CV file",
                    onClick = {
                        picker.launch(
                            arrayOf(
                                "application/pdf",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "text/plain",
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val found = cv.found
            if (found != null) {
                CvPicker(found.matched, found.other, onAdd = vm::acceptCvSkills, onCancel = vm::dismissCv)
            }
        }

        // ----- About you -----
        SmCard {
            Text("About you", style = MaterialTheme.typography.titleMedium, color = Sm.PlumDark)
            Text("Year of study", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..6).forEach { y -> SelectableChip("Year $y", edit.year == y, onClick = { vm.setYear(y) }) }
            }
            SmTextField(edit.programme, vm::setProgramme, "Programme (for example BICS)")
            SmTextField(
                edit.units, vm::setUnits, "Units you have done (one per line)",
                singleLine = false, minLines = 3, imeAction = ImeAction.Default,
                supporting = "For example: Database Systems, Data Structures and Algorithms",
            )
        }

        // ----- Skills -----
        SmCard {
            Text("Your skills", style = MaterialTheme.typography.titleMedium, color = Sm.PlumDark)
            if (edit.skills.isEmpty()) {
                Text("No skills yet. Add them below or start from your CV.", style = MaterialTheme.typography.bodyMedium, color = Sm.Muted)
            } else {
                Text("Tap a skill to remove it.", style = MaterialTheme.typography.bodySmall, color = Sm.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    edit.skills.forEach { name -> SelectableChip("$name  ✕", true, onClick = { vm.removeSkill(name) }) }
                }
            }
            SmTextField(
                skillText, { skillText = it; vm.setSkillQuery(it) }, "Add a skill",
                imeAction = ImeAction.Done, onAction = { vm.addSkill(skillText); skillText = "" },
            )
            if (edit.suggestions.isNotEmpty() && skillText.isNotBlank()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    edit.suggestions.forEach { s -> SelectableChip(s, false, onClick = { vm.addSkill(s); skillText = "" }) }
                }
            }
            SecondaryButton(
                "Add “${skillText.trim().ifEmpty { "skill" }}”",
                onClick = { vm.addSkill(skillText); skillText = "" },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        edit.error?.let { MessageBanner(it, MessageKind.Error) }
        PrimaryButton(if (edit.saving) "Saving…" else "Save and see my fit", onClick = { vm.saveEdit(onSaved) }, enabled = !edit.saving)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CvPicker(matched: List<String>, other: List<String>, onAdd: (List<String>) -> Unit, onCancel: () -> Unit) {
    var picked by remember(matched, other) { mutableStateOf(matched.toSet()) }
    fun toggle(name: String) { picked = if (name in picked) picked - name else picked + name }

    if (matched.isEmpty() && other.isEmpty()) {
        MessageBanner("No skills were found in that file. You can still add them by hand.", MessageKind.Info)
        SecondaryButton("Close", onClick = onCancel, modifier = Modifier.fillMaxWidth())
        return
    }
    if (matched.isNotEmpty()) {
        Text("Found in your CV", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            matched.forEach { n -> SelectableChip(n, n in picked, onClick = { toggle(n) }) }
        }
    }
    if (other.isNotEmpty()) {
        Text("Other items from your skills section", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            other.forEach { n -> SelectableChip(n, n in picked, onClick = { toggle(n) }) }
        }
    }
    PrimaryButton("Add ${picked.size} selected", onClick = { onAdd(picked.toList()) }, icon = Icons.Filled.Add, enabled = picked.isNotEmpty())
    SecondaryButton("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
}

/** Reads the chosen file into memory (2 MB limit) and sends it to the server. */
private fun readCv(context: Context, uri: Uri, vm: OnlineViewModel) {
    try {
        val resolver = context.contentResolver
        var name = "cv"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) c.getString(i)?.let { name = it }
            }
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val out = ByteArrayOutputStream()
        val input = resolver.openInputStream(uri)
        if (input == null) {
            vm.cvFailed("The app could not open that file.")
            return
        }
        input.use {
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_CV_BYTES) {
                    vm.cvFailed("That file is too large (2 MB at most).")
                    return
                }
                out.write(buf, 0, n)
            }
        }
        vm.uploadCv(name, mime, out.toByteArray())
    } catch (e: Exception) {
        vm.cvFailed("The app could not read that file.")
    }
}
