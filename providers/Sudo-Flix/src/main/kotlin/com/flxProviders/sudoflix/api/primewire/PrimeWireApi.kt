package com.flxProviders.sudoflix.api.primewire

import android.content.Context
import com.flixclusive.core.util.coroutines.asyncCalls
import com.flixclusive.core.util.coroutines.mapAsync
import com.flixclusive.core.util.exception.safeCall
import com.flixclusive.core.util.film.FilmType
import com.flixclusive.core.util.network.CryptographyUtil
import com.flixclusive.core.util.network.asJsoup
import com.flixclusive.core.util.network.fromJson
import com.flixclusive.core.util.network.request
import com.flixclusive.model.provider.MediaLink
import com.flixclusive.model.tmdb.FilmDetails
import com.flixclusive.model.tmdb.common.tv.Episode
import com.flixclusive.provider.Provider
import com.flixclusive.provider.ProviderApi
import com.flxProviders.sudoflix.api.opensubs.SubtitleUtil.fetchSubtitles
import com.flxProviders.sudoflix.api.primewire.extractor.DoodStream
import com.flxProviders.sudoflix.api.primewire.extractor.DropLoad
import com.flxProviders.sudoflix.api.primewire.extractor.FileLions
import com.flxProviders.sudoflix.api.primewire.extractor.MixDrop
import com.flxProviders.sudoflix.api.primewire.extractor.StreamVid
import com.flxProviders.sudoflix.api.primewire.extractor.StreamWish
import com.flxProviders.sudoflix.api.primewire.extractor.UpStream
import com.flxProviders.sudoflix.api.primewire.extractor.VTube
import com.flxProviders.sudoflix.api.primewire.extractor.Voe
import com.flxProviders.sudoflix.api.primewire.util.decrypt.getLinks
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Suppress("SpellCheckingInspection")
internal class PrimeWireApi(
    client: OkHttpClient,
    context: Context,
    provider: Provider
) : ProviderApi(
    client = client,
    context = context,
    provider = provider
) {
    private val name = "PrimeWire"
    override val baseUrl = "https://www.primewire.tf"

    private val extractors = mapOf(
        "mixdrop" to MixDrop(client),
        "voe" to Voe(client),
        "upstream" to UpStream(client),
        "dood" to DoodStream(client),
        "filelions" to FileLions(client),
        "dropload" to DropLoad(client),
        "streamvid" to StreamVid(client),
        "vtube" to VTube(client),
        "streamwish" to StreamWish(client)
    )

    override suspend fun getLinks(
        watchId: String,
        film: FilmDetails,
        episode: Episode?,
        onLinkFound: (MediaLink) -> Unit
    ) {
        val imdbId = film.imdbId
            ?: throw NullPointerException("[$name]> Could not get IMDB ID")

        val id = getMediaId(imdbId = imdbId)
        val availableServers = getAvailableServers(
            id = id,
            season = episode?.season,
            episode = episode?.number,
            filmType = film.filmType
        )

        asyncCalls(
            {
                client.fetchSubtitles(
                    imdbId = imdbId,
                    season = episode?.season,
                    episode = episode?.number,
                    onSubtitleLoaded = onLinkFound
                )
            },
            {
                availableServers.mapAsync { (serverName, url) ->
                    val extractor = extractors[serverName]
                        ?: return@mapAsync

                    safeCall {
                        extractor.extract(
                            url = url,
                            onLinkFound = onLinkFound
                        )
                    }
                }
            },
        )
    }

    private fun getMediaId(imdbId: String): Int {
        val searchKey = CryptographyUtil.base64Decode("bHpRUHNYU0tjRw==")

        val response = client.request(
            url = "$baseUrl/api/v1/show?key=$searchKey&imdb_id=$imdbId"
        ).execute().body?.string()
            ?: throw NullPointerException("[$name]> Could not get ID from search endpoint")

        val json = fromJson<Map<String, Any>>(response)

        return (json["id"] as Double).toInt()
    }

    private fun getAvailableServers(
        id: Int,
        season: Int?,
        episode: Int?,
        filmType: FilmType
    ): List<Pair<String, String>> {
        var html = client.request(
            url = "$baseUrl/${filmType.type}/$id"
        ).execute()
            .asJsoup()

        if (episode != null && season != null) {
            html = html.getEpisodePageUrl(
                season = season,
                episode = episode
            )
        }

        val encryptedLinks = html.select("#user-data").attr("v")
        val decryptedLinks = getLinks(encryptedLinks)

        if (decryptedLinks.isEmpty()) {
            throw NullPointerException("[$name]> Could not get links")
        }

        val serverNameAndEmbedLink = mutableListOf<Pair<String, String>>()
        decryptedLinks.forEachIndexed { index, embedId ->
            val anchorElement = html.select(".propper-link[link_version='$index']")

            val sourceName = anchorElement.parents().select(".version-host").text().trim().split(".")
                .firstOrNull()
                ?.lowercase()
                ?: return@forEachIndexed

            serverNameAndEmbedLink.add(sourceName to "$baseUrl/links/go/$embedId")
        }

        return serverNameAndEmbedLink.toList()
    }

    private fun Document.getEpisodePageUrl(
        season: Int,
        episode: Int,
    ): Document {
        val episodeLink = select(".show_season[data-id='${season}'] > div > a")
            .find {
                it.attr("href").contains("-episode-$episode", true)
            }?.attr("href") ?: throw NullPointerException("[$name]> Could not find episode link")

        return client.request(
            url = "$baseUrl$episodeLink"
        ).execute().asJsoup()
    }
}