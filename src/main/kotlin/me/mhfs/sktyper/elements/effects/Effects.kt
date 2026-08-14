package me.mhfs.sktyper.elements.effects

import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.RequiredPlugins
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Effect
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser.ParseResult
import ch.njol.util.Kleenean
import com.typewritermc.core.entries.Page
import me.mhfs.sktyper.bridge.QuestBridge
import me.mhfs.sktyper.tw.Tw
import org.bukkit.entity.Player
import org.bukkit.event.Event

@Name("Trigger Typewriter Entry")
@Description(
    "Fires a Typewriter entry for players, exactly as if the entry had been reached through the " +
        "story graph: criteria are checked, modifiers are applied and the entry's own triggers run.",
    "Only triggerable entries (dialogue, actions, events, ...) do anything - facts and manifest " +
        "entries are silently skipped.",
)
@Examples(
    "trigger typewriter entry \"welcome_dialogue\" for player",
    "on join:",
    "\ttrigger typewriter entry \"tutorial_start\" for player",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class EffTriggerEntry : Effect() {

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

    override fun execute(event: Event) {
        val resolved = Tw.resolveAll(entries.getArray(event))
        for (player in players.getArray(event)) {
            resolved.forEach { Tw.trigger(it, player) }
        }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "trigger typewriter entry ${entries.toString(event, debug)} for ${players.toString(event, debug)}"
}

@Name("Start Typewriter Cinematic")
@Description(
    "Starts a cinematic page for players.",
    "The page is addressed by id or by name and must be a cinematic page.",
    "By default Typewriter hides chat and action bar messages for the duration; add " +
        "`without blocking messages` to leave them visible.",
)
@Examples(
    "start typewriter cinematic \"intro_cutscene\" for player",
    "play typewriter cinematic \"credits\" for all players without blocking messages",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class EffStartCinematic : Effect() {

    private lateinit var page: Expression<*>
    private lateinit var players: Expression<Player>
    private var blockMessages = true

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        page = exprs[0]
        players = exprs[1] as Expression<Player>
        blockMessages = parseResult.mark == 0
        return true
    }

    override fun execute(event: Event) {
        val target = page.getSingle(event) ?: return
        val pageId = when (target) {
            is Page -> target.id
            else -> target.toString()
        }
        for (player in players.getArray(event)) {
            Tw.startCinematic(player, pageId, blockMessages)
        }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "start typewriter cinematic ${page.toString(event, debug)} for ${players.toString(event, debug)}"
}

@Name("Stop Typewriter Cinematic")
@Description("Stops the cinematic a player is watching. Does nothing when they are not in one.")
@Examples("stop the typewriter cinematic for player")
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class EffStopCinematic : Effect() {

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

    override fun execute(event: Event) {
        players.getArray(event).forEach { Tw.stopCinematic(it) }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "stop the typewriter cinematic for ${players.toString(event, debug)}"
}

@Name("Continue Typewriter Dialogue")
@Description(
    "Advances a player's dialogue, as if they had pressed the continue key.",
    "The plain form first completes the typing animation when it is still running, which is what " +
        "the key press does. `force` skips straight to the next entry.",
)
@Examples(
    "continue the typewriter dialogue for player",
    "force the next typewriter dialogue for player",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class EffNextDialogue : Effect() {

    private lateinit var players: Expression<Player>
    private var force = false

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        players = exprs[0] as Expression<Player>
        force = matchedPattern == 1
        return true
    }

    override fun execute(event: Event) {
        players.getArray(event).forEach { Tw.nextDialogue(it, force) }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "continue the typewriter dialogue for ${players.toString(event, debug)}"
}

@Name("End Typewriter Interaction")
@Description(
    "Ends whatever Typewriter interaction a player is in - a dialogue, a cinematic or a content editor.",
)
@Examples("end the typewriter interaction for player")
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class EffEndInteraction : Effect() {

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

    override fun execute(event: Event) {
        players.getArray(event).forEach { Tw.endInteraction(it) }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "end the typewriter interaction for ${players.toString(event, debug)}"
}

@Name("Track Typewriter Quest")
@Description(
    "Starts or stops tracking a quest for players, which is what decides whose objectives show up " +
        "in the quest tracker.",
    "Typewriter tracks quests automatically when they become active; this only overrides that choice.",
    "Requires Typewriter's Quest extension.",
)
@Examples(
    "track typewriter quest \"main_story\" for player",
    "untrack the typewriter quest for player",
)
@RequiredPlugins("Typewriter", "Typewriter Quest extension")
@Since("1.0.0")
class EffTrackQuest : Effect() {

    private var quests: Expression<*>? = null
    private lateinit var players: Expression<Player>
    private var untrack = false

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        untrack = matchedPattern == 1
        if (untrack) {
            players = exprs[0] as Expression<Player>
        } else {
            quests = exprs[0]
            players = exprs[1] as Expression<Player>
        }
        return true
    }

    override fun execute(event: Event) {
        val players = this.players.getArray(event)
        if (untrack) {
            players.forEach { QuestBridge.untrack(it) }
            return
        }
        val quest = Tw.resolve(quests?.getSingle(event)) ?: return
        players.forEach { QuestBridge.track(it, quest) }
    }

    override fun toString(event: Event?, debug: Boolean): String = if (untrack) {
        "untrack the typewriter quest for ${players.toString(event, debug)}"
    } else {
        "track typewriter quest ${quests?.toString(event, debug)} for ${players.toString(event, debug)}"
    }
}

@Name("Modify Typewriter Audience")
@Description(
    "Forces players into or out of an audience entry.",
    "Audiences normally manage themselves from their own filters; a manual change holds until the " +
        "audience recalculates.",
)
@Examples(
    "add player to the typewriter audience of \"event_sidebar\"",
    "remove all players from the typewriter audience of \"event_sidebar\"",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class EffAudience : Effect() {

    private lateinit var players: Expression<Player>
    private lateinit var entries: Expression<*>
    private var add = true

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        players = exprs[0] as Expression<Player>
        entries = exprs[1]
        add = matchedPattern == 0
        return true
    }

    override fun execute(event: Event) {
        val resolved = Tw.resolveAll(entries.getArray(event))
        for (player in players.getArray(event)) {
            for (entry in resolved) {
                if (add) Tw.addToAudience(player, entry) else Tw.removeFromAudience(player, entry)
            }
        }
    }

    override fun toString(event: Event?, debug: Boolean): String {
        val verb = if (add) "add" else "remove"
        val preposition = if (add) "to" else "from"
        return "$verb ${players.toString(event, debug)} $preposition the typewriter audience of " +
            entries.toString(event, debug)
    }
}

@Name("Refresh Typewriter Fact")
@Description(
    "Re-fires Typewriter's fact refresh trigger for a fact, making everything that listens to it " +
        "(objectives, audiences, quest states) re-evaluate immediately.",
    "Useful after changing the underlying data of a placeholder or permission fact, which SkTyper " +
        "cannot write to directly.",
)
@Examples("refresh typewriter fact \"rank_fact\" for player")
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class EffRefreshFact : Effect() {

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

    override fun execute(event: Event) {
        val resolved = Tw.resolveAll(entries.getArray(event))
        for (player in players.getArray(event)) {
            resolved.forEach { Tw.refreshFact(it, player) }
        }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "refresh typewriter fact ${entries.toString(event, debug)} for ${players.toString(event, debug)}"
}
