package org.lynxz.utils

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * 线程安全的 key-value 缓存工具类
 *
 * 特性：
 * - 支持多线程环境，使用 ConcurrentHashMap 保证并发安全
 * - key 和 value 类型由外部指定，类型安全
 * - value 允许为 null
 * - 每个实例独立维护自己的缓存，不同调用方可以创建独立的缓存实例
 * - 支持过期时间设置
 * - 支持最大容量限制和 LRU 淘汰策略
 *
 * 使用示例：
 * ```
 * // 创建缓存实例,并指定key和value类型
 * val userCache = KVCache<String, User>()
 * val productCache = KVCache<Long, String>()
 * val anyCache = KVCache<String, Any?>() // 支持存储任意类型的value
 *
 * // 存储数据
 * userCache.put("user1", User(id = 1, name = "张三"))
 * userCache.put("user2", User(id = 2, name = "李四"), 10000) // 10秒后过期
 * productCache.put(1001, "产品名称")
 *
 * // 判断缓存是否过期
 * val isExpired = userCache.isExpired("user2")
 *
 * // 获取数据
 * val user = userCache.get("user1")
 * val userName = user?.name
 * val productName = productCache.get(1001)
 * val defaultValue = productCache.getOrDefault(9999, "默认产品")
 *
 * // 遍历所有key并执行action操作
 * userCache.forEach { key, value ->
 *     println("User: $key -> $value")
 * }
 *
 * // 遍历所有key并执行action操作，允许修改缓存
 * userCache.forEachMutable { key, value ->
 *     if (value?.id == 1) {
 *         userCache.remove(key)
 *     }
 * }
 *
 * // 删除数据
 * userCache.remove("user1")
 *
 * // 清空所有缓存
 * userCache.clear()
 * ```
 */
