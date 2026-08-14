package me.mhfs.sktyper.elements.conditions

import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.RequiredPlugins
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Condition
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser.ParseResult
import ch.njol.util.Kleenean
import me.mhfs.sktyper.bridge.QuestBridge
import me.mhfs.sktyper.tw.Tw
import org.bukkit.entity.Player
import org.bukkit.event.Event

@Name("Is In Typewriter Dialogue")
@Description("Whether players are currently inside a Typewriter dialogue interaction.")
@Examples(
    "if player is in a typewriter dialogue:",
    "\tcancel event",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class CondInDialogue : Condition() {

    private lateinit var players: Expression<Player>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        players = exprs[0] as Expression<Player>
        setNegated(matchedPattern == 1)
        return true
    }

    override fun check(event: Event): Boolean =
        players.check(event, { Tw.isInDialogue(it) }, isNegated)

    override fun toString(event: Event?, debug: Boolean): String =
        "${players.toString(event, debug)} is${if (isNegated) " not" else ""} in a typewriter dialogue"
}

@Name("Is Playing Typewriter Cinematic")
@Description(
    "Whether players are currently watching a Typewriter cinematic.",
    "Optionally restricted to one page, given by id or name.",
)
@Examples(
    "if player is playing a typewriter cinematic:",
    "\tcancel event",
    "if player is playing typewriter cinematic \"intro_cutscene\":",
    "\tsend \"Enjoy the show.\"",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class CondInCinematic : Condition() {

    private lateinit var players: Expression<Player>
    private var page: Expression<String>? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        players = exprs[0] as Expression<Player>
        page = exprs.getOrNull(1) as? Expression<String>
        setNegated(matchedPattern == 1)
        return true
    }

    override fun check(event: Event): Boolean {
        val pageId = page?.getSingle(event)
        return players.check(
            event,
            { player -> if (pageId == null) Tw.isPlayingCinematic(player) else Tw.isPlayingCinematic(player, pageId) },
            isNegated,
        )
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "${players.toString(event, debug)} is${if (isNegated) " not" else ""} playing a typewriter cinematic"
}

@Name("Is In Typewriter Audience")
@Description("Whether players are inside the audience of a Typewriter audience entry.")
@Examples(
    "if player is in the typewriter audience of \"boss_bar\":",
    "\tsend \"You can see the boss bar.\"",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class CondInAudience : Condition() {

    private lateinit var players: Expression<Player>
    private lateinit var entry: Expression<*>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        players = exprs[0] as Expression<Player>
        entry = exprs[1]
        setNegated(matchedPattern == 1)
        return true
    }

    override fun check(event: Event): Boolean {
        val target = Tw.resolve(entry.getSingle(event)) ?: return isNegated
        return players.check(event, { Tw.inAudience(it, target) }, isNegated)
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "${players.toString(event, debug)} is${if (isNegated) " not" else ""} in the typewriter audience of " +
            entry.toString(event, debug)
}

@Name("Typewriter Quest Status")
@Description(
    "Whether a quest is active, completed or inactive for players.",
    "Requires Typewriter's Quest extension.",
)
@Examples(
    "if typewriter quest \"main_story\" is completed for player:",
    "\tsend \"Thanks for playing!\"",
    "if typewriter quest \"side_quest\" is not active for player:",
    "\ttrigger typewriter entry \"side_quest_offer\" for player",
)
@RequiredPlugins("Typewriter", "Typewriter Quest extension")
@Since("1.0.0")
class CondQuestStatus : Condition() {

    private lateinit var quest: Expression<*>
    private lateinit var players: Expression<Player>
    private var expected = "active"

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        quest = exprs[0]
        players = exprs[1] as Expression<Player>
        expected = when {
            parseResult.hasTag("completed") -> "completed"
            parseResult.hasTag("inactive") -> "inactive"
            else -> "active"
        }
        setNegated(matchedPattern == 1)
        return true
    }

    override fun check(event: Event): Boolean {
        val target = Tw.resolve(quest.getSingle(event)) ?: return isNegated
        return players.check(event, { QuestBridge.status(target, it) == expected }, isNegated)
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "typewriter quest ${quest.toString(event, debug)} is${if (isNegated) " not" else ""} $expected for " +
            players.toString(event, debug)
}

@Name("Is Tracking Typewriter Quest")
@Description(
    "Whether players are tracking a quest. Without a quest, whether they are tracking anything at all.",
    "Requires Typewriter's Quest extension.",
)
@Examples(
    "if player is tracking typewriter quest \"main_story\":",
    "\tsend \"Follow the marker.\"",
)
@RequiredPlugins("Typewriter", "Typewriter Quest extension")
@Since("1.0.0")
class CondQuestTracked : Condition() {

    private lateinit var players: Expression<Player>
    private var quest: Expression<*>? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        players = exprs[0] as Expression<Player>
        quest = exprs.getOrNull(1)
        setNegated(matchedPattern == 1)
        return true
    }

    override fun check(event: Event): Boolean {
        val target = quest?.getSingle(event)?.let { Tw.resolve(it) }
        return players.check(
            event,
            { player ->
                if (target == null) QuestBridge.trackedQuest(player) != null
                else QuestBridge.isTracking(player, target)
            },
            isNegated,
        )
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "${players.toString(event, debug)} is${if (isNegated) " not" else ""} tracking a typewriter quest"
}

@Name("Typewriter Entry Exists")
@Description("Whether Typewriter has an entry loaded with the given id or name.")
@Examples(
    "if typewriter entry \"welcome_dialogue\" exists:",
    "\ttrigger typewriter entry \"welcome_dialogue\" for player",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class CondEntryExists : Condition() {

    private lateinit var query: Expression<String>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        query = exprs[0] as Expression<String>
        setNegated(matchedPattern == 1)
        return true
    }

    override fun check(event: Event): Boolean =
        query.check(event, { Tw.entry(it) != null }, isNegated)

    override fun toString(event: Event?, debug: Boolean): String =
        "typewriter entry ${query.toString(event, debug)} does${if (isNegated) " not" else ""} exist"
}

@Name("Typewriter Criteria Are Met")
@Description(
    "Whether the criteria of an entry are satisfied for players.",
    "This is the same check Typewriter runs before it triggers the entry, so it answers \"would " +
        "this fire right now?\" without firing it.",
)
@Examples(
    "if the typewriter criteria of \"secret_dialogue\" are met for player:",
    "\tsend \"You found a secret.\"",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class CondCriteriaMet : Condition() {

    private lateinit var entry: Expression<*>
    private lateinit var players: Expression<Player>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        entry = exprs[0]
        players = exprs[1] as Expression<Player>
        setNegated(matchedPattern == 1)
        return true
    }

    override fun check(event: Event): Boolean {
        val target = Tw.resolve(entry.getSingle(event)) ?: return isNegated
        return players.check(event, { Tw.criteriaMatch(target, it) }, isNegated)
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "the typewriter criteria of ${entry.toString(event, debug)} are${if (isNegated) " not" else ""} met for " +
            players.toString(event, debug)
}

@Name("Typewriter Is Loaded")
@Description(
    "Whether the Typewriter plugin is enabled.",
    "`typewriter quests are available` additionally checks that the Quest extension is loaded, which " +
        "is worth guarding on before using any quest syntax.",
)
@Examples(
    "if typewriter is loaded:",
    "\tsend \"Story system online.\"",
    "if typewriter quests are available:",
    "\tsend \"You have %size of the active typewriter quests of player% quests\"",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class CondTypewriterLoaded : Condition() {

    private var questsOnly = false

    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        // 0/1 ask about Typewriter, 2/3 about the Quest extension, odd ones negate.
        questsOnly = matchedPattern >= 2
        setNegated(matchedPattern % 2 == 1)
        return true
    }

    override fun check(event: Event): Boolean {
        val available = if (questsOnly) Tw.isAvailable && QuestBridge.isAvailable else Tw.isAvailable
        return available != isNegated
    }

    override fun toString(event: Event?, debug: Boolean): String =
        if (questsOnly) "typewriter quests are${if (isNegated) " not" else ""} available"
        else "typewriter is${if (isNegated) " not" else ""} loaded"
}
