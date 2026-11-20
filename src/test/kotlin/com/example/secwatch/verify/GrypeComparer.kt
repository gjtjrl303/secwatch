package com.example.secwatch.verify

import com.fasterxml.jackson.databind.ObjectMapper

/** 우리 결과와 Grype 결과를 비교한다. 두 항목의 id 집합(id와 별칭)이 겹치면 같은 취약점이고, 별칭을 거쳐 이어진 것도 같은 묶음이다. */
object GrypeComparer {
    data class Comparison(val both: Int, val onlyOurs: Int, val onlyGrype: Int)

    /** 한쪽에만 있는 묶음에 속한 항목이 입력 목록의 몇 번째인지(0부터) 담는다. */
    data class OnlyItems(val ours: List<Int>, val grype: List<Int>)

    fun key(name: String, version: String, id: String) = "$name@$version|$id"

    fun compare(ours: Collection<Set<String>>, grypeIds: List<Set<String>>): Comparison {
        val grouping = group(ours, grypeIds)
        val groups = grouping.sidesByRoot.values
        return Comparison(
            both = groups.count { it.size == 2 },
            onlyOurs = groups.count { it == setOf(Side.OURS) },
            onlyGrype = groups.count { it == setOf(Side.GRYPE) },
        )
    }

    /** 집계(compare)와 같은 묶음 분류로, 한쪽에만 있는 묶음의 항목 위치를 돌려준다. */
    fun onlyItems(ours: Collection<Set<String>>, grypeIds: List<Set<String>>): OnlyItems {
        val grouping = group(ours, grypeIds)
        val oursCount = ours.size
        fun isOnly(index: Int, side: Side) = grouping.sidesByRoot.getValue(grouping.rootOf[index]) == setOf(side)
        return OnlyItems(
            ours = (0 until oursCount).filter { index -> isOnly(index, Side.OURS) },
            grype = grypeIds.indices.filter { index -> isOnly(oursCount + index, Side.GRYPE) },
        )
    }

    /** matches가 없거나 배열이 아니면 발견 0건으로 읽지 않고 실패시킨다. 잘못된 파일이 정상 결과처럼 보이지 않게 하기 위해서다. */
    fun fromGrypeJson(json: String): List<Set<String>> {
        val matches = ObjectMapper().readTree(json)?.get("matches")
        require(matches != null && matches.isArray) { "Grype 결과에 matches 배열이 없습니다." }
        return matches.map { match ->
            val artifact = match.path("artifact")
            val ids = listOf(match.path("vulnerability").path("id").asText()) +
                match.path("relatedVulnerabilities").map { it.path("id").asText() }
            ids.filter { it.isNotBlank() }
                .map { key(artifact.path("name").asText(), artifact.path("version").asText(), it) }
                .toSet()
        }
    }

    private class Grouping(val rootOf: IntArray, val sidesByRoot: Map<Int, Set<Side>>)

    private fun group(ours: Collection<Set<String>>, grypeIds: List<Set<String>>): Grouping {
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

        val rootOf = IntArray(items.size) { index -> find(index) }
        val sidesByRoot = HashMap<Int, MutableSet<Side>>()
        items.forEachIndexed { index, (side, _) -> sidesByRoot.getOrPut(rootOf[index]) { mutableSetOf() } += side }
        return Grouping(rootOf, sidesByRoot)
    }

    private enum class Side { OURS, GRYPE }
}
