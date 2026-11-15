package com.example.secwatch.osv

import com.example.secwatch.common.PackageReference
import com.example.secwatch.common.Severity
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

private data class OsvQuery(val packageReference: PackageReference, val pageToken: String?)

class OsvClient(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : VulnerabilitySource {

    override fun findVulnerabilityIds(packageReferences: List<PackageReference>): Map<PackageReference, List<String>> {
        val vulnerabilityIds = LinkedHashMap<PackageReference, MutableList<String>>()
        for (packageReference in packageReferences) {
            vulnerabilityIds.getOrPut(packageReference) { mutableListOf() }
        }

        val usedPageTokens = mutableMapOf<PackageReference, MutableSet<String>>()
        var pendingQueries = vulnerabilityIds.keys.map { packageReference -> OsvQuery(packageReference, null) }
        while (pendingQueries.isNotEmpty()) {
            val nextQueries = mutableListOf<OsvQuery>()
            for (queries in pendingQueries.chunked(MAX_BATCH_SIZE)) {
                val results = queryBatch(queries).path("results")
                if (!results.isArray || results.size() != queries.size) throw OsvUnavailableException(INVALID_FORMAT_MESSAGE)

                for ((index, query) in queries.withIndex()) {
                    val result = results.path(index)
                    if (!result.isObject) throw OsvUnavailableException(INVALID_FORMAT_MESSAGE)
                    for (vulnerability in result.path("vulns")) {
                        vulnerabilityIds.getValue(query.packageReference).add(vulnerability.path("id").asText())
                    }
                    if (result.hasNonNull("next_page_token")) {
                        val nextPageToken = result.get("next_page_token").asText()
                        val usedTokens = usedPageTokens.getOrPut(query.packageReference) { mutableSetOf() }
                        if (!usedTokens.add(nextPageToken)) throw OsvUnavailableException("OSV가 같은 페이지 토큰을 반복해서 돌려줍니다.")
                        nextQueries.add(OsvQuery(query.packageReference, nextPageToken))
                    }
                }
            }
            pendingQueries = nextQueries
        }
        return vulnerabilityIds
    }

    override fun getVulnerability(id: String): OsvVulnerability? {
        val rawJson = try {
            restClient.get().uri("/v1/vulns/{id}", id).retrieve().body(String::class.java)
        } catch (e: HttpClientErrorException) {
            // 업스트림에서 삭제되거나 병합된 기록은 가져올 수 없으므로 점검을 막지 않는다
            if (e.statusCode.value() in GONE_STATUS_CODES) return null
            throw OsvUnavailableException("OSV 조회에 실패했습니다.", e)
        } catch (e: RestClientException) {
            throw OsvUnavailableException("OSV 조회에 실패했습니다.", e)
        } ?: throw OsvUnavailableException("OSV 응답이 비어 있습니다.")

        if (rawJson.isBlank()) throw OsvUnavailableException("OSV 응답이 비어 있습니다.")
        val node = try {
            objectMapper.readTree(rawJson)
        } catch (e: JsonProcessingException) {
            throw OsvUnavailableException("OSV 응답이 JSON이 아닙니다.", e)
        }
        val vulnerabilityId = node.path("id").asText()
        if (!node.isObject || vulnerabilityId.isBlank()) throw OsvUnavailableException(INVALID_FORMAT_MESSAGE)
        if (node.hasNonNull("withdrawn")) return null

        return OsvVulnerability(
            id = vulnerabilityId,
            aliases = node.path("aliases").map { alias -> alias.asText() },
            summary = node.path("summary").asText(""),
            severity = Severity.fromOsv(node.path("database_specific").path("severity").asText(null)),
            fixedVersions = fixedVersions(node),
            referenceUrl = "https://osv.dev/vulnerability/$vulnerabilityId",
            rawJson = rawJson,
        )
    }

    private fun queryBatch(queries: List<OsvQuery>): JsonNode {
        val requestQueries = queries.map { query -> query.toRequestBody() }
        return try {
            restClient.post().uri("/v1/querybatch")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("queries" to requestQueries))
                .retrieve()
                .body(JsonNode::class.java)
        } catch (e: RestClientException) {
            throw OsvUnavailableException("OSV 조회에 실패했습니다.", e)
        } ?: throw OsvUnavailableException("OSV 응답이 비어 있습니다.")
    }

    private fun OsvQuery.toRequestBody(): Map<String, Any> {
        val body = mutableMapOf<String, Any>(
            "package" to mapOf("name" to packageReference.name, "ecosystem" to packageReference.ecosystem),
            "version" to packageReference.version,
        )
        if (pageToken != null) body["page_token"] = pageToken
        return body
    }

    private fun fixedVersions(node: JsonNode): List<String> {
        val fixedVersions = mutableListOf<String>()
        for (affected in node.path("affected")) {
            for (range in affected.path("ranges")) {
                for (event in range.path("events")) {
                    if (event.hasNonNull("fixed")) fixedVersions.add(event.get("fixed").asText())
                }
            }
        }
        return fixedVersions.distinct()
    }

    private companion object {
        const val MAX_BATCH_SIZE = 1000
        const val INVALID_FORMAT_MESSAGE = "OSV 응답 형식이 올바르지 않습니다."
        val GONE_STATUS_CODES = setOf(404, 410)
    }
}
