package com.skillmatch.app.ui.online

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skillmatch.app.net.ApiException
import com.skillmatch.app.net.CvSuggestions
import com.skillmatch.app.net.JobDetail
import com.skillmatch.app.net.JobSummary
import com.skillmatch.app.net.LoginOutcome
import com.skillmatch.app.net.SessionStore
import com.skillmatch.app.net.SkillMatchApi
import com.skillmatch.app.net.UserProfile
import kotlinx.coroutines.Job
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthStep { Login, SignupEmail, SignupCode, SignupDetails, ForgotEmail, ForgotReset }

data class AuthState(
    val step: AuthStep = AuthStep.Login,
    val email: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    /** Only set when the server is in demo mode and returns the code instead of emailing it. */
    val devCode: String? = null,
    val signupToken: String? = null,
)

data class JobsState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val items: List<JobSummary> = emptyList(),
    val needsSkills: Boolean = false,
    /** null = all, otherwise "internship" or "wbl". */
    val type: String? = null,
    val query: String = "",
    val onlySaved: Boolean = false,
)

data class DetailState(
    val id: Int = -1,
    val loading: Boolean = false,
    val error: String? = null,
    val detail: JobDetail? = null,
)

data class EditState(
    val year: Int? = null,
    val programme: String = "",
    val units: String = "",
    val skills: List<String> = emptyList(),
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
)

data class CvState(
    val busy: Boolean = false,
    val error: String? = null,
    val found: CvSuggestions? = null,
)

enum class ResearchPhase { Idle, Setup, Running, Finish, Survey, Done }

/** Study mode: a timed session on one listing set, with or without the fit shown. */
data class ResearchState(
    val phase: ResearchPhase = ResearchPhase.Idle,
    val sessionId: Int = -1,
    /** "manual" hides the fit. "app" shows it. */
    val condition: String = "app",
    val listingSet: String = "A",
    val shortlist: List<Int> = emptyList(),
    val seconds: Double? = null,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val active get() = phase == ResearchPhase.Running || phase == ResearchPhase.Finish
    val showFit get() = !(active && condition == "manual")
}

data class OnlineState(
    /** False until the saved login has been checked. */
    val ready: Boolean = false,
    val profile: UserProfile? = null,
    /** Set when a saved login exists but the server could not be reached. */
    val startupError: String? = null,
    val auth: AuthState = AuthState(),
    val jobs: JobsState = JobsState(),
    val detail: DetailState = DetailState(),
    val edit: EditState = EditState(),
    val cv: CvState = CvState(),
    val research: ResearchState = ResearchState(),
    val serverUrl: String = "",
    val connectionError: String? = null,
    val notice: String? = null,
    val accountBusy: Boolean = false,
    val accountError: String? = null,
)

class OnlineViewModel(app: Application) : AndroidViewModel(app) {
    private val session = SessionStore(app)
    private val api = SkillMatchApi(session)
    private val _state = MutableStateFlow(OnlineState(serverUrl = session.baseUrl))
    val state: StateFlow<OnlineState> = _state.asStateFlow()

    private var jobsJob: Job? = null
    private var suggestJob: Job? = null

    init {
        api.onSessionExpired = {
            _state.update {
                it.copy(profile = null, jobs = JobsState(), auth = AuthState(info = "Your session expired. Please log in again."))
            }
        }
        restore()
    }

    // ---------- start up ----------
    fun restore() {
        if (!session.hasSession) {
            _state.update { it.copy(ready = true, startupError = null) }
            return
        }
        _state.update { it.copy(ready = false, startupError = null) }
        viewModelScope.launch {
            try {
                val me = api.me()
                _state.update { it.copy(ready = true, profile = me, edit = editFrom(me)) }
            } catch (e: ApiException) {
                _state.update { it.copy(ready = true, startupError = if (e.isUnauthorized) null else e.message) }
            }
        }
    }

    fun setServerUrl(url: String) {
        val v = url.trim()
        if (!(v.startsWith("http://") || v.startsWith("https://")) || v.length < 10) {
            _state.update { it.copy(connectionError = "The address must start with http:// or https://") }
            return
        }
        session.baseUrl = v
        _state.update { it.copy(serverUrl = session.baseUrl, connectionError = null, notice = "Server address saved.") }
        if (session.hasSession) restore()
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }
    fun setNotice(text: String) = _state.update { it.copy(notice = text) }

