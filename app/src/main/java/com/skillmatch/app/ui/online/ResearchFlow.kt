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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skillmatch.app.ui.components.BackLink
import com.skillmatch.app.ui.components.MessageBanner
import com.skillmatch.app.ui.components.MessageKind
import com.skillmatch.app.ui.components.PrimaryButton
import com.skillmatch.app.ui.components.ScreenTitle
import com.skillmatch.app.ui.components.SecondaryButton
import com.skillmatch.app.ui.components.SmCard
import com.skillmatch.app.ui.theme.Sm

private val susItems = listOf(
    "I think that I would like to use this system frequently.",
    "I found the system unnecessarily complex.",
    "I thought the system was easy to use.",
    "I think that I would need the support of a technical person to be able to use this system.",
    "I found the various functions in this system were well integrated.",
    "I thought there was too much inconsistency in this system.",
    "I would imagine that most people would learn to use this system very quickly.",
    "I found the system very cumbersome to use.",
    "I felt very confident using the system.",
    "I needed to learn a lot of things before I could get going with this system.",
)

private val tamUseful = listOf(
    "Using this app would help me find suitable internships more quickly.",
    "Using this app would make me more effective at finding suitable internships.",
    "I would find this app useful when looking for an internship.",
)

private val tamEase = listOf(
    "Learning to use this app would be easy for me.",
    "I would find it easy to get this app to do what I want.",
    "It would be easy for me to become skilful at using this app.",
)

/** The study screens that sit around the listings: setup, finishing, the survey and the thank-you. */
@Composable
fun ResearchFlow(state: OnlineState, vm: OnlineViewModel) {
    val r = state.research
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (r.phase) {
            ResearchPhase.Setup -> SetupStep(r, vm)
            ResearchPhase.Finish -> FinishStep(r, vm)
            ResearchPhase.Survey -> SurveyStep(r, vm)
            ResearchPhase.Done -> DoneStep(r, vm)
            else -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupStep(r: ResearchState, vm: OnlineViewModel) {
    var condition by rememberSaveable { mutableStateOf("") }
    var set by rememberSaveable { mutableStateOf("") }
    BackLink("Back", { vm.closeResearch() }, Sm.Plum)
    ScreenTitle("Start a study session", Sm.PlumDark)
    Text(
        "Your researcher will tell you which options to choose. The session is timed from the moment you tap Start. " +
            "It records what you open, search and shortlist under your participant code.",
        style = MaterialTheme.typography.bodyMedium, color = Sm.Muted,
    )
    SmCard {
        Text("How will you search?", style = MaterialTheme.typography.titleMedium, color = Sm.PlumDark)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableChip("Without fit scores", condition == "manual", onClick = { condition = "manual" })
            SelectableChip("With fit scores", condition == "app", onClick = { condition = "app" })
        }
        Text("Which set of listings?", style = MaterialTheme.typography.titleMedium, color = Sm.PlumDark)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableChip("Set A", set == "A", onClick = { set = "A" })
            SelectableChip("Set B", set == "B", onClick = { set = "B" })
        }
    }
    r.error?.let { MessageBanner(it, MessageKind.Error) }
    PrimaryButton(
        if (r.busy) "Starting…" else "Start",
        onClick = { vm.startResearch(condition, set) },
        enabled = !r.busy && condition.isNotEmpty() && set.isNotEmpty(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinishStep(r: ResearchState, vm: OnlineViewModel) {
    var confidence by rememberSaveable { mutableStateOf(0) }
    ScreenTitle("Finish the session", Sm.PlumDark)
    Text(
        "You shortlisted ${r.shortlist.size} ${if (r.shortlist.size == 1) "listing" else "listings"}.",
        style = MaterialTheme.typography.bodyLarge, color = Sm.Ink,
    )
    SmCard {
        Text("How confident are you that your shortlist fits you?", style = MaterialTheme.typography.titleMedium, color = Sm.PlumDark)
        Text("1 is not confident, 5 is very confident.", style = MaterialTheme.typography.bodySmall, color = Sm.Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { n -> SelectableChip("$n", confidence == n, onClick = { confidence = n }) }
        }
    }
    r.error?.let { MessageBanner(it, MessageKind.Error) }
    PrimaryButton(
        if (r.busy) "Saving…" else "Finish and answer a short survey",
        onClick = { vm.endResearch(confidence) }, enabled = !r.busy && confidence > 0,
    )
    SecondaryButton("Back to the listings", onClick = { vm.backToListings() }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SurveyStep(r: ResearchState, vm: OnlineViewModel) {
    val showTam = r.condition == "app"
    val sus = remember { mutableStateListOf<Int>().apply { repeat(susItems.size) { add(0) } } }
    val useful = remember { mutableStateListOf<Int>().apply { repeat(tamUseful.size) { add(0) } } }
    val ease = remember { mutableStateListOf<Int>().apply { repeat(tamEase.size) { add(0) } } }
    val complete = sus.all { it > 0 } && (!showTam || (useful.all { it > 0 } && ease.all { it > 0 }))

    ScreenTitle("Short survey", Sm.PlumDark)
    Text(
        "For each statement, choose how much you agree. 1 is strongly disagree and 5 is strongly agree. There are no right or wrong answers.",
        style = MaterialTheme.typography.bodyMedium, color = Sm.Muted,
    )
    SmCard {
        Text("About the way you searched", style = MaterialTheme.typography.titleMedium, color = Sm.PlumDark)
        susItems.forEachIndexed { i, q -> Likert(q, sus[i]) { sus[i] = it } }
    }
    if (showTam) {
        SmCard {
            Text("About this app", style = MaterialTheme.typography.titleMedium, color = Sm.PlumDark)
            tamUseful.forEachIndexed { i, q -> Likert(q, useful[i]) { useful[i] = it } }
            tamEase.forEachIndexed { i, q -> Likert(q, ease[i]) { ease[i] = it } }
        }
    }
    r.error?.let { MessageBanner(it, MessageKind.Error) }
    PrimaryButton(
        if (r.busy) "Sending…" else "Send my answers",
        onClick = {
            vm.submitSurvey(sus.toList(), if (showTam) useful.toList() else emptyList(), if (showTam) ease.toList() else emptyList())
        },
        enabled = !r.busy && complete,
    )
}

@Composable
private fun Likert(question: String, value: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(question, style = MaterialTheme.typography.bodyMedium, color = Sm.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { n -> SelectableChip("$n", value == n, onClick = { onPick(n) }) }
        }
    }
}

@Composable
private fun DoneStep(r: ResearchState, vm: OnlineViewModel) {
    ScreenTitle("Thank you", Sm.PlumDark)
    SmCard {
        Text("Your answers were saved under your participant code.", style = MaterialTheme.typography.bodyLarge, color = Sm.Ink)
        r.seconds?.let {
            Text("Your session took about ${Math.round(it / 60.0)} minutes.", style = MaterialTheme.typography.bodyMedium, color = Sm.Muted)
        }
    }
    PrimaryButton("Back to SkillMatch", onClick = { vm.closeResearch() })
}
