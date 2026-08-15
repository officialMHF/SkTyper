package me.mhfs.sktyper.tw

import com.typewritermc.core.books.pages.PageType
import com.typewritermc.core.entries.Entry
import com.typewritermc.core.entries.Page
import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.formattedName
import com.typewritermc.core.interaction.context
import com.typewritermc.engine.paper.entry.AudienceManager
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.entries.AudienceEntry
import com.typewritermc.engine.paper.entry.entries.CinematicEntry
import com.typewritermc.engine.paper.entry.entity.AudienceEntityDisplay
import com.typewritermc.engine.paper.entry.findDisplay
import com.typewritermc.engine.paper.entry.entries.EntityDefinitionEntry
import com.typewritermc.engine.paper.entry.entries.EntityInstanceEntry
import com.typewritermc.engine.paper.entry.entries.InteractionEndTrigger
import com.typewritermc.engine.paper.entry.entries.ReadableFactEntry
import com.typewritermc.engine.paper.entry.entries.SpeakerEntry
import com.typewritermc.engine.paper.entry.entries.WritableFactEntry
import com.typewritermc.engine.paper.entry.dialogue.DialogueTrigger
import com.typewritermc.engine.paper.entry.dialogue.currentDialogue
import com.typewritermc.engine.paper.entry.dialogue.isInDialogue
import com.typewritermc.engine.paper.entry.dialogue.speakersInDialogue
import com.typewritermc.engine.paper.entry.inAudience
import com.typewritermc.engine.paper.entry.matches
import com.typewritermc.engine.paper.entry.temporal.TemporalSetFrameTrigger
import com.typewritermc.engine.paper.entry.temporal.TemporalSettings
import com.typewritermc.engine.paper.entry.temporal.TemporalStartTrigger
import com.typewritermc.engine.paper.entry.temporal.TemporalStopTrigger
import com.typewritermc.engine.paper.entry.temporal.currentTemporalFrame
import com.typewritermc.engine.paper.entry.temporal.isPlayingTemporal
import com.typewritermc.engine.paper.entry.triggerFor
import com.typewritermc.engine.paper.facts.FactData
import com.typewritermc.engine.paper.facts.FactDatabase
import com.typewritermc.engine.paper.facts.RefreshFactTrigger
import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.UUID
import org.bukkit.entity.Player
import org.koin.java.KoinJavaComponent

/**
 * Wrapper around the Typewriter engine API.
 *
 * Nothing in here throws. Typewriter can be mid-reload at any point and a script blowing up with a
 * stack trace is a lot worse than a statement quietly doing nothing.
 */
object Tw {

    const val PLUGIN_NAME = "Typewriter"

