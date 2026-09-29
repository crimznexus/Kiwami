@file:OptIn(ExperimentalSerializationApi::class)

package eu.kanade.tachiyomi.extension.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// Mihon's v2 extension store format. Repos such as Keiyoushi now publish their
// real extension list only as a (usually gzipped) protobuf `index.pb`; their
// `index.min.json` is a stub telling old apps to update. Field numbers must
// match mihonapp/mihon data/.../NetworkExtensionStore.kt.

@Serializable
internal data class NetworkExtensionStore(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val badgeLabel: String = "",
    @ProtoNumber(3) val signingKey: String = "",
    @ProtoNumber(101) val extensionList: ExtensionList? = null,
    @ProtoNumber(102) val extensionListUrl: String? = null,
) {
    @Serializable
    data class ExtensionList(@ProtoNumber(1) val extensions: List<Extension> = emptyList())

    @Serializable
    data class Extension(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val packageName: String,
        @ProtoNumber(3) val resources: Resources,
        @ProtoNumber(4) val extensionLib: String,
        @ProtoNumber(5) val versionCode: Long,
        @ProtoNumber(6) val versionName: String,
        @ProtoNumber(7) val contentWarning: Int = CONTENT_WARNING_UNSPECIFIED,
        @ProtoNumber(8) val sources: List<Source> = emptyList(),
    ) {
        val isNsfw get() = contentWarning == CONTENT_WARNING_NSFW

        val lang: String
            get() = sources.map { it.language }.toSet().singleOrNull() ?: "all"
    }

    @Serializable
    data class Resources(
        @ProtoNumber(1) val apkUrl: String,
        @ProtoNumber(2) val iconUrl: String = "",
    )

    @Serializable
    data class Source(
        @ProtoNumber(1) val id: Long,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(3) val language: String,
        @ProtoNumber(4) val homeUrl: String = "",
    )

    companion object {
        const val CONTENT_WARNING_UNSPECIFIED = 0
        const val CONTENT_WARNING_NSFW = 3
    }
}

/** A legacy repo's `repo.json`; `index_v2` points at the store index when the repo has one. */
@Serializable
internal data class LegacyRepoJson(
    @SerialName("index_v2") val indexV2: String? = null,
)
