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
import ch.njol.skript.util.Date
import ch.njol.util.Kleenean
import me.mhfs.sktyper.tw.Tw
import org.bukkit.entity.Player
import org.bukkit.event.Event
import java.time.ZoneId

@Name("Typewriter Fact")
@Description(
    "The value of a Typewriter fact for a player.",
    "Facts are plain integers. Writing to a fact goes through Typewriter's fact database, so " +
        "audiences, objectives and quest states react to the change exactly as they would if " +
        "Typewriter had written it itself.",
    "Only writable facts (cached, persistent, session, timed, ...) can be changed - read-only facts " +
        "such as permission or placeholder facts silently ignore writes.",
)
@Examples(
    "set typewriter fact \"talked_to_mayor\" of player to 1",
    "add 5 to typewriter fact \"coins_collected\" of player",
    "if typewriter fact \"talked_to_mayor\" of player is 0:",
    "\ttrigger typewriter entry \"mayor_intro\" for player",
    "reset typewriter fact \"daily_progress\" of all players",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprFact : SimpleExpression<Number>() {

    private lateinit var facts: Expression<*>
    private lateinit var players: Expression<Player>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        facts = exprs[0]
        players = exprs[1] as Expression<Player>
        return true
    }

    override fun get(event: Event): Array<Number> {
        val entries = Tw.resolveAll(facts.getArray(event))
        val values = ArrayList<Number>()
        for (player in players.getArray(event)) {
            for (entry in entries) {
                values += Tw.readFact(entry, player) ?: 0
            }
        }
        return values.toTypedArray()
    }

    override fun isSingle(): Boolean = facts.isSingle && players.isSingle

    override fun getReturnType(): Class<out Number> = Number::class.java

    override fun acceptChange(mode: ChangeMode): Array<Class<*>>? = when (mode) {
        ChangeMode.SET, ChangeMode.ADD, ChangeMode.REMOVE -> arrayOf<Class<*>>(Number::class.java)
        ChangeMode.DELETE, ChangeMode.RESET -> arrayOf()
        else -> null
    }

    override fun change(event: Event, delta: Array<out Any>?, mode: ChangeMode) {
        val amount = (delta?.firstOrNull() as? Number)?.toInt() ?: 0
        val entries = Tw.resolveAll(facts.getArray(event))
        for (player in players.getArray(event)) {
            for (entry in entries) {
                val current = Tw.readFact(entry, player) ?: 0
                val updated = when (mode) {
                    ChangeMode.SET -> amount
                    ChangeMode.ADD -> current + amount
                    ChangeMode.REMOVE -> current - amount
                    ChangeMode.DELETE, ChangeMode.RESET -> 0
                    else -> return
                }
                Tw.writeFact(entry, player, updated)
            }
        }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "typewriter fact ${facts.toString(event, debug)} of ${players.toString(event, debug)}"
}

@Name("Typewriter Fact Last Update")
@Description(
    "When a Typewriter fact was last written for a player.",
    "Timed and session facts use this internally to decide when they expire.",
)
@Examples(
    "if typewriter fact last update of \"daily_reward\" of player is less than 1 day ago:",
    "\tsend \"Come back tomorrow.\"",
)
@RequiredPlugins("Typewriter")
@Since("1.0.0")
class ExprFactLastUpdate : SimpleExpression<Date>() {

    private lateinit var facts: Expression<*>
    private lateinit var players: Expression<Player>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        facts = exprs[0]
        players = exprs[1] as Expression<Player>
        return true
    }

    override fun get(event: Event): Array<Date> {
        val entries = Tw.resolveAll(facts.getArray(event))
        val dates = ArrayList<Date>()
        for (player in players.getArray(event)) {
            for (entry in entries) {
                val data = Tw.factData(entry, player) ?: continue
                val millis = data.lastUpdate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                dates += Date(millis)
            }
        }
        return dates.toTypedArray()
    }

    override fun isSingle(): Boolean = facts.isSingle && players.isSingle

    override fun getReturnType(): Class<out Date> = Date::class.java

    override fun toString(event: Event?, debug: Boolean): String =
        "typewriter fact last update of ${facts.toString(event, debug)} of ${players.toString(event, debug)}"
}
