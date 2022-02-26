package xyz.cssxsh.mirai.plugin

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.utils.*
import org.openqa.selenium.remote.*
import xyz.cssxsh.mirai.plugin.command.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.tools.*

object BiliHelperPlugin : KotlinPlugin(
    JvmPluginDescription("xyz.cssxsh.mirai.plugin.bilibili-helper", "1.1.0") {
        name("bilibili-helper")
        author("cssxsh")
    }
) {

    lateinit var driver: RemoteWebDriver
        private set

    val selenium: Boolean by lazy {
        SeleniumToolConfig.setup && SeleniumToolConfig.runCatching {
            setupSelenium(dataFolder, browser)
        }.onFailure {
            if (it is UnsupportedOperationException) {
                logger.warning { "截图模式，请安装 Chrome 或者 Firefox 浏览器 $it" }
            } else {
                logger.warning { "截图模式，初始化浏览器驱动失败 $it" }
            }
        }.isSuccess
    }


    override fun onEnable() {
        BiliTaskData.reload()
        SeleniumToolConfig.reload()
        SeleniumToolConfig.save()
        BiliHelperSettings.reload()
        BiliHelperSettings.save()
        BiliCleanerConfig.reload()
        BiliCleanerConfig.save()

        client.load()
        BiliListener.subscribe()

        BiliInfoCommand.register()
        BiliDynamicCommand.register()
        BiliVideoCommand.register()
        BiliLiveCommand.register()
        BiliSeasonCommand.register()
        BiliSearchCommand.register()
        BiliReplayCommand.register()

        if (selenium) {
            driver = RemoteWebDriver(config = SeleniumToolConfig)
        }

        BiliTasker.startAll()
        BiliCleaner.start()
    }

    override fun onDisable() {
        BiliInfoCommand.unregister()
        BiliDynamicCommand.unregister()
        BiliVideoCommand.unregister()
        BiliLiveCommand.unregister()
        BiliSeasonCommand.unregister()
        BiliSearchCommand.unregister()
        BiliReplayCommand.unregister()

        BiliListener.stop()

        if (selenium) {
            driver.quit()
        }

        BiliTasker.stopAll()
        BiliCleaner.stop()
        client.save()
    }
}