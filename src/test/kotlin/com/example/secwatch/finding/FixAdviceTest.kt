package com.example.secwatch.finding

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("고치는 방법 문구")
class FixAdviceTest {

    @Test
    @DisplayName("고쳐진 버전이 하나면 그 버전 이상으로 올리라고 안내한다")
    fun single_fixed_version() {
        assertThat(FixAdvice.text("4.17.20", listOf("4.17.21"))).isEqualTo("4.17.21 이상으로 올립니다.")
    }

    @Test
    @DisplayName("고쳐진 버전이 여러 개면 지금 버전보다 높은 것 중 가장 가까운 버전을 안내한다")
    fun nearest_higher_fixed_version() {
        assertThat(FixAdvice.text("5.3.20", listOf("6.1.13", "5.3.39"))).isEqualTo("5.3.39 이상으로 올립니다.")
        assertThat(FixAdvice.text("6.0.5", listOf("5.3.39", "6.1.13"))).isEqualTo("6.1.13 이상으로 올립니다.")
    }

    @Test
    @DisplayName("버전의 숫자는 글자가 아니라 수로 비교한다")
    fun numeric_comparison() {
        assertThat(FixAdvice.text("4.1.9.Final", listOf("4.1.10.Final", "4.1.118.Final"))).isEqualTo("4.1.10.Final 이상으로 올립니다.")
    }

    @Test
    @DisplayName("고쳐진 버전이 없으면 원문을 확인하라고 안내한다")
    fun no_fixed_version() {
        assertThat(FixAdvice.text("1.0", emptyList())).isEqualTo("아직 고쳐진 버전이 없습니다. 원문에서 임시 대응 방법을 확인해 주세요.")
    }

    @Test
    @DisplayName("지금 버전보다 높은 고쳐진 버전을 고르지 못하면 고쳐진 버전을 모두 보여준다")
    fun cannot_pick_lists_all() {
        assertThat(FixAdvice.text("9.0", listOf("1.0", "2.0"))).isEqualTo("고쳐진 버전(1.0, 2.0) 중 하나로 바꿉니다.")
        assertThat(FixAdvice.text("abc", listOf("1.0"))).isEqualTo("고쳐진 버전(1.0) 중 하나로 바꿉니다.")
    }
}
