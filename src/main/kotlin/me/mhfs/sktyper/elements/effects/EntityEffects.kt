@file:Suppress("DEPRECATION")

package me.mhfs.sktyper.elements.effects

import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.RequiredPlugins
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Effect
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser.ParseResult
import ch.njol.skript.util.Timespan
import ch.njol.util.Kleenean
import me.mhfs.sktyper.SkTyper
import me.mhfs.sktyper.authoring.Authoring
import me.mhfs.sktyper.authoring.Skins
import me.mhfs.sktyper.tw.Tw
import org.bukkit.Location
import org.bukkit.event.Event


/**
 * Skins and display names live on the definition, not on the instances made from it. Writing to an
 * instance would quietly do nothing, so say so instead.
 */
private fun guardIsDefinition(event: Event, effect: String, name: String): Boolean {
    val blueprint = Authoring.blueprintOf(name)
    if (blueprint == null) {
        report(event, "$effect: nothing staged called \"$name\"")
        return false
    }
    if (!Authoring.isDefinition(name)) {
        report(
            event,
            "$effect: \"$name\" is a $blueprint, not a definition. " +
                "Skins and display names belong on the definition the NPC was made from.",
        )
        return false
    }
    return true
}

@Name("Create Typewriter Definition")
@Description(
    "Creates an NPC definition with a skin taken from a Minecraft account.",
    "The skin is fetched from Mojang, so the definition appears a moment after the effect runs " +
        "rather than immediately. Both the lookup and the result are reported.",
    "Definitions describe what an NPC is. Place one in the world with `create typewriter entity`.",
)
@Examples(
    "create typewriter definition \"guard\" with skin of \"Notch\" display name \"<red>Town Guard\"",
    "publish typewriter pages",
)
@RequiredPlugins("Typewriter", "Typewriter Entity extension")
@Since("1.0.5")
class EffCreateDefinition : Effect() {

    private lateinit var name: Expression<String>
    private lateinit var ign: Expression<String>
    private var display: Expression<String>? = null
    private var page: Expression<String>? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        name = exprs[0] as Expression<String>
        ign = exprs[1] as Expression<String>
        display = exprs.getOrNull(2) as? Expression<String>
        page = exprs.getOrNull(3) as? Expression<String>
        return true
    }

    override fun execute(event: Event) {
        val name = this.name.getSingle(event) ?: return
        val ign = this.ign.getSingle(event) ?: return
        val display = this.display?.getSingle(event) ?: name
        val pageName = page?.getSingle(event) ?: "sktyper_entities"

        Skins.lookup(ign) { skin, error ->
            if (skin == null) {
                report(event, "create typewriter definition: skin lookup for \"$ign\" failed - $error")
                return@lookup
            }
            val created = Authoring.createNpcDefinition(name, display, skin.texture, skin.signature, pageName)
            if (created == null) {
                report(event, "create typewriter definition: could not create \"$name\"")
            } else {
                SkTyper.instance.logger.info("Created NPC definition \"$name\" ($created).")
            }
        }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "create typewriter definition ${name.toString(event, debug)} with skin of ${ign.toString(event, debug)}"
}

@Name("Set Typewriter Skin")
@Description(
    "Replaces the skin on an NPC definition with the one belonging to a Minecraft account.",
    "Fetched from Mojang, so it applies a moment later. Publish afterwards.",
)
@Examples("set typewriter skin of \"guard\" to \"Notch\"", "publish typewriter pages")
@RequiredPlugins("Typewriter", "Typewriter Entity extension")
@Since("1.0.5")
class EffSetSkin : Effect() {

    private lateinit var definition: Expression<String>
    private lateinit var ign: Expression<String>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        definition = exprs[0] as Expression<String>
        ign = exprs[1] as Expression<String>
        return true
    }

    override fun execute(event: Event) {
        val definition = this.definition.getSingle(event) ?: return
        val ign = this.ign.getSingle(event) ?: return

        if (!guardIsDefinition(event, "set typewriter skin", definition)) return

        Skins.lookup(ign) { skin, error ->
            if (skin == null) {
                report(event, "set typewriter skin: lookup for \"$ign\" failed - $error")
                return@lookup
            }
            if (!Authoring.setSkin(definition, skin.texture, skin.signature)) {
                report(event, "set typewriter skin: could not write to \"$definition\"")
            }
        }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "set typewriter skin of ${definition.toString(event, debug)} to ${ign.toString(event, debug)}"
}

@Name("Set Typewriter Display Name")
@Description("Changes the display name on an NPC definition. Publish afterwards.")
@Examples("set typewriter display name of \"guard\" to \"<red>Captain\"")
@RequiredPlugins("Typewriter", "Typewriter Entity extension")
@Since("1.0.5")
class EffSetDisplayName : Effect() {

    private lateinit var definition: Expression<String>
    private lateinit var display: Expression<String>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        definition = exprs[0] as Expression<String>
        display = exprs[1] as Expression<String>
        return true
    }

    override fun execute(event: Event) {
        val definition = this.definition.getSingle(event) ?: return
        val display = this.display.getSingle(event) ?: return
        if (!guardIsDefinition(event, "set typewriter display name", definition)) return
        if (!Authoring.setDisplayName(definition, display)) {
            report(event, "set typewriter display name: could not write to \"$definition\"")
        }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "set typewriter display name of ${definition.toString(event, debug)}"
}

