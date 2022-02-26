package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import net.mamoe.mirai.console.permission.PermissionService.Companion.testPermission
import net.mamoe.mirai.console.permission.PermitteeId.Companion.permitteeId
import net.mamoe.mirai.console.util.CoroutineScopeUtils.childScope
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.bilibili.api.*
import xyz.cssxsh.bilibili.*
import xyz.cssxsh.bilibili.data.*
import xyz.cssxsh.mirai.plugin.data.*
import java.time.*
import kotlin.math.*

interface BiliTasker {

    suspend fun addContact(id: Long, subject: Contact): BiliTask?

    suspend fun removeContact(id: Long, subject: Contact): BiliTask?

    suspend fun list(subject: Contact): String

    suspend fun start()

    suspend fun stop()

    companion object {
        private val all by lazy {
            (Loader::class.sealedSubclasses + Waiter::class.sealedSubclasses).mapNotNull { it.objectInstance }
        }

        fun startAll() = runBlocking {
            all.forEach { it.start() }
        }

        fun stopAll() = runBlocking {
            all.forEach { it.stop() }
        }
    }
}

abstract class AbstractTasker<T> : BiliTasker, CoroutineScope {

    protected val mutex = Mutex()

    protected abstract val fast: Long

    protected abstract val slow: Long

    protected abstract val tasks: MutableMap<Long, BiliTask>

    protected abstract suspend fun T.build(contact: Contact): Message

    protected open suspend fun contacts(id: Long) = mutex.withLock { tasks[id]?.contacts.orEmpty() }

    protected open suspend fun Set<Long>.send(item: T) = map { delegate ->
        runCatching {
            requireNotNull(findContact(delegate)) { "找不到联系人" }.let { contact ->
                contact.sendMessage(item.build(contact))
            }
        }.onFailure {
            logger.warning({ "对[${delegate}]构建消息失败" }, it)
        }
    }

    private val taskJobs = mutableMapOf<Long, Job>()

    protected abstract fun addListener(id: Long): Job

    protected open fun removeListener(id: Long) = synchronized(taskJobs) { taskJobs.remove(id)?.cancel() }

    abstract suspend fun initTask(id: Long): BiliTask

    override suspend fun addContact(id: Long, subject: Contact) = mutex.withLock {
        val old = tasks[id] ?: initTask(id)
        tasks.compute(id) { _, _ ->
            old.copy(contacts = old.contacts + subject.delegate)
        }
        taskJobs.compute(id) { _, job ->
            job?.takeIf { it.isActive } ?: addListener(id)
        }
        tasks[id]
    }

    override suspend fun removeContact(id: Long, subject: Contact) = mutex.withLock {
        tasks.compute(id) { _, info ->
            info?.run {
                copy(contacts = contacts - subject.delegate).apply {
                    if (contacts.isEmpty()) {
                        taskJobs[id]?.cancel()
                    }
                }
            }
        }
        tasks[id]
    }

    override suspend fun list(subject: Contact): String = mutex.withLock {
        buildString {
            appendLine("监听状态:")
            tasks.forEach { (id, info) ->
                if (subject.delegate in info.contacts) {
                    appendLine("@${info.name}#$id -> ${info.last} | ${taskJobs[id]}")
                }
            }
        }
    }

    override suspend fun start(): Unit = mutex.withLock {
        tasks.forEach { (id, _) ->
            taskJobs[id] = addListener(id)
        }
    }

    override suspend fun stop(): Unit = mutex.withLock {
        coroutineContext.cancelChildren()
        taskJobs.clear()
    }
}

sealed class Loader<T> : AbstractTasker<T>() {

    protected abstract suspend fun load(id: Long): List<T>

    protected abstract fun List<T>.last(): OffsetDateTime

    protected abstract fun List<T>.after(last: OffsetDateTime): List<T>

    protected abstract suspend fun List<T>.near(): Boolean

