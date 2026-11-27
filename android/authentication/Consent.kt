package com.indoone.authentication

import com.google.firebase.database.FirebaseDatabase

/**
 * The consent a person gives once, and the version they gave it for.
 *
 * One file, one version string, the same number the web uses (web/authentication/consent.js).
 * Signup records it, login compares against it, and Settings shows it. When the documents
 * change, [VERSION] changes here and there - nowhere else - so every screen asks about the
 * same version.
 *
 * The rule that keeps this from becoming a bug: if we cannot read what was stored, we ask.
 * An unreadable consent is not consent. Reading it wrong in that direction costs one
 * checkbox; reading it wrong the other way puts a person inside the app without having
 * agreed to anything.
 */
object Consent {

    /** Bump when the documents change. The month form reads correctly a year later. */
    const val VERSION = "2026-11"

    const val TERMS_URL = "/legal/terms-of-use.html"
    const val PRIVACY_URL = "/legal/privacy-policy.html"

    private fun ref(database: FirebaseDatabase, uid: String) =
        database.reference.child("users").child(uid).child("consent")

    /**
     * True when this person has to be asked.
     *
     * Everything except a record that names the current version answers true - a missing
     * record, an older version, and a read that failed. A failed read is the case that
     * matters: swallowing it as "already agreed" would be the one wrong answer here.
     */
    fun needsConsent(database: FirebaseDatabase, uid: String): Boolean {
        if (uid.isBlank()) return true
        val stored = runCatching {
            com.google.android.gms.tasks.Tasks.await(ref(database, uid).get())
        }.getOrNull() ?: return true

        if (!stored.exists()) return true
        val terms = stored.child("termsVersion").getValue(String::class.java).orEmpty()
        val privacy = stored.child("privacyVersion").getValue(String::class.java).orEmpty()
        val termsAt = stored.child("termsAcceptedAt").getValue(Long::class.java)
        val privacyAt = stored.child("privacyAcceptedAt").getValue(Long::class.java)
        return terms != VERSION || privacy != VERSION || termsAt == null || privacyAt == null
    }

    /**
     * Record what was just agreed to. Returns true when it was written.
     *
     * Written to the person's own node, which their own session is allowed to write; nothing
     * here needs admin credentials.
     */
    fun record(
        database: FirebaseDatabase,
        uid: String,
        ageConfirmed: Boolean,
    ): Boolean {
        if (uid.isBlank()) return false
        val now = System.currentTimeMillis()
        val values = hashMapOf<String, Any>(
            "termsVersion" to VERSION,
            "termsAcceptedAt" to now,
            "privacyVersion" to VERSION,
            "privacyAcceptedAt" to now,
            "updatedAt" to now,
        )
        if (ageConfirmed) values["ageConfirmedAt"] = now
        return runCatching {
            com.google.android.gms.tasks.Tasks.await(ref(database, uid).updateChildren(values))
            true
        }.getOrDefault(false)
    }
}
