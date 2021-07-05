package org.lynxz.utils.log

/**
 * log日志持久化接口
 * 与 [LoggerUtil] 配套使用,根据 TAG 和 LogLevel 来过滤需要持久化到文件的数据
 */
interface ILogPersistence {

    /**
     * 需要持久化的tag日志,可添加多条(无视日志等级限制)
     */
    fun addTag(tag: String): ILogPersistence

    /**
     * 需要持久化的日志等级
     * 大于等于该等级的日志才会进行持久化(无视tag)
     */
    fun setLevel(@LogLevel.LogLevel1 logLevel: Int): ILogPersistence

    /**
     * 写入缓存到文件
     * */
    fun flush()

    /**
     * 关闭持久化工具
     */
    fun close()

    /**
     * 过滤处理 [LoggerUtil] 传入的所有日志内容,按需持久化
     */
    fun filterPersistenceLog(@LogLevel.LogLevel1 logLevel: Int, tag: String, msg: String?)

}