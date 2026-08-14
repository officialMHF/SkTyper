@file:Suppress("DEPRECATION")

package me.mhfs.sktyper.events

import ch.njol.skript.Skript
import ch.njol.skript.lang.SkriptEvent
import ch.njol.skript.lang.util.SimpleEvent
import ch.njol.skript.registrations.EventValues
import com.typewritermc.core.entries.Entry
import com.typewritermc.engine.paper.events.AsyncCinematicEndEvent
import com.typewritermc.engine.paper.events.AsyncCinematicStartEvent
import com.typewritermc.engine.paper.events.AsyncCinematicTickEvent
import com.typewritermc.engine.paper.events.AsyncDialogueEndEvent
import com.typewritermc.engine.paper.events.AsyncDialogueStartEvent
import com.typewritermc.engine.paper.events.AsyncDialogueSwitchEvent
import com.typewritermc.engine.paper.events.AsyncEntityDefinitionInteract
import com.typewritermc.engine.paper.events.ContentEditorEndEvent
import com.typewritermc.engine.paper.events.ContentEditorStartEvent
import com.typewritermc.engine.paper.events.StagingChangeEvent
import com.typewritermc.engine.paper.events.TypewriterUnloadEvent
import me.mhfs.sktyper.bridge.TypewriterBlockInteractEvent
import me.mhfs.sktyper.bridge.TypewriterQuestStatusEvent
import me.mhfs.sktyper.bridge.TypewriterTrackedQuestEvent
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.event.Event
import org.bukkit.inventory.ItemStack
import org.skriptlang.skript.lang.converter.Converter

/**
 * Typewriter fires everything asynchronously. Skript hops back to the main thread before running a
 * trigger, so ordinary effects are safe inside these events.
 */
object TypewriterEvents {

    private const val SINCE = "1.0.0"

    fun register() {
        registerDialogueEvents()
        registerCinematicEvents()
        registerQuestEvents()
        registerEntityEvents()
        registerBlockEvents()
        registerEngineEvents()
    }

    private fun registerDialogueEvents() {
        event(
            "Typewriter Dialogue Start",
            SimpleEvent::class.java,
            AsyncDialogueStartEvent::class.java,
            "[on] typewriter dialogue start[ed]",
        )
            .description("Called when a player enters a Typewriter dialogue interaction.")
            .examples(
                "on typewriter dialogue start:",
                "\tsend action bar \"Press SHIFT to skip\" to event-player",
            )
            .since(SINCE)

        event(
            "Typewriter Dialogue Switch",
            SimpleEvent::class.java,
            AsyncDialogueSwitchEvent::class.java,
            "[on] typewriter dialogue (switch[ed]|next|advance[d])",
        )
            .description("Called every time the dialogue moves on to the next entry for a player.")
            .examples(
                "on typewriter dialogue switch:",
                "\tset {_line} to the current typewriter dialogue of event-player",
            )
            .since(SINCE)

        event(
            "Typewriter Dialogue End",
            SimpleEvent::class.java,
            AsyncDialogueEndEvent::class.java,
            "[on] typewriter dialogue (end[ed]|stop[ped]|finish[ed])",
        )
            .description("Called when a player's dialogue interaction ends, for any reason.")
            .examples(
                "on typewriter dialogue end:",
                "\tsend \"Talk to me again any time.\" to event-player",
            )
            .since(SINCE)
    }

