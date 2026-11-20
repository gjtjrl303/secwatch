package com.example.secwatch.verify

import com.fasterxml.jackson.databind.ObjectMapper

/** 우리 결과와 Grype 결과를 비교한다. 두 항목의 id 집합(id와 별칭)이 겹치면 같은 취약점이다. */
object GrypeComparer {
    data class Comparison(val both: Int, val onlyOurs: Int, val onlyGrype: Int)

    fun key(name: String, version: String, id: String) = "$name@$version|$id"

    fun compare(ours: Set<Set<String>>, grypeIds: List<Set<String>>): Comparison {
        val items = ours.map { Side.OURS to it } + grypeIds.map { Side.GRYPE to it }
        val parent = IntArray(items.size) { it }

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            return root
        }

        val firstItemByKey = HashMap<String, Int>()
        items.forEachIndexed { index, (_, keys) ->
            for (key in keys) {
                val other = firstItemByKey.putIfAbsent(key, index) ?: continue
                parent[find(index)] = find(other)
            }
        }

        val sidesByRoot = HashMap<Int, MutableSet<Side>>()
        items.forEachIndexed { index, (side, _) -> sidesByRoot.getOrPut(find(index)) { mutableSetOf() } += side }

        val groups = sidesByRoot.values
        return Comparison(
            both = groups.count { it.size == 2 },
            onlyOurs = groups.count { it == setOf(Side.OURS) },
            onlyGrype = groups.count { it == setOf(Side.GRYPE) },
        )
    }

    fun fromGrypeJson(json: String): List<Set<String>> =
        ObjectMapper().readTree(json).path("matches").map { match ->
            val artifact = match.path("artifact")
            val ids = listOf(match.path("vulnerability").path("id").asText()) +
                match.path("relatedVulnerabilities").map { it.path("id").asText() }
            ids.filter { it.isNotBlank() }
                .map { key(artifact.path("name").asText(), artifact.path("version").asText(), it) }
                .toSet()
        }

    private enum class Side { OURS, GRYPE }
}
