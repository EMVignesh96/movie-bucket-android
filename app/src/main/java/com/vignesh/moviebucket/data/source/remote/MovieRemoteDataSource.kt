/*
 * Copyright 2020 V App Factory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vignesh.moviebucket.data.source.remote

import com.vignesh.moviebucket.data.Result
import com.vignesh.moviebucket.data.model.Movie
import com.vignesh.moviebucket.data.model.SearchResult
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

object MovieRemoteDataSource : RemoteDataSource {

    private const val RESULTS = "results"
    private const val RELEASE_DATE = "release_date"
    private const val MOVIE_ID = "id"
    private const val TITLE = "title"
    private const val POPULARITY = "popularity"
    private const val RATING = "vote_average"
    private const val POSTER_PATH = "poster_path"
    private const val GENRES = "genres"
    private const val GENRE_NAME = "name"
    private const val CAST = "cast"
    private const val CAST_NAME = "name"
    private const val RUNTIME = "runtime"
    private const val OVERVIEW = "overview"

    private const val LIB_TYPE_POPULAR = 1
    private const val LIB_TYPE_TOP_RATED = 2
    private const val LIB_TYPE_UPCOMING = 3

    override suspend fun search(query: String): Result<List<SearchResult>> {
        return try {
            val response = JSONObject(TMDbApi.retrofitService.search(query))
            val results = response.getJSONArray(RESULTS)
            val list = mutableListOf<SearchResult>()

            for (i in 0 until results.length()) {
                list.add(parseSearchResult(results.getJSONObject(i)))
            }

            Result.Success(list)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun loadLibraries(): Result<List<Movie>> {
        val lib = mutableListOf<Movie>().apply {
            addAll(getPopularMovies())
            addAll(getTopRatedMovies())
            addAll(getUpcomingMovies())
        }
        return Result.Success(lib)
    }

    private suspend fun getUpcomingMovies(): List<Movie> {
        val upcoming = mutableListOf<Movie>()
        try {
            val response = JSONObject(TMDbApi.retrofitService.getUpcomingMovies())
            val results = response.getJSONArray(RESULTS)
            upcoming.addAll(getMovieList(results, LIB_TYPE_UPCOMING))
        } catch (e: Exception) {
            return upcoming
        }
        return upcoming
    }

    private suspend fun getTopRatedMovies(): List<Movie> {
        val topRatedMovies = mutableListOf<Movie>()
        try {
            val response = JSONObject(TMDbApi.retrofitService.getTopRatedMovies())
            val results = response.getJSONArray(RESULTS)
            topRatedMovies.addAll(getMovieList(results, LIB_TYPE_TOP_RATED))
        } catch (e: Exception) {
            return topRatedMovies
        }
        return topRatedMovies
    }

    private suspend fun getPopularMovies(): List<Movie> {
        val popularMovies = mutableListOf<Movie>()
        try {
            val response = JSONObject(TMDbApi.retrofitService.getPopularMovies())
            val results = response.getJSONArray(RESULTS)
            popularMovies.addAll(getMovieList(results, LIB_TYPE_POPULAR))
        } catch (e: Exception) {
            return popularMovies
        }
        return popularMovies
    }

    private suspend fun getMovieList(results: JSONArray, type: Int): List<Movie> {
        val popularMovies = mutableListOf<Movie>()
        for (i in 0 until results.length()) {
            try {
                val id = results.getJSONObject(i).getString(MOVIE_ID)
                val details = JSONObject(TMDbApi.retrofitService.getMovieDetails(id))
                val credits = JSONObject(TMDbApi.retrofitService.getMovieCredits(id))
                popularMovies.add(parseMovie(id, details, credits, type))
            } catch (e: Exception) {
            }
        }
        return popularMovies
    }

    private fun parseMovie(id: String, details: JSONObject, credits: JSONObject, type: Int): Movie {
        val genres = getGenres(details.getJSONArray(GENRES))
        val cast = getCast(credits)

        with(details) {
            val title = getString(TITLE)
            val releaseDate = getString(RELEASE_DATE)
            val runtime = getInt(RUNTIME)
            val popularity = getString(POPULARITY)
            val rating = getString(RATING)
            val overView = getString(OVERVIEW)
            val posterPath = getString(POSTER_PATH)
            return Movie(
                id,
                title,
                releaseDate,
                runtime,
                popularity,
                rating,
                genres,
                overView,
                cast,
                posterPath
            ).apply { libraryItemType = type }
        }
    }

    private fun getCast(credits: JSONObject): String {

        val castArray = credits.getJSONArray(CAST)

        return StringBuilder().apply {
            val len = min(castArray.length(), 10)
            for (i in 0 until len) {
                val cast = castArray.getJSONObject(i)
                append(cast.getString(CAST_NAME))
                if (i != len - 1) {
                    append(", ")
                }
            }
        }.toString()
    }

    private fun getGenres(genreArray: JSONArray): String {
        return StringBuilder().apply {
            for (i in 0 until genreArray.length()) {
                append(genreArray.getJSONObject(i).getString(GENRE_NAME))
            }
        }.toString()
    }

    private fun parseSearchResult(result: JSONObject): SearchResult {
        return with(result) {
            val releaseYear = with(getString(RELEASE_DATE)) {
                val parts = split("-")
                if (parts.size == 3) parts[0]
                else ""
            }

            SearchResult(
                optString(MOVIE_ID),
                getString(TITLE),
                optString(POPULARITY),
                releaseYear,
                getString(RATING),
                getString(POSTER_PATH)
            )
        }
    }

}