@Name("Teleport Typewriter Entity")
@Description(
    "Moves an NPC instance to a location by rewriting its spawn point. Publish afterwards.",
    "This is a permanent move, not a walk - the NPC reappears at the new spot when pages reload.",
)
@Examples("teleport typewriter entity \"gate_guard\" to location of player", "publish typewriter pages")
@RequiredPlugins("Typewriter", "Typewriter Entity extension")
@Since("1.0.5")
class EffTeleportEntity : Effect() {

    private lateinit var entity: Expression<String>
    private lateinit var location: Expression<Location>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        entity = exprs[0] as Expression<String>
        location = exprs[1] as Expression<Location>
        return true
    }

    override fun execute(event: Event) {
        val entity = this.entity.getSingle(event) ?: return
        val location = this.location.getSingle(event) ?: return
        if (!Authoring.teleport(entity, location)) {
            report(event, "teleport typewriter entity: no staged entry called \"$entity\"")
        }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "teleport typewriter entity ${entity.toString(event, debug)}"
}

@Name("Set Typewriter Activity")
@Description(
    "Gives an NPC a patrol route through the given locations.",
    "Typewriter walks NPCs along a road network rather than raw points, so this builds the network " +
        "and the patrol activity, then points the NPC at it. The route loops back to the first point.",
    "Publish afterwards.",
)
@Examples(
    "set typewriter activity of \"gate_guard\" to patrol along {route::*}",
    "publish typewriter pages",
)
@RequiredPlugins("Typewriter", "Typewriter Entity extension")
@Since("1.0.5")
class EffSetActivity : Effect() {

    private lateinit var entity: Expression<String>
    private lateinit var points: Expression<Location>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        entity = exprs[0] as Expression<String>
        points = exprs[1] as Expression<Location>
        return true
    }

    override fun execute(event: Event) {
        val entity = this.entity.getSingle(event) ?: return
        val points = this.points.getArray(event).toList()
        if (points.size < 2) {
            report(event, "set typewriter activity: a patrol needs at least two points, got ${points.size}")
            return
        }

        val activity = Authoring.createPatrolActivity(entity, points, "sktyper_entities")
        if (activity == null) {
            report(event, "set typewriter activity: could not build a patrol route for \"$entity\"")
            return
        }
        if (!Authoring.setActivity(entity, activity)) {
            report(event, "set typewriter activity: no staged entry called \"$entity\"")
        }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "set typewriter activity of ${entity.toString(event, debug)} to patrol"
}

@Name("Create Typewriter Entity Cinematic")
@Description(
    "Creates a cinematic in which an NPC walks the given path.",
    "Typewriter usually records these in game. This lays the path down directly, writing a frame " +
        "for every tick so the entity walks rather than jumping between the points you gave it.",
    "Pass `on page` to put the walk onto an existing cinematic page. Everything on one page plays " +
        "together, which is how you get an NPC walking through a camera shot.",
    "The length takes a timespan or a plain number of seconds, same as a camera cinematic. Publish " +
        "afterwards, then start it like any other cinematic.",
)
@Examples(
    "create typewriter entity cinematic \"guard_walk\" for \"guard\" along {path::*} over 10 seconds",
    "publish typewriter pages",
    "start typewriter cinematic \"guard_walk\" for player",
)
@RequiredPlugins("Typewriter", "Typewriter Entity extension")
@Since("1.0.5")
class EffCreateEntityCinematic : Effect() {

    private lateinit var name: Expression<String>
    private lateinit var definition: Expression<*>
    private lateinit var points: Expression<Location>
    private var duration: Expression<*>? = null
    private var page: Expression<String>? = null
    private var inTicks = false

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        name = exprs[0] as Expression<String>
        definition = exprs[1]
        points = exprs[2] as Expression<Location>
        duration = exprs.getOrNull(3)
        page = exprs.getOrNull(4) as? Expression<String>
        inTicks = parseResult.hasTag("tick")
        return true
    }

    override fun execute(event: Event) {
        val name = this.name.getSingle(event) ?: return
        val points = this.points.getArray(event).toList()
        if (points.size < 2) {
            report(event, "create typewriter entity cinematic: needs at least two points, got ${points.size}")
            return
        }

        val raw = definition.getSingle(event)
        val definitionId = Tw.resolve(raw)?.id ?: (raw as? String)
        if (definitionId.isNullOrBlank()) {
            report(event, "create typewriter entity cinematic: no definition given for \"$name\"")
            return
        }

        val ticks = when (val given = duration?.getSingle(event)) {
            is Timespan -> given.getAs(Timespan.TimePeriod.TICK).toInt()
            is Number -> if (inTicks) given.toInt() else given.toInt() * 20
            else -> points.size * 20
        }

        val target = page?.getSingle(event)
        if (Authoring.createEntityCinematic(name, definitionId, points, ticks, target) == null) {
            report(event, "create typewriter entity cinematic: could not create \"$name\"")
        }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "create typewriter entity cinematic ${name.toString(event, debug)}"
}
