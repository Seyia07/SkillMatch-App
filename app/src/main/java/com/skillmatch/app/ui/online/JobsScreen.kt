package com.skillmatch.app.ui.online

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.skillmatch.app.net.JobSummary
import com.skillmatch.app.ui.components.EmptyState
import com.skillmatch.app.ui.components.LabelStyle
import com.skillmatch.app.ui.components.MessageBanner
import com.skillmatch.app.ui.components.MessageKind
import com.skillmatch.app.ui.components.PrimaryButton
import com.skillmatch.app.ui.components.ScreenTitle
import com.skillmatch.app.ui.components.SecondaryButton
import com.skillmatch.app.ui.components.SmCard
import com.skillmatch.app.ui.theme.Sm

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobsScreen(state: OnlineState, vm: OnlineViewModel, onOpen: (Int) -> Unit, onEditSkills: () -> Unit) {
    val jobs = state.jobs
    val research = state.research
    var query by rememberSaveable { mutableStateOf(jobs.query) }

    LaunchedEffect(jobs.loaded, jobs.loading) {
        if (!jobs.loaded && !jobs.loading) vm.loadJobs()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScreenTitle(if (research.active) "Study session" else "Internships and WBL", Sm.PlumDark)
                state.notice?.let { NoticeBanner(it, vm::clearNotice) }
                if (research.active) ResearchBanner(research, onFinish = { vm.beginFinish() })
                SmTextField(
                    query, { query = it; vm.setQuery(it) }, "Search title or organisation",
                    imeAction = ImeAction.Search, onAction = { vm.search() },
                )
                PrimaryButton("Search", onClick = { vm.search() }, icon = Icons.Filled.Search)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableChip("All", jobs.type == null, onClick = { vm.setType(null) })
                    SelectableChip("Internships", jobs.type == "internship", onClick = { vm.setType("internship") })
                    SelectableChip("WBL placements", jobs.type == "wbl", onClick = { vm.setType("wbl") })
                    if (!research.active) SelectableChip("Saved only", jobs.onlySaved, onClick = { vm.setOnlySaved(!jobs.onlySaved) })
                }
                if (jobs.needsSkills && research.showFit) {
                    SmCard {
                        Text("Add your skills to see how well you fit each listing.", style = MaterialTheme.typography.bodyMedium, color = Sm.Ink)
                        PrimaryButton("Add my skills", onClick = onEditSkills)
                    }
                }
                jobs.error?.let {
                    MessageBanner(it, MessageKind.Error)
                    SecondaryButton("Try again", onClick = { vm.loadJobs() }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (jobs.loading && jobs.items.isEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Sm.Plum, trackColor = Sm.BabyBlue)
                    Text("  Loading listings…", color = Sm.BlueDeep, style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else if (jobs.loaded && jobs.error == null && jobs.items.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = if (jobs.onlySaved) "Nothing saved yet" else "No listings found",
                    body = if (jobs.onlySaved) "Open a listing and tap Save to keep it here." else "Try a different search, or check back later. Only listings an admin has checked are shown.",
                    action = null, onAction = {},
                )
            }
        }
        items(jobs.items, key = { it.id }) { job ->
            JobCard(
                job = job,
                showFit = research.showFit,
                research = research,
                onOpen = { onOpen(job.id) },
                onShortlist = { vm.toggleShortlist(job.id) },
            )
        }
    }
}

@Composable
private fun ResearchBanner(r: ResearchState, onFinish: () -> Unit) {
    SmCard {
        Text(
            "Set ${r.listingSet} · " + if (r.condition == "app") "fit shown" else "fit hidden",
            style = MaterialTheme.typography.titleMedium, color = Sm.PlumDark,
        )
        Text(
            "Look through the listings and tap the star on the ones you would apply for. " +
                    "Shortlisted: ${r.shortlist.size}",
            style = MaterialTheme.typography.bodyMedium, color = Sm.Muted,
        )
        PrimaryButton("Finish session", onClick = onFinish)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobCard(job: JobSummary, showFit: Boolean, research: ResearchState, onOpen: () -> Unit, onShortlist: () -> Unit) {
    SmCard(Modifier.clickable(onClickLabel = "Open listing", onClick = onOpen)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(job.title, style = MaterialTheme.typography.titleMedium, color = Sm.Ink)
                Text(job.organisation, style = MaterialTheme.typography.bodyMedium, color = Sm.Muted)
            }
            if (research.active) {
                val on = job.id in research.shortlist
                IconButton(onClick = onShortlist) {
                    Icon(
                        if (on) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (on) "Remove from shortlist" else "Add to shortlist",
                        tint = Sm.Plum,
                    )
                }
            } else if (job.saved) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Saved", tint = Sm.Plum)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StyleChip(LabelStyle(job.typeLabel, Sm.PlumTint, Sm.PlumDark, Sm.PlumSoft, Icons.Filled.Work))
            prettyDate(job.closingDate)?.let {
                StyleChip(LabelStyle("Closes $it", Sm.BlueBg, Sm.BlueDeep, Sm.BlueLine, Icons.Filled.Event))
            }
            val w = job.warnings.size
            if (w > 0) {
                StyleChip(LabelStyle(if (w == 1) "1 warning" else "$w warnings", Sm.BrownTint, Sm.BrownDark, Sm.Brown, Icons.Filled.Warning))
            }
        }
        val fit = job.fit
        if (showFit && fit != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StyleChip(fitStyle(fit.label))
                if (fit.overall != null) {
                    Text("${fit.overall}% match", style = MaterialTheme.typography.bodyMedium, color = Sm.Muted)
                }
            }
        }
    }
}