package com.lagradost.cloudstream3.metaproviders

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * episode and season starting from 1
 * they are null if movie
 */
@Serializable
data class TmdbLink(
    @JsonProperty("imdbID") @SerialName("imdbID") val imdbID: String?,
    @JsonProperty("tmdbID") @SerialName("tmdbID") val tmdbID: Int?,
    @JsonProperty("episode") @SerialName("episode") val episode: Int?,
    @JsonProperty("season") @SerialName("season") val season: Int?,
    @JsonProperty("movieName") @SerialName("movieName") val movieName: String? = null,
)

data class TmdbEnrichmentData(
    val tmdbId: Int?,
    val imdbId: String?,
    val title: String?,
    val plot: String?,
    val tagline: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val year: Int?,
    val duration: Int?,
    val score: Score?,
    val contentRating: String?,
    val contentDescriptors: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val moodTags: List<String> = emptyList(),
    val actors: List<ActorData> = emptyList(),
    val directors: List<String> = emptyList(),
    val creators: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val productionCompanies: List<String> = emptyList(),
    val networks: List<String> = emptyList(),
    val trailers: List<String> = emptyList(),
    val recommendations: List<SearchResponse> = emptyList(),
    val popularity: Double? = null,
    val isMovie: Boolean = true,
)

open class TmdbProvider : MainAPI() {
    // This should always be false, but might as well make it easier for forks
    open val includeAdult = false

    // Use the LoadResponse from the metadata provider
    open val useMetaLoadResponse = false
    open val apiName = "TMDB"

    // As some sites don't support s0
    open val disableSeasonZero = true

    override val hasMainPage = true
    override val providerType = ProviderType.MetaProvider