    override fun addListener(id: Long) = launch(SupervisorJob()) {
        delay((fast..slow).random())
        while (isActive && contacts(id).isNotEmpty()) {
            runCatching {
                val list = load(id)
                mutex.withLock {
                    val task = tasks.getValue(id)
                    list.after(task.last).forEach { item ->
                        task.contacts.send(item)
                    }
                    tasks[id] = task.copy(last = list.last())
                }
                if (list.near()) fast else slow
            }.onSuccess { interval ->
                delay(interval)
            }.onFailure {
                delay(slow)
            }
        }
    }
}

sealed class Waiter<T> : AbstractTasker<T>() {

    private val states = mutableMapOf<Long, Boolean>()

    protected abstract suspend fun load(id: Long): T

    protected abstract suspend fun T.success(): Boolean

    protected abstract suspend fun T.near(): Boolean

    override fun addListener(id: Long) = launch(SupervisorJob()) {
        delay((fast..slow).random())
        while (isActive && contacts(id).isNotEmpty()) {
            runCatching {
                val item = load(id)
                mutex.withLock {
                    val task = tasks.getValue(id)
                    states.put(id, item.success()).let { old ->
                        if (old != true && item.success()) {
                            task.contacts.send(item)
                            delay(slow)
                        }
                    }
                }
                if (item.near()) fast else slow
            }.onSuccess { interval ->
                delay(interval)
            }.onFailure {
                delay(fast)
            }
        }
    }
}

private fun List<LocalTime>.near(slow: Long): Boolean {
    val now = LocalTime.now().toSecondOfDay()
    return any { abs(it.toSecondOfDay() - now) * 1000 < slow }
}

const val Minute = 60 * 1000L

object BiliVideoLoader : Loader<Video>(), CoroutineScope by BiliHelperPlugin.childScope("VideoTasker") {
    override val tasks: MutableMap<Long, BiliTask> by BiliTaskData::video

    override val fast get() = Minute

    override val slow get() = BiliHelperSettings.video * Minute

    override suspend fun load(id: Long) = client.getVideos(id).list.videos

    override fun List<Video>.last(): OffsetDateTime = maxOfOrNull { it.datetime } ?: OffsetDateTime.now()

    override fun List<Video>.after(last: OffsetDateTime) = filter { it.datetime > last }

    override suspend fun List<Video>.near() = map { it.datetime.toLocalTime() }.near(slow)

    override suspend fun Video.build(contact: Contact) = toMessage(contact)

    override suspend fun initTask(id: Long): BiliTask = BiliTask(client.getUserInfo(id).name)
}

object BiliDynamicLoader : Loader<DynamicInfo>(), CoroutineScope by BiliHelperPlugin.childScope("DynamicTasker") {
    override val tasks: MutableMap<Long, BiliTask> by BiliTaskData::dynamic

    override val fast get() = Minute

    override val slow = BiliHelperSettings.dynamic * Minute

    override suspend fun load(id: Long) = client.getSpaceHistory(id).dynamics

    override fun List<DynamicInfo>.last(): OffsetDateTime = maxOfOrNull { it.datetime } ?: OffsetDateTime.now()

    override fun List<DynamicInfo>.after(last: OffsetDateTime) = filter { it.datetime > last }

    override suspend fun List<DynamicInfo>.near() = map { it.datetime.toLocalTime() }.near(slow)

    override suspend fun DynamicInfo.build(contact: Contact) = toMessage(contact)

    override suspend fun initTask(id: Long): BiliTask = BiliTask(client.getUserInfo(id).name)
}

object BiliLiveWaiter : Waiter<BiliUserInfo>(), CoroutineScope by BiliHelperPlugin.childScope("LiveWaiter") {
    override val tasks: MutableMap<Long, BiliTask> by BiliTaskData::live

    override val fast get() = Minute

    override val slow get() = BiliHelperSettings.live * Minute

