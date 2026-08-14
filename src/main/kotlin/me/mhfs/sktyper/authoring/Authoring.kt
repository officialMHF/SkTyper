package me.mhfs.sktyper.authoring

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.typewritermc.core.utils.UntickedAsync
import com.typewritermc.core.utils.launch
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
            val result = runCatching { manager.publish() }
            val ok = result.getOrNull()?.isSuccess == true
            val message = result.getOrNull()?.getOrNull()
                ?: result.exceptionOrNull()?.message
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

        val pageId = name.lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_')
        if (pageId.isEmpty() || pageExists(pageId)) return null
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

        val pageId = pageName.lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_')
        if (pageId.isEmpty()) return null
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
