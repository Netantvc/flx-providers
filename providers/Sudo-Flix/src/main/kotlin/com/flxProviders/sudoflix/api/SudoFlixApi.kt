package com.flxProviders.sudoflix.api

import android.content.Context
import com.flixclusive.core.util.coroutines.mapAsync
import com.flixclusive.core.util.exception.safeCall
import com.flixclusive.core.util.film.FilmType
import com.flixclusive.core.util.film.filter.FilterList
import com.flixclusive.model.provider.MediaLink
import com.flixclusive.model.tmdb.FilmDetails
import com.flixclusive.model.tmdb.FilmSearchItem
import com.flixclusive.model.tmdb.Movie
import com.flixclusive.model.tmdb.SearchResponseData
import com.flixclusive.model.tmdb.common.tv.Episode
import com.flixclusive.provider.Provider
import com.flixclusive.provider.ProviderApi
import com.flxProviders.sudoflix.api.nsbx.NsbxApi
import com.flxProviders.sudoflix.api.nsbx.VidBingeApi
import com.flxProviders.sudoflix.api.primewire.PrimeWireApi
import com.flxProviders.sudoflix.api.ridomovies.RidoMoviesApi
import com.flxProviders.sudoflix.api.vidsrcto.VidSrcToApi
import okhttp3.OkHttpClient

/**
 *
 * RIP m-w
 * */
class SudoFlixApi(
    client: OkHttpClient,
    context: Context,
    provider: Provider
) : ProviderApi(
    client = client,
    context = context,
    provider = provider
) {
    private val providersList = listOf(
        NsbxApi(
            client = client,
            context = context,
            provider = provider
        ),
        VidBingeApi(
            client = client,
            context = context,
            provider = provider
        ),
        RidoMoviesApi(
            client = client,
            context = context,
            provider = provider
        ),
        PrimeWireApi(
            client = client,
            context = context,
            provider = provider
        ),
        VidSrcToApi(
            client = client,
            context = context,
            provider = provider
        ),
    )

    override val testFilm: FilmDetails
        get() = Movie(
            tmdbId = 299534,
            imdbId = "tt4154796",
            title = "Avengers: Endgame",
            posterImage = null,
            backdropImage = "/orjiB3oUIsyz60hoEqkiGpy5CeO.jpg",
            homePage = null,
            id = null,
            providerName = "TMDB"
        )

    override suspend fun getLinks(
        watchId: String,
        film: FilmDetails,
        episode: Episode?,
        onLinkFound: (MediaLink) -> Unit
    ) {
        providersList.mapAsync {
            safeCall {
                it.getLinks(
                    watchId = watchId,
                    film = film,
                    episode = episode,
                    onLinkFound = onLinkFound
                )
            }
        }
    }

    override suspend fun search(
        title: String,
        page: Int,
        id: String?,
        imdbId: String?,
        tmdbId: Int?,
        filters: FilterList,
    ): SearchResponseData<FilmSearchItem> {
        val identifier = id ?: tmdbId?.toString() ?: imdbId
        if (identifier == null) {
            throw IllegalStateException("${provider.name} is not a searchable provider. It is a set of providers combined into one.")
        }

        return SearchResponseData(
            results = listOf(
                FilmSearchItem(
                    id = identifier,
                    title = title,
                    providerName = provider.name,
                    filmType = FilmType.MOVIE,
                    posterImage = null,
                    backdropImage = null,
                    homePage = null
                )
            )
        )
    }
}