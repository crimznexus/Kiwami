package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class SMangaImpl : SManga {

    override lateinit var url: String

    override lateinit var title: String

    override var artist: String? = null

    override var author: String? = null

    override var description: String? = null

    override var genre: String? = null

    override var status: Int = 0

    override var thumbnail_url: String? = null

    override var update_strategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE

    override var initialized: Boolean = false

    // JsonObject is not java.io.Serializable, and this class is persisted with Java
    // serialization (ShowResponse, saved selections), so the memo is kept as JSON text.
    private var memoJson: String? = null

    override var memo: JsonObject
        get() = memoJson?.let { Json.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap())
        set(value) {
            memoJson = if (value.isEmpty()) null else value.toString()
        }

    companion object {
        // Pinned to the value computed before memo was added, so selections saved by
        // earlier builds still deserialize.
        private const val serialVersionUID = -282514509299716946L
    }
}
