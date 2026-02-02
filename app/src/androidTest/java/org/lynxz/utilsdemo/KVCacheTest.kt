package org.lynxz.utilsdemo

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.lynxz.utils.KVCache
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * KVCache 测试类
 */
@RunWith(AndroidJUnit4::class)
class KVCacheTest {

    @Test
    fun testBasicPutGet() {
        // 创建缓存实例
        val cache = KVCache<String, String>()

        // 存储数据
        cache.put("key1", "value1")
        cache.put("key2", "value2")

        // 获取数据
        assertEquals("value1", cache.get("key1"))
        assertEquals("value2", cache.get("key2"))
        assertNull(cache.get("non_existent_key"))

        // 测试 null 值
        cache.put("null_key", null)
        assertNull(cache.get("null_key"))
    }

    @Test
    fun testExpireTime() {
        // 创建缓存实例
        val cache = KVCache<String, String>()

        // 存储带过期时间的数据（100ms后过期）
        cache.put("temp_key", "temp_value", 100)
        assertEquals("temp_value", cache.get("temp_key"))
        assertTrue(cache.containsKey("temp_key"))

        // 等待过期
        Thread.sleep(150)

        // 验证数据已过期
        assertNull(cache.get("temp_key"))
        assertFalse(cache.containsKey("temp_key"))
        assertTrue(cache.isExpired("temp_key"))
    }

    @Test
    fun testPutIfAbsent() {
        // 创建缓存实例
        val cache = KVCache<String, String>()

        // 当key不存在时
        assertNull(cache.putIfAbsent("new_key", "new_value"))
        assertEquals("new_value", cache.get("new_key"))

        // 当key已存在时
        assertEquals("new_value", cache.putIfAbsent("new_key", "override_value"))
        assertEquals("new_value", cache.get("new_key")) // 验证值未被覆盖
    }

    @Test
    fun testReplace() {
        // 创建缓存实例
        val cache = KVCache<String, String>()

        // 替换不存在的key
        assertNull(cache.replace("non_existent", "value"))

        // 存储数据
        cache.put("key", "old_value")

        // 替换已存在的key
        assertEquals("old_value", cache.replace("key", "new_value"))
        assertEquals("new_value", cache.get("key"))

        // 替换已过期的key
        cache.put("expire_key", "value", 50)
        Thread.sleep(100)
        assertNull(cache.replace("expire_key", "new_value"))
        assertFalse(cache.containsKey("expire_key"))
    }

    @Test
    fun testContainsKeyAndIsExpired() {
        // 创建缓存实例
        val cache = KVCache<String, String>()

        // 检查不存在的key
        assertFalse(cache.containsKey("non_existent"))
        assertTrue(cache.isExpired("non_existent"))

        // 存储数据
        cache.put("key", "value")
        cache.put("expire_key", "expire_value", 50)

        // 检查存在的key
        assertTrue(cache.containsKey("key"))
        assertFalse(cache.isExpired("key"))

        // 检查未过期的key
        assertTrue(cache.containsKey("expire_key"))
        assertFalse(cache.isExpired("expire_key"))

        // 等待过期
        Thread.sleep(100)

        // 检查已过期的key
        assertFalse(cache.containsKey("expire_key"))
        assertTrue(cache.isExpired("expire_key"))
    }

    @Test
    fun testRemoveAndClear() {
        // 创建缓存实例
        val cache = KVCache<String, String>()

        // 存储数据
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        cache.put("key3", "value3")

        // 删除单个key
        assertEquals("value1", cache.remove("key1"))
        assertNull(cache.get("key1"))
        assertEquals(2, cache.size())

        // 删除不存在的key
        assertNull(cache.remove("non_existent"))

        // 清空所有数据
        cache.clear()
        assertEquals(0, cache.size())
        assertTrue(cache.isEmpty())
        assertNull(cache.get("key2"))
        assertNull(cache.get("key3"))
    }

    @Test
    fun testForEachAndForEachMutable() {
        // 创建缓存实例
        val cache = KVCache<String, String>()

        // 存储数据
        val data = mapOf(
            "key1" to "value1",
            "key2" to "value2",
            "key3" to "value3"
        )
        data.forEach { (key, value) ->
            cache.put(key, value)
        }

        // 测试 forEach
        val result = mutableListOf<Pair<String, String?>>()
        cache.forEach { key, value ->
            result.add(key to value)
        }
        assertEquals(3, result.size)
        assertTrue(result.containsAll(data.entries.map { it.key to it.value }))

        // 测试 forEachMutable
        val mutableResult = mutableListOf<Pair<String, String?>>()
        cache.forEachMutable { key, value ->
            mutableResult.add(key to value)
            // 在遍历时修改缓存
            if (key == "key2") {
                cache.remove(key)
            }
        }
        assertEquals(3, mutableResult.size)
        assertNull(cache.get("key2")) // 验证key2已被删除
    }

