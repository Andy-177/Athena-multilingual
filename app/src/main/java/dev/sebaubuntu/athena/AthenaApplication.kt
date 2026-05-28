/*
 * SPDX-FileCopyrightText: Sebastiano Barezzi
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.sebaubuntu.athena

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.google.android.material.color.DynamicColors
import dev.sebaubuntu.athena.models.Preference
import dev.sebaubuntu.athena.repositories.PreferencesRepository
import dev.sebaubuntu.athena.utils.ModulesManager
import dev.sebaubuntu.athena.utils.PreferencesManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.runBlocking
import java.util.Locale

class AthenaApplication : Application() {
    val coroutineScope = MainScope()

    val modulesManager by lazy { ModulesManager(this) }
    val preferencesManager by lazy { PreferencesManager.get(this) }

    // Repositories
    val preferencesRepository by lazy { PreferencesRepository(preferencesManager, coroutineScope) }

    override fun onCreate() {
        super.onCreate()

        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    override fun attachBaseContext(base: Context) {
        val languageTag = try {
            runBlocking {
                preferencesManager.getValue(
                    Preference.Companion.primitivePreference("language", "system")
                )
            }
        } catch (e: Exception) {
            "system"
        }

        savedLanguageTag = languageTag

        super.attachBaseContext(
            wrapContextWithLocale(base, languageTag)
        )
    }

    companion object {
        @Volatile
        var savedLanguageTag: String = "system"

        fun wrapContextWithLocale(context: Context, languageTag: String): Context {
            if (languageTag == "system") return context

            val config = Configuration(context.resources.configuration)
            config.setLocale(localeFromTag(languageTag))
            return context.createConfigurationContext(config)
        }

        private fun localeFromTag(tag: String): Locale {
            return when (tag) {
                "id" -> Locale("in", "ID")
                "he" -> Locale("iw", "IL")
                "zh-CN" -> Locale("zh", "CN")
                "zh-TW" -> Locale("zh", "TW")
                "pt-BR" -> Locale("pt", "BR")
                "pt-PT" -> Locale("pt", "PT")
                else -> Locale(tag)
            }
        }
    }
}
