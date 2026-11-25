package com.skillmatch.app.ui.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.skillmatch.app.net.JobDetail
import com.skillmatch.app.net.sourceLabel
import com.skillmatch.app.ui.components.BackLink
import com.skillmatch.app.ui.components.LabelStyle
import com.skillmatch.app.ui.components.MessageBanner
import com.skillmatch.app.ui.components.MessageKind
import com.skillmatch.app.ui.components.PrimaryButton
import com.skillmatch.app.ui.components.ScreenTitle
import com.skillmatch.app.ui.components.SecondaryButton
import com.skillmatch.app.ui.components.SmCard
import com.skillmatch.app.ui.theme.Sm

private val reportReasons = listOf(
    "fee_requested" to "They asked for money",
    "scam_or_fake" to "Looks like a scam or fake",
    "expired" to "Already closed",
    "wrong_information" to "Wrong information",
    "other" to "Something else",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnlineDetailScreen(state: OnlineState, vm: OnlineViewModel, id: Int, onBack: () -> Unit, onEditSkills: () -> Unit) {
    LaunchedEffect(id) { vm.openDetail(id) }
    val d = state.detail
    val research = state.research
    var reporting by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BackLink("Back to listings", onBack, Sm.Plum)
        state.notice?.let { NoticeBanner(it, vm::clearNotice) }
        val job = d.detail
        if (d.id != id || d.loading) {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Sm.Plum, trackColor = Sm.BabyBlue)
                Text("  Loading…", color = Sm.BlueDeep, style = MaterialTheme.typography.bodyLarge)
            }
        } else if (d.error != null) {
            MessageBanner(d.error, MessageKind.Error)
            SecondaryButton("Try again", onClick = { vm.openDetail(id) }, modifier = Modifier.fillMaxWidth())
        } else if (job != null) {
            DetailBody(job, state, vm, onEditSkills, onReport = { reporting = true })
        }
    }

    if (reporting) {
        ReportDialog(
            onDismiss = { reporting = false },
            onSend = { reason, note -> vm.report(reason, note); reporting = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(job: JobDetail, state: OnlineState, vm: OnlineViewModel, onEditSkills: () -> Unit, onReport: () -> Unit) {
    val s = job.summary
    val research = state.research
    val uri = LocalUriHandler.current
    fun open(url: String) {
        try { uri.openUri(url) } catch (e: Exception) { vm.setNotice("The app could not open that link.") }
    }

    ScreenTitle(s.title, Sm.PlumDark)
    Text(s.organisation, style = MaterialTheme.typography.titleMedium, color = Sm.Muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StyleChip(LabelStyle(s.typeLabel, Sm.PlumTint, Sm.PlumDark, Sm.PlumSoft, Icons.Filled.Work))
        prettyDate(s.closingDate)?.let {
            StyleChip(LabelStyle("Closes $it", Sm.BlueBg, Sm.BlueDeep, Sm.BlueLine, Icons.Filled.Event))
        }
    }

    s.flags.forEach { f -> MessageBanner(f.message, if (f.isWarning) MessageKind.Warning else MessageKind.Info) }
    if (job.note.isNotBlank()) MessageBanner(job.note, MessageKind.Info)

    if (research.showFit) {
        val fit = s.fit
        if (job.needsSkills || fit == null) {
            SmCard {
                Text("Add your skills to see how you fit this listing.", style = MaterialTheme.typography.bodyMedium, color = Sm.Ink)
                PrimaryButton("Add my skills", onClick = onEditSkills)
            }
        } else {
            SmCard {
                Text("Your fit", style = MaterialTheme.typography.titleMedium, color = Sm.PlumDark)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StyleChip(fitStyle(fit.label))
                    if (fit.overall != null) Text("${fit.overall}%", style = MaterialTheme.typography.titleMedium, color = Sm.Ink)
                }
                Text(
                    "Met ${fit.met} · Partly met ${fit.partly} · Missing ${fit.missing}",
                    style = MaterialTheme.typography.bodyMedium, color = Sm.Muted,
                )
                Text(
                    "This is a guide to help you decide where to look closer. It is not a promise about what an employer will decide.",
                    style = MaterialTheme.typography.bodySmall, color = Sm.Faint,
                )
            }
        }
    }

    SmCard {
        Text("What the listing asks for", style = MaterialTheme.typography.titleMedium, color = Sm.PlumDark)
        if (job.requirements.isEmpty()) {
            Text("No separate requirements were listed.", style = MaterialTheme.typography.bodyMedium, color = Sm.Muted)
        }
        job.requirements.forEach { r ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.text, style = MaterialTheme.typography.bodyLarge, color = Sm.Ink)
                if (research.showFit && r.status != null) {
                    StyleChip(statusStyle(r.status))
                    if (!r.matchedWith.isNullOrBlank()) {
                        Text("Closest skill of yours: ${r.matchedWith}", style = MaterialTheme.typography.bodySmall, color = Sm.Muted)
                    }
                    if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall, color = Sm.Muted)
                }
            }
        }
    }

    if (job.description.isNotBlank()) {
        SmCard {
            Text("Description", style = MaterialTheme.typography.titleMedium, color = Sm.PlumDark)
            Text(job.description, style = MaterialTheme.typography.bodyMedium, color = Sm.Ink)
        }
    }

    SmCard {
        Text("Where it came from", style = MaterialTheme.typography.titleMedium, color = Sm.PlumDark)
        Text(sourceLabel(s.source), style = MaterialTheme.typography.bodyMedium, color = Sm.Ink)
        if (job.contactEmail.isNotBlank()) Text("Contact: ${job.contactEmail}", style = MaterialTheme.typography.bodyMedium, color = Sm.Ink)
        if (s.applyUrl.isNotBlank()) PrimaryButton("Open the apply page", onClick = { open(s.applyUrl) })
        if (s.sourceUrl.isNotBlank()) SecondaryButton("Open the original post", onClick = { open(s.sourceUrl) }, modifier = Modifier.fillMaxWidth())
    }

    if (research.active) {
        val on = s.id in research.shortlist
        PrimaryButton(
            if (on) "Remove from my shortlist" else "Add to my shortlist",
            onClick = { vm.toggleShortlist(s.id) },
            icon = if (on) Icons.Filled.Star else Icons.Filled.StarBorder,
        )
    } else {
        PrimaryButton(
            if (s.saved) "Remove from saved" else "Save this listing",
            onClick = { vm.toggleSaved() },
            icon = if (s.saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
        )
    }
    SecondaryButton("Report a problem with this listing", onClick = onReport, modifier = Modifier.fillMaxWidth(), color = Sm.BerryDark)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Report this listing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("What is wrong with it?", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reportReasons.forEach { (code, text) ->
                        SelectableChip(text, reason == code, onClick = { reason = code })
                    }
                }
                SmTextField(
                    note, { note = it.take(500) }, "More detail (optional)",
                    singleLine = false, minLines = 2, imeAction = ImeAction.Default,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(reason, note) }, enabled = reason.isNotEmpty()) {
                Text("Send report", color = if (reason.isNotEmpty()) Sm.Plum else Sm.Faint)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Sm.Plum) } },
    )
}
