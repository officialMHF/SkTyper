package me.mhfs.sktyper.elements.expressions

import ch.njol.skript.Skript
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
import com.typewritermc.engine.paper.events.AsyncEntityDefinitionInteract
import me.mhfs.sktyper.tw.Tw
import org.bukkit.entity.Player
import org.bukkit.event.Event

@Name("Typewriter Cinematic Frame")
@Description(
    "The frame a player's cinematic is currently on, or nothing when they are not watching one.",
    "Setting it seeks the cinematic; Typewriter only ever seeks forwards, so a lower frame is ignored.",
)
@Examples(
    "if the typewriter cinematic frame of player is greater than 200:",
    "\tstop the typewriter cinematic for player",
    "set the typewriter cinematic frame of player to 400",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprCinematicFrame : SimpleExpression<Number>() {

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

    override fun get(event: Event): Array<Number> =
        players.getArray(event).mapNotNull { Tw.cinematicFrame(it) }.toTypedArray()

    override fun isSingle(): Boolean = players.isSingle

    override fun getReturnType(): Class<out Number> = Number::class.java

    override fun acceptChange(mode: ChangeMode): Array<Class<*>>? = when (mode) {
        ChangeMode.SET, ChangeMode.ADD, ChangeMode.REMOVE -> arrayOf<Class<*>>(Number::class.java)
        else -> null
    }

    override fun change(event: Event, delta: Array<out Any>?, mode: ChangeMode) {
        val amount = (delta?.firstOrNull() as? Number)?.toInt() ?: return
        for (player in players.getArray(event)) {
            val target = when (mode) {
                ChangeMode.SET -> amount
                ChangeMode.ADD -> (Tw.cinematicFrame(player) ?: continue) + amount
                ChangeMode.REMOVE -> (Tw.cinematicFrame(player) ?: continue) - amount
                else -> return
            }
            Tw.setCinematicFrame(player, target)
        }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "the typewriter cinematic frame of ${players.toString(event, debug)}"
}

@Name("Current Typewriter Dialogue")
@Description("The dialogue entry a player is currently being shown, if any.")
@Examples(
    "on typewriter dialogue switch:",
    "\tsend \"Now showing %the typewriter name of the current typewriter dialogue of event-player%\" to console",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprCurrentDialogue : SimpleExpression<Entry>() {

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
        players.getArray(event).mapNotNull { Tw.currentDialogue(it) }.toTypedArray()

    override fun isSingle(): Boolean = players.isSingle

    override fun getReturnType(): Class<out Entry> = Entry::class.java

    override fun toString(event: Event?, debug: Boolean): String =
        "the current typewriter dialogue of ${players.toString(event, debug)}"
}

@Name("Typewriter Dialogue Speakers")
@Description("Every speaker (NPC definition or instance) that took part in a player's current dialogue.")
@Examples(
    "on typewriter dialogue end:",
    "\tsend \"You spoke with %the typewriter dialogue speakers of event-player%\"",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprDialogueSpeakers : SimpleExpression<Entry>() {

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
        players.getArray(event).flatMap { Tw.dialogueSpeakers(it) }.distinctBy { it.id }.toTypedArray()

    override fun isSingle(): Boolean = false

    override fun getReturnType(): Class<out Entry> = Entry::class.java

    override fun toString(event: Event?, debug: Boolean): String =
        "the typewriter dialogue speakers of ${players.toString(event, debug)}"
}

@Name("Typewriter Audience")
@Description(
    "Every online player currently inside the audience of an audience entry.",
    "Audience entries are the manifest-page entries that decide who sees an NPC, a sidebar, an " +
        "objective, and so on.",
)
@Examples("send \"Boss bar is showing for %the typewriter audience of \"\"boss_bar\"\"%\"")
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprAudience : SimpleExpression<Player>() {

    private lateinit var entries: Expression<*>

    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        entries = exprs[0]
        return true
    }

    override fun get(event: Event): Array<Player> =
        Tw.resolveAll(entries.getArray(event)).flatMap { Tw.audienceOf(it) }.distinct().toTypedArray()

    override fun isSingle(): Boolean = false

    override fun getReturnType(): Class<out Player> = Player::class.java

    override fun toString(event: Event?, debug: Boolean): String =
        "the typewriter audience of ${entries.toString(event, debug)}"
}

@Name("Typewriter Display Name")
@Description(
    "The display name a Typewriter speaker (an NPC definition or an NPC instance) shows to a player.",
    "Colour codes and placeholders are resolved for that specific player.",
)
@Examples(
    "on typewriter entity interact:",
    "\tsend \"You clicked %the typewriter display name of event-typewriter entry for event-player%\"",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprDisplayName : SimpleExpression<String>() {

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
        val names = ArrayList<String>()
        for (player in players.getArray(event)) {
            for (entry in resolved) {
                names += Tw.speakerDisplayName(entry, player) ?: continue
            }
        }
        return names.toTypedArray()
    }

    override fun isSingle(): Boolean = entries.isSingle && players.isSingle

    override fun getReturnType(): Class<out String> = String::class.java

    override fun toString(event: Event?, debug: Boolean): String =
        "the typewriter display name of ${entries.toString(event, debug)} for ${players.toString(event, debug)}"
}

@Name("Interacted Typewriter Entity Instance")
@Description(
    "In a `typewriter entity interact` event, the entity *instance* that was clicked.",
    "`event-typewriter entry` gives the definition (what the NPC is); this gives the placement " +
        "(where that NPC was put on a manifest page).",
)
@Examples(
    "on typewriter entity interact:",
    "\tif the typewriter name of the interacted typewriter entity instance is \"town_guard_gate\":",
    "\t\ttrigger typewriter entry \"guard_greeting\" for event-player",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprInteractedInstance : SimpleExpression<Entry>() {

    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        if (!parser.isCurrentEvent(AsyncEntityDefinitionInteract::class.java)) {
            Skript.error(
                "The interacted typewriter entity instance can only be used in a typewriter entity interact event.",
            )
            return false
        }
        return true
    }

    override fun get(event: Event): Array<Entry> {
        val interact = event as? AsyncEntityDefinitionInteract ?: return emptyArray()
        return arrayOf(interact.instance)
    }

    override fun isSingle(): Boolean = true

    override fun getReturnType(): Class<out Entry> = Entry::class.java

    override fun toString(event: Event?, debug: Boolean): String = "the interacted typewriter entity instance"
}
