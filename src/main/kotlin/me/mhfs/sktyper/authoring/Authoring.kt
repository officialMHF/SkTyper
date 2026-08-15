package me.mhfs.sktyper.authoring

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.typewritermc.core.utils.UntickedAsync
import com.typewritermc.core.utils.launch
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.typewritermc.engine.paper.entry.AssetStorage
import com.typewritermc.engine.paper.entry.StagingManager
import com.typewritermc.engine.paper.entry.StagingState
import kotlinx.coroutines.Dispatchers
import me.mhfs.sktyper.tw.Tw
import org.bukkit.Location
import org.koin.java.KoinJavaComponent
import java.util.UUID

/**
 * Writes Typewriter pages and entries through the same StagingManager the web panel uses, so
 * anything built from a script shows up in the panel and can be edited there afterwards.
 *
 * Nothing here publishes on its own. Publishing rewrites the live pages directory from staging and
 * removes published pages that staging no longer has, which is not something a script should do as
 * a side effect of creating a cinematic.
 */
object Authoring {

    const val CINEMATIC_PAGE = "cinematic"
    const val MANIFEST_PAGE = "manifest"

    private fun staging(): StagingManager? =
        runCatching { KoinJavaComponent.get<StagingManager>(StagingManager::class.java) }.getOrNull()

    val stagingState: String?
        get() = runCatching { staging()?.stagingState?.name?.lowercase() }.getOrNull()

    val hasUnpublishedChanges: Boolean
        get() = runCatching { staging()?.stagingState == StagingState.STAGING }.getOrElse { false }

    fun pageExists(id: String): Boolean =
        runCatching { staging()?.pages?.containsKey(id) == true }.getOrElse { false }

    /**
     * Removes a staged entry by its name or id, wherever it lives.
     *
     * Page ids and page names can differ, so this walks staging itself rather than going through
     * findEntryPage, which answers with the name.
     */
    fun deleteEntry(nameOrId: String): Boolean {
        val manager = staging() ?: return false
        val pages = runCatching { manager.pages }.getOrNull() ?: return false
        for ((pageId, page) in pages) {
            val entries = runCatching { page.getAsJsonArray("entries") }.getOrNull() ?: continue
            val match = entries.firstOrNull { element ->
                val entry = runCatching { element.asJsonObject }.getOrNull() ?: return@firstOrNull false
                entry.get("id")?.asString == nameOrId || entry.get("name")?.asString == nameOrId
            } ?: continue
            val entryId = match.asJsonObject.get("id")?.asString ?: continue
            return runCatching { manager.deleteEntry(pageId, entryId).isSuccess }.getOrElse { false }
        }
        return false
    }

    /** Locates a staged entry by name or id, with the page it sits on. */
    private fun locate(nameOrId: String): Pair<String, String>? {
        val pages = runCatching { staging()?.pages }.getOrNull() ?: return null
        for ((pageId, page) in pages) {
            val entries = runCatching { page.getAsJsonArray("entries") }.getOrNull() ?: continue
            for (element in entries) {
                val entry = runCatching { element.asJsonObject }.getOrNull() ?: continue
                val id = entry.get("id")?.asString ?: continue
                if (id == nameOrId || entry.get("name")?.asString == nameOrId) return pageId to id
            }
        }
        return null
    }

    /** Reads a staged instance's spawn point, so a teleport shows up without waiting for a publish. */
    fun spawnOf(nameOrId: String): Location? {
        val pages = runCatching { staging()?.pages }.getOrNull() ?: return null
        for ((_, page) in pages) {
            val entries = runCatching { page.getAsJsonArray("entries") }.getOrNull() ?: continue
            for (element in entries) {
                val entry = runCatching { element.asJsonObject }.getOrNull() ?: continue
                val id = entry.get("id")?.asString ?: continue
                if (id != nameOrId && entry.get("name")?.asString != nameOrId) continue
                val spawn = entry.getAsJsonObject("spawnLocation") ?: return null
                val world = runCatching {
                    org.bukkit.Bukkit.getWorld(java.util.UUID.fromString(spawn.get("world").asString))
                }.getOrNull() ?: return null
                return Location(
                    world,
                    spawn.get("x")?.asDouble ?: 0.0,
                    spawn.get("y")?.asDouble ?: 0.0,
                    spawn.get("z")?.asDouble ?: 0.0,
                    spawn.get("yaw")?.asFloat ?: 0f,
                    spawn.get("pitch")?.asFloat ?: 0f,
                )
            }
        }
        return null
    }