    // ---------- sign up, log in, passwords ----------
    private fun updateAuth(f: AuthState.() -> AuthState) = _state.update { it.copy(auth = it.auth.f()) }

    private fun authCall(block: suspend () -> Unit) {
        updateAuth { copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiException) {
                updateAuth { copy(error = e.message ?: "Something went wrong. Please try again.") }
            }
            updateAuth { copy(busy = false) }
        }
    }

    fun goStep(step: AuthStep) = updateAuth { copy(step = step, error = null, info = null, devCode = null) }

    fun requestSignupCode(email: String) {
        val e = email.trim()
        if (e.isEmpty()) { updateAuth { copy(error = "Enter your Strathmore email address.") }; return }
        authCall {
            val dev = api.signupRequest(e)
            updateAuth { copy(step = AuthStep.SignupCode, email = e, devCode = dev, info = "Code sent.") }
        }
    }

    fun verifySignupCode(code: String) {
        if (code.isBlank()) { updateAuth { copy(error = "Enter the code from your email.") }; return }
        authCall {
            val token = api.signupVerify(_state.value.auth.email, code.trim())
            updateAuth { copy(step = AuthStep.SignupDetails, signupToken = token, devCode = null) }
        }
    }

    fun completeSignup(password: String, confirm: String, year: Int?, programme: String, consent: Boolean) {
        when {
            year == null -> { updateAuth { copy(error = "Choose your year of study.") }; return }
            password != confirm -> { updateAuth { copy(error = "The two passwords do not match.") }; return }
            !consent -> { updateAuth { copy(error = "You need to accept the consent notice to create an account.") }; return }
        }
        val token = _state.value.auth.signupToken ?: run { goStep(AuthStep.SignupEmail); return }
        authCall {
            api.signupComplete(token, password, year!!, programme.trim(), consent)
            finishLogin()
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isEmpty()) {
            updateAuth { copy(error = "Enter your email and password.") }
            return
        }
        authCall {
            when (api.login(email.trim(), password)) {
                LoginOutcome.LoggedIn -> finishLogin()
                LoginOutcome.AdminAccount -> updateAuth {
                    copy(error = "Admin accounts sign in on the website. Open the server address followed by /admin in a browser.")
                }
            }
        }
    }

    private suspend fun finishLogin() {
        val me = api.me()
        _state.update { it.copy(profile = me, auth = AuthState(), jobs = JobsState(), edit = editFrom(me), startupError = null) }
    }

    fun requestReset(email: String) {
        val e = email.trim()
        if (e.isEmpty()) { updateAuth { copy(error = "Enter your Strathmore email address.") }; return }
        authCall {
            val dev = api.forgot(e)
            updateAuth { copy(step = AuthStep.ForgotReset, email = e, devCode = dev, info = "Code sent.") }
        }
    }

    fun completeReset(code: String, newPassword: String) {
        if (code.isBlank()) { updateAuth { copy(error = "Enter the code from your email.") }; return }
        authCall {
            api.reset(_state.value.auth.email, code.trim(), newPassword)
            updateAuth { copy(step = AuthStep.Login, devCode = null, info = "Password changed. Log in with your new password.") }
        }
    }

    fun logout() {
        viewModelScope.launch {
            api.logout()
            _state.value = OnlineState(ready = true, serverUrl = session.baseUrl)
        }
    }

    fun deleteAccount(password: String) {
        if (password.isEmpty()) {
            _state.update { it.copy(accountError = "Enter your password to confirm.") }
            return
        }
        _state.update { it.copy(accountBusy = true, accountError = null) }
        viewModelScope.launch {
            try {
                api.deleteAccount(password)
                session.clear()
                _state.value = OnlineState(ready = true, serverUrl = session.baseUrl, notice = "Your account and data were deleted.")
            } catch (e: ApiException) {
                _state.update { it.copy(accountBusy = false, accountError = e.message) }
            }
        }
    }

    // ---------- listings ----------
    fun setType(type: String?) {
        _state.update { it.copy(jobs = it.jobs.copy(type = type)) }
        loadJobs()
    }

    fun setOnlySaved(value: Boolean) {
        _state.update { it.copy(jobs = it.jobs.copy(onlySaved = value)) }
        loadJobs()
    }

    fun setQuery(q: String) = _state.update { it.copy(jobs = it.jobs.copy(query = q)) }

    fun search() {
        logEvent("search", JSONObject().put("query", _state.value.jobs.query.trim()))
        loadJobs()
    }

    fun loadJobs() {
        jobsJob?.cancel()
        val now = _state.value.jobs
        _state.update { it.copy(jobs = it.jobs.copy(loading = true, error = null)) }
        jobsJob = viewModelScope.launch {
            try {
                val list = if (now.onlySaved) {
                    val q = now.query.trim().lowercase()
                    val all = api.saved()
                    all.copy(items = all.items.filter { j ->
                        (now.type == null || j.type == now.type) &&
                                (q.isEmpty() || j.title.lowercase().contains(q) || j.organisation.lowercase().contains(q))
                    })
                } else {
                    val r = _state.value.research
                    if (r.active) api.listings(now.type, now.query.trim(), researchSet = r.listingSet, includeFit = r.condition == "app")
                    else api.listings(now.type, now.query.trim())
                }
                _state.update { it.copy(jobs = it.jobs.copy(loading = false, loaded = true, items = list.items, needsSkills = list.needsSkills)) }
            } catch (e: ApiException) {
                _state.update { it.copy(jobs = it.jobs.copy(loading = false, loaded = true, error = e.message)) }
            }
        }
    }

    fun openDetail(id: Int) {
        _state.update { it.copy(detail = DetailState(id = id, loading = true)) }
        viewModelScope.launch {
            try {
                val r = _state.value.research
                val d = api.listing(id, includeFit = !(r.active && r.condition == "manual"))
                _state.update { it.copy(detail = DetailState(id = id, detail = d)) }
                logEvent("open_listing", JSONObject().put("listing_id", id))
            } catch (e: ApiException) {
                _state.update { it.copy(detail = DetailState(id = id, error = e.message)) }
            }
        }
    }

    fun toggleSaved() {
        val d = _state.value.detail.detail ?: return
        val want = !d.summary.saved
        viewModelScope.launch {
            try {
                api.setSaved(d.summary.id, want)
                _state.update { s ->
                    s.copy(
                        detail = s.detail.copy(detail = s.detail.detail?.let { it.copy(summary = it.summary.copy(saved = want)) }),
                        jobs = s.jobs.copy(
                            items = s.jobs.items.map { if (it.id == d.summary.id) it.copy(saved = want) else it },
                            loaded = if (s.jobs.onlySaved) false else s.jobs.loaded,
                        ),
                    )
                }
            } catch (e: ApiException) {
                _state.update { it.copy(notice = e.message) }
            }
        }
    }

    fun report(reason: String, note: String) {
        val id = _state.value.detail.id
        viewModelScope.launch {
            try {
                api.report(id, reason, note.trim())
                _state.update { it.copy(notice = "Thank you. An admin will review this listing.") }
            } catch (e: ApiException) {
                _state.update { it.copy(notice = e.message) }
            }
        }
    }

    // ---------- my skills ----------
    private fun editFrom(p: UserProfile) = EditState(
        year = p.year, programme = p.programme, units = p.units.joinToString("\n"), skills = p.skills,
    )

    fun beginEdit() {
        val p = _state.value.profile ?: return
        _state.update { it.copy(edit = editFrom(p)) }
    }

    private fun updateEdit(f: EditState.() -> EditState) = _state.update { it.copy(edit = it.edit.f()) }

    fun setYear(year: Int) = updateEdit { copy(year = year, error = null) }
    fun setProgramme(v: String) = updateEdit { copy(programme = v) }
    fun setUnits(v: String) = updateEdit { copy(units = v) }

    fun addSkill(name: String) {
        val n = name.trim().replace(Regex("\\s+"), " ")
        if (n.isEmpty()) return
        if (n.length > 80) { updateEdit { copy(error = "That skill name is too long.") }; return }
        updateEdit {
            if (skills.any { it.equals(n, ignoreCase = true) }) copy(query = "", suggestions = emptyList())
            else copy(skills = skills + n, query = "", suggestions = emptyList(), error = null)
        }
    }

    fun removeSkill(name: String) = updateEdit { copy(skills = skills.filterNot { it == name }) }

    fun setSkillQuery(q: String) {
        updateEdit { copy(query = q) }
        suggestJob?.cancel()
        if (q.isBlank()) { updateEdit { copy(suggestions = emptyList()) }; return }
        suggestJob = viewModelScope.launch {
            delay(250)
            try {
                val found = api.suggest(q.trim())
                updateEdit { copy(suggestions = found.filter { s -> skills.none { it.equals(s, ignoreCase = true) } }) }
            } catch (e: ApiException) {
                // Suggestions are a convenience. Typing a skill by hand still works.
            }
        }
    }

    fun saveEdit(onSaved: () -> Unit) {
        val e = _state.value.edit
        val year = e.year
        if (year == null) {
            updateEdit { copy(error = "Choose your year of study.") }
            return
        }
        updateEdit { copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val units = e.units.split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
                api.saveProfile(year, e.programme.trim(), units)
                val p = api.saveSkills(e.skills)
                _state.update { it.copy(profile = p, edit = editFrom(p), jobs = it.jobs.copy(loaded = false), notice = "Saved. Your fit has been updated.") }
                onSaved()
            } catch (ex: ApiException) {
                updateEdit { copy(saving = false, error = ex.message) }
            }
        }
    }

    // ---------- CV ----------
    fun uploadCv(filename: String, mime: String, bytes: ByteArray) {
        _state.update { it.copy(cv = CvState(busy = true)) }
        viewModelScope.launch {
            try {
                val found = api.uploadCv(filename, mime, bytes)
                _state.update { it.copy(cv = CvState(found = found)) }
            } catch (e: ApiException) {
                _state.update { it.copy(cv = CvState(error = e.message)) }
            }
        }
    }

    fun cvFailed(message: String) = _state.update { it.copy(cv = CvState(error = message)) }

    /** Adds the ticked skills to the list being edited. Nothing is saved until the student taps Save. */
    fun acceptCvSkills(picked: List<String>) {
        picked.forEach { addSkill(it) }
        _state.update { it.copy(cv = CvState()) }
    }

    fun dismissCv() = _state.update { it.copy(cv = CvState()) }

    // ---------- research mode ----------
    private fun updateResearch(f: ResearchState.() -> ResearchState) = _state.update { it.copy(research = it.research.f()) }

    fun openResearchSetup() = updateResearch { ResearchState(phase = ResearchPhase.Setup) }

    fun closeResearch() {
        _state.update { it.copy(research = ResearchState(), jobs = JobsState(), detail = DetailState()) }
    }

    fun startResearch(condition: String, listingSet: String) {
        updateResearch { copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val id = api.researchStart(condition, listingSet)
                _state.update {
                    it.copy(
                        research = ResearchState(phase = ResearchPhase.Running, sessionId = id, condition = condition, listingSet = listingSet),
                        jobs = JobsState(), detail = DetailState(),
                    )
                }
            } catch (e: ApiException) {
                updateResearch { copy(busy = false, error = e.message) }
            }
        }
    }

    private fun logEvent(event: String, payload: JSONObject = JSONObject()) {
        val r = _state.value.research
        if (r.phase != ResearchPhase.Running || r.sessionId < 0) return
        viewModelScope.launch {
            try { api.researchEvent(r.sessionId, event, payload) } catch (e: ApiException) { /* A lost log line must not interrupt the participant. */ }
        }
    }

    fun toggleShortlist(listingId: Int) {
        val r = _state.value.research
        if (r.phase != ResearchPhase.Running) return
        val adding = listingId !in r.shortlist
        updateResearch { copy(shortlist = if (adding) shortlist + listingId else shortlist - listingId) }
        logEvent(if (adding) "shortlist_add" else "shortlist_remove", JSONObject().put("listing_id", listingId))
    }

    fun beginFinish() = updateResearch { copy(phase = ResearchPhase.Finish, error = null) }
    fun backToListings() = updateResearch { copy(phase = ResearchPhase.Running, error = null) }

    fun endResearch(confidence: Int?) {
        val r = _state.value.research
        updateResearch { copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val secs = api.researchEnd(r.sessionId, r.shortlist, confidence)
                updateResearch { copy(busy = false, phase = ResearchPhase.Survey, seconds = secs) }
            } catch (e: ApiException) {
                updateResearch { copy(busy = false, error = e.message) }
            }
        }
    }

    fun submitSurvey(sus: List<Int>, useful: List<Int>, ease: List<Int>) {
        updateResearch { copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                api.researchSurvey(sus, useful, ease)
                updateResearch { copy(busy = false, phase = ResearchPhase.Done) }
            } catch (e: ApiException) {
                updateResearch { copy(busy = false, error = e.message) }
            }
        }
    }
}