    val isAvailable: Boolean
        get() = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME)?.isEnabled == true

    val version: String?
        get() = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME)?.pluginMeta?.version

    // Koin's Java helper takes a raw Class and can't infer the result, so <T> has to be spelled out.
    private fun <T : Any> koin(type: Class<T>): T? = runCatching { KoinJavaComponent.get<T>(type) }.getOrNull()

    fun entryById(id: String): Entry? = runCatching { Query.findById(Entry::class, id) }.getOrNull()

    fun entryByName(name: String): Entry? = runCatching { Query.findByName(Entry::class, name) }.getOrNull()

    fun entry(query: String): Entry? = entryById(query) ?: entryByName(query)

    fun resolve(value: Any?): Entry? = when (value) {
        is Entry -> value
        is String -> entry(value)
        else -> null
    }

    fun resolveAll(values: Array<out Any?>?): List<Entry> = values.orEmpty().mapNotNull { resolve(it) }

    fun allEntries(): List<Entry> = runCatching { Query.find(Entry::class).toList() }.getOrElse { emptyList() }

    fun displayString(entry: Entry): String = entry.formattedName

    fun allInstances(): List<Entry> =
        runCatching { Query.find(EntityInstanceEntry::class).toList() }.getOrElse { emptyList() }

    fun allDefinitions(): List<Entry> =
        runCatching { Query.find(EntityDefinitionEntry::class).toList() }.getOrElse { emptyList() }

    /**
     * Where an entity actually is for a given viewer, which is not the spawn point once it starts
     * walking. Returns null when the entity is not being displayed to that player.
     */
    fun livePosition(entry: Entry, player: Player): Location? = runCatching {
        val display = Ref(entry.id, AudienceEntry::class, entry as? AudienceEntry)
            .findDisplay(AudienceEntityDisplay::class) ?: return null
        val position = display.position(player.uniqueId) ?: return null
        val world = Bukkit.getWorld(UUID.fromString(position.world.identifier)) ?: return null
        Location(world, position.x, position.y, position.z, position.yaw, position.pitch)
    }.getOrNull()

    fun pageOf(entry: Entry): Page? = allPages().firstOrNull { page -> page.entries.any { it.id == entry.id } }

    fun allPages(): List<Page> = runCatching {
        PageType.values().flatMap { type -> Query.findPagesOfType(type).toList() }
    }.getOrElse { emptyList() }

    fun page(query: String): Page? = runCatching { Query.findPageById(query) }.getOrNull()
        ?: allPages().firstOrNull { it.name.equals(query, ignoreCase = true) }

    fun resolvePage(value: Any?): Page? = when (value) {
        is Page -> value
        is String -> page(value)
        else -> null
    }

    fun trigger(entry: Entry, player: Player): Boolean {
        if (entry !is TriggerableEntry) return false
        return runCatching {
            Ref(entry.id, TriggerableEntry::class, entry).triggerFor(player, context())
        }.isSuccess
    }

    fun factData(entry: Entry, player: Player): FactData? = runCatching {
        (entry as? ReadableFactEntry)?.readForPlayersGroup(player)
    }.getOrNull()

    fun readFact(entry: Entry, player: Player): Int? = factData(entry, player)?.value

    // Goes through FactDatabase instead of WritableFactEntry.write so the engine fires its own
    // refresh trigger. A raw write leaves objectives and audiences stale until the next fact tick.
    fun writeFact(entry: Entry, player: Player, value: Int): Boolean {
        if (entry !is WritableFactEntry) return false
        val database = koin(FactDatabase::class.java) ?: return false
        return runCatching { database.modify(player) { this[entry.id] = value } }.isSuccess
    }

    fun refreshFact(entry: Entry, player: Player): Boolean {
        if (entry !is ReadableFactEntry) return false
        return runCatching {
            RefreshFactTrigger(Ref(entry.id, ReadableFactEntry::class, entry)).triggerFor(player, context())
        }.isSuccess
    }

    fun isFact(entry: Entry): Boolean = entry is ReadableFactEntry || entry is WritableFactEntry

    fun inAudience(player: Player, entry: Entry): Boolean {
        if (entry !is AudienceEntry) return false
        return runCatching { player.inAudience(Ref(entry.id, AudienceEntry::class, entry)) }.getOrElse { false }
    }

    fun audienceOf(entry: Entry): List<Player> {
        if (entry !is AudienceEntry) return emptyList()
        return Bukkit.getOnlinePlayers().filter { inAudience(it, entry) }
    }

    fun addToAudience(player: Player, entry: Entry): Boolean {
        if (entry !is AudienceEntry) return false
        val manager = koin(AudienceManager::class.java) ?: return false
        return runCatching { manager.addPlayerFor(player, Ref(entry.id, AudienceEntry::class, entry)) }.isSuccess
    }

    fun removeFromAudience(player: Player, entry: Entry): Boolean {
        if (entry !is AudienceEntry) return false
        val manager = koin(AudienceManager::class.java) ?: return false
        return runCatching { manager.removePlayerFor(player, Ref(entry.id, AudienceEntry::class, entry)) }.isSuccess
    }

    fun startCinematic(player: Player, page: String, blockMessages: Boolean): Boolean {
        val pageId = pageId(page) ?: return false
        val settings = TemporalSettings(blockMessages, blockMessages)
        return runCatching {
            TemporalStartTrigger(pageId, emptyList(), settings).triggerFor(player, context())
        }.isSuccess
    }

    fun stopCinematic(player: Player) {
        runCatching { TemporalStopTrigger.triggerFor(player, context()) }
    }

    fun setCinematicFrame(player: Player, frame: Int) {
        runCatching { TemporalSetFrameTrigger(frame.coerceAtLeast(0)).triggerFor(player, context()) }
    }

    fun cinematicFrame(player: Player): Int? = runCatching { player.currentTemporalFrame() }.getOrNull()

    fun isPlayingCinematic(player: Player): Boolean =
        runCatching { player.isPlayingTemporal() }.getOrElse { false }

    fun isPlayingCinematic(player: Player, page: String): Boolean {
        val pageId = pageId(page) ?: page
        return runCatching { player.isPlayingTemporal(pageId) }.getOrElse { false }
    }

    // Cinematics are addressed by page id, but scripters usually know pages by name.
    private fun pageId(page: String): String? = runCatching { Query.findPageById(page) }.getOrNull()?.id
        ?: allPages().firstOrNull { it.name.equals(page, ignoreCase = true) }?.id

    fun isInDialogue(player: Player): Boolean = runCatching { player.isInDialogue }.getOrElse { false }

    fun currentDialogue(player: Player): Entry? = runCatching { player.currentDialogue }.getOrNull()

    fun dialogueSpeakers(player: Player): List<Entry> = runCatching {
        player.speakersInDialogue.mapNotNull { it.get() }
    }.getOrElse { emptyList() }

    fun nextDialogue(player: Player, force: Boolean) {
        val trigger = if (force) DialogueTrigger.FORCE_NEXT else DialogueTrigger.NEXT_OR_SKIP_ANIMATION
        runCatching { trigger.triggerFor(player, context()) }
    }

    fun endInteraction(player: Player) {
        runCatching { InteractionEndTrigger.triggerFor(player, context()) }
    }

    fun criteriaMatch(entry: Entry, player: Player): Boolean {
        val criteria: List<Criteria> = when (entry) {
            is TriggerableEntry -> entry.criteria
            is CinematicEntry -> entry.criteria
            else -> emptyList()
        }
        return runCatching { criteria.matches(player, context()) }.getOrElse { false }
    }

    fun speakerDisplayName(entry: Entry, player: Player): String? = runCatching {
        (entry as? SpeakerEntry)?.displayName?.get(player)
    }.getOrNull()
}
