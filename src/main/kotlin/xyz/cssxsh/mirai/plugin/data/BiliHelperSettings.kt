package xyz.cssxsh.mirai.plugin.data

import net.mamoe.mirai.console.data.*

object BiliHelperSettings : ReadOnlyPluginConfig("BiliHelperSettings") {
    @ValueDescription("图片缓存位置")
    val cache: String by value("ImageCache")

    @ValueDescription("API 访问间隔时间，单位秒")
    val api: Long by value(10L)

    @ValueDescription("视频 订阅 访问间隔时间，单位分钟")
    val video: Long by value(10L)

    @ValueDescription("动态 订阅 访问间隔时间，单位分钟")
    val dynamic: Long by value(10L)

    @ValueDescription("直播 订阅 访问间隔时间，单位分钟")
    val live: Long by value(30L)

    @ValueDescription("番剧 订阅 访问间隔时间，单位分钟")
    val season: Long by value(30L)

    @ValueDescription("回复拉取时间间隔 单位分钟")
    val reply: Long by value(1L)
}