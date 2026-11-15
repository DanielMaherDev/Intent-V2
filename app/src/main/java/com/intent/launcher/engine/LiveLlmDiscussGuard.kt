package com.intent.launcher.engine

import com.intent.launcher.domain.ProvenanceKind

/**
 * Hard honesty guards for any future live Discuss model path.
 * Model output must never become OBSERVED evidence and must never auto-Complete.
 */
object LiveLlmDiscussGuard {

    /** Allowed provenance for user-confirmed model suggestions. */
    val allowedConfirmedProvenance: ProvenanceKind = ProvenanceKind.SAID

    fun mayWriteObservedFromModel(): Boolean = false

    fun mayAutoCompleteSession(): Boolean = false

    /**
     * Rejects model→store paths that would invent OBSERVED or auto-Complete.
     * Call before applying any FlowCommand derived from model text.
     */
    fun assertSafeModelSideEffect(
        writesObserved: Boolean,
        autoCompletes: Boolean
    ): Boolean = !writesObserved && !autoCompletes &&
        !mayWriteObservedFromModel() &&
        !mayAutoCompleteSession()
}