    private fun registerCinematicEvents() {
        event(
            "Typewriter Cinematic Start",
            SimpleEvent::class.java,
            AsyncCinematicStartEvent::class.java,
            "[on] typewriter cinematic start[ed]",
        )
            .description(
                "Called when a cinematic page starts playing for a player.",
                "`event-string` is the id of the page being played.",
            )
            .examples(
                "on typewriter cinematic start:",
                "\tbroadcast \"%event-player% is watching %event-string%\"",
            )
            .since(SINCE)

        event(
            "Typewriter Cinematic End",
            SimpleEvent::class.java,
            AsyncCinematicEndEvent::class.java,
            "[on] typewriter cinematic (end[ed]|stop[ped]|finish[ed])",
        )
            .description(
                "Called when a cinematic finishes or is interrupted.",
                "`event-string` is the page id, `event-number` the frame it ended on.",
            )
            .examples(
                "on typewriter cinematic end:",
                "\tif event-string is \"intro_cutscene\":",
                "\t\tgive 1 diamond to event-player",
            )
            .since(SINCE)

        event(
            "Typewriter Cinematic Tick",
            SimpleEvent::class.java,
            AsyncCinematicTickEvent::class.java,
            "[on] typewriter cinematic tick",
        )
            .description(
                "Called on every frame of a cinematic. `event-number` is the current frame.",
                "This fires up to twenty times a second per viewer - keep the trigger cheap.",
            )
            .examples(
                "on typewriter cinematic tick:",
                "\tif event-number is 100:",
                "\t\tplay sound \"entity.wither.spawn\" to event-player",
            )
            .since(SINCE)

        EventValues.registerEventValue(
            AsyncCinematicStartEvent::class.java,
            String::class.java,
            Converter { it.pageId },
        )
        EventValues.registerEventValue(
            AsyncCinematicEndEvent::class.java,
            String::class.java,
            Converter { it.pageId },
        )
        EventValues.registerEventValue(
            AsyncCinematicEndEvent::class.java,
            Number::class.java,
            Converter { it.frame },
        )
        EventValues.registerEventValue(
            AsyncCinematicTickEvent::class.java,
            Number::class.java,
            Converter { it.frame },
        )
    }

    private fun registerQuestEvents() {
        event(
            "Typewriter Quest Status Change",
            SimpleEvent::class.java,
            TypewriterQuestStatusEvent::class.java,
            "[on] typewriter quest status (chang(e|ed)|updat(e|ed))",
        )
            .description(
                "Called when a quest moves between inactive, active and completed for a player.",
                "`event-typewriter entry` is the quest, `past event-string` the previous status and " +
                    "`event-string` the new one.",
                "Requires Typewriter's Quest extension.",
            )
            .examples(
                "on typewriter quest status change:",
                "\tif event-string is \"completed\":",
                "\t\tsend \"Quest complete: %typewriter quest display of event-typewriter entry for event-player%\"",
            )
            .since(SINCE)

        event(
            "Typewriter Tracked Quest Change",
            SimpleEvent::class.java,
            TypewriterTrackedQuestEvent::class.java,
            "[on] typewriter tracked quest (chang(e|ed)|updat(e|ed))",
        )
            .description(
                "Called when the quest a player is tracking changes.",
                "`event-typewriter entry` is the newly tracked quest (not set when the player stopped " +
                    "tracking), `past event-typewriter entry` the previous one.",
                "Requires Typewriter's Quest extension.",
            )
            .examples(
                "on typewriter tracked quest change:",
                "\tsend action bar \"Now tracking: %event-typewriter entry%\" to event-player",
            )
            .since(SINCE)

        EventValues.registerEventValue(
            TypewriterQuestStatusEvent::class.java,
            Entry::class.java,
            Converter { it.quest },
        )
        EventValues.registerEventValue(
            TypewriterQuestStatusEvent::class.java,
            String::class.java,
            Converter { it.to },
            EventValues.TIME_NOW,
        )
        EventValues.registerEventValue(
            TypewriterQuestStatusEvent::class.java,
            String::class.java,
            Converter { it.from },
            EventValues.TIME_PAST,
        )
        EventValues.registerEventValue(
            TypewriterTrackedQuestEvent::class.java,
            Entry::class.java,
            Converter { it.to },
            EventValues.TIME_NOW,
        )
        EventValues.registerEventValue(
            TypewriterTrackedQuestEvent::class.java,
            Entry::class.java,
            Converter { it.from },
            EventValues.TIME_PAST,
        )
    }