    companion object {
        const val TMDB_API_KEY = "e6333b32409e02a4a6eba6fb7ff866bb"
        const val TMDB_API_URL = "https://api.themoviedb.org/3"

        fun getImageUrl(link: String?): String? {
            link ?: return null
            return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500$link" else link
        }

        fun getOriginalImageUrl(link: String?): String? {
            link ?: return null
            return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original$link" else link
        }

        fun getUrl(id: Int?, tvShow: Boolean): String {
            return if (tvShow) "https://www.themoviedb.org/tv/${id ?: -1}"
            else "https://www.themoviedb.org/movie/${id ?: -1}"
        }

        private suspend fun getApiStatic(path: String, extraParams: Map<String, String> = emptyMap()): String {
            val params = buildMap {
                put("api_key", TMDB_API_KEY)
                putAll(extraParams)
            }
            return app.get(
                url = "$TMDB_API_URL$path",
                params = params,
            ).text
        }

        private fun extractLogoUrl(images: TmdbImages?): String? {
            val logos = images?.logos ?: return null
            if (logos.isEmpty()) return null
            val enLogo = logos.firstOrNull { it.iso639 == "en" }?.filePath
            return getOriginalImageUrl(enLogo ?: logos.firstOrNull()?.filePath)
        }

        private fun extractBackdropUrl(backdropPath: String?, images: TmdbImages?): String? {
            if (!backdropPath.isNullOrBlank()) {
                return getOriginalImageUrl(backdropPath)
            }
            val backdrops = images?.backdrops ?: return null
            return getOriginalImageUrl(backdrops.firstOrNull()?.filePath)
        }

        @Suppress("DEPRECATION_ERROR")
        fun TmdbSearchResult.toSearchResponse(): SearchResponse = if (isTv) {
            TvSeriesSearchResponse(
                name = displayTitle,
                url = getUrl(id, true),
                apiName = "TMDB",
                type = TvType.TvSeries
            ).apply {
                this.id = this@toSearchResponse.id
                this.posterUrl = getImageUrl(posterPath)
                this.score = Score.from10(voteAverage)
                this.year = this@toSearchResponse.year
            }
        } else {
            MovieSearchResponse(
                name = displayTitle,
                url = getUrl(id, false),
                apiName = "TMDB",
                type = TvType.Movie
            ).apply {
                this.id = this@toSearchResponse.id
                this.posterUrl = getImageUrl(posterPath)
                this.score = Score.from10(voteAverage)
                this.year = this@toSearchResponse.year
            }
        }

        suspend fun fetchTmdbSeasonEpisodes(tmdbId: Int, seasonNumber: Int): List<TmdbEpisode>? {
            return try {
                val json = getApiStatic(
                    "/tv/$tmdbId/season/$seasonNumber",
                    mapOf("language" to "en-US", "append_to_response" to "external_ids")
                )
                tryParseJson<TmdbSeasonDetail>(json)?.episodes
            } catch (e: Exception) {
                logError(e)
                null
            }
        }

        suspend fun fetchTmdbMetadata(
            imdbId: String? = null,
            tmdbId: Int? = null,
            title: String? = null,
            year: Int? = null,
            isMovie: Boolean = true
        ): TmdbEnrichmentData? {
            return try {
                var resolvedTmdbId = tmdbId
                var resolvedIsMovie = isMovie

                // 1. Resolve by IMDb ID if available and TMDB ID is missing
                if (resolvedTmdbId == null && !imdbId.isNullOrBlank() && imdbId.startsWith("tt", ignoreCase = true)) {
                    val findJson = getApiStatic("/find/$imdbId", mapOf("external_source" to "imdb_id"))
                    val findResult = tryParseJson<TmdbFindResults>(findJson)
                    val movie = findResult?.movieResults?.firstOrNull()
                    val tv = findResult?.tvResults?.firstOrNull()

                    if (movie != null) {
                        resolvedTmdbId = movie.id
                        resolvedIsMovie = true
                    } else if (tv != null) {
                        resolvedTmdbId = tv.id
                        resolvedIsMovie = false
                    }
                }

                // 2. Resolve by Search if still missing
                if (resolvedTmdbId == null && !title.isNullOrBlank()) {
                    val cleanTitle = cleanQueryTitle(title)
                    val searchPath = if (resolvedIsMovie) "/search/movie" else "/search/tv"
                    val params = buildMap {
                        put("query", cleanTitle)
                        put("language", "en-US")
                        if (year != null && year > 1900) {
                            if (resolvedIsMovie) put("year", "$year")
                            else put("first_air_date_year", "$year")
                        }
                    }
                    val searchJson = getApiStatic(searchPath, params)
                    val searchResults = tryParseJson<TmdbPageResult>(searchJson)?.results
                    val match = findBestSearchResult(searchResults, cleanTitle, year)
                    if (match != null) {
                        resolvedTmdbId = match.id
                    } else if (resolvedIsMovie) {
                        // Fallback try tv search if movie search gave no match
                        val tvSearchJson = getApiStatic("/search/tv", buildMap {
                            put("query", cleanTitle)
                            put("language", "en-US")
                        })
                        val tvMatch = findBestSearchResult(tryParseJson<TmdbPageResult>(tvSearchJson)?.results, cleanTitle, year)
                        if (tvMatch != null) {
                            resolvedTmdbId = tvMatch.id
                            resolvedIsMovie = false
                        }
                    }
                }

                resolvedTmdbId ?: return null

                // 3. Fetch Full Details with append_to_response
                if (resolvedIsMovie) {
                    val json = getApiStatic(
                        "/movie/$resolvedTmdbId",
                        mapOf(
                            "language" to "en-US",
                            "append_to_response" to "external_ids,videos,credits,recommendations,similar,release_dates,images,keywords"
                        )
                    )
                    val detail = tryParseJson<TmdbMovieDetail>(json) ?: return null
                    detail.toEnrichmentData()
                } else {
                    val json = getApiStatic(
                        "/tv/$resolvedTmdbId",
                        mapOf(
                            "language" to "en-US",
                            "append_to_response" to "external_ids,videos,credits,recommendations,similar,content_ratings,images,keywords"
                        )
                    )
                    val detail = tryParseJson<TmdbTvDetail>(json) ?: return null
                    detail.toEnrichmentData()
                }
            } catch (e: Exception) {
                logError(e)
                null
            }
        }

        private fun cleanQueryTitle(title: String): String {
            return title
                .replace(Regex("""\b(1080p|720p|4k|hdr|bluray|web-dl|x264|x265|hevc|season\s*\d+|episode\s*\d+|s\d+e\d+)\b""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""[\[\](){}]"""), " ")
                .trim()
                .replace(Regex("""\s+"""), " ")
        }

        private fun findBestSearchResult(results: List<TmdbSearchResult>?, cleanTitle: String, year: Int?): TmdbSearchResult? {
            results ?: return null
            if (results.isEmpty()) return null
            if (results.size == 1) return results.first()

            val exactMatch = results.firstOrNull { it.displayTitle.equals(cleanTitle, ignoreCase = true) && (year == null || it.year == year) }
            if (exactMatch != null) return exactMatch

            val yearMatch = results.firstOrNull { year != null && it.year == year }
            if (yearMatch != null) return yearMatch

            return results.first()
        }

        private fun TmdbMovieDetail.toEnrichmentData(): TmdbEnrichmentData {
            val castList = credits?.cast?.mapNotNull { member ->
                val name = member.name ?: return@mapNotNull null
                ActorData(
                    Actor(name, getImageUrl(member.profilePath)),
                    roleString = member.character
                )
            } ?: emptyList()

            val directorsList = credits?.crew?.filter { it.job.equals("Director", ignoreCase = true) }
                ?.mapNotNull { it.name }?.distinct() ?: emptyList()

            val writersList = credits?.crew?.filter {
                it.department.equals("Writing", ignoreCase = true) ||
                        it.job.equals("Screenplay", ignoreCase = true) ||
                        it.job.equals("Writer", ignoreCase = true)
            }?.mapNotNull { it.name }?.distinct() ?: emptyList()

            val prodCompanies = productionCompanies?.mapNotNull { it.name } ?: emptyList()
            val moodTagsList = keywords?.allKeywords?.mapNotNull { it.name } ?: emptyList()

            val usRelease = releaseDates?.results?.firstOrNull { it.country == "US" }
            val certEntry = usRelease?.releaseDates?.firstOrNull { !it.certification.isNullOrBlank() }
            val rating = certEntry?.certification

            val contentDescList = certEntry?.descriptors ?: emptyList()

            val trailerList = videos?.results
                ?.filter { it.type in setOf("Trailer", "Teaser") }
                ?.sortedBy { if (it.type == "Trailer") 0 else 1 }
                ?.mapNotNull {
                    if (it.site?.trim()?.equals("YouTube", ignoreCase = true) == true && !it.key.isNullOrBlank()) {
                        "https://www.youtube.com/watch?v=${it.key}"
                    } else null
                } ?: emptyList()

            val recs = (recommendations ?: similar)?.results?.map { it.toSearchResponse() } ?: emptyList()

            return TmdbEnrichmentData(
                tmdbId = id,
                imdbId = imdbId ?: externalIds?.imdbId,
                title = displayTitle,
                plot = overview,
                tagline = tagline?.takeIf { it.isNotBlank() },
                posterUrl = getImageUrl(posterPath),
                backdropUrl = extractBackdropUrl(backdropPath, images),
                logoUrl = extractLogoUrl(images),
                year = year,
                duration = runtime,
                score = Score.from10(voteAverage),
                contentRating = rating,
                contentDescriptors = contentDescList,
                genres = genres?.mapNotNull { it.name } ?: emptyList(),
                moodTags = moodTagsList,
                actors = castList,
                directors = directorsList,
                creators = emptyList(),
                writers = writersList,
                productionCompanies = prodCompanies,
                networks = emptyList(),
                trailers = trailerList,
                recommendations = recs,
                popularity = popularity,
                isMovie = true
            )
        }

        private fun TmdbTvDetail.toEnrichmentData(): TmdbEnrichmentData {
            val castList = credits?.cast?.mapNotNull { member ->
                val name = member.name ?: return@mapNotNull null
                ActorData(
                    Actor(name, getImageUrl(member.profilePath)),
                    roleString = member.character
                )
            } ?: emptyList()

            val creatorsList = createdBy?.mapNotNull { it.name } ?: emptyList()

            val directorsList = credits?.crew?.filter { it.job.equals("Director", ignoreCase = true) }
                ?.mapNotNull { it.name }?.distinct() ?: emptyList()

            val writersList = credits?.crew?.filter {
                it.department.equals("Writing", ignoreCase = true) ||
                        it.job.equals("Writer", ignoreCase = true)
            }?.mapNotNull { it.name }?.distinct() ?: emptyList()

            val netList = networks?.mapNotNull { it.name } ?: emptyList()
            val prodCompanies = productionCompanies?.mapNotNull { it.name } ?: emptyList()
            val moodTagsList = keywords?.allKeywords?.mapNotNull { it.name } ?: emptyList()

            val usRating = contentRatings?.results?.firstOrNull { it.country == "US" }
            val rating = usRating?.rating
            val contentDescList = usRating?.descriptors ?: emptyList()

            val trailerList = videos?.results
                ?.filter { it.type in setOf("Trailer", "Teaser") }
                ?.sortedBy { if (it.type == "Trailer") 0 else 1 }
                ?.mapNotNull {
                    if (it.site?.trim()?.equals("YouTube", ignoreCase = true) == true && !it.key.isNullOrBlank()) {
                        "https://www.youtube.com/watch?v=${it.key}"
                    } else null
                } ?: emptyList()

            val recs = (recommendations ?: similar)?.results?.map { it.toSearchResponse() } ?: emptyList()

            return TmdbEnrichmentData(
                tmdbId = id,
                imdbId = externalIds?.imdbId,
                title = displayTitle,
                plot = overview,
                tagline = tagline?.takeIf { it.isNotBlank() },
                posterUrl = getImageUrl(posterPath),
                backdropUrl = extractBackdropUrl(backdropPath, images),
                logoUrl = extractLogoUrl(images),
                year = year,
                duration = episodeRunTime?.average()?.toInt(),
                score = Score.from10(voteAverage),
                contentRating = rating,
                contentDescriptors = contentDescList,
                genres = genres?.mapNotNull { it.name } ?: emptyList(),
                moodTags = moodTagsList,
                actors = castList,
                directors = directorsList,
                creators = creatorsList,
                writers = writersList,
                productionCompanies = prodCompanies,
                networks = netList,
                trailers = trailerList,
                recommendations = recs,
                popularity = popularity,
                isMovie = false
            )
        }
    }

