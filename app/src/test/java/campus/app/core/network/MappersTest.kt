package campus.app.core.network

import campus.app.core.model.Category
import campus.app.core.model.DatePrecision
import campus.app.core.model.MatchState
import campus.app.core.model.UrgencyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSON 映射：未知值必须落到 UNKNOWN/null，不能用 0 或空串冒充。
 */
class MappersTest {

    private fun feed(json: String) = Mappers.feed(JsonKit.parseObject(json)!!)

    @Test
    fun `未知类别落到 UNKNOWN`() {
        val page = feed(
            """{"items":[{"notice_id":"n1","title":"标题","category":"something_new"}]}""",
        )
        assertEquals(Category.UNKNOWN, page.items[0].category)
        assertEquals(Category.UNKNOWN.label, "待确认")
    }

    @Test
    fun `缺失截止时间时是未知而不是零`() {
        val page = feed("""{"items":[{"notice_id":"n1","title":"标题"}]}""")
        val deadline = page.items[0].deadline
        assertEquals(UrgencyState.UNKNOWN, deadline.urgency)
        assertEquals(DatePrecision.UNKNOWN, deadline.precision)
        assertNull(deadline.daysLeft)
    }

    @Test
    fun `只有日期时精度为 DATE_ONLY`() {
        val page = feed(
            """
            {"items":[{"notice_id":"n1","title":"标题",
              "deadline":{"iso":"2026-12-15","precision":"date","urgency":"date_only","days_left":38}}]}
            """.trimIndent(),
        )
        val deadline = page.items[0].deadline
        assertEquals(DatePrecision.DATE_ONLY, deadline.precision)
        assertEquals(38, deadline.daysLeft)
    }

    @Test
    fun `没有综合分时显示破折号而不是零`() {
        val page = feed("""{"items":[{"notice_id":"n1","title":"标题"}]}""")
        assertEquals("—", page.items[0].ranking.scoreText)
    }

    @Test
    fun `title_only 会被识别为正文受限`() {
        val page = feed("""{"items":[{"notice_id":"n1","title":"标题","status":"title_only"}]}""")
        assertTrue(page.items[0].titleOnly)
    }

    @Test
    fun `匹配状态与排序理由被保留`() {
        val page = feed(
            """
            {"items":[{"notice_id":"n1","title":"标题","category":"competition",
              "match":{"state":"possible","reasons":["涉及全体本科生"]},
              "ranking":{"composite_score":52.0,"reasons":["政策类","截止待核对"]}}]}
            """.trimIndent(),
        )
        val item = page.items[0]
        assertEquals(MatchState.POSSIBLE, item.match.state)
        assertEquals(52.0, item.ranking.compositeScore!!, 0.001)
        assertEquals(listOf("政策类", "截止待核对"), item.ranking.reasons)
    }

    @Test
    fun `四问缺失时标记为待确认`() {
        val explanation = Mappers.explanation(
            JsonKit.parseObject("""{"summary":"摘要","four_questions":{}}""")!!,
        )
        assertTrue(explanation.four.shouldJoin.pending)
        assertEquals("摘要", explanation.summary)
    }

    @Test
    fun `错误信封可解析`() {
        val (code, message, requestId) = Mappers.errorEnvelope(
            JsonKit.parseObject(
                """{"error":{"code":"storage_unavailable","message":"稍后重试","request_id":"r-1"}}""",
            )!!,
        )
        assertEquals("storage_unavailable", code)
        assertEquals("稍后重试", message)
        assertEquals("r-1", requestId)
    }
}