class KVCache<K : Any, V>(
    /** 最大容量，默认无限制 */
    private val maxSize: Int = Int.MAX_VALUE,
    /** 是否启用 LRU 淘汰策略，默认不启用 */
    private val enableLru: Boolean = false
) {

    private val TAG = "KVCache"

    /**
     * 缓存条目，封装了值和过期时间
     */
    private data class CacheEntry<V>(
        val value: V?, val expireTime: Long = Long.MAX_VALUE,
        /** 最后访问时间，用于 LRU 淘汰策略 */
        var lastAccessTime: Long = System.currentTimeMillis()
    )

    /**
     * 使用 ConcurrentHashMap 作为底层存储
     */
    private val cacheMap = ConcurrentHashMap<K, CacheEntry<V>>()

    /**
     * 用于 LRU 淘汰的锁
     */
    private val lruLock = ReentrantLock()

    /**
     * 存储键值对
     *
     * @param key 键
     * @param value 值，允许为 null
     */
    fun put(key: K, value: V?) {
        putInternal(key, value, Long.MAX_VALUE)
    }

    /**
     * 存储键值对，并设置过期时间
     *
     * @param key 键
     * @param value 值
     * @param expireMs 过期时间（毫秒），从当前时间开始计算
     */
    fun put(key: K, value: V?, expireMs: Long) {
        val expireTime = if (expireMs > 0 && expireMs < Long.MAX_VALUE) System.currentTimeMillis() + expireMs else Long.MAX_VALUE
        putInternal(key, value, expireTime)
    }

    /**
     * 内部存储方法
     */
    private fun putInternal(key: K, value: V?, expireTime: Long) {
        // 检查容量限制
        if (maxSize < Int.MAX_VALUE && cacheMap.size >= maxSize && !cacheMap.containsKey(key)) {
            evictEntry()
        }

        // 如果键已存在且启用了LRU，更新最后访问时间
        cacheMap[key]?.let { existingEntry ->
            if (enableLru) {
                lruLock.lock()
                try {
                    existingEntry.lastAccessTime = System.currentTimeMillis()
                } finally {
                    lruLock.unlock()
                }
            }
        }

        cacheMap[key] = CacheEntry(value, expireTime)
    }

    /**
     * 当缓存达到最大容量时，淘汰一个条目
     */
    private fun evictEntry() {
        if (!enableLru) {
            // 不启用 LRU 时，随机淘汰一个条目
            cacheMap.keys.firstOrNull()?.let { cacheMap.remove(it) }
        } else {
            // 启用 LRU 时，淘汰最后访问时间最早的条目
            lruLock.lock()
            try {
                cacheMap.minByOrNull { it.value.lastAccessTime }?.key?.let { cacheMap.remove(it) }
            } finally {
                lruLock.unlock()
            }
        }
    }

    /**
     * 获取指定 key 对应的值
     * 如果 key 已过期，会自动删除并返回 null
     *
     * @param key 键
     * @return 值，如果 key 不存在或已过期则返回 null
     */
    fun get(key: K): V? {
        val entry = cacheMap[key] ?: return null

        // 检查是否过期
        if (entry.expireTime < System.currentTimeMillis()) {
            cacheMap.remove(key)
            return null
        }

        // 更新最后访问时间（用于 LRU）
        if (enableLru) {
            lruLock.lock()
            try {
                entry.lastAccessTime = System.currentTimeMillis()
            } finally {
                lruLock.unlock()
            }
        }

        return entry.value
    }

    /**
     * 获取指定 key 对应的值，如果不存在则返回默认值
     * 如果 key 已过期，会自动删除并返回默认值
     *
     * @param key 键
     * @param defaultValue 默认值
     * @return 值或默认值
     */
    fun getOrDefault(key: K, defaultValue: V): V {
        return get(key) ?: defaultValue
    }

    /**
     * 只有当 key 不存在时才存储键值对
     *
     * @param key 键
     * @param value 值
     * @return 如果 key 不存在则返回 null，否则返回已存在的值
     */
    fun putIfAbsent(key: K, value: V?): V? {
        return putIfAbsent(key, value, Int.MAX_VALUE.toLong())
    }

    /**
     * 只有当 key 不存在时才存储键值对，并设置过期时间
     *
     * @param key 键
     * @param value 值
     * @param expireMs 过期时间（毫秒）
     * @return 如果 key 不存在则返回 null，否则返回已存在的值
     */
    fun putIfAbsent(key: K, value: V?, expireMs: Long): V? {
        val expireTime = if (expireMs > 0 && expireMs < Long.MAX_VALUE) System.currentTimeMillis() + expireMs else Long.MAX_VALUE
        val newEntry = CacheEntry(value, expireTime)

        // 首先检查key是否存在且未过期
        val existingEntry = cacheMap[key]
        if (existingEntry != null && existingEntry.expireTime > System.currentTimeMillis()) {
            return existingEntry.value
        }

        // key不存在或已过期，尝试存储新值
        val result = cacheMap.putIfAbsent(key, newEntry)

        // 如果putIfAbsent返回null，表示成功插入
        if (result == null) {
            return null
        }

        // 如果putIfAbsent返回非null，表示key已存在
        // 检查返回的条目是否过期
        if (result.expireTime > System.currentTimeMillis()) {
            return result.value
        }

        // 如果返回的条目已过期，尝试用replace替换它
        if (cacheMap.replace(key, result, newEntry)) {
            return null
        }

        // 替换失败，再次获取当前条目
        return cacheMap[key]?.value
    }

    /**
     * 替换指定 key 的值
     *
     * @param key 键
     * @param value 新值
     * @return 旧值，如果 key 不存在则返回 null
     */
    fun replace(key: K, value: V?): V? {
        return replace(key, value, Long.MAX_VALUE)
    }

    /**
     * 替换指定 key 的值，并设置过期时间
     *
     * @param key 键
     * @param value 新值
     * @param expireMs 过期时间（毫秒）
     * @return 旧值，如果 key 不存在则返回 null
     */
    fun replace(key: K, value: V?, expireMs: Long): V? {
        val expireTime = if (expireMs > 0 && expireMs < Long.MAX_VALUE) System.currentTimeMillis() + expireMs else Long.MAX_VALUE

        // 首先检查key是否存在且未过期
        val existingEntry = cacheMap[key]
        if (existingEntry == null || existingEntry.expireTime < System.currentTimeMillis()) {
            return null
        }

        // 创建新条目
        val newEntry = CacheEntry(value, expireTime)

        // 替换值并返回旧值
        val result = cacheMap.replace(key, newEntry)
        return result?.value
    }

    /**
     * 判断指定 key 是否存在（未过期）
     *
     * @param key 键
     * @return true 如果存在且未过期，false 如果不存在或已过期
     */
    fun containsKey(key: K): Boolean {
        val entry = cacheMap[key] ?: return false

        // 检查是否过期
        if (entry.expireTime < System.currentTimeMillis()) {
            cacheMap.remove(key)
            return false
        }

        return true
    }

    /**
     * 删除指定 key 对应的值
     *
     * @param key 键
     * @return 被删除的值，如果 key 不存在则返回 null
     */
    fun remove(key: K): V? {
        val entry = cacheMap.remove(key) ?: return null

        // 检查是否过期
        if (entry.expireTime < System.currentTimeMillis()) {
            return null
        }

        return entry.value
    }

    /**
     * 清空所有缓存
     */
    fun clear() {
        cacheMap.clear()
    }

    /**
     * 获取缓存中所有 key 的数量（包括已过期的）
     *
     * @return 缓存大小
     */
    fun size(): Int {
        return cacheMap.size
    }

    /**
     * 清理所有已过期的缓存项
     *
     * @return 被清理的 key 数量
     */
    fun cleanExpired(): Int {
        val currentTime = System.currentTimeMillis()
        var count = 0

        // 使用迭代器遍历并清理过期项
        val iterator = cacheMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.expireTime < currentTime) {
                iterator.remove()
                count++
            }
        }

        return count
    }

    /**
     * 判断缓存是否为空（包括已过期的项）
     *
     * @return true 如果为空，false 如果不为空
     */
    fun isEmpty(): Boolean {
        return cacheMap.isEmpty()
    }

    /**
     * 获取所有 key 的集合（包括已过期的）
     *
     * @return 所有 key 的集合
     */
    fun keys(): Set<K> {
        return cacheMap.keys.toSet()
    }

    /**
     * 遍历所有缓存项（包括已过期的）
     *
     * @param action 对每个键值对执行的操作，参数为 (key, value)
     */
    fun forEach(action: (K, V?) -> Unit) {
        cacheMap.forEach { (key, entry) ->
            action(key, entry.value)
        }
    }

    /**
     * 遍历所有缓存项（包括已过期的），允许在 action 回调中修改缓存
     *
     * @param action 对每个键值对执行的操作，参数为 (key, value)
     */
    fun forEachMutable(action: (K, V?) -> Unit) {
        // 创建快照以避免 ConcurrentModificationException
        val snapshot = cacheMap.toMap()
        snapshot.forEach { (key, entry) ->
            action(key, entry.value)
        }
    }

    /**
     * 判断指定 key 是否已过期
     *
     * @param key 键
     * @return true 如果已过期或未设置过期时间，false 如果未过期
     */
    fun isExpired(key: K): Boolean {
        val entry = cacheMap[key] ?: return true
        return entry.expireTime < System.currentTimeMillis()
    }
}