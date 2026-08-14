package me.mhfs.sktyper.bridge

import com.typewritermc.core.entries.Entry
import com.typewritermc.core.entries.Ref
import com.typewritermc.loader.ExtensionLoader
import me.mhfs.sktyper.tw.Tw
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.Plugin
import org.koin.java.KoinJavaComponent
import java.lang.reflect.Method
import kotlin.reflect.KClass

/**
 * Reflective access to Typewriter's Quest extension.
 *
 * The engine is a normal plugin so its classes can be linked against directly, but extensions get
 * loaded into a private URLClassLoader that Typewriter throws away and rebuilds on every reload.
 * Going through [ExtensionLoader] is the only stable route in, and it keeps SkTyper working on
 * servers that never installed the Quest extension.
 */
object QuestBridge {

    private const val QUEST_ENTRY = "com.typewritermc.quest.QuestEntry"
    private const val OBJECTIVE_ENTRY = "com.typewritermc.quest.ObjectiveEntry"
    private const val QUEST_TRACKER = "com.typewritermc.quest.QuestTracker"
    private const val STATUS_EVENT = "com.typewritermc.quest.events.AsyncQuestStatusUpdate"
    private const val TRACKED_EVENT = "com.typewritermc.quest.events.AsyncTrackedQuestUpdate"

    private var questEntryClass: Class<*>? = null
    private var objectiveEntryClass: Class<*>? = null
    private var trackerClass: Class<*>? = null

    private val methods = HashMap<String, Method?>()
    private val listener = object : Listener {}

    val isAvailable: Boolean
        get() = questEntryClass != null

    private fun extensionLoader(): ExtensionLoader? =
        runCatching { KoinJavaComponent.get<ExtensionLoader>(ExtensionLoader::class.java) }.getOrNull()

    private fun loadClass(name: String): Class<*>? =
        runCatching { extensionLoader()?.loadClass(name) }.getOrNull()

    /** Safe to call again after a reload; every cached Class is dropped first. */
    fun attach(plugin: Plugin): Boolean {
        reset()
        if (!Tw.isAvailable) return false

        questEntryClass = loadClass(QUEST_ENTRY) ?: return false
        objectiveEntryClass = loadClass(OBJECTIVE_ENTRY)
        trackerClass = loadClass(QUEST_TRACKER)

        registerMirror(plugin, loadClass(STATUS_EVENT)) { event -> mirrorStatusUpdate(event) }
        registerMirror(plugin, loadClass(TRACKED_EVENT)) { event -> mirrorTrackedUpdate(event) }
        return true
    }

    fun detach() {
        HandlerList.unregisterAll(listener)
        reset()
    }

