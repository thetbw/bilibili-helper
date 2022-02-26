package xyz.cssxsh.bilibili.api

import cn.hutool.http.HttpRequest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeFromJsonElement
import xyz.cssxsh.bilibili.BiliClient
import xyz.cssxsh.bilibili.data.BiliReplyInfo
import xyz.cssxsh.bilibili.data.TempData
import xyz.cssxsh.mirai.plugin.logger
import xyz.cssxsh.mirai.plugin.toHttpCookie

suspend fun BiliClient.getReplyInfo(
    id: Long? = null,
    replyTime: Long? = null,
    url: String = REPLY_INFO
): BiliReplyInfo {
    val tempData = BiliClient.Json.decodeFromString<TempData>(
        HttpRequest.get(url)
            .cookie(storage.container.map { it.toHttpCookie() })
            .form("id", id)
            .form("reply_time", replyTime)
            .execute().body()
    );
    if (tempData.code != 0) {
        logger.info("请求异常 code： ${tempData.code} message: ${tempData.message}")
    }
    val temp = tempData.data ?: tempData.result
    return BiliClient.Json.decodeFromJsonElement(requireNotNull(temp) { tempData.message })
}

