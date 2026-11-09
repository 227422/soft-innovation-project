package campus.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 契约 M13「最后点击生效」：A→B→C 之后，只有 C 的响应能更新界面。
 */
class RequestGateTest {

    @Test
    fun `只有最新一次请求有效`() {
        val gate = RequestGate()
        val a = gate.next()
        val b = gate.next()
        val c = gate.next()

        assertFalse(gate.isCurrent(a))
        assertFalse(gate.isCurrent(b))
        assertTrue(gate.isCurrent(c))
    }

    @Test
    fun `新请求会使旧令牌立即失效`() {
        val gate = RequestGate()
        val first = gate.next()
        assertTrue(gate.isCurrent(first))

        gate.next()
        assertFalse("第二次点击后，第一次的回调不能再改界面", gate.isCurrent(first))
    }

    @Test
    fun `令牌单调递增`() {
        val gate = RequestGate()
        assertEquals(1, gate.next())
        assertEquals(2, gate.next())
        assertEquals(3, gate.next())
    }
}