    private fun reset() {
        HandlerList.unregisterAll(listener)
        questEntryClass = null
        objectiveEntryClass = null
        trackerClass = null
        methods.clear()
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerMirror(plugin: Plugin, eventClass: Class<*>?, handler: (Event) -> Unit) {
        if (eventClass == null || !Event::class.java.isAssignableFrom(eventClass)) return
        val executor = EventExecutor { _, event -> runCatching { handler(event) } }
        Bukkit.getPluginManager().registerEvent(
            eventClass as Class<out Event>,
            listener,
            EventPriority.MONITOR,
            executor,
            plugin,
            true,
        )
    }

    private fun mirrorStatusUpdate(event: Event) {
        val player = invoke(event.javaClass, "getPlayer", event) as? Player ?: return
        val quest = invoke(event.javaClass, "getQuest", event) as? Ref<*>
        val questId = quest?.id ?: return
        val from = (invoke(event.javaClass, "getFrom", event) as? Enum<*>)?.name?.lowercase() ?: return
        val to = (invoke(event.javaClass, "getTo", event) as? Enum<*>)?.name?.lowercase() ?: return
        TypewriterQuestStatusEvent(player, Tw.entryById(questId), questId, from, to).callEvent()
    }

    private fun mirrorTrackedUpdate(event: Event) {
        val player = invoke(event.javaClass, "getPlayer", event) as? Player ?: return
        val from = (invoke(event.javaClass, "getFrom", event) as? Ref<*>)?.id?.let { Tw.entryById(it) }
        val to = (invoke(event.javaClass, "getTo", event) as? Ref<*>)?.id?.let { Tw.entryById(it) }
        TypewriterTrackedQuestEvent(player, from, to).callEvent()
    }

    private fun invoke(owner: Class<*>, name: String, target: Any, vararg args: Any?): Any? {
        val key = "${owner.name}#$name/${args.size}"
        val method = methods.getOrPut(key) {
            owner.methods.firstOrNull { it.name == name && it.parameterCount == args.size }
                ?.apply { isAccessible = true }
        } ?: return null
        return runCatching { method.invoke(target, *args) }.getOrNull()
    }

    // Carries the extension's own KClass so that ref.get() resolves back to the quest entry.
    @Suppress("UNCHECKED_CAST")
    private fun questRef(id: String): Ref<Entry>? {
        val klass = questEntryClass?.kotlin as? KClass<Entry> ?: return null
        return Ref(id, klass)
    }

    private fun tracker(name: String, vararg types: Class<*>): Method? {
        val owner = trackerClass ?: return null
        val key = "tracker#$name/${types.size}"
        return methods.getOrPut(key) {
            runCatching { owner.getMethod(name, *types) }.getOrNull()?.apply { isAccessible = true }
        }
    }

    fun isQuest(entry: Entry): Boolean = questEntryClass?.isInstance(entry) == true

    fun isObjective(entry: Entry): Boolean = objectiveEntryClass?.isInstance(entry) == true

    /** One of `inactive`, `active`, `completed`, or null when the entry isn't a quest. */
    fun status(entry: Entry, player: Player): String? {
        val owner = questEntryClass ?: return null
        if (!owner.isInstance(entry)) return null
        val method = methods.getOrPut("quest#status") {
            runCatching { owner.getMethod("questStatus", Player::class.java) }.getOrNull()
        } ?: return null
        return (runCatching { method.invoke(entry, player) }.getOrNull() as? Enum<*>)?.name?.lowercase()
    }

    fun display(entry: Entry, player: Player): String? {
        val owner = when {
            questEntryClass?.isInstance(entry) == true -> questEntryClass
            objectiveEntryClass?.isInstance(entry) == true -> objectiveEntryClass
            else -> null
        } ?: return null
        val method = methods.getOrPut("display#${owner.name}") {
            runCatching { owner.getMethod("display", Player::class.java) }.getOrNull()
        } ?: return null
        return runCatching { method.invoke(entry, player) }.getOrNull() as? String
    }

    private fun questList(name: String, player: Player): List<Entry> {
        val method = tracker(name, Player::class.java) ?: return emptyList()
        val refs = runCatching { method.invoke(null, player) }.getOrNull() as? Collection<*> ?: return emptyList()
        return refs.filterIsInstance<Ref<*>>().mapNotNull { Tw.entryById(it.id) }
    }

    fun activeQuests(player: Player): List<Entry> = questList("activeQuests", player)

    fun completedQuests(player: Player): List<Entry> = questList("completedQuests", player)

    fun inactiveQuests(player: Player): List<Entry> = questList("inactiveQuests", player)

    fun trackedQuest(player: Player): Entry? {
        val method = tracker("trackedQuest", Player::class.java) ?: return null
        val ref = runCatching { method.invoke(null, player) }.getOrNull() as? Ref<*> ?: return null
        return Tw.entryById(ref.id)
    }

    fun isTracking(player: Player, quest: Entry): Boolean {
        val method = tracker("isQuestTracked", Player::class.java, Ref::class.java) ?: return false
        val ref = questRef(quest.id) ?: return false
        return runCatching { method.invoke(null, player, ref) }.getOrNull() as? Boolean ?: false
    }

    fun track(player: Player, quest: Entry): Boolean {
        val method = tracker("trackQuest", Player::class.java, Ref::class.java) ?: return false
        val ref = questRef(quest.id) ?: return false
        return runCatching { method.invoke(null, player, ref) }.isSuccess
    }

    fun untrack(player: Player): Boolean {
        val method = tracker("unTrackQuest", Player::class.java) ?: return false
        return runCatching { method.invoke(null, player) }.isSuccess
    }
}