    @Serializable
    data class TmdbFindResults(
        @JsonProperty("movie_results") @SerialName("movie_results") val movieResults: List<TmdbSearchResult>? = null,
        @JsonProperty("tv_results") @SerialName("tv_results") val tvResults: List<TmdbSearchResult>? = null,
    )

    @Serializable
    data class TmdbIds(
        @JsonProperty("imdb_id") @SerialName("imdb_id") val imdbId: String? = null,
        @JsonProperty("tvdb_id") @SerialName("tvdb_id") val tvdbId: Int? = null,
    )

    @Serializable
    data class TmdbGenre(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
        @JsonProperty("name") @SerialName("name") val name: String? = null,
    )

    @Serializable
    data class TmdbCastMember(
        @JsonProperty("name") @SerialName("name") val name: String? = null,
        @JsonProperty("character") @SerialName("character") val character: String? = null,
        @JsonProperty("profile_path") @SerialName("profile_path") val profilePath: String? = null,
    )

    @Serializable
    data class TmdbCrewMember(
        @JsonProperty("name") @SerialName("name") val name: String? = null,
        @JsonProperty("job") @SerialName("job") val job: String? = null,
        @JsonProperty("department") @SerialName("department") val department: String? = null,
        @JsonProperty("profile_path") @SerialName("profile_path") val profilePath: String? = null,
    )

