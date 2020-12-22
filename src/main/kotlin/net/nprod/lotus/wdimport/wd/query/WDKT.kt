// SPDX-License-Identifier: AGPL-3.0-or-later
/**
 * Copyright (c) 2020 Jonathan Bisson
 */

package net.nprod.lotus.wdimport.wd.query

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SearchInfoResponse(
    val totalhits: Int
)

@Serializable
data class SearchResponse(
    val ns: Int,
    val title: String,
    val pageid: Long,
    val size: Int,
    val wordcount: Int,
    val snippet: String,
    val timestamp: String
)

@Serializable
data class QueryResponse(
    val searchinfo: SearchInfoResponse,
    val search: List<SearchResponse>
)

@Serializable
data class QueryActionResponse(
    val batchcomplete: String,
    val query: QueryResponse
)

interface IWDKT {
    fun close()
    fun searchDOI(doi: String): QueryActionResponse?
}

/**
 * A way to run queries directly using WDTK
 */
class WDKT : IWDKT {
    private val client: HttpClient = HttpClient(CIO) {
        engine {
            endpoint {
                keepAliveTime = 10_000
                connectTimeout = 10_000
                connectRetryAttempts = 5
            }
        }
    }

    /**
     * Close the client
     */
    override fun close(): Unit = client.close()

    /**
     * Search for a given doi
     * returns the deserialized response
     */
    override fun searchDOI(doi: String): QueryActionResponse {
        val out: String = runBlocking {
            client.get("https://www.wikidata.org/w/api.php") {
                parameter("action", "query")
                parameter("format", "json")
                parameter("list", "search")
                parameter("srsearch", "haswbstatement:\"P356=$doi\"")
            }
        }
        return Json.decodeFromString(QueryActionResponse.serializer(), out)
    }
}