    override suspend fun load(id: Long) = client.getUserInfo(id)

    override suspend fun BiliUserInfo.success(): Boolean = liveRoom.liveStatus

    private fun withAtAll(contact: Contact): Message {
        return if (contact is Group && LiveAtAll.testPermission(contact.permitteeId)) {
            @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
            AtAll + net.mamoe.mirai.internal.message.ForceAsLongMessage
        } else {
            EmptyMessageChain
        }
    }

    override suspend fun BiliUserInfo.build(contact: Contact): Message {
        return "主播: $name#$mid \n".toPlainText() + liveRoom.toMessage(contact) + withAtAll(contact)
    }

    override suspend fun BiliUserInfo.near(): Boolean = false // TODO by live history

    override suspend fun initTask(id: Long): BiliTask = BiliTask(client.getUserInfo(id).name)
}

object BiliSeasonWaiter : Waiter<SeasonSection>(), CoroutineScope by BiliHelperPlugin.childScope("SeasonWaiter") {
    override val tasks: MutableMap<Long, BiliTask> by BiliTaskData::season

    override val fast get() = Minute

    override val slow = BiliHelperSettings.season * Minute

    private val data = mutableMapOf<Long, Video>()

    override suspend fun load(id: Long): SeasonSection = client.getSeasonSection(id).mainSection

    override suspend fun SeasonSection.success(): Boolean {
        val aid = episodes.maxOf { it.aid }
        val video = data.getOrElse(aid) { client.getVideoInfo(aid) }
        return video.datetime > OffsetDateTime.now()
    }

    override suspend fun SeasonSection.build(contact: Contact): Message {
        val aid = episodes.maxOf { it.aid }
        val video = data.getOrElse(aid) { client.getVideoInfo(aid) }
        return video.toMessage(contact)
    }

    override suspend fun SeasonSection.near(): Boolean {
        return episodes.map {
            data.getOrElse(it.aid) { client.getVideoInfo(it.aid) }.datetime.toLocalTime()
        }.near(slow)
    }

    override suspend fun initTask(id: Long): BiliTask = BiliTask(client.getSeasonSection(id).mainSection.title)
}

object BiliReplyLoader : Loader<BiliReplyItem>(), CoroutineScope by BiliHelperPlugin.childScope("ReplayLoader") {


    override val fast: Long = Minute
    override val slow: Long = BiliHelperSettings.reply * Minute
    override val tasks: MutableMap<Long, BiliTask> by BiliTaskData::reply

    override suspend fun BiliReplyItem.build(contact: Contact): Message {
        return ("你的内容 '${this.item.title}' 收到一个回复!\n" +
            "回复人：${this.user.nickname},\n" +
            "回复信息：${this.item.sourceContent}").toPlainText()
    }

    override suspend fun initTask(id: Long): BiliTask = BiliTask("开启回复通知")

    override suspend fun load(id: Long): List<BiliReplyItem> {
        val totalItems = ArrayList<BiliReplyItem>()
        var replyInfo = client.getReplyInfo()
        val currentCursor = replyInfo.lastViewAt
        var hasLast = true
        var pageCount = 0
        val maxPage = 5
        while (hasLast) {
            pageCount++
            replyInfo.items.forEach {
                if (it.replyTime > currentCursor) {
                    totalItems.add(it)
                } else {
                    hasLast = false
                }
            }
            if (hasLast) {
                replyInfo = client.getReplyInfo(replyTime = currentCursor)
            }
            if (pageCount > maxPage) {
                break
            }
        }
        return totalItems
    }

    //返回最后更新的时间
    override fun List<BiliReplyItem>.last(): OffsetDateTime = OffsetDateTime.now()

    //返回这个时间点之后的数据
    override fun List<BiliReplyItem>.after(last: OffsetDateTime): List<BiliReplyItem> = this

    override suspend fun List<BiliReplyItem>.near(): Boolean = false

}