    @Serializable
    data class TmdbCredits(
        @JsonProperty("cast") @SerialName("cast") val cast: List<TmdbCastMember>? = null,
        @JsonProperty("crew") @SerialName("crew") val crew: List<TmdbCrewMember>? = null,
    )

    @Serializable
    data class TmdbImageLogo(
        @JsonProperty("file_path") @SerialName("file_path") val filePath: String? = null,
        @JsonProperty("iso_639_1") @SerialName("iso_639_1") val iso639: String? = null,
        @JsonProperty("aspect_ratio") @SerialName("aspect_ratio") val aspectRatio: Double? = null,
        @JsonProperty("vote_average") @SerialName("vote_average") val voteAverage: Double? = null,
    )

    @Serializable
    data class TmdbImages(
        @JsonProperty("logos") @SerialName("logos") val logos: List<TmdbImageLogo>? = null,
        @JsonProperty("backdrops") @SerialName("backdrops") val backdrops: List<TmdbImageLogo>? = null,
        @JsonProperty("posters") @SerialName("posters") val posters: List<TmdbImageLogo>? = null,
    )

    @Serializable
    data class TmdbKeyword(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
        @JsonProperty("name") @SerialName("name") val name: String? = null,
    )

    @Serializable
    data class TmdbKeywords(
        @JsonProperty("results") @SerialName("results") val results: List<TmdbKeyword>? = null,
        @JsonProperty("keywords") @SerialName("keywords") val keywords: List<TmdbKeyword>? = null,
    ) {
        val allKeywords get() = results ?: keywords ?: emptyList()
    }

    @Serializable
    data class TmdbCreatedBy(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
        @JsonProperty("name") @SerialName("name") val name: String? = null,
        @JsonProperty("profile_path") @SerialName("profile_path") val profilePath: String? = null,
    )

    @Serializable
    data class TmdbNetwork(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
        @JsonProperty("name") @SerialName("name") val name: String? = null,
        @JsonProperty("logo_path") @SerialName("logo_path") val logoPath: String? = null,
    )

    @Serializable
    data class TmdbProductionCompany(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
        @JsonProperty("name") @SerialName("name") val name: String? = null,
        @JsonProperty("logo_path") @SerialName("logo_path") val logoPath: String? = null,
        @JsonProperty("origin_country") @SerialName("origin_country") val originCountry: String? = null,
    )

    @Serializable
    data class TmdbVideo(
        @JsonProperty("key") @SerialName("key") val key: String? = null,
        @JsonProperty("site") @SerialName("site") val site: String? = null,
        @JsonProperty("type") @SerialName("type") val type: String? = null,
    )

