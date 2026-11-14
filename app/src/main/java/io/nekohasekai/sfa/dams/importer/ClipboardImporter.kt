package io.nekohasekai.sfa.dams.importer

import android.content.Context
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.bg.UpdateProfileWork
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

/**
 * Creates profiles from clipboard content. The original text is never logged or stored,
 * only the resulting sing-box configuration is written to the profile directory.
 */
class ClipboardImporter(private val context: Context) {
    data class Imported(
        val profiles: List<Profile>,
        val skipped: Int,
        val failures: List<String>,
        val subscriptionUrl: String?,
    )

    sealed class Outcome {
        data class Success(val result: Imported) : Outcome()

        data class Error(val message: String) : Outcome()
    }

    suspend fun importText(text: String): Outcome = withContext(Dispatchers.IO) {
        try {
            val entries = Importer.parse(text)
            val result = importEntries(entries)
            when {
                result.profiles.isNotEmpty() || result.subscriptionUrl != null || result.skipped > 0 ->
                    Outcome.Success(result)
                result.failures.isNotEmpty() -> Outcome.Error(result.failures.joinToString("\n"))
                else -> Outcome.Error("No importable content found")
            }
        } catch (e: Exception) {
            Outcome.Error(e.message ?: "Import failed")
        }
    }

    /**
     * Downloads a subscription. When [asRemote] is true the URL is stored as an
     * auto-updating remote profile, otherwise its content is expanded into local profiles.
     */
    suspend fun importSubscription(url: String, asRemote: Boolean): Outcome =
        withContext(Dispatchers.IO) {
            val content = try {
                HTTPClient().use { it.getString(url) }
            } catch (e: Exception) {
                return@withContext Outcome.Error(e.message ?: "Download failed")
            }
            try {
                if (asRemote) {
                    Libbox.checkConfig(content)
                    val profile = createRemoteProfile(nameFromUrl(url), url, content)
                    Outcome.Success(Imported(listOf(profile), 0, emptyList(), null))
                } else {
                    Outcome.Success(importEntries(Importer.parse(content)))
                }
            } catch (e: Exception) {
                Outcome.Error(e.message ?: "Invalid subscription")
            }
        }

    private suspend fun importEntries(entries: List<Importer.Entry>): Imported {
        val existingKeys = existingDedupKeys().toMutableSet()
        val usedNames = ProfileManager.list().mapTo(mutableSetOf<String>()) { it.name }
        val imported = mutableListOf<Profile>()
        val failures = mutableListOf<String>()
        var skipped = 0
        var subscriptionUrl: String? = null

        for (entry in entries) {
            when (entry) {
                is Importer.Entry.Invalid -> failures.add(entry.reason)
                is Importer.Entry.Subscription -> {
                    if (subscriptionUrl == null) {
                        subscriptionUrl = entry.url
                    } else {
                        failures.add("Only one subscription URL per import is supported")
                    }
                }
                is Importer.Entry.Link -> {
                    val profile = entry.profile
                    if (profile.dedupKey in existingKeys) {
                        skipped++
                        continue
                    }
                    val config = Importer.buildConfig(profile)
                    try {
                        Libbox.checkConfig(config)
                    } catch (e: Exception) {
                        failures.add("${profile.displayName}: ${e.message}")
                        continue
                    }
                    val name = uniqueName(profile.profileName, usedNames)
                    imported.add(createLocalProfile(name, config, imported.isEmpty()))
                    existingKeys.add(profile.dedupKey)
                }
                is Importer.Entry.Config -> {
                    val key = entry.dedupKey
                    if (key != null && key in existingKeys) {
                        skipped++
                        continue
                    }
                    try {
                        Libbox.checkConfig(entry.content)
                    } catch (e: Exception) {
                        failures.add("${entry.name}: ${e.message}")
                        continue
                    }
                    val name = uniqueName(entry.name, usedNames)
                    imported.add(createLocalProfile(name, entry.content, imported.isEmpty()))
                    if (key != null) existingKeys.add(key)
                }
            }
        }

        return Imported(imported, skipped, failures, subscriptionUrl)
    }

    private suspend fun createLocalProfile(name: String, config: String, select: Boolean): Profile {
        val typedProfile = TypedProfile().apply {
            type = TypedProfile.Type.Local
        }
        val profile = Profile(name = name, typed = typedProfile).apply {
            userOrder = ProfileManager.nextOrder()
        }
        val configFile = nextConfigFile()
        configFile.writeText(config)
        typedProfile.path = configFile.path
        ProfileManager.create(profile, andSelect = select)
        return profile
    }

    private suspend fun createRemoteProfile(name: String, url: String, content: String): Profile {
        val typedProfile = TypedProfile().apply {
            type = TypedProfile.Type.Remote
            remoteURL = url
            autoUpdate = true
            autoUpdateInterval = 60
            lastUpdated = Date()
        }
        val profile = Profile(name = name, typed = typedProfile).apply {
            userOrder = ProfileManager.nextOrder()
        }
        val configFile = nextConfigFile()
        configFile.writeText(content)
        typedProfile.path = configFile.path
        ProfileManager.create(profile, andSelect = true)
        UpdateProfileWork.reconfigureUpdater()
        return profile
    }

    private suspend fun nextConfigFile(): File {
        val directory = File(context.filesDir, "configs").also { it.mkdirs() }
        return File(directory, "${ProfileManager.nextFileID()}.json")
    }

    private suspend fun existingDedupKeys(): Set<String> {
        return ProfileManager.list().mapNotNullTo(mutableSetOf()) { profile ->
            if (profile.typed.type != TypedProfile.Type.Local) return@mapNotNullTo null
            runCatching {
                Importer.dedupKeyOfConfig(File(profile.typed.path).readText())
            }.getOrNull()
        }
    }

    private fun uniqueName(base: String, used: MutableSet<String>): String {
        val candidate = base.ifBlank { "Profile" }
        if (used.add(candidate)) return candidate
        var index = 2
        while (true) {
            val next = "$candidate ($index)"
            if (used.add(next)) return next
            index++
        }
    }

    private fun nameFromUrl(url: String): String {
        val tail = url.substringBefore('?')
            .substringBefore('#')
            .trimEnd('/')
            .substringAfterLast('/')
        return tail.substringBeforeLast('.').ifBlank { "Subscription" }
    }
}
