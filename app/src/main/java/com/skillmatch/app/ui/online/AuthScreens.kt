package com.skillmatch.app.ui.online

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skillmatch.app.R
import com.skillmatch.app.ui.components.MessageBanner
import com.skillmatch.app.ui.components.MessageKind
import com.skillmatch.app.ui.components.PrimaryButton
import com.skillmatch.app.ui.components.ScreenTitle
import com.skillmatch.app.ui.components.SecondaryButton
import com.skillmatch.app.ui.components.SmCard
import com.skillmatch.app.ui.theme.Sm

/** Log in, sign up with an emailed code, and reset a password. One screen, several steps. */
@Composable
fun AuthPane(state: OnlineState, vm: OnlineViewModel) {
    val auth = state.auth
    Box(Modifier.fillMaxSize()) {
        Image(
            painterResource(R.drawable.photo_team), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Sm.PlumDark.copy(alpha = 0.72f), Sm.PlumDark.copy(alpha = 0.45f), Sm.PlumDark.copy(alpha = 0.85f))),
            ),
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)) {
                ScreenTitle(
                    when (auth.step) {
                        AuthStep.Login -> "Welcome back"
                        AuthStep.SignupEmail, AuthStep.SignupCode, AuthStep.SignupDetails -> "Create your account"
                        AuthStep.ForgotEmail, AuthStep.ForgotReset -> "Reset your password"
                    },
                    Color.White,
                )
                Text(
                    "Checked internship and WBL listings, with your fit shown requirement by requirement.",
                    color = Sm.PlumTint, style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                state.notice?.let { NoticeBanner(it, vm::clearNotice) }
                auth.error?.let { MessageBanner(it, MessageKind.Error) }
                auth.info?.let { MessageBanner(it, MessageKind.Info) }
                auth.devCode?.let { MessageBanner("Demo code: $it", MessageKind.Warning) }
                when (auth.step) {
                    AuthStep.Login -> LoginStep(auth, vm)
                    AuthStep.SignupEmail -> SignupEmailStep(auth, vm)
                    AuthStep.SignupCode -> SignupCodeStep(auth, vm)
                    AuthStep.SignupDetails -> SignupDetailsStep(auth, vm)
                    AuthStep.ForgotEmail -> ForgotEmailStep(auth, vm)
                    AuthStep.ForgotReset -> ForgotResetStep(auth, vm)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LoginStep(auth: AuthState, vm: OnlineViewModel) {
    var email by rememberSaveable { mutableStateOf(auth.email) }
    var password by remember { mutableStateOf("") }
    SmCard {
        SmTextField(email, { email = it }, "Strathmore email", keyboardType = KeyboardType.Email)
        SmTextField(
            password, { password = it }, "Password", password = true,
            imeAction = ImeAction.Done, onAction = { vm.login(email, password) },
        )
        PrimaryButton(if (auth.busy) "Please wait…" else "Log in", onClick = { vm.login(email, password) }, enabled = !auth.busy)
        TextButton(onClick = { vm.goStep(AuthStep.ForgotEmail) }) {
            Text("Forgot password?", color = Sm.Plum, style = MaterialTheme.typography.labelLarge)
        }
    }
    SecondaryButton("Create an account", onClick = { vm.goStep(AuthStep.SignupEmail) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SignupEmailStep(auth: AuthState, vm: OnlineViewModel) {
    var email by rememberSaveable { mutableStateOf(auth.email) }
    SmCard {
        Text("Use your Strathmore email address. We send a 6 digit code to confirm it is yours.", style = MaterialTheme.typography.bodyMedium, color = Sm.Muted)
        SmTextField(
            email, { email = it }, "Strathmore email", keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done, onAction = { vm.requestSignupCode(email) },
        )
        PrimaryButton(if (auth.busy) "Please wait…" else "Send code", onClick = { vm.requestSignupCode(email) }, enabled = !auth.busy)
    }
    TextButton(onClick = { vm.goStep(AuthStep.Login) }) {
        Text("Back to log in", color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SignupCodeStep(auth: AuthState, vm: OnlineViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    SmCard {
        Text("Enter the code sent to ${auth.email}. It works once and expires in 10 minutes.", style = MaterialTheme.typography.bodyMedium, color = Sm.Muted)
        SmTextField(
            code, { code = it.filter { c -> c.isDigit() }.take(10) }, "Code", keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done, onAction = { vm.verifySignupCode(code) },
        )
        PrimaryButton(if (auth.busy) "Please wait…" else "Verify", onClick = { vm.verifySignupCode(code) }, enabled = !auth.busy)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { vm.requestSignupCode(auth.email) }) {
            Text("Send a new code", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = { vm.goStep(AuthStep.SignupEmail) }) {
            Text("Use another email", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignupDetailsStep(auth: AuthState, vm: OnlineViewModel) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var programme by rememberSaveable { mutableStateOf("") }
    var consent by rememberSaveable { mutableStateOf(false) }
    SmCard {
        Text("Email confirmed. Now set up your account.", style = MaterialTheme.typography.titleSmall)
        SmTextField(password, { password = it }, "Password", password = true, supporting = "At least 8 characters. The server tells you if it is too weak.")
        SmTextField(confirm, { confirm = it }, "Confirm password", password = true)
        Text("Year of study", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..6).forEach { y -> SelectableChip("Year $y", year == y, onClick = { year = y }) }
        }
        SmTextField(programme, { programme = it }, "Programme (for example BICS)", imeAction = ImeAction.Done)
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = consent, onCheckedChange = { consent = it },
                colors = CheckboxDefaults.colors(checkedColor = Sm.Plum),
            )
            Text(
                "I agree that my skills and how I use the app may be used for this research, shown only under a participant code and never my email. I can delete my account and all my data at any time in Account.",
                style = MaterialTheme.typography.bodySmall, color = Sm.Muted,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        PrimaryButton(
            if (auth.busy) "Please wait…" else "Create account",
            onClick = { vm.completeSignup(password, confirm, year, programme, consent) },
            enabled = !auth.busy,
        )
    }
}

@Composable
private fun ForgotEmailStep(auth: AuthState, vm: OnlineViewModel) {
    var email by rememberSaveable { mutableStateOf(auth.email) }
    SmCard {
        Text("Enter your Strathmore email. If it has an account, we send a code to reset the password.", style = MaterialTheme.typography.bodyMedium, color = Sm.Muted)
        SmTextField(
            email, { email = it }, "Strathmore email", keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done, onAction = { vm.requestReset(email) },
        )
        PrimaryButton(if (auth.busy) "Please wait…" else "Send code", onClick = { vm.requestReset(email) }, enabled = !auth.busy)
    }
    TextButton(onClick = { vm.goStep(AuthStep.Login) }) {
        Text("Back to log in", color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ForgotResetStep(auth: AuthState, vm: OnlineViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    SmCard {
        Text("Enter the code sent to ${auth.email} and choose a new password. All your other logins will be signed out.", style = MaterialTheme.typography.bodyMedium, color = Sm.Muted)
        SmTextField(code, { code = it.filter { c -> c.isDigit() }.take(10) }, "Code", keyboardType = KeyboardType.NumberPassword)
        SmTextField(
            password, { password = it }, "New password", password = true,
            imeAction = ImeAction.Done, onAction = { vm.completeReset(code, password) },
        )
        PrimaryButton(if (auth.busy) "Please wait…" else "Change password", onClick = { vm.completeReset(code, password) }, enabled = !auth.busy)
    }
    TextButton(onClick = { vm.goStep(AuthStep.Login) }) {
        Text("Back to log in", color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}