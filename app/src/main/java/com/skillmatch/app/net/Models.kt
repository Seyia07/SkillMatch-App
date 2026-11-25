package com.skillmatch.app.net

import org.json.JSONArray
import org.json.JSONObject

data class UserProfile(
    val id: Int,
    val email: String,
    val year: Int?,
    val programme: String,
    val units: List<String>,
    val skills: List<String>,
    val isAdmin: Boolean,
    val participantCode: String,
)

data class Flag(val code: String, val severity: String, val message: String) {
    val isWarning get() = severity == "warning"
}

/** overall is null when the student has not added skills yet. */
data class Fit(val overall: Int?, val label: String, val met: Int, val partly: Int, val missing: Int)

data class JobSummary(
    val id: Int,
    val title: String,
    val organisation: String,
    val type: String,
    val source: String,
    val sourceUrl: String,
    val applyUrl: String,
    val closingDate: String?,
    val dateAdded: String,
    val flags: List<Flag>,
    val saved: Boolean,
    val fit: Fit?,
) {
    val warnings get() = flags.filter { it.isWarning }
    val typeLabel get() = if (type == "wbl") "WBL placement" else "Internship"
}

data class RequirementFit(
    val text: String,
    /** "Met", "Partly met", "Missing", or null when no skills have been added. */
    val status: String?,
    val score: Double?,
    val matchedWith: String?,
    val note: String,
)

data class JobDetail(
    val summary: JobSummary,
    val description: String,
    val contactEmail: String,
    val note: String,
    val requirements: List<RequirementFit>,
    val needsSkills: Boolean,
)

data class JobList(val items: List<JobSummary>, val needsSkills: Boolean)

/** Skills found in an uploaded CV. matched are in the skills list, other are extra items from the skills section. */
data class CvSuggestions(val matched: List<String>, val other: List<String>)

sealed interface LoginOutcome {
    data object LoggedIn : LoginOutcome
    data object AdminAccount : LoginOutcome
}

fun sourceLabel(source: String) = when (source) {
    "partner_list" -> "Attachment Office partner list"
    "careers_email" -> "Careers Office email"
    "career_page" -> "Organisation career page"
    "job_platform" -> "Job platform"
    "referral" -> "Referral"
    else -> "Other source"
}

// ---------- JSON helpers (org.json ships with Android, so no extra library is needed) ----------
private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
private fun JSONObject.str(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)

fun JSONObject.toProfile() = UserProfile(
    id = getInt("id"),
    email = getString("email"),
    year = if (isNull("year_of_study")) null else getInt("year_of_study"),
    programme = optString("programme", ""),
    units = optJSONArray("units")?.strings() ?: emptyList(),
    skills = optJSONArray("skills")?.strings() ?: emptyList(),
    isAdmin = optBoolean("is_admin", false),
    participantCode = optString("participant_code", ""),
)

private fun JSONObject.toFlag() = Flag(getString("code"), getString("severity"), getString("message"))

private fun JSONObject.toFit() = Fit(
    overall = if (isNull("overall")) null else getInt("overall"),
    label = getString("label"),
    met = getInt("met"),
    partly = getInt("partly"),
    missing = getInt("missing"),
)

fun JSONObject.toSummary() = JobSummary(
    id = getInt("id"),
    title = getString("title"),
    organisation = getString("organisation"),
    type = getString("type"),
    source = getString("source"),
    sourceUrl = optString("source_url", ""),
    applyUrl = optString("apply_url", ""),
    closingDate = str("closing_date"),
    dateAdded = optString("date_added", ""),
    flags = optJSONArray("flags")?.let { a -> List(a.length()) { a.getJSONObject(it).toFlag() } } ?: emptyList(),
    saved = optBoolean("saved", false),
    fit = if (isNull("fit")) null else getJSONObject("fit").toFit(),
)

fun JSONObject.toJobList() = JobList(
    items = getJSONArray("items").let { a -> List(a.length()) { a.getJSONObject(it).toSummary() } },
    needsSkills = optBoolean("needs_skills", false),
)

fun JSONObject.toDetail() = JobDetail(
    summary = toSummary(),
    description = optString("description", ""),
    contactEmail = optString("contact_email", ""),
    note = optString("note", ""),
    requirements = getJSONArray("requirements").let { a ->
        List(a.length()) {
            val o = a.getJSONObject(it)
            RequirementFit(
                text = o.getString("text"),
                status = o.str("status"),
                score = if (o.isNull("score")) null else o.getDouble("score"),
                matchedWith = o.str("matched_with"),
                note = o.optString("note", ""),
            )
        }
    },
    needsSkills = optBoolean("needs_skills", false),
)

fun JSONArray.toStringList(): List<String> = strings()

fun JSONObject.toCv() = CvSuggestions(
    matched = optJSONArray("matched")?.strings() ?: emptyList(),
    other = optJSONArray("other")?.strings() ?: emptyList(),
)
