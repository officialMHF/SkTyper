package me.mhfs.sktyper.bridge

import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

/**
 * Fires once per block interaction, matching what Typewriter's own Interact Block Event entry does:
 * off-hand is dropped so a single right click does not run the trigger twice.
 */
class TypewriterBlockInteractEvent(
    player: Player,
    val block: Block?,
    val action: Action,
    val item: ItemStack?,
    val sneaking: Boolean,
) : PlayerEvent(player), Cancellable {

    private var cancelled = false

    val rightClick: Boolean
        get() = action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        @JvmStatic
        val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}

class BlockInteractListener : Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand == EquipmentSlot.OFF_HAND) return
        if (TypewriterBlockInteractEvent.HANDLER_LIST.registeredListeners.isEmpty()) return

        val mirror = TypewriterBlockInteractEvent(
            event.player,
            event.clickedBlock,
            event.action,
            event.item,
            event.player.isSneaking,
        )
        mirror.callEvent()
        if (mirror.isCancelled) event.isCancelled = true
    }
}