    @Test
    fun testLRU() {
        // 创建容量为3的LRU缓存
        val cache = KVCache<String, String>(maxSize = 3, enableLru = true)

        // 存储3个数据
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        cache.put("key3", "value3")
        Thread.sleep(10)

        // 访问key1，使其成为最新访问的
        cache.get("key1")

        // 存储第4个数据，应该淘汰最早访问的key2
        cache.put("key4", "value4")

        // 验证结果
        assertNotNull(cache.get("key1"))
        assertNull(cache.get("key2")) // key2应该被淘汰
        assertNotNull(cache.get("key3"))
        assertNotNull(cache.get("key4"))
    }

    @Test
    fun testCapacityLimit() {
        // 创建容量为2的缓存
        val cache = KVCache<String, String>(maxSize = 2)

        // 存储2个数据
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        assertEquals(2, cache.size())

        // 存储第3个数据，应该淘汰一个（非LRU模式下随机淘汰）
        cache.put("key3", "value3")
        assertEquals(2, cache.size())

        // 至少有两个key存在
        val existsKeys = listOfNotNull(cache.get("key1"), cache.get("key2"), cache.get("key3"))
        assertEquals(2, existsKeys.size)
    }

    @Test
    fun testThreadSafety() {
        // 创建缓存实例
        val cache = KVCache<String, Int>()
        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val executor: ExecutorService = Executors.newFixedThreadPool(threadCount)

        // 并发执行put和get操作
        for (i in 0 until threadCount) {
            executor.execute {
                try {
                    val key = "key_${i % 5}" // 5个不同的key
                    cache.put(key, i)
                    Thread.sleep(10)
                    val value = cache.get(key)
                    assertNotNull(value)
                } finally {
                    latch.countDown()
                }
            }
        }

        // 等待所有线程完成
        latch.await(1, TimeUnit.SECONDS)

        // 验证数据完整性
        assertEquals(5, cache.keys().size)
        cache.forEach { key, value ->
            assertNotNull(value)
        }
    }

    @Test
    fun testCleanExpired() {
        // 创建缓存实例
        val cache = KVCache<String, String>()

        // 存储多个数据，部分带有过期时间
        cache.put("permanent1", "value1")
        cache.put("permanent2", "value2")
        cache.put("temp1", "temp_value1", 50)
        cache.put("temp2", "temp_value2", 100)

        // 等待过期
        Thread.sleep(150)

        // 清理过期数据
        val cleanedCount = cache.cleanExpired()
        assertEquals(2, cleanedCount)

        // 验证结果
        assertEquals(2, cache.size())
        assertTrue(cache.containsKey("permanent1"))
        assertTrue(cache.containsKey("permanent2"))
        assertFalse(cache.containsKey("temp1"))
        assertFalse(cache.containsKey("temp2"))
    }

    @Test
    fun testGetOrDefault() {
        // 创建缓存实例
        val cache = KVCache<String, String>()

        // 设置默认值
        val defaultValue = "default"
        assertEquals(defaultValue, cache.getOrDefault("non_existent", defaultValue))

        // 存储数据
        cache.put("key", "value")
        assertEquals("value", cache.getOrDefault("key", defaultValue))
    }

    @Test
    fun testSizeAndIsEmpty() {
        // 创建缓存实例
        val cache = KVCache<String, String>()

        // 初始状态
        assertTrue(cache.isEmpty())
        assertEquals(0, cache.size())

        // 存储数据
        cache.put("key1", "value1")
        cache.put("key2", "value2")

        // 验证大小
        assertFalse(cache.isEmpty())
        assertEquals(2, cache.size())

        // 移除数据
        cache.remove("key1")
        assertEquals(1, cache.size())
        assertFalse(cache.isEmpty())

        // 清空数据
        cache.clear()
        assertTrue(cache.isEmpty())
        assertEquals(0, cache.size())
    }

    @Test
    fun testKeys() {
        // 创建缓存实例
        val cache = KVCache<String, String>()

        // 存储数据
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        cache.put("key3", "value3")

        // 获取所有key
        val keys = cache.keys()
        assertEquals(3, keys.size)
        assertTrue(keys.contains("key1"))
        assertTrue(keys.contains("key2"))
        assertTrue(keys.contains("key3"))
    }
}