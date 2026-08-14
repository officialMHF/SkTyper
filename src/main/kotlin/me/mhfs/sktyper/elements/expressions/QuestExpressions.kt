package me.mhfs.sktyper.elements.expressions

import ch.njol.skript.classes.Changer.ChangeMode
import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.RequiredPlugins
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser.ParseResult
import ch.njol.skript.lang.util.SimpleExpression
import ch.njol.util.Kleenean
import com.typewritermc.core.entries.Entry
import me.mhfs.sktyper.bridge.QuestBridge
import me.mhfs.sktyper.tw.Tw
import org.bukkit.entity.Player
import org.bukkit.event.Event

@Name("Tracked Typewriter Quest")
@Description(
    "The quest a player is currently tracking, i.e. the one whose objectives are on their screen.",
    "Setting it starts tracking a quest, deleting it stops tracking altogether.",
    "Requires Typewriter's Quest extension.",
)
@Examples(
    "set {_quest} to the tracked typewriter quest of player",
    "set the tracked typewriter quest of player to typewriter entry \"main_story\"",
    "delete the tracked typewriter quest of player",
)
@RequiredPlugins("Typewriter", "Typewriter Quest extension")
@Since("1.0.0")
class ExprTrackedQuest : SimpleExpression<Entry>() {

    private lateinit var players: Expression<Player>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        players = exprs[0] as Expression<Player>
        return true
    }

    override fun get(event: Event): Array<Entry> =
        players.getArray(event).mapNotNull { QuestBridge.trackedQuest(it) }.toTypedArray()

    override fun isSingle(): Boolean = players.isSingle

    override fun getReturnType(): Class<out Entry> = Entry::class.java

    override fun acceptChange(mode: ChangeMode): Array<Class<*>>? = when (mode) {
        ChangeMode.SET -> arrayOf<Class<*>>(Entry::class.java, String::class.java)
        ChangeMode.DELETE, ChangeMode.RESET -> arrayOf()
        else -> null
    }

    override fun change(event: Event, delta: Array<out Any>?, mode: ChangeMode) {
        when (mode) {
            ChangeMode.SET -> {
                val quest = Tw.resolve(delta?.firstOrNull()) ?: return
                players.getArray(event).forEach { QuestBridge.track(it, quest) }
            }

            ChangeMode.DELETE, ChangeMode.RESET -> players.getArray(event).forEach { QuestBridge.untrack(it) }
            else -> Unit
        }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "the tracked typewriter quest of ${players.toString(event, debug)}"
}

@Name("Typewriter Quests")
@Description(
    "All active, completed or inactive quests of a player.",
    "A quest's status is derived from its facts by Typewriter itself; there is no way to force a " +
        "status directly. Change the facts the quest is built on instead.",
    "Requires Typewriter's Quest extension.",
)
@Examples(
    "send \"You have %size of the active typewriter quests of player% quests in progress\"",
    "loop the completed typewriter quests of player:",
    "\tsend \"- %the typewriter quest display of loop-value for player%\"",
)
@RequiredPlugins("Typewriter", "Typewriter Quest extension")
@Since("1.0.0")
class ExprQuests : SimpleExpression<Entry>() {

    private var kind = 0
    private lateinit var players: Expression<Player>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        kind = matchedPattern
        players = exprs[0] as Expression<Player>
        return true
    }

    override fun get(event: Event): Array<Entry> = players.getArray(event)
        .flatMap { player ->
            when (kind) {
                PATTERN_COMPLETED -> QuestBridge.completedQuests(player)
                PATTERN_INACTIVE -> QuestBridge.inactiveQuests(player)
                else -> QuestBridge.activeQuests(player)
            }
        }
        .distinctBy { it.id }
        .toTypedArray()

    override fun isSingle(): Boolean = false

    override fun getReturnType(): Class<out Entry> = Entry::class.java

    override fun toString(event: Event?, debug: Boolean): String {
        val name = when (kind) {
            PATTERN_COMPLETED -> "completed"
            PATTERN_INACTIVE -> "inactive"
            else -> "active"
        }
        return "the $name typewriter quests of ${players.toString(event, debug)}"
    }

    companion object {
        const val PATTERN_ACTIVE = 0
        const val PATTERN_COMPLETED = 1
        const val PATTERN_INACTIVE = 2
    }
}

@Name("Typewriter Quest Status")
@Description(
    "The status of a quest for a player: `inactive`, `active` or `completed`.",
    "Returns nothing when the entry is not a quest, or when the Quest extension is not installed.",
    "Requires Typewriter's Quest extension.",
)
@Examples(
    "if the typewriter quest status of \"main_story\" for player is \"completed\":",
    "\tsend \"Thanks for playing!\"",
)
@RequiredPlugins("Typewriter", "Typewriter Quest extension")
@Since("1.0.0")
class ExprQuestStatus : SimpleExpression<String>() {

    private lateinit var quests: Expression<*>
    private lateinit var players: Expression<Player>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        quests = exprs[0]
        players = exprs[1] as Expression<Player>
        return true
    }

    override fun get(event: Event): Array<String> {
        val entries = Tw.resolveAll(quests.getArray(event))
        val statuses = ArrayList<String>()
        for (player in players.getArray(event)) {
            for (entry in entries) {
                statuses += QuestBridge.status(entry, player) ?: continue
            }
        }
        return statuses.toTypedArray()
    }

    override fun isSingle(): Boolean = quests.isSingle && players.isSingle

    override fun getReturnType(): Class<out String> = String::class.java

    override fun toString(event: Event?, debug: Boolean): String =
        "the typewriter quest status of ${quests.toString(event, debug)} for ${players.toString(event, debug)}"
}

@Name("Typewriter Quest Display")
@Description(
    "The player-facing display text of a quest or an objective, with colours and placeholders resolved.",
    "Objectives additionally render their completed/showing/inactive styling, exactly as they appear " +
        "in the quest tracker.",
    "Requires Typewriter's Quest extension.",
)
@Examples(
    "send \"Tracking: %the typewriter quest display of the tracked typewriter quest of player for player%\"",
)
@RequiredPlugins("Typewriter", "Typewriter Quest extension")
@Since("1.0.0")
class ExprQuestDisplay : SimpleExpression<String>() {

    private lateinit var entries: Expression<*>
    private lateinit var players: Expression<Player>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        entries = exprs[0]
        players = exprs[1] as Expression<Player>
        return true
    }

    override fun get(event: Event): Array<String> {
        val resolved = Tw.resolveAll(entries.getArray(event))
        val displays = ArrayList<String>()
        for (player in players.getArray(event)) {
            for (entry in resolved) {
                displays += QuestBridge.display(entry, player) ?: continue
            }
        }
        return displays.toTypedArray()
    }

    override fun isSingle(): Boolean = entries.isSingle && players.isSingle

    override fun getReturnType(): Class<out String> = String::class.java

    override fun toString(event: Event?, debug: Boolean): String =
        "the typewriter quest display of ${entries.toString(event, debug)} for ${players.toString(event, debug)}"
}
