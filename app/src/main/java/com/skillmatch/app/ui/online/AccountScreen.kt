package com.skillmatch.app.ui.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skillmatch.app.ui.components.MessageBanner
import com.skillmatch.app.ui.components.MessageKind
import com.skillmatch.app.ui.components.PrimaryButton
import com.skillmatch.app.ui.components.ScreenTitle
import com.skillmatch.app.ui.components.SecondaryButton
import com.skillmatch.app.ui.components.SmCard
import com.skillmatch.app.ui.theme.Sm

@Composable
fun AccountScreen(state: OnlineState, vm: OnlineViewModel) {
    val p = state.profile
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Account", Sm.PlumDark)
        state.notice?.let { NoticeBanner(it, vm::clearNotice) }

        if (p != null) {
            SmCard {
                Text(p.email, style = MaterialTheme.typography.titleMedium, color = Sm.Ink)
                val about = listOfNotNull(
                    p.year?.let { "Year $it" },
                    p.programme.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (about.isNotEmpty()) Text(about, style = MaterialTheme.typography.bodyMedium, color = Sm.Muted)
                Text("${p.skills.size} skills saved", style = MaterialTheme.typography.bodyMedium, color = Sm.Muted)
                if (p.participantCode.isNotBlank()) {
                    Text(
                        "Your participant code: ${p.participantCode}. Research data is stored under this code, never your email.",
                        style = MaterialTheme.typography.bodySmall, color = Sm.Faint,
                    )
                }
            }
        }

        SmCard {
            Text("Study session", style = MaterialTheme.typography.titleMedium, color = Sm.PlumDark)
            Text(
                "Taking part in the research? Your researcher will tell you when to start. A session is timed and records what you open and shortlist, under your participant code.",
                style = MaterialTheme.typography.bodyMedium, color = Sm.Muted,
            )
            SecondaryButton("Start a study session", onClick = { vm.openResearchSetup() }, modifier = Modifier.fillMaxWidth())
        }

        SmCard {
            Text("How matching works", style = MaterialTheme.typography.titleMedium, color = Sm.PlumDark)
            Text(
                "Each listing is split into its separate requirements. Your skills are compared with each one, and every requirement is marked Met, Partly met or Missing. " +
                        "The overall fit comes from a model trained on real resume and job pairs. It is a guide only. Admins check each listing before it appears, and listings with warning signs are flagged.",
                style = MaterialTheme.typography.bodyMedium, color = Sm.Muted,
            )
        }


        SecondaryButton("Log out", onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth())

        SmCard {
            Text("Delete my account", style = MaterialTheme.typography.titleMedium, color = Sm.BerryDark)
            Text(
                "This permanently deletes your account, skills, saved listings and research records.",
                style = MaterialTheme.typography.bodyMedium, color = Sm.Muted,
            )
            if (!confirmDelete) {
                SecondaryButton("Delete my account", onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), color = Sm.BerryDark)
            } else {
                SmTextField(
                    password, { password = it }, "Your password", password = true,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done, onAction = { vm.deleteAccount(password) },
                )
                state.accountError?.let { MessageBanner(it, MessageKind.Error) }
                PrimaryButton(
                    if (state.accountBusy) "Deleting…" else "Yes, delete everything",
                    onClick = { vm.deleteAccount(password) }, enabled = !state.accountBusy,
                )
                SecondaryButton("Cancel", onClick = { confirmDelete = false; password = "" }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}