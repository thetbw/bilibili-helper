package xyz.cssxsh.bilibili.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class BiliReplyInfo(
    @SerialName("cursor")
    val cursor: BiliReplyCursor,
    @SerialName("items")
    val items: List<BiliReplyItem>,
    @SerialName("last_view_at")
    val lastViewAt: Long
)

@Serializable
data class BiliReplyCursor(
    @SerialName("id")
    val id: Long,
    @SerialName("is_end")
    val isEnd: Boolean,
    @SerialName("time")
    val time: Long,
)


@Serializable
data class BiliReplyItem(
    @SerialName("counts")
    val counts: Long,
    @SerialName("id")
    val id: Long,
    @SerialName("is_multi")
    val isMulti: Long,
    @SerialName("item")
    val item: BiliReplyItemDetail,
    @SerialName("reply_time")
    val replyTime: Long,
    @SerialName("user")
    val user: BiliReplyUser

)


@Serializable
data class BiliReplyItemDetail(
//    @SerialName("at_details")
//    val atDetails: List?, //TODO 这里是数组，暂时还不知道是什么
    @SerialName("business")
    val business: String,
    @SerialName("business_id")
    val businessId: Long,
    @SerialName("danmu")
    val danmu: String?,
    @SerialName("desc")
    val desc: String,
    @SerialName("detail_title")
    val detailTitle: String,
    @SerialName("hide_like_button")
    val hideLikeButton: Boolean,
    @SerialName("hide_reply_button")
    val hideReplyButton: Boolean,
    @SerialName("image")
    val image: String,
    @SerialName("like_state")
    val likeState: String,
    @SerialName("native_uri")
    val nativeUri: String,
    @SerialName("root_id")
    val rootId: Long,
    @SerialName("root_reply_content")
    val rootReplyContent: String,
    @SerialName("source_content")
    val sourceContent: String,
    @SerialName("source_id")
    val sourceId: Long,
    @SerialName("subject_id")
    val subjectId: Long,
    @SerialName("target_id")
    val targetId: Long,
    @SerialName("target_reply_content")
    val targetReplyContent: String,
    @SerialName("title")
    val title: String,
//    @SerialName("topic_details")
//    val topicDetails: String,//TODO 这个是数组，暂时还不知道是啥
    @SerialName("type")
    val type: String,
    @SerialName("uri")
    val uri: String,
)

@Serializable
data class BiliReplyUser(
    @SerialName("avatar")
    val avatar: String,
    @SerialName("fans")
    val fans: Int,
    @SerialName("follow")
    val follow: Boolean,
    @SerialName("mid")
    val mid: Long,
    @SerialName("mid_link")
    val midLink: String?,
    @SerialName("nickname")
    val nickname: String

)