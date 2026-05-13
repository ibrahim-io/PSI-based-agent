package io.github.ibrahimio.psiagent.toolwindow

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Paths

data class SearchResult(val name: String, val file: String?)

object PsiAgentClient {
    private val client: HttpClient = HttpClient.newBuilder().build()
    private val gson = Gson()
    private val base = "http://127.0.0.1:9742"

    private fun readToken(): String? {
        try {
            val home = System.getenv("USERPROFILE") ?: System.getProperty("user.home")
            val tokenPath = Paths.get(home, ".psi-agent", "token")
            if (Files.exists(tokenPath)) {
                return Files.readString(tokenPath).trim()
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun newRequestBuilder(uri: String): HttpRequest.Builder {
        val b = HttpRequest.newBuilder().uri(URI.create(uri))
        val token = readToken()
        if (!token.isNullOrBlank()) b.header("Authorization", "Bearer $token")
        return b
    }

    fun ping(): Boolean {
        return try {
            val req = newRequestBuilder("$base/api/health").GET().build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
            resp.statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }

    fun search(query: String): List<SearchResult> {
        try {
            val body = gson.toJson(mapOf("query" to query, "type" to "all"))
            val req = newRequestBuilder("$base/api/search")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() !in 200..299) return emptyList()
            val json = resp.body()
            val el: JsonElement = JsonParser.parseString(json)
            // support responses of form { results: [...] } or top-level array
            val results = mutableListOf<SearchResult>()
            if (el.isJsonObject) {
                val obj = el.asJsonObject
                val arr = when {
                    obj.has("results") -> obj.get("results")
                    obj.has("items") -> obj.get("items")
                    else -> null
                }
                if (arr != null && arr.isJsonArray) {
                    arr.asJsonArray.forEach { item ->
                        if (item.isJsonObject) {
                            val o = item.asJsonObject
                            val name = (o.get("name")?.asString ?: o.get("qualifiedName")?.asString ?: o.get("text")?.asString
                                ?: o.toString())
                            val file = o.get("file")?.asString ?: o.get("path")?.asString
                            results.add(SearchResult(name, file))
                        } else {
                            results.add(SearchResult(item.toString(), null))
                        }
                    }
                    return results
                }
            } else if (el.isJsonArray) {
                el.asJsonArray.forEach { item ->
                    if (item.isJsonObject) {
                        val o = item.asJsonObject
                        val name = (o.get("name")?.asString ?: o.get("qualifiedName")?.asString ?: o.toString())
                        val file = o.get("file")?.asString ?: o.get("path")?.asString
                        results.add(SearchResult(name, file))
                    } else {
                        results.add(SearchResult(item.toString(), null))
                    }
                }
                return results
            }
            // fallback: return raw body as single result
            return listOf(SearchResult(json, null))
        } catch (e: Exception) {
            return emptyList()
        }
    }
}