    private fun registerEntityEvents() {
        event(
            "Typewriter Entity Interact",
            SimpleEvent::class.java,
            AsyncEntityDefinitionInteract::class.java,
            "[on] typewriter (entity|npc) interact[ion]",
        )
            .description(
                "Called when a player interacts with a Typewriter entity (NPC).",
                "`event-typewriter entry` is the entity definition; the concrete placement is " +
                    "available through `the interacted typewriter entity instance`.",
            )
            .examples(
                "on typewriter entity interact:",
                "\tsend \"You clicked %the typewriter display name of event-typewriter entry for event-player%\"",
            )
            .since(SINCE)

        EventValues.registerEventValue(
            AsyncEntityDefinitionInteract::class.java,
            Entry::class.java,
            Converter { it.definition },
        )
    }

    private fun registerBlockEvents() {
        event(
            "Typewriter Block Interact",
            SimpleEvent::class.java,
            TypewriterBlockInteractEvent::class.java,
            "[on] typewriter block interact[ion]",
        )
            .description(
                "Called when a player interacts with a block. Fires once per interaction - the " +
                    "off-hand pass is dropped, the same way Typewriter's own Interact Block Event does.",
                "`event-block`, `event-location` and `event-item` describe what was clicked. The " +
                    "event is cancellable.",
            )
            .examples(
                "on typewriter block interact:",
                "	if event-block is a lever:",
                "		trigger typewriter entry \"secret_door_dialogue\" for event-player",
                "		cancel event",
            )
            .since("1.0.1")

        EventValues.registerEventValue(
            TypewriterBlockInteractEvent::class.java,
            Block::class.java,
            Converter { it.block },
        )
        EventValues.registerEventValue(
            TypewriterBlockInteractEvent::class.java,
            Location::class.java,
            Converter { it.block?.location ?: it.player.location },
        )
        EventValues.registerEventValue(
            TypewriterBlockInteractEvent::class.java,
            ItemStack::class.java,
            Converter { it.item },
        )
        EventValues.registerEventValue(
            TypewriterBlockInteractEvent::class.java,
            String::class.java,
            Converter { it.action.name.lowercase() },
        )
    }

    private fun registerEngineEvents() {
        event(
            "Typewriter Unload",
            SimpleEvent::class.java,
            TypewriterUnloadEvent::class.java,
            "[on] typewriter unload[ed]",
        )
            .description("Called right before Typewriter unloads its pages and extensions, e.g. on a reload.")
            .examples("on typewriter unload:", "\tbroadcast \"Typewriter is reloading\"")
            .since(SINCE)

        event(
            "Typewriter Staging Change",
            SimpleEvent::class.java,
            StagingChangeEvent::class.java,
            "[on] typewriter staging [state] (chang(e|ed)|updat(e|ed))",
        )
            .description(
                "Called when Typewriter's staging state changes.",
                "`event-string` is one of `staging`, `publishing` or `published`.",
            )
            .examples("on typewriter staging change:", "\tbroadcast \"Typewriter is now %event-string%\"")
            .since(SINCE)

        event(
            "Typewriter Content Editor Start",
            SimpleEvent::class.java,
            ContentEditorStartEvent::class.java,
            "[on] typewriter content editor (start|open)[ed]",
        )
            .description("Called when a player enters one of Typewriter's in-game content editors.")
            .examples("on typewriter content editor start:", "\tsend \"Editing mode enabled\" to event-player")
            .since(SINCE)

        event(
            "Typewriter Content Editor End",
            SimpleEvent::class.java,
            ContentEditorEndEvent::class.java,
            "[on] typewriter content editor (end|clos(e|ed)|stop)[ed]",
        )
            .description("Called when a player leaves one of Typewriter's in-game content editors.")
            .examples("on typewriter content editor end:", "\tsend \"Editing mode disabled\" to event-player")
            .since(SINCE)

        EventValues.registerEventValue(
            StagingChangeEvent::class.java,
            String::class.java,
            Converter { it.newState.name.lowercase() },
        )
    }

    private fun <E : SkriptEvent> event(
        name: String,
        skriptEvent: Class<E>,
        bukkitEvent: Class<out Event>,
        vararg patterns: String,
    ) = Skript.registerEvent(name, skriptEvent, arrayOf(bukkitEvent), *patterns)
}
