package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class SChapterImpl : SChapter {

    override lateinit var url: String

    override lateinit var name: String

    override var date_upload: Long = 0

    override var chapter_number: Float = -1f

    override var scanlator: String? = null

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
        private const val serialVersionUID = -3139460928763432282L
    }
}
