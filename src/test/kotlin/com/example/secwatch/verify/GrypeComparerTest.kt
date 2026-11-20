package com.example.secwatch.verify

import com.example.secwatch.verify.GrypeComparer.Comparison
import com.example.secwatch.verify.GrypeComparer.key
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Grype 결과 비교")
class GrypeComparerTest {
    private fun set(vararg keys: String) = keys.toSet()

    @Test
    @DisplayName("별칭이 겹치면 같은 취약점으로 본다")
    fun matches_when_alias_overlaps() {
        val ours = setOf(set(key("lodash", "4.17.20", "GHSA-1"), key("lodash", "4.17.20", "CVE-1")))
        val grype = listOf(set(key("lodash", "4.17.20", "CVE-1")))

        assertThat(GrypeComparer.compare(ours, grype)).isEqualTo(Comparison(both = 1, onlyOurs = 0, onlyGrype = 0))
    }

    @Test
    @DisplayName("한쪽에만 있는 항목을 따로 센다")
    fun counts_only_sides() {
        val ours = setOf(set("a@1|X"), set("a@1|OnlyOurs"))
        val grype = listOf(set("a@1|X"), set("a@1|OnlyGrype"))

        assertThat(GrypeComparer.compare(ours, grype)).isEqualTo(Comparison(both = 1, onlyOurs = 1, onlyGrype = 1))
    }

    @Test
    @DisplayName("같은 번호라도 다른 패키지면 같은 것으로 보지 않는다")
    fun same_id_on_a_different_package_is_not_a_match() {
        val ours = setOf(set(key("a", "1", "CVE-1")))
        val grype = listOf(set(key("b", "1", "CVE-1")))

        assertThat(GrypeComparer.compare(ours, grype)).isEqualTo(Comparison(both = 0, onlyOurs = 1, onlyGrype = 1))
    }

    @Test
    @DisplayName("Grype가 한 취약점을 두 번 나열해도 한 번으로 센다")
    fun grype_listing_one_vulnerability_twice_counts_once() {
        val ours = setOf(set(key("a", "1", "GHSA-1"), key("a", "1", "CVE-1")))
        val grype = listOf(set(key("a", "1", "GHSA-1")), set(key("a", "1", "CVE-1")))

        assertThat(GrypeComparer.compare(ours, grype)).isEqualTo(Comparison(both = 1, onlyOurs = 0, onlyGrype = 0))
    }

    @Test
    @DisplayName("입력이 비어 있어도 올바르게 센다")
    fun empty_inputs() {
        assertThat(GrypeComparer.compare(emptySet(), emptyList())).isEqualTo(Comparison(0, 0, 0))
        assertThat(GrypeComparer.compare(setOf(set("a@1|X")), emptyList())).isEqualTo(Comparison(0, 1, 0))
    }

    @Test
    @DisplayName("Grype JSON에서 번호와 관련 번호를 읽는다")
    fun reads_ids_and_related_ids_from_grype_json() {
        val json = javaClass.getResource("/fixtures/grype-sample.json")!!.readText()

        val sets = GrypeComparer.fromGrypeJson(json)

        assertThat(sets).contains(
            set(key("lodash", "4.17.20", "GHSA-35jh-r3h4-6jhm"), key("lodash", "4.17.20", "CVE-2021-23337")),
            set(key("minimist", "1.2.0", "GHSA-xxxx-yyyy-zzzz")),
        )
    }

    @Test
    @DisplayName("matches가 빈 배열이면 발견 0건으로 읽는다")
    fun empty_matches_array_is_zero_findings() {
        assertThat(GrypeComparer.fromGrypeJson("""{"matches":[]}""")).isEmpty()
    }

    @Test
    @DisplayName("matches가 없거나 배열이 아니면 0건으로 읽지 않고 실패시킨다")
    fun missing_or_non_array_matches_fails_instead_of_reading_zero_findings() {
        assertThatThrownBy { GrypeComparer.fromGrypeJson("{}") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { GrypeComparer.fromGrypeJson("""{"matches":null}""") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { GrypeComparer.fromGrypeJson("""{"matches":{}}""") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    @DisplayName("별칭을 거쳐 간접으로 이어진 항목도 단독 목록에 넣지 않고 집계와 같게 분류한다")
    fun indirectly_connected_items_are_not_listed_as_only_one_side() {
        val ours = listOf(set(key("a", "1", "A"), key("a", "1", "B")))
        val grype = listOf(set(key("a", "1", "A")), set(key("a", "1", "B"), key("a", "1", "C")), set(key("a", "1", "C")))

        assertThat(GrypeComparer.compare(ours, grype)).isEqualTo(Comparison(both = 1, onlyOurs = 0, onlyGrype = 0))
        val only = GrypeComparer.onlyItems(ours, grype)
        assertThat(only.ours).isEmpty()
        assertThat(only.grype).isEmpty()
    }

    @Test
    @DisplayName("한쪽에만 있는 항목은 그 위치를 돌려준다")
    fun only_items_return_the_positions_of_one_sided_items() {
        val ours = listOf(set("a@1|X"), set("a@1|OnlyOurs"))
        val grype = listOf(set("a@1|X"), set("a@1|OnlyGrype"), set("b@1|Y"))

        val only = GrypeComparer.onlyItems(ours, grype)

        assertThat(only.ours).containsExactly(1)
        assertThat(only.grype).containsExactly(1, 2)
    }
}
