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
import ch.njol.skript.registrations.EventValues
import ch.njol.skript.util.Timespan
import ch.njol.util.Kleenean
import me.mhfs.sktyper.SkTyper
import me.mhfs.sktyper.authoring.Authoring
import me.mhfs.sktyper.tw.Tw
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Event

@Name("Create Typewriter Cinematic")
@Description(
    "Builds a cinematic page with a camera path running through the given locations.",
    "Each location becomes a path point and keeps its yaw and pitch, so recording a shot is just a " +
        "matter of collecting the player's location into a list as they move around.",
    "The page is created in staging. Run `publish typewriter pages` to make it playable, then start " +
        "it like any other cinematic.",
    "Does nothing if fewer than two points are given or a page with that name already exists.",
)
@Examples(
    "add location of player to {shot::*}",
    "create typewriter cinematic \"tower_flyby\" along {shot::*} over 8 seconds",
    "publish typewriter pages",
    "start typewriter cinematic \"tower_flyby\" for player",
)
@RequiredPlugins("Typewriter")
@Since("1.0.1")
class EffCreateCinematic : Effect() {

    private lateinit var name: Expression<String>
    private lateinit var points: Expression<Location>
    private var duration: Expression<Timespan>? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        name = exprs[0] as Expression<String>
        points = exprs[1] as Expression<Location>
        duration = exprs.getOrNull(2) as? Expression<Timespan>
        return true
    }

    override fun execute(event: Event) {
        val name = this.name.getSingle(event) ?: return
        val points = this.points.getArray(event).toList()
        if (points.size < 2) {
            warn("needs at least two points, got ${points.size}")
            return
        }

        val ticks = duration?.getSingle(event)?.getAs(Timespan.TimePeriod.TICK)?.toInt() ?: (points.size * 20)
        val page = Authoring.createCameraCinematic(name, points, ticks)
        if (page == null) {
            warn("could not create \"$name\" - the page may already exist, or Typewriter refused the write")
        }
    }

    private fun warn(message: String) {
        SkTyper.instance.logger.warning("create typewriter cinematic: $message")
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "create typewriter cinematic ${name.toString(event, debug)} along ${points.toString(event, debug)}"
}

@Name("Create Typewriter Entity Instance")
@Description(
    "Places an instance of an existing entity definition at a location, on a manifest page.",
    "The definition has to exist already - definitions carry the skin, entity data and activities, " +
        "which are far easier to build in the Typewriter panel than to describe from a script.",
    "Created in staging, so run `publish typewriter pages` afterwards. Players see the entity once " +
        "they are in its audience.",
)
@Examples(
    "create typewriter entity \"gate_guard\" of \"town_guard_definition\" at location of player",
    "publish typewriter pages",
    "add player to the typewriter audience of \"gate_guard\"",
)
@RequiredPlugins("Typewriter", "Typewriter Entity extension")
@Since("1.0.1")
class EffCreateEntity : Effect() {

    private lateinit var name: Expression<String>
    private lateinit var definition: Expression<*>
    private lateinit var location: Expression<Location>
    private var page: Expression<String>? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        name = exprs[0] as Expression<String>
        definition = exprs[1]
        location = exprs[2] as Expression<Location>
        page = exprs.getOrNull(3) as? Expression<String>
        return true
    }

    override fun execute(event: Event) {
        val name = this.name.getSingle(event) ?: return
        val location = this.location.getSingle(event) ?: return

        val raw = definition.getSingle(event)
        val definitionId = Tw.resolve(raw)?.id ?: (raw as? String)
        if (definitionId.isNullOrBlank()) {
            SkTyper.instance.logger.warning("create typewriter entity: no definition given for \"$name\"")
            return
        }

        val pageName = page?.getSingle(event) ?: "sktyper_entities"
        val created = Authoring.createEntityInstance(
            name,
            definitionId,
            location,
            pageName,
            "shared_advanced_entity_instance",
        )
        if (created == null) {
            SkTyper.instance.logger.warning("create typewriter entity: could not create \"$name\"")
        }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "create typewriter entity ${name.toString(event, debug)} of ${definition.toString(event, debug)}"
}

@Name("Publish Typewriter Pages")
@Description(
    "Publishes everything currently in Typewriter's staging area, which is what makes pages created " +
        "from a script actually load.",
    "This writes the whole staging area over the live pages directory and removes published pages " +
        "that staging no longer has, exactly like pressing publish in the panel. Anything a builder " +
        "left half-finished in staging goes live too, so do not run it on a timer.",
    "Publishing is asynchronous. The result is reported to whoever ran the effect.",
)
@Examples(
    "create typewriter cinematic \"tower_flyby\" along {shot::*}",
    "publish typewriter pages",
)
@RequiredPlugins("Typewriter")
@Since("1.0.1")
class EffPublishPages : Effect() {

    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean = true

    override fun execute(event: Event) {
        val player = runCatching {
            EventValues.getEventValue(event, Player::class.java, EventValues.TIME_NOW)
        }.getOrNull()

        Authoring.publish { ok, message ->
            val text = if (ok) "Typewriter pages published." else "Typewriter publish failed: $message"
            SkTyper.instance.server.scheduler.runTask(SkTyper.instance, Runnable {
                if (player != null && player.isOnline) player.sendMessage(text)
                SkTyper.instance.logger.info(text)
            })
        }
    }

    override fun toString(event: Event?, debug: Boolean): String = "publish typewriter pages"
}

@Name("Delete Typewriter Page")
@Description(
    "Removes a page from Typewriter's staging area. Takes effect on the next publish.",
)
@Examples("delete typewriter page \"tower_flyby\"")
@RequiredPlugins("Typewriter")
@Since("1.0.1")
class EffDeletePage : Effect() {

    private lateinit var page: Expression<String>

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
    ): Boolean {
        page = exprs[0] as Expression<String>
        return true
    }

    override fun execute(event: Event) {
        page.getArray(event).forEach { Authoring.deletePage(it) }
    }

    override fun toString(event: Event?, debug: Boolean): String =
        "delete typewriter page ${page.toString(event, debug)}"
}
