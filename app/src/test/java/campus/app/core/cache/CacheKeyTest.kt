package campus.app.core.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 缓存键必须能区分环境与账号，否则换账号会看到上一个人的通知。
 */
class CacheKeyTest {

    @Test
    fun `不同账号的缓存键必须不同`() {
        val a = CacheKey.list("LOCAL", "user-a", "category=COMPETITION")
        val b = CacheKey.list("LOCAL", "user-b", "category=COMPETITION")
        assertNotEquals(a, b)
    }

    @Test
    fun `不同环境的缓存键必须不同`() {
        val local = CacheKey.list("LOCAL", "user-a", "category=ALL")
        val real = CacheKey.list("REAL", "user-a", "category=ALL")
        assertNotEquals(local, real)
    }

    @Test
    fun `同账号同查询的键保持一致`() {
        val first = CacheKey.list("LOCAL", "user-a", "category=ALL")
        val second = CacheKey.list("LOCAL", "user-a", "category=ALL")
        assertEquals(first, second)
    }

    @Test
    fun `详情键包含内容版本`() {
        val v1 = CacheKey.detail("LOCAL", "user-a", "notice-1", "v1")
        val v2 = CacheKey.detail("LOCAL", "user-a", "notice-1", "v2")
        assertNotEquals("正文版本变化后旧缓存不能命中", v1, v2)
    }
}
