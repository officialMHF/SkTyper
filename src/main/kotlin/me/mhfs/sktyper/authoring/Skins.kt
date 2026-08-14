package me.mhfs.sktyper.authoring

import com.google.gson.JsonParser
import me.mhfs.sktyper.SkTyper
import org.bukkit.Bukkit
import java.net.HttpURLConnection
import java.net.URI

data class Skin(val texture: String, val signature: String)

/**
 * Resolves a Minecraft skin from an in-game name.
 *
 * Two calls to Mojang: the name gives a uuid, the uuid gives the signed textures property that
 * Typewriter stores. Both are network calls, so everything here runs off the main thread and hands
 * the result back on it.
 */
object Skins {

    private const val UUID_ENDPOINT = "https://api.mojang.com/users/profiles/minecraft/"
    private const val PROFILE_ENDPOINT =
        "https://sessionserver.mojang.com/session/minecraft/profile/%s?unsigned=false"

    private val cache = HashMap<String, Skin>()

    fun lookup(ign: String, onDone: (Skin?, String?) -> Unit) {
        val key = ign.lowercase()
        cache[key]?.let {
            onDone(it, null)
            return
        }

        Bukkit.getScheduler().runTaskAsynchronously(SkTyper.instance, Runnable {
            val result = runCatching { fetch(ign) }
            val skin = result.getOrNull()
            val error = when {
                skin != null -> null
                result.isFailure -> result.exceptionOrNull()?.message ?: "lookup failed"
                else -> "no player called \"$ign\""
            }
            if (skin != null) cache[key] = skin

            Bukkit.getScheduler().runTask(SkTyper.instance, Runnable { onDone(skin, error) })
        })
    }

    private fun fetch(ign: String): Skin? {
        val uuid = get(UUID_ENDPOINT + ign)
            ?.let { JsonParser.parseString(it).asJsonObject.get("id")?.asString }
            ?: return null

        val profile = get(PROFILE_ENDPOINT.format(uuid)) ?: return null
        val properties = JsonParser.parseString(profile).asJsonObject.getAsJsonArray("properties")
        val textures = properties?.firstOrNull { it.asJsonObject.get("name")?.asString == "textures" }
            ?.asJsonObject ?: return null

        val value = textures.get("value")?.asString ?: return null
        val signature = textures.get("signature")?.asString ?: ""
        return Skin(value, signature)
    }

    private fun get(url: String): String? {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.setRequestProperty("User-Agent", "SkTyper")
        return try {
            if (connection.responseCode != 200) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