    @Serializable
    data class TmdbVideos(
        @JsonProperty("results") @SerialName("results") val results: List<TmdbVideo>? = null,
    )

    // Shared between movie and tv search results
    @Serializable
    data class TmdbSearchResult(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
        @JsonProperty("title") @SerialName("title") val title: String? = null, // movies
        @JsonProperty("original_title") @SerialName("original_title") val originalTitle: String? = null,
        @JsonProperty("name") @SerialName("name") val name: String? = null, // tv
        @JsonProperty("original_name") @SerialName("original_name") val originalName: String? = null,
        @JsonProperty("poster_path") @SerialName("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") @SerialName("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("vote_average") @SerialName("vote_average") val voteAverage: Double? = null,
        @JsonProperty("release_date") @SerialName("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") @SerialName("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("media_type") @SerialName("media_type") val mediaType: String? = null, // for multi-search
    ) {
        @get:JsonIgnore val isTv get() = name != null || mediaType == "tv"
        @get:JsonIgnore val displayTitle get() = title ?: originalTitle ?: name ?: originalName ?: ""
        @get:JsonIgnore val year get() = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()
    }

    @Serializable
    data class TmdbPageResult(
        @JsonProperty("results") @SerialName("results") val results: List<TmdbSearchResult>? = null,
        @JsonProperty("total_pages") @SerialName("total_pages") val totalPages: Int? = null,
        @JsonProperty("total_results") @SerialName("total_results") val totalResults: Int? = null,
    )

    @Serializable
    data class TmdbMultiResult(
        @JsonProperty("results") @SerialName("results") val results: List<TmdbSearchResult>? = null,
    )

    @Serializable
    data class TmdbEpisode(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
        @JsonProperty("name") @SerialName("name") val name: String? = null,
        @JsonProperty("overview") @SerialName("overview") val overview: String? = null,
        @JsonProperty("episode_number") @SerialName("episode_number") val episodeNumber: Int? = null,
        @JsonProperty("season_number") @SerialName("season_number") val seasonNumber: Int? = null,
        @JsonProperty("still_path") @SerialName("still_path") val stillPath: String? = null,
        @JsonProperty("air_date") @SerialName("air_date") val airDate: String? = null,
        @JsonProperty("vote_average") @SerialName("vote_average") val voteAverage: Double? = null,
        @JsonProperty("runtime") @SerialName("runtime") val runtime: Int? = null,
        @JsonProperty("external_ids") @SerialName("external_ids") val externalIds: TmdbIds? = null,
    )

    @Serializable
    data class TmdbSeasonDetail(
        @JsonProperty("season_number") @SerialName("season_number") val seasonNumber: Int? = null,
        @JsonProperty("episodes") @SerialName("episodes") val episodes: List<TmdbEpisode>? = null,
    )

    @Serializable
    data class TmdbSeasonSummary(
        @JsonProperty("season_number") @SerialName("season_number") val seasonNumber: Int? = null,
        @JsonProperty("episode_count") @SerialName("episode_count") val episodeCount: Int? = null,
    )

    @Serializable
    data class TmdbContentRating(
        @JsonProperty("iso_3166_1") @SerialName("iso_3166_1") val country: String? = null,
        @JsonProperty("rating") @SerialName("rating") val rating: String? = null,
        @JsonProperty("descriptors") @SerialName("descriptors") val descriptors: List<String>? = null,
    )

    @Serializable
    data class TmdbContentRatings(
        @JsonProperty("results") @SerialName("results") val results: List<TmdbContentRating>? = null,
    )

    @Serializable
    data class TmdbReleaseDateEntry(
        @JsonProperty("certification") @SerialName("certification") val certification: String? = null,
        @JsonProperty("type") @SerialName("type") val type: Int? = null,
        @JsonProperty("descriptors") @SerialName("descriptors") val descriptors: List<String>? = null,
    )

    @Serializable
    data class TmdbReleaseDateResult(
        @JsonProperty("iso_3166_1") @SerialName("iso_3166_1") val country: String? = null,
        @JsonProperty("release_dates") @SerialName("release_dates") val releaseDates: List<TmdbReleaseDateEntry>? = null,
    )

    @Serializable
    data class TmdbReleaseDates(
        @JsonProperty("results") @SerialName("results") val results: List<TmdbReleaseDateResult>? = null,
    )

    @Serializable
    data class TmdbTvDetail(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
        @JsonProperty("name") @SerialName("name") val name: String? = null,
        @JsonProperty("original_name") @SerialName("original_name") val originalName: String? = null,
        @JsonProperty("overview") @SerialName("overview") val overview: String? = null,
        @JsonProperty("tagline") @SerialName("tagline") val tagline: String? = null,
        @JsonProperty("poster_path") @SerialName("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") @SerialName("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("first_air_date") @SerialName("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("vote_average") @SerialName("vote_average") val voteAverage: Double? = null,
        @JsonProperty("vote_count") @SerialName("vote_count") val voteCount: Int? = null,
        @JsonProperty("popularity") @SerialName("popularity") val popularity: Double? = null,
        @JsonProperty("genres") @SerialName("genres") val genres: List<TmdbGenre>? = null,
        @JsonProperty("episode_run_time") @SerialName("episode_run_time") val episodeRunTime: List<Int>? = null,
        @JsonProperty("seasons") @SerialName("seasons") val seasons: List<TmdbSeasonSummary>? = null,
        @JsonProperty("created_by") @SerialName("created_by") val createdBy: List<TmdbCreatedBy>? = null,
        @JsonProperty("networks") @SerialName("networks") val networks: List<TmdbNetwork>? = null,
        @JsonProperty("production_companies") @SerialName("production_companies") val productionCompanies: List<TmdbProductionCompany>? = null,
        @JsonProperty("external_ids") @SerialName("external_ids") val externalIds: TmdbIds? = null,
        @JsonProperty("videos") @SerialName("videos") val videos: TmdbVideos? = null,
        @JsonProperty("credits") @SerialName("credits") val credits: TmdbCredits? = null,
        @JsonProperty("images") @SerialName("images") val images: TmdbImages? = null,
        @JsonProperty("keywords") @SerialName("keywords") val keywords: TmdbKeywords? = null,
        @JsonProperty("recommendations") @SerialName("recommendations") val recommendations: TmdbPageResult? = null,
        @JsonProperty("similar") @SerialName("similar") val similar: TmdbPageResult? = null,
        @JsonProperty("content_ratings") @SerialName("content_ratings") val contentRatings: TmdbContentRatings? = null,
    ) {
        @get:JsonIgnore val displayTitle get() = name ?: originalName ?: ""
        @get:JsonIgnore val year get() = firstAirDate?.take(4)?.toIntOrNull()
    }

    @Serializable
    data class TmdbMovieDetail(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
        @JsonProperty("title") @SerialName("title") val title: String? = null,
        @JsonProperty("original_title") @SerialName("original_title") val originalTitle: String? = null,
        @JsonProperty("overview") @SerialName("overview") val overview: String? = null,
        @JsonProperty("tagline") @SerialName("tagline") val tagline: String? = null,
        @JsonProperty("poster_path") @SerialName("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") @SerialName("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("release_date") @SerialName("release_date") val releaseDate: String? = null,
        @JsonProperty("vote_average") @SerialName("vote_average") val voteAverage: Double? = null,
        @JsonProperty("vote_count") @SerialName("vote_count") val voteCount: Int? = null,
        @JsonProperty("popularity") @SerialName("popularity") val popularity: Double? = null,
        @JsonProperty("genres") @SerialName("genres") val genres: List<TmdbGenre>? = null,
        @JsonProperty("runtime") @SerialName("runtime") val runtime: Int? = null,
        @JsonProperty("imdb_id") @SerialName("imdb_id") val imdbId: String? = null,
        @JsonProperty("production_companies") @SerialName("production_companies") val productionCompanies: List<TmdbProductionCompany>? = null,
        @JsonProperty("external_ids") @SerialName("external_ids") val externalIds: TmdbIds? = null,
        @JsonProperty("videos") @SerialName("videos") val videos: TmdbVideos? = null,
        @JsonProperty("credits") @SerialName("credits") val credits: TmdbCredits? = null,
        @JsonProperty("images") @SerialName("images") val images: TmdbImages? = null,
        @JsonProperty("keywords") @SerialName("keywords") val keywords: TmdbKeywords? = null,
        @JsonProperty("recommendations") @SerialName("recommendations") val recommendations: TmdbPageResult? = null,
        @JsonProperty("similar") @SerialName("similar") val similar: TmdbPageResult? = null,
        @JsonProperty("release_dates") @SerialName("release_dates") val releaseDates: TmdbReleaseDates? = null,
    ) {
        @get:JsonIgnore val displayTitle get() = title ?: originalTitle ?: ""
        @get:JsonIgnore val year get() = releaseDate?.take(4)?.toIntOrNull()
    }

    private fun getUrl(id: Int?, tvShow: Boolean): String {
        return if (tvShow) "https://www.themoviedb.org/tv/${id ?: -1}"
        else "https://www.themoviedb.org/movie/${id ?: -1}"
    }

    private suspend fun getApi(path: String, extraParams: Map<String, String> = emptyMap()): String {
        return getApiStatic(path, extraParams)
    }

    private fun List<TmdbCastMember?>?.toActors(): List<Pair<Actor, String?>>? {
        return this?.mapNotNull {
            it ?: return@mapNotNull null
            Pair(
                Actor(it.name ?: return@mapNotNull null, getImageUrl(it.profilePath)),
                it.character,
            )
        }
    }

    private fun TmdbVideos?.toTrailers(): List<String>? {
        val skipTypes = setOf("Opening Credits", "Featurette")
        return this?.results
            ?.filter { it.type !in skipTypes }
            ?.sortedBy { it.type }
            ?.mapNotNull {
                when (it.site?.trim()?.lowercase()) {
                    "youtube" -> "https://www.youtube.com/watch?v=${it.key}"
                    else -> null
                }
            }
    }

    open suspend fun fetchContentRating(id: Int?, country: String): String? {
        id ?: return null
        // Try TV content ratings first
        val tvRating = tryParseJson<TmdbContentRatings>(
            getApi("/tv/$id/content_ratings")
        )?.results?.firstOrNull { it.country == country }?.rating
        if (tvRating != null) return tvRating

        // Fall back to movie release dates
        return tryParseJson<TmdbReleaseDates>(
            getApi("/movie/$id/release_dates")
        )?.results?.firstOrNull { it.country == country }
            ?.releaseDates?.firstOrNull { !it.certification.isNullOrBlank() }
            ?.certification
    }

    private suspend fun TmdbTvDetail.toLoadResponse(): TvSeriesLoadResponse {
        val episodes = mutableListOf<Episode>()
        val validSeasons = seasons?.filter { !disableSeasonZero || (it.seasonNumber ?: 0) != 0 }
            ?: emptyList()

        for (season in validSeasons) {
            val seasonNum = season.seasonNumber ?: continue
            val fullSeason = tryParseJson<TmdbSeasonDetail>(
                getApi("/tv/$id/season/$seasonNum", mapOf("append_to_response" to "external_ids"))
            )

            fullSeason?.episodes?.forEach { episode ->
                episodes += newEpisode(
                    TmdbLink(
                        episode.externalIds?.imdbId ?: externalIds?.imdbId,
                        id,
                        episode.episodeNumber,
                        episode.seasonNumber,
                        displayTitle,
                    ).toJson()
                ) {
                    this.name = episode.name
                    this.season = episode.seasonNumber
                    this.episode = episode.episodeNumber
                    this.score = Score.from10(episode.voteAverage)
                    this.description = episode.overview
                    this.posterUrl = getImageUrl(episode.stillPath)
                    this.runTime = episode.runtime
                    this.addDate(episode.airDate)
                }
            }
        }

        return newTvSeriesLoadResponse(
            displayTitle,
            getUrl(id, true),
            TvType.TvSeries,
            episodes,
        ) {
            posterUrl = getImageUrl(posterPath)
            backgroundPosterUrl = extractBackdropUrl(backdropPath, images)
            logoUrl = extractLogoUrl(images)
            this.year = this@toLoadResponse.year
            plot = overview
            addImdbId(externalIds?.imdbId)
            tags = genres?.mapNotNull { it.name }
            duration = episodeRunTime?.average()?.toInt()
            score = Score.from10(voteAverage)
            addTrailer(videos.toTrailers())
            recommendations = (this@toLoadResponse.recommendations
                ?: this@toLoadResponse.similar)?.results?.map { it.toSearchResponse() }
            addActors(credits?.cast?.toList().toActors())
            contentRating = contentRatings?.results?.firstOrNull { it.country == "US" }?.rating
                ?: fetchContentRating(id, "US")
        }
    }

    private suspend fun TmdbMovieDetail.toLoadResponse(): MovieLoadResponse {
        return newMovieLoadResponse(
            displayTitle,
            getUrl(id, false),
            TvType.Movie,
            TmdbLink(
                imdbId ?: externalIds?.imdbId,
                id,
                null,
                null,
                displayTitle,
            ).toJson()
        ) {
            posterUrl = getImageUrl(posterPath)
            backgroundPosterUrl = extractBackdropUrl(backdropPath, images)
            logoUrl = extractLogoUrl(images)
            this.year = this@toLoadResponse.year
            plot = overview
            addImdbId(imdbId ?: externalIds?.imdbId)
            tags = genres?.mapNotNull { it.name }
            duration = runtime
            score = Score.from10(voteAverage)
            addTrailer(videos.toTrailers())
            recommendations = (this@toLoadResponse.recommendations
                ?: this@toLoadResponse.similar)?.results?.map { it.toSearchResponse() }
            addActors(credits?.cast?.toList().toActors())
            contentRating = releaseDates?.results
                ?.firstOrNull { it.country == "US" }
                ?.releaseDates?.firstOrNull { !it.certification.isNullOrBlank() }
                ?.certification
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var discoverMovies: List<MovieSearchResponse> = listOf()
        var discoverSeries: List<TvSeriesSearchResponse> = listOf()
        var topMovies: List<MovieSearchResponse> = listOf()
        var topSeries: List<TvSeriesSearchResponse> = listOf()
        runAllAsync(
            {
                discoverMovies = tryParseJson<TmdbPageResult>(
                    getApi("/discover/movie", mapOf("page" to "$page"))
                )?.results?.map { it.toSearchResponse() as MovieSearchResponse } ?: listOf()
            },
            {
                discoverSeries = tryParseJson<TmdbPageResult>(
                    getApi("/discover/tv", mapOf("page" to "$page"))
                )?.results?.map { it.toSearchResponse() as TvSeriesSearchResponse } ?: listOf()
            },
            {
                topMovies = tryParseJson<TmdbPageResult>(
                    getApi("/movie/top_rated", mapOf("page" to "$page", "language" to "en-US", "region" to "US"))
                )?.results?.map { it.toSearchResponse() as MovieSearchResponse } ?: listOf()
            },
            {
                topSeries = tryParseJson<TmdbPageResult>(
                    getApi("/tv/top_rated", mapOf("page" to "$page", "language" to "en-US"))
                )?.results?.map { it.toSearchResponse() as TvSeriesSearchResponse } ?: listOf()
            },
        )

        return newHomePageResponse(
            listOf(
                HomePageList("Popular Movies", discoverMovies),
                HomePageList("Popular Series", discoverSeries),
                HomePageList("Top Movies", topMovies),
                HomePageList("Top Series", topSeries),
            )
        )
    }

    open fun loadFromImdb(imdb: String, seasons: List<TmdbSeasonSummary>): LoadResponse? = null
    open fun loadFromTmdb(tmdbId: Int, seasons: List<TmdbSeasonSummary>): LoadResponse? = null
    open fun loadFromImdb(imdb: String): LoadResponse? = null
    open fun loadFromTmdb(tmdbId: Int): LoadResponse? = null

    override suspend fun load(url: String): LoadResponse? {
        // https://www.themoviedb.org/movie/7445-brothers
        // https://www.themoviedb.org/tv/71914-the-wheel-of-time
        val idRegex = Regex("""themoviedb\.org/(.*)/(\d+)""")
        val found = idRegex.find(url)

        val isTvSeries = found?.groupValues?.getOrNull(1).equals("tv", ignoreCase = true)
        val id = found?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: throw ErrorLoadingException("No id found")

        return if (useMetaLoadResponse) {
            if (isTvSeries) {
                val detail = parseJson<TmdbTvDetail>(
                    getApi(
                        "/tv/$id",
                        mapOf(
                            "language" to "en-US",
                            "append_to_response" to "external_ids,videos,credits,recommendations,similar,content_ratings,images,keywords",
                        )
                    )
                )
                detail.toLoadResponse()
            } else {
                val detail = parseJson<TmdbMovieDetail>(
                    getApi(
                        "/movie/$id",
                        mapOf(
                            "language" to "en-US",
                            "append_to_response" to "external_ids,videos,credits,recommendations,similar,release_dates,images,keywords",
                        )
                    )
                )
                detail.toLoadResponse()
            }
        } else {
            loadFromTmdb(id)?.let { return it }
            if (isTvSeries) {
                val externalIds = parseJson<TmdbIds>(getApi("/tv/$id/external_ids"))
                val imdbId = externalIds.imdbId
                if (imdbId != null) {
                    val fromImdb = loadFromImdb(imdbId)
                    if (fromImdb != null) return fromImdb
                }
                val seasons = parseJson<TmdbTvDetail>(getApi("/tv/$id")).seasons ?: listOf()
                if (imdbId != null) {
                    loadFromImdb(imdbId, seasons) ?: loadFromTmdb(id, seasons)
                } else {
                    loadFromTmdb(id, seasons)
                }
            } else {
                val imdbId = parseJson<TmdbMovieDetail>(getApi("/movie/$id")).imdbId
                if (imdbId != null) loadFromImdb(imdbId) else null
            }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        return tryParseJson<TmdbMultiResult>(
            getApi(
                "/search/multi",
                mapOf(
                    "query" to query,
                    "page" to "$page",
                    "language" to "en-US",
                    "include_adult" to "$includeAdult",
                )
            )
        )?.results?.mapNotNull {
            if (it.mediaType == "person") null else it.toSearchResponse()
        }?.toNewSearchResponseList()
    }
}
