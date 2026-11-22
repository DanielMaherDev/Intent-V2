package com.intent.launcher.engine

import com.intent.launcher.domain.AnswerSchema
import com.intent.launcher.domain.BuilderDecision
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType

/**
 * Encodes adaptive-builder in-chat answer cards on [ResponseBlock] without a
 * schema migration. All [AnswerSchema] shapes share one AnswerQuestion path.
 */
object BuilderAnswerCard {
    const val SOURCE_SCOPE = "adaptive_builder"
    private const val KIND_QUANTITY = "quantity"
    private const val KIND_CHOICE = "choice"
    private const val KIND_MULTI = "multi"
    private const val KIND_WEEKDAYS = "weekdays"
    private const val KIND_DATETIME = "datetime"
    private const val KIND_FREE = "free"
    private const val KIND_CONFIRM = "confirm"

    /** Default practice-minute span — every whole integer (F3). */
    const val DEFAULT_MIN = 1
    const val DEFAULT_MAX = 120
    const val DEFAULT_VALUE = 20
    const val DEFAULT_UNIT = "minutes per day"

    val WEEKDAY_OPTIONS = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )

    data class QuantitySpec(
        val promptId: String,
        val unitLabel: String,
        val min: Int,
        val max: Int,
        val defaultValue: Int,
        val summaryTemplate: String = "{value} {unit}"
    ) {
        val values: List<Int> get() = (min..max).toList()
    }

    data class ChoiceSpec(
        val promptId: String,
        /** Predefined options — deferral options appended for adaptive Ask. */
        val options: List<String>,
        val includeDeferral: Boolean = true
    ) {
        val displayOptions: List<String>
            get() = if (includeDeferral) {
                ChoiceOptions.withDeferralOptions(options)
            } else {
                ChoiceOptions.withOther(options)
            }
    }

    sealed class Decoded {
        abstract val promptId: String

        data class Quantity(
            override val promptId: String,
            val unitLabel: String,
            val min: Int,
            val max: Int,
            val defaultValue: Int,
            val summaryTemplate: String
        ) : Decoded() {
            val values: List<Int> get() = (min..max).toList()
        }

        data class Choice(
            override val promptId: String,
            val options: List<String>
        ) : Decoded()

        data class MultiChoice(
            override val promptId: String,
            val options: List<String>
        ) : Decoded()

        data class Weekdays(
            override val promptId: String,
            val options: List<String> = WEEKDAY_OPTIONS
        ) : Decoded()

        data class DateTime(
            override val promptId: String,
            val hint: String = "Approximate dates are OK"
        ) : Decoded()

        data class FreeText(
            override val promptId: String,
            val hint: String = "Answer in your own words"
        ) : Decoded()

        data class Confirm(
            override val promptId: String = "confirm"
        ) : Decoded()
    }

    fun quantityBlock(
        blockId: String,
        title: String,
        body: String,
        spec: QuantitySpec
    ): ResponseBlock =
        ResponseBlock(
            blockId = blockId,
            type = ResponseBlockType.QUESTION,
            title = title,
            body = body,
            sourceLabel = encodeQuantity(spec),
            linkedObjectIds = listOf(spec.promptId),
            actions = emptyList(),
            active = true
        )

    fun choiceBlock(
        blockId: String,
        title: String,
        body: String,
        spec: ChoiceSpec
    ): ResponseBlock =
        ResponseBlock(
            blockId = blockId,
            type = ResponseBlockType.CHOICE_SET,
            title = title,
            body = body,
            sourceLabel = encodeChoice(spec.promptId),
            linkedObjectIds = listOf(spec.promptId),
            actions = spec.displayOptions,
            active = true
        )

    fun multiChoiceBlock(
        blockId: String,
        title: String,
        body: String,
        promptId: String,
        options: List<String>
    ): ResponseBlock =
        ResponseBlock(
            blockId = blockId,
            type = ResponseBlockType.CHOICE_SET,
            title = title,
            body = body,
            sourceLabel = encodeKind(KIND_MULTI, promptId),
            linkedObjectIds = listOf(promptId),
            actions = ChoiceOptions.withDeferralOptions(options),
            active = true
        )

    fun weekdaysBlock(
        blockId: String,
        title: String,
        body: String,
        promptId: String
    ): ResponseBlock =
        ResponseBlock(
            blockId = blockId,
            type = ResponseBlockType.CHOICE_SET,
            title = title,
            body = body,
            sourceLabel = encodeKind(KIND_WEEKDAYS, promptId),
            linkedObjectIds = listOf(promptId),
            actions = ChoiceOptions.withDeferralOptions(
                listOf("Everyday") + WEEKDAY_OPTIONS
            ),
            active = true
        )

    fun dateTimeBlock(
        blockId: String,
        title: String,
        body: String,
        promptId: String,
        hint: String = "Approximate dates are OK"
    ): ResponseBlock =
        ResponseBlock(
            blockId = blockId,
            type = ResponseBlockType.QUESTION,
            title = title,
            body = body,
            sourceLabel = encodeKind(KIND_DATETIME, promptId, hint.replace(' ', '+')),
            linkedObjectIds = listOf(promptId),
            actions = listOf(ChoiceOptions.NOT_SURE, ChoiceOptions.SKIP_FOR_NOW),
            active = true
        )

    fun freeTextBlock(
        blockId: String,
        title: String,
        body: String,
        promptId: String,
        hint: String = "Answer in your own words"
    ): ResponseBlock =
        ResponseBlock(
            blockId = blockId,
            type = ResponseBlockType.QUESTION,
            title = title,
            body = body,
            sourceLabel = encodeKind(KIND_FREE, promptId, hint.replace(' ', '+')),
            linkedObjectIds = listOf(promptId),
            actions = listOf(ChoiceOptions.NOT_SURE, ChoiceOptions.SKIP_FOR_NOW),
            active = true
        )

    fun confirmBlock(
        blockId: String,
        title: String,
        body: String
    ): ResponseBlock =
        ResponseBlock(
            blockId = blockId,
            type = ResponseBlockType.CHOICE_SET,
            title = title,
            body = body,
            sourceLabel = encodeConfirm(),
            linkedObjectIds = listOf("confirm"),
            actions = listOf("Confirm intention", "Keep discussing"),
            active = true
        )

    /** Project a schema-aware in-chat card from a builder Ask decision. */
    fun blockForAsk(decision: BuilderDecision.Ask, nowEpochMs: Long = 0L): ResponseBlock {
        val blockId = "block-ask-${decision.questionId}"
        val title = askTitle(decision)
        val body = decision.whyItMatters
        return when (decision.answerSchema) {
            AnswerSchema.NUMBER, AnswerSchema.DURATION -> quantityBlock(
                blockId = blockId,
                title = title,
                body = body,
                spec = QuantitySpec(
                    promptId = decision.questionId,
                    unitLabel = if (decision.answerSchema == AnswerSchema.DURATION) {
                        DEFAULT_UNIT
                    } else {
                        "count"
                    },
                    min = DEFAULT_MIN,
                    max = DEFAULT_MAX,
                    defaultValue = DEFAULT_VALUE
                )
            )
            AnswerSchema.MULTI_CHOICE -> multiChoiceBlock(
                blockId = blockId,
                title = title,
                body = body,
                promptId = decision.questionId,
                options = decision.options.ifEmpty {
                    listOf("Option A", "Option B", "Something else")
                }
            )
            AnswerSchema.WEEKDAYS -> weekdaysBlock(
                blockId = blockId,
                title = title,
                body = body,
                promptId = decision.questionId
            )
            AnswerSchema.DATE_TIME -> dateTimeBlock(
                blockId = blockId,
                title = title,
                body = body,
                promptId = decision.questionId
            )
            AnswerSchema.FREE_TEXT -> freeTextBlock(
                blockId = blockId,
                title = title,
                body = body,
                promptId = decision.questionId
            )
            AnswerSchema.CONFIRM -> confirmBlock(
                blockId = blockId,
                title = title,
                body = body
            ).copy(
                sourceLabel = encodeKind(KIND_CONFIRM, decision.questionId),
                linkedObjectIds = listOf(decision.questionId),
                actions = decision.options.ifEmpty {
                    listOf("Still about right", "Update the date")
                }.let { ChoiceOptions.withDeferralOptions(it) }
            )
            AnswerSchema.SINGLE_CHOICE -> choiceBlock(
                blockId = blockId,
                title = title,
                body = body,
                spec = ChoiceSpec(
                    promptId = decision.questionId,
                    options = decision.options,
                    // Motivation create walk: chips + Other (no Not sure/Skip clutter).
                    includeDeferral = decision.purpose != AdaptiveBuilderEngine.KEY_MOTIVATION
                )
            )
        }
    }

    private fun askTitle(decision: BuilderDecision.Ask): String =
        when (decision.purpose) {
            AdaptiveBuilderEngine.KEY_MOTIVATION ->
                "What brings this language into focus for you?"
            AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE ->
                "Which language are you focusing on?"
            AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE ->
                "When do you expect the move or deadline?"
            AdaptiveBuilderEngine.KEY_PRACTICE_FREQUENCY ->
                "How often can you practise?"
            AdaptiveBuilderEngine.KEY_DECIDE_BY_DATE ->
                "When exactly is the response due?"
            AdaptiveBuilderEngine.KEY_DECISION_CRITERIA ->
                "What matters most for this decision?"
            AdaptiveBuilderEngine.KEY_DECISION_ALTERNATIVES ->
                "What are you comparing against?"
            AdaptiveBuilderEngine.KEY_DECISION_UNKNOWNS ->
                "What information is still unknown?"
            AdaptiveBuilderEngine.KEY_COMPARISON_REFRESH ->
                "How should we use the new information?"
            AdaptiveBuilderEngine.KEY_SELECTED_IDEA ->
                AdaptiveBuilderEngine.CHIP_CHOOSE_IDEA_TO_VALIDATE
            AdaptiveBuilderEngine.KEY_VALIDATION_APPROACH ->
                "How do you want to validate this idea?"
            AdaptiveBuilderEngine.KEY_SKILLS_OR_INTERESTS ->
                "What skills or interests should shape exploration?"
            AdaptiveBuilderEngine.KEY_WEEKLY_TIME_BUDGET ->
                "How much time can you put into this each week?"
            AdaptiveBuilderEngine.KEY_EXPLORATION_MODE ->
                "How do you want to explore?"
            else -> decision.purpose.ifBlank { "Question" }
        }

    fun decode(block: ResponseBlock): Decoded? {
        if (!block.active) return null
        val parts = block.sourceLabel.split('|')
        if (parts.isEmpty() || parts[0] != SOURCE_SCOPE) return null
        return when (parts.getOrNull(1)) {
            KIND_QUANTITY -> {
                val promptId = parts.getOrNull(2) ?: return null
                val unit = parts.getOrNull(3) ?: DEFAULT_UNIT
                val min = parts.getOrNull(4)?.toIntOrNull() ?: DEFAULT_MIN
                val max = parts.getOrNull(5)?.toIntOrNull() ?: DEFAULT_MAX
                val default = parts.getOrNull(6)?.toIntOrNull()
                    ?: DEFAULT_VALUE
                val template = parts.getOrNull(7)?.replace('+', ' ')
                    ?: "{value} {unit}"
                Decoded.Quantity(
                    promptId = promptId,
                    unitLabel = unit.replace('+', ' '),
                    min = min,
                    max = max,
                    defaultValue = default.coerceIn(min, max),
                    summaryTemplate = template
                )
            }
            KIND_CHOICE -> {
                val promptId = parts.getOrNull(2) ?: return null
                Decoded.Choice(
                    promptId = promptId,
                    options = block.actions
                )
            }
            KIND_MULTI -> {
                val promptId = parts.getOrNull(2) ?: return null
                Decoded.MultiChoice(promptId = promptId, options = block.actions)
            }
            KIND_WEEKDAYS -> {
                val promptId = parts.getOrNull(2) ?: return null
                Decoded.Weekdays(promptId = promptId, options = block.actions)
            }
            KIND_DATETIME -> {
                val promptId = parts.getOrNull(2) ?: return null
                val hint = parts.getOrNull(3)?.replace('+', ' ') ?: "Approximate dates are OK"
                Decoded.DateTime(promptId = promptId, hint = hint)
            }
            KIND_FREE -> {
                val promptId = parts.getOrNull(2) ?: return null
                val hint = parts.getOrNull(3)?.replace('+', ' ') ?: "Answer in your own words"
                Decoded.FreeText(promptId = promptId, hint = hint)
            }
            KIND_CONFIRM -> Decoded.Confirm(promptId = parts.getOrNull(2) ?: "confirm")
            else -> null
        }
    }

    fun isBuilderCard(block: ResponseBlock): Boolean =
        block.sourceLabel.startsWith("$SOURCE_SCOPE|")

    fun schemaOf(decoded: Decoded): AnswerSchema = when (decoded) {
        is Decoded.Quantity ->
            if (decoded.unitLabel.contains("minute", ignoreCase = true)) {
                AnswerSchema.DURATION
            } else {
                AnswerSchema.NUMBER
            }
        is Decoded.Choice -> AnswerSchema.SINGLE_CHOICE
        is Decoded.MultiChoice -> AnswerSchema.MULTI_CHOICE
        is Decoded.Weekdays -> AnswerSchema.WEEKDAYS
        is Decoded.DateTime -> AnswerSchema.DATE_TIME
        is Decoded.FreeText -> AnswerSchema.FREE_TEXT
        is Decoded.Confirm -> AnswerSchema.CONFIRM
    }

    private fun encodeQuantity(spec: QuantitySpec): String =
        listOf(
            SOURCE_SCOPE,
            KIND_QUANTITY,
            spec.promptId,
            spec.unitLabel.replace(' ', '+'),
            spec.min.toString(),
            spec.max.toString(),
            spec.defaultValue.toString(),
            spec.summaryTemplate.replace(' ', '+')
        ).joinToString("|")

    private fun encodeChoice(promptId: String): String =
        "$SOURCE_SCOPE|$KIND_CHOICE|$promptId"

    private fun encodeKind(kind: String, promptId: String, extra: String? = null): String =
        buildString {
            append(SOURCE_SCOPE).append('|').append(kind).append('|').append(promptId)
            if (extra != null) append('|').append(extra)
        }

    private fun encodeConfirm(): String =
        "$SOURCE_SCOPE|$KIND_CONFIRM|confirm"
}
