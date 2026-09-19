package org.nigao.zhihuLite.model.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reads a `Boolean?` from either a JSON boolean or a 0/1 number.
 *
 * Needed because the two endpoints that carry [org.nigao.zhihuLite.model.feed.Target] do **not** encode
 * `allow_segment_interaction` the same way: `/api/v4/answers/{id}` sends `1` (the captured body in
 * `KtorAnswerApiTest` has it), while `/api/v3/feed/topstory/recommend` sends `true`. A plain
 * `Boolean?` throws `JsonDecodingException: Expected valid boolean literal prefix, but had '1'` on
 * the answer endpoint, and since `KtorAnswerApi.parseAnswer` decodes the raw body straight into
 * [org.nigao.zhihuLite.model.feed.Target], that one unread field would fail the whole answer and reach the reader as
 * "该回答可能已删除，无法置顶显示". Anything that is neither a boolean nor a 0/1 number is `null`,
 * never an exception: this flag is not read anywhere, so it must cost nothing to be odd.
 */
object LenientBooleanSerializer : KSerializer<Boolean?> {

    /** Encoding (including `null`) is the library's job; only the numeric form needs handling. */
    private val delegate: KSerializer<Boolean?> = Boolean.Companion.serializer().nullable

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): Boolean? {
        val json = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        return when (val element = json.decodeJsonElement()) {
            is JsonPrimitive -> element.booleanOrNull ?: element.intOrNull?.let { it != 0 }
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean?) = delegate.serialize(encoder, value)
}