    /** The blueprint a staged entry was built from, used to keep skins off instances. */
    fun blueprintOf(nameOrId: String): String? {
        val pages = runCatching { staging()?.pages }.getOrNull() ?: return null
        for ((_, page) in pages) {
            val entries = runCatching { page.getAsJsonArray("entries") }.getOrNull() ?: continue
            for (element in entries) {
                val entry = runCatching { element.asJsonObject }.getOrNull() ?: continue
                val id = entry.get("id")?.asString ?: continue
                if (id == nameOrId || entry.get("name")?.asString == nameOrId) {
                    return entry.get("blueprintId")?.asString
                }
            }
        }
        return null
    }

    fun isDefinition(nameOrId: String): Boolean =
        blueprintOf(nameOrId)?.contains("definition") == true

    fun updateField(nameOrId: String, path: String, value: JsonElement): Boolean {
        val manager = staging() ?: return false
        val (pageId, entryId) = locate(nameOrId) ?: return false
        return runCatching { manager.updateEntryField(pageId, entryId, path, value).isSuccess }
            .getOrElse { false }
    }

    fun teleport(nameOrId: String, location: Location): Boolean =
        updateField(nameOrId, "spawnLocation", position(location))

    fun setSkin(nameOrId: String, texture: String, signature: String): Boolean =
        updateField(nameOrId, "skin", skinJson(texture, signature))

    fun setDisplayName(nameOrId: String, display: String): Boolean =
        updateField(nameOrId, "displayName", JsonPrimitive(display))

    fun setActivity(nameOrId: String, activityEntryId: String): Boolean =
        updateField(nameOrId, "activity", JsonPrimitive(activityEntryId))

    /** Writes an artifact payload to disk; the entry owning it need not be published yet. */
    private fun storeArtifact(artifactId: String, json: String): Boolean {
        val storage = runCatching { KoinJavaComponent.get<AssetStorage>(AssetStorage::class.java) }
            .getOrNull() ?: return false
        return runCatching {
            Dispatchers.UntickedAsync.launch {
                runCatching { storage.storeStringAsset("artifacts/$artifactId.json", json) }
            }
        }.isSuccess
    }

    /** Creates an NPC definition. Texture and signature come from a Mojang profile lookup. */
    fun createNpcDefinition(
        name: String,
        displayName: String,
        texture: String,
        signature: String,
        pageName: String,
    ): String? {
        if (!Tw.isAvailable) return null
        val pageId = slug(pageName) ?: return null
        if (!createPage(pageId, pageName, MANIFEST_PAGE)) return null

        val id = entryId()
        val entry = JsonObject().apply {
            addProperty("id", id)
            addProperty("blueprintId", "npc_definition")
            addProperty("name", name)
            addProperty("displayName", displayName)
            add("sound", emptySound())
            add("skin", skinJson(texture, signature))
            add("data", JsonArray())
        }
        return if (createEntry(pageId, entry)) id else null
    }

    /**
     * Builds a road network through the given points and a patrol activity that walks it.
     *
     * Patrol activities navigate a road network rather than raw locations, so the network has to
     * exist first. Nodes are chained in order and the walk loops back to the start.
     */
    fun createPatrolActivity(name: String, points: List<Location>, pageName: String): String? {
        if (points.size < 2) return null
        if (!Tw.isAvailable) return null
        val pageId = slug(pageName) ?: return null
        if (!createPage(pageId, pageName, MANIFEST_PAGE)) return null

        // RoadNodeId is an inline value class, so Gson reads and writes it as a bare int.
        val nodes = JsonArray()
        val edges = JsonArray()
        points.forEachIndexed { index, point ->
            nodes.add(JsonObject().apply {
                addProperty("id", index)
                add("location", position(point))
                addProperty("radius", 1.0)
            })
            val next = (index + 1) % points.size
            val raw = runCatching { point.distance(points[next]) }.getOrElse { 1.0 }
            val length = if (raw.isFinite() && raw > 0.0) raw else 1.0
            edges.add(JsonObject().apply {
                addProperty("start", index)
                addProperty("end", next)
                addProperty("weight", length)
                addProperty("length", length)
            })
        }

        val artifactId = entryId()
        val network = JsonObject().apply {
            add("nodes", nodes)
            add("edges", edges)
            add("modifications", JsonArray())
            add("negativeNodes", JsonArray())
        }
        if (!storeArtifact(artifactId, network.toString())) return null

        val networkId = entryId()
        val networkEntry = JsonObject().apply {
            addProperty("id", networkId)
            addProperty("blueprintId", "base_road_network")
            addProperty("name", name + "_network")
            addProperty("artifactId", artifactId)
        }
        if (!createEntry(pageId, networkEntry)) return null

        val activityId = entryId()
        val activity = JsonObject().apply {
            addProperty("id", activityId)
            addProperty("blueprintId", "patrol_activity")
            addProperty("name", name + "_patrol")
            addProperty("roadNetwork", networkId)
            add("nodes", JsonArray().apply {
                points.indices.forEach { index -> add(index) }
            })
        }
        return if (createEntry(pageId, activity)) activityId else null
    }

