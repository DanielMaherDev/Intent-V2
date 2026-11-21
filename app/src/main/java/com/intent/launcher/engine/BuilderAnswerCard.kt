package com.intent.launcher.engine

import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType

/**
 * Encodes adaptive-builder in-chat answer cards on [ResponseBlock] without a
 * schema migration. Quantity wheels and choice sets (with Other) share one
 * AnswerQuestion path via [CreateIntentionChat].
 */
object BuilderAnswerCard {
    const val SOURCE_SCOPE = "adaptive_builder"
    private const val KIND_QUANTITY = "quantity"
    private const val KIND_CHOICE = "choice"
    private const val KIND_CONFIRM = "confirm"

    /** Default practice-minute span — every whole integer (F3). */
    const val DEFAULT_MIN = 1
    const val DEFAULT_MAX = 120
    const val DEFAULT_VALUE = 20
    const val DEFAULT_UNIT = "minutes per day"

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
        /** Predefined options — [ChoiceOptions.OTHER] is appended for display. */
        val options: List<String>
    ) {
        val displayOptions: List<String> get() = ChoiceOptions.withOther(options)
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
            KIND_CONFIRM -> Decoded.Confirm()
            else -> null
        }
    }

    fun isBuilderCard(block: ResponseBlock): Boolean =
        block.sourceLabel.startsWith("$SOURCE_SCOPE|")

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

    private fun encodeConfirm(): String =
        "$SOURCE_SCOPE|$KIND_CONFIRM|confirm"
}
