@file:Suppress("DEPRECATION")

package me.mhfs.sktyper

import ch.njol.skript.Skript
import ch.njol.skript.SkriptAddon
import com.typewritermc.engine.paper.events.TypewriterUnloadEvent
import me.mhfs.sktyper.bridge.BlockInteractListener
import me.mhfs.sktyper.bridge.QuestBridge
import me.mhfs.sktyper.elements.Elements
import me.mhfs.sktyper.events.TypewriterEvents
import me.mhfs.sktyper.tw.Tw
import me.mhfs.sktyper.types.TypewriterTypes
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class SkTyper : JavaPlugin(), Listener {

    lateinit var addon: SkriptAddon
        private set

    override fun onEnable() {
        instance = this

        if (!Tw.isAvailable) {
            logger.severe("Typewriter is not installed or failed to enable - SkTyper has nothing to talk to.")
            logger.severe("Grab it from https://docs.typewritermc.com and restart the server.")
            server.pluginManager.disablePlugin(this)
            return
        }

        if (!Skript.isAcceptRegistrations()) {
            logger.severe("Skript is no longer accepting registrations - SkTyper was enabled too late.")
            server.pluginManager.disablePlugin(this)
            return
        }

        addon = Skript.registerAddon(this)

        try {
            // Types first, the syntax patterns below reference them.
            TypewriterTypes.register()
            TypewriterEvents.register()
            Elements.register()
        } catch (e: Exception) {
            logger.severe("Failed to register SkTyper syntax: ${e.message}")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
            return
        }

        server.pluginManager.registerEvents(this, this)
        server.pluginManager.registerEvents(BlockInteractListener(), this)
        attachQuestBridge()

        logger.info("SkTyper enabled against Typewriter ${Tw.version ?: "unknown"}.")
    }

    override fun onDisable() {
        QuestBridge.detach()
    }

    /**
     * Extensions load asynchronously, so the Quest extension usually isn't there yet when we enable.
     * Keep trying for a minute, then stop bothering - plenty of servers don't run it at all.
     */
    private fun attachQuestBridge(attemptsLeft: Int = 60) {
        if (!isEnabled) return
        if (QuestBridge.attach(this)) {
            logger.info("Typewriter Quest extension detected - quest syntax is active.")
            return
        }
        if (attemptsLeft <= 0) {
            logger.info("Typewriter Quest extension not found - quest syntax will do nothing.")
            return
        }
        server.scheduler.runTaskLater(this, Runnable { attachQuestBridge(attemptsLeft - 1) }, 20L)
    }

    @EventHandler
    fun onTypewriterUnload(event: TypewriterUnloadEvent) {
        // The extension classloader is gone after this, every cached Class is stale.
        QuestBridge.detach()
        server.scheduler.runTaskLater(this, Runnable { attachQuestBridge() }, 40L)
    }

    companion object {
        @JvmStatic
        lateinit var instance: SkTyper
            private set
    }
}
