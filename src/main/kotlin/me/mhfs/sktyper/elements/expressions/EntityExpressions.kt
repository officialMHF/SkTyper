package me.mhfs.sktyper.elements.expressions

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
import me.mhfs.sktyper.authoring.Authoring
import me.mhfs.sktyper.tw.Tw
import org.bukkit.Location
import org.bukkit.event.Event

@Name("All Typewriter Entities")
@Description(
    "Every NPC instance Typewriter has loaded, or every definition, depending on which you ask for.",
    "Instances are placements in the world; definitions are what those placements are made of.",
)
@Examples(
    "send \"%size of all typewriter entities% NPCs placed\"",
    "loop all typewriter definitions:",
    "\tsend \" - %the typewriter name of loop-value%\"",
)
@RequiredPlugins("Typewriter", "Typewriter Entity extension")
@Since("1.0.5")
class ExprAllEntities : SimpleExpression<Entry>() {

    private var definitions = false

    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        definitions = matchedPattern == 1
        return true
    }

    override fun get(event: Event): Array<Entry> =
        (if (definitions) Tw.allDefinitions() else Tw.allInstances()).toTypedArray()

    override fun isSingle(): Boolean = false

    override fun getReturnType(): Class<out Entry> = Entry::class.java

    override fun toString(event: Event?, debug: Boolean): String =
        if (definitions) "all typewriter definitions" else "all typewriter entities"
}

@Name("Typewriter Entity Spawn")
@Description(
    "Where an NPC instance is placed.",
    "Read from the staged page, so it reflects a teleport straight away rather than waiting for a " +
        "publish.",
)
@Examples("send \"%the typewriter spawn of \"\"gate_guard\"\"%\"")
@RequiredPlugins("Typewriter", "Typewriter Entity extension")
@Since("1.0.5")
class ExprSpawn : SimpleExpression<Location>() {

    private lateinit var entities: Expression<*>

    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        entities = exprs[0]
        return true
    }

    override fun get(event: Event): Array<Location> = entities.getArray(event)
        .mapNotNull { value ->
            val name = (value as? Entry)?.id ?: value as? String ?: return@mapNotNull null
            Authoring.spawnOf(name)
        }
        .toTypedArray()

    override fun isSingle(): Boolean = entities.isSingle

    override fun getReturnType(): Class<out Location> = Location::class.java

    override fun toString(event: Event?, debug: Boolean): String =
        "the typewriter spawn of ${entities.toString(event, debug)}"
}