    /**
     * Creates an entity cinematic that walks a definition along the given points.
     *
     * Typewriter records these in game and stores them as a tape of frames, so a path can be laid
     * down by spreading the points evenly across the frame range and writing the tape directly.
     */
    fun createEntityCinematic(
        name: String,
        definitionId: String,
        points: List<Location>,
        frames: Int,
    ): String? {
        if (points.size < 2) return null
        if (!Tw.isAvailable) return null
        if (definitionId.isBlank()) return null

        val pageId = slug(name) ?: return null
        if (pageExists(pageId)) return null
        if (!createPage(pageId, name, CINEMATIC_PAGE)) return null

        val total = frames.coerceAtLeast(10)
        val step = total.toDouble() / (points.size - 1).toDouble()
        val tape = JsonObject()
        points.forEachIndexed { index, point ->
            val frame = (index * step).toInt().coerceIn(0, total)
            tape.add(frame.toString(), JsonObject().apply { add("location", coordinate(point)) })
        }

        val artifactId = entryId()
        val payload = JsonObject().apply { add("default", tape) }
        if (!storeArtifact(artifactId, payload.toString())) return null

        val artifactEntryId = entryId()
        val artifactEntry = JsonObject().apply {
            addProperty("id", artifactEntryId)
            addProperty("blueprintId", "entity_cinematic_artifact")
            addProperty("name", name + "_artifact")
            addProperty("artifactId", artifactId)
        }
        if (!createEntry(pageId, artifactEntry)) {
            deletePage(pageId)
            return null
        }

        val id = entryId()
        val entry = JsonObject().apply {
            addProperty("id", id)
            addProperty("blueprintId", "entity_cinematic")
            addProperty("name", name + "_entity")
            add("criteria", JsonArray())
            addProperty("definition", definitionId)
            add("segments", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("startFrame", 0)
                    addProperty("endFrame", total)
                    addProperty("artifact", artifactEntryId)
                })
            })
        }
        if (!createEntry(pageId, entry)) {
            deletePage(pageId)
            return null
        }
        return pageId
    }

    fun deletePage(id: String): Boolean {
        val manager = staging() ?: return false
        return runCatching { manager.deletePage(id).isSuccess }.getOrElse { false }
    }

    fun publish(onDone: (Boolean, String) -> Unit) {
        val manager = staging()
        if (manager == null) {
            onDone(false, "Typewriter is not available")
            return
        }
        Dispatchers.UntickedAsync.launch {
            val call = runCatching { manager.publish() }
            val published = call.getOrNull()
            val ok = published?.isSuccess == true
            // The reason lives on the Result that publish returned, not on the runCatching around
            // the call. Reading the wrong one turns "Can only publish when in staging" into noise.
            val message = published?.getOrNull()
                ?: published?.exceptionOrNull()?.message
                ?: call.exceptionOrNull()?.message
                ?: "Publish failed"
            onDone(ok, message)
        }
    }

    private fun page(id: String, name: String, type: String): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("name", name)
        addProperty("type", type)
        addProperty("priority", 0)
        add("entries", JsonArray())
    }

    private fun createPage(id: String, name: String, type: String): Boolean {
        val manager = staging() ?: return false
        if (pageExists(id)) return true
        return runCatching { manager.createPage(page(id, name, type)).isSuccess }.getOrElse { false }
    }

    private fun createEntry(pageId: String, entry: JsonObject): Boolean {
        val manager = staging() ?: return false
        return runCatching { manager.createEntry(pageId, entry).isSuccess }.getOrElse { false }
    }

    private fun entryId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    private fun position(location: Location): JsonObject = JsonObject().apply {
        addProperty("world", location.world?.uid?.toString() ?: "")
        addProperty("x", location.x)
        addProperty("y", location.y)
        addProperty("z", location.z)
        addProperty("yaw", location.yaw)
        addProperty("pitch", location.pitch)
    }

    private fun coordinate(location: Location): JsonObject = JsonObject().apply {
        addProperty("x", location.x)
        addProperty("y", location.y)
        addProperty("z", location.z)
        addProperty("yaw", location.yaw)
        addProperty("pitch", location.pitch)
    }

    private fun emptySound(): JsonObject = JsonObject().apply {
        add("soundId", JsonObject().apply {
            addProperty("type", "default")
            addProperty("value", "null")
        })
        add("soundSource", JsonObject().apply { addProperty("type", "self") })
        addProperty("track", "MASTER")
        addProperty("volume", 1.0f)
        addProperty("pitch", 1.0f)
    }

    private fun skinJson(texture: String, signature: String): JsonObject = JsonObject().apply {
        addProperty("texture", texture)
        addProperty("signature", signature)
    }

    private fun slug(name: String): String? =
        name.lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').takeIf { it.isNotEmpty() }

    private fun optional(value: Int?): JsonObject = JsonObject().apply {
        addProperty("enabled", value != null)
        if (value != null) addProperty("value", value)
    }

    /**
     * Builds a cinematic page holding a single camera path.
     *
     * Each location becomes a path point, keeping its yaw and pitch, so a script can record points
     * by dropping the player's location into a list and hand that straight over.
     *
     * @param frames how long the whole path takes. Typewriter runs cinematics at twenty frames a
     *               second and spreads the time evenly over the points that have no duration set.
     * @return the page id, or null when the page already exists or Typewriter refused the write.
     */
    fun createCameraCinematic(name: String, points: List<Location>, frames: Int): String? {
        if (points.size < 2) return null
        if (!Tw.isAvailable) return null

        val pageId = slug(name) ?: return null
        if (pageExists(pageId)) return null
        if (!createPage(pageId, name, CINEMATIC_PAGE)) return null

        val path = JsonArray()
        points.forEach { point ->
            path.add(JsonObject().apply {
                add("location", position(point))
                add("duration", optional(null))
            })
        }

        val segment = JsonObject().apply {
            addProperty("startFrame", 0)
            addProperty("endFrame", frames.coerceAtLeast(10))
            add("path", path)
        }

        val entry = JsonObject().apply {
            addProperty("id", entryId())
            addProperty("blueprintId", "camera_cinematic")
            addProperty("name", "${name}_camera")
            add("criteria", JsonArray())
            add("segments", JsonArray().apply { add(segment) })
        }

        if (!createEntry(pageId, entry)) {
            deletePage(pageId)
            return null
        }
        return pageId
    }

    /**
     * Places an instance of an existing entity definition on a manifest page.
     *
     * The definition has to already exist - definitions carry skins, entity data and activities that
     * are worth building in the panel rather than guessing at from a script.
     *
     * @return the entry id of the new instance, or null when the write was refused.
     */
    fun createEntityInstance(
        name: String,
        definitionId: String,
        location: Location,
        pageName: String,
        blueprint: String,
    ): String? {
        if (!Tw.isAvailable) return null
        if (definitionId.isBlank()) return null

        val pageId = slug(pageName) ?: return null
        if (!createPage(pageId, pageName, MANIFEST_PAGE)) return null

        val id = entryId()
        val entry = JsonObject().apply {
            addProperty("id", id)
            addProperty("blueprintId", blueprint)
            addProperty("name", name)
            addProperty("definition", definitionId)
            add("spawnLocation", position(location))
            add("showRange", optional(null))
            add("children", JsonArray())
            addProperty("activity", "")
        }

        return if (createEntry(pageId, entry)) id else null
    }
}
