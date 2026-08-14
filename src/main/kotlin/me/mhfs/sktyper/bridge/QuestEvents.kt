package me.mhfs.sktyper.bridge

import com.typewritermc.core.entries.Entry
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent

/**
 * Plugin-owned copies of the Quest extension's events.
 *
 * The real ones live behind Typewriter's extension classloader and have no Class object at the time
 * Skript wants its event registrations, so [QuestBridge] listens for them reflectively and re-fires
 * these instead.
 */
class TypewriterQuestStatusEvent(
    player: Player,
    val quest: Entry?,
    val questId: String,
    val from: String,
    val to: String,
) : PlayerEvent(player, true) {
    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        @JvmStatic
        val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}

class TypewriterTrackedQuestEvent(
    player: Player,
    val from: Entry?,
    val to: Entry?,
) : PlayerEvent(player, true) {
    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        @JvmStatic
        val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
