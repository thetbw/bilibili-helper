package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.contact.Contact
import xyz.cssxsh.mirai.plugin.BiliHelperPlugin
import xyz.cssxsh.mirai.plugin.BiliReplyLoader
import xyz.cssxsh.mirai.plugin.BiliTasker

object BiliReplayCommand : CompositeCommand(
    owner = BiliHelperPlugin,
    "bili-reply", "B回复",
    description = "开启b站回复通知"
), BiliTasker by BiliReplyLoader {

    @SubCommand("start", "开启")
    suspend fun CommandSender.start(contact: Contact = subject()) = sendMessage(
        addContact(0, contact).let { "已开启回复通知" }
    )

    @SubCommand("stop", "停止")
    suspend fun CommandSender.stop(contact: Contact = subject()) = sendMessage(
        removeContact(0, contact).let { "已关闭回复通知" }
    )

}