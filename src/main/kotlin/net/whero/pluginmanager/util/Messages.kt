package net.whero.pluginmanager.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender

object Messages {

    private val mini = MiniMessage.miniMessage()

    val PREFIX: Component = mini.deserialize("<gradient:#5e4fa2:#f79459>WPM</gradient> <dark_gray>»</dark_gray> ")

    fun info(text: String): Component =
        PREFIX.append(Component.text(text, NamedTextColor.GRAY))

    fun success(text: String): Component =
        PREFIX.append(Component.text(text, NamedTextColor.GREEN))

    fun error(text: String): Component =
        PREFIX.append(Component.text(text, NamedTextColor.RED))

    fun warning(text: String): Component =
        PREFIX.append(Component.text(text, NamedTextColor.YELLOW))

    fun raw(miniMessage: String): Component =
        PREFIX.append(mini.deserialize(miniMessage))

    fun CommandSender.sendInfo(text: String) = sendMessage(info(text))
    fun CommandSender.sendSuccess(text: String) = sendMessage(success(text))
    fun CommandSender.sendError(text: String) = sendMessage(error(text))
    fun CommandSender.sendWarning(text: String) = sendMessage(warning(text))
    fun CommandSender.sendRaw(miniMessage: String) = sendMessage(raw(miniMessage))
}
