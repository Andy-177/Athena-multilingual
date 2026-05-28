/*
 * SPDX-FileCopyrightText: Sebastiano Barezzi
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.sebaubuntu.athena.ui.screens

import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sebaubuntu.athena.AthenaApplication
import dev.sebaubuntu.athena.R
import dev.sebaubuntu.athena.core.models.Result
import dev.sebaubuntu.athena.models.Theme
import dev.sebaubuntu.athena.ui.LocalPermissionsManager
import dev.sebaubuntu.athena.ui.LocalSnackbarHostState
import dev.sebaubuntu.athena.ui.composables.EnumPreferenceListItem
import dev.sebaubuntu.athena.ui.composables.PreferenceCategoryCard
import dev.sebaubuntu.athena.ui.composables.PreferenceListItem
import dev.sebaubuntu.athena.ui.composables.SwitchPreferenceListItem
import dev.sebaubuntu.athena.viewmodels.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val languageTags = listOf(
    "system", "af", "ar", "ca", "cs", "da", "de", "el", "en", "eo", "es",
    "fa", "fi", "fr", "hi", "hu", "id", "it", "he", "ja", "ko", "nl", "no",
    "pl", "pt-BR", "pt-PT", "ro", "ru", "sr", "sv", "ta", "tr", "uk", "vi",
    "zh-CN", "zh-TW"
)

private fun languageDisplayName(tag: String): String {
    if (tag == "system") return "System default"
    val locale = when (tag) {
        "id" -> Locale("in", "ID")
        "he" -> Locale("iw", "IL")
        "zh-CN" -> Locale("zh", "CN")
        "zh-TW" -> Locale("zh", "TW")
        "pt-BR" -> Locale("pt", "BR")
        "pt-PT" -> Locale("pt", "PT")
        else -> Locale(tag)
    }
    return locale.getDisplayName(locale)
}

/**
 * App settings screen.
 */
@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
) {
    val permissionsManager = LocalPermissionsManager.current

    val settingsViewModel = viewModel {
        SettingsViewModel(
            application = get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY)!!,
            permissionsManager = permissionsManager,
        )
    }

    val supportsDynamicColors = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = paddingValues,
    ) {
        // General
        item {
            PreferenceCategoryCard(
                titleStringResId = R.string.settings_general,
            ) {
                LanguagePreferenceListItem(
                    preferenceHolder = settingsViewModel.language,
                    onPreferenceChange = settingsViewModel::setPreferenceValue,
                )

                EnumPreferenceListItem(
                    preferenceHolder = settingsViewModel.theme,
                    onPreferenceChange = settingsViewModel::setPreferenceValue,
                    titleStringResId = R.string.theme,
                    valueToDescriptionStringResId = {
                        when (it) {
                            Theme.LIGHT -> R.string.theme_light
                            Theme.DARK -> R.string.theme_dark
                            Theme.SYSTEM -> R.string.theme_system
                        }
                    }
                )

                if (supportsDynamicColors) {
                    SwitchPreferenceListItem(
                        preferenceHolder = settingsViewModel.dynamicColors,
                        onPreferenceChange = settingsViewModel::setPreferenceValue,
                        titleStringResId = R.string.dynamic_colors,
                        descriptionStringResId = R.string.dynamic_colors_description,
                    )
                }
            }
        }

        // Export data
        item {
            ExportDataCard(
                settingsViewModel = settingsViewModel,
            )
        }

        // About
        item {
            AboutCard()
        }
    }
}

@Composable
private fun LanguagePreferenceListItem(
    preferenceHolder: dev.sebaubuntu.athena.repositories.PreferencesRepository.PreferenceHolder<String>,
    onPreferenceChange: (dev.sebaubuntu.athena.repositories.PreferencesRepository.PreferenceHolder<String>, String) -> Unit,
) {
    val currentTag by preferenceHolder.collectAsStateWithLifecycle("system")
    var dialogOpened by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    if (dialogOpened) {
        var selectedTag by remember { mutableStateOf(currentTag) }

        // 使用 BasicAlertDialog 替代 AlertDialog 以支持滚动
        BasicAlertDialog(
            onDismissRequest = { dialogOpened = false }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 500.dp),
                shape = MaterialTheme.shapes.large,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 标题
                    Text(
                        text = "Language",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(20.dp)
                    )
                    
                    // 可滚动的语言列表
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                    ) {
                        items(languageTags) { tag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .selectable(
                                        selected = (tag == selectedTag),
                                        onClick = { selectedTag = tag },
                                        role = Role.RadioButton
                                    )
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = tag == selectedTag,
                                    onClick = null,
                                )
                                Text(
                                    text = languageDisplayName(tag),
                                    modifier = Modifier.padding(start = 16.dp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                    
                    // 按钮区域
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { dialogOpened = false }
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        TextButton(
                            onClick = {
                                if (selectedTag != currentTag) {
                                    scope.launch(Dispatchers.IO) {
                                        preferenceHolder.setValue(selectedTag)
                                        AthenaApplication.savedLanguageTag = selectedTag
                                        withContext(Dispatchers.Main) {
                                            (context as ComponentActivity).recreate()
                                        }
                                    }
                                }
                                dialogOpened = false
                            }
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                }
            }
        }
    }

    ListItem(
        headlineContent = {
            Text(text = "Language")
        },
        supportingContent = {
            Text(text = languageDisplayName(currentTag))
        },
        modifier = Modifier.clickable { dialogOpened = true },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDataCard(
    settingsViewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current

    val exportDataStatus by settingsViewModel.exportDataStatus.collectAsStateWithLifecycle(null)

    val createJsonDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(settingsViewModel::exportData) }

    PreferenceCategoryCard(
        titleStringResId = R.string.export_data,
    ) {
        PreferenceListItem(
            titleStringResId = R.string.export_data,
            descriptionStringResId = R.string.export_data_description,
        ) {
            createJsonDocumentLauncher.launch("data.json")
        }
    }

    if (exportDataStatus == SettingsViewModel.ExportDataStatus.Processing) {
        BasicAlertDialog(
            onDismissRequest = {},
        ) {
            Surface(
                modifier = Modifier.wrapContentSize(),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Row(
                    modifier = Modifier.padding(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 16.dp),
                    )

                    Text(
                        text = stringResource(R.string.export_data_in_progress),
                    )
                }
            }
        }
    }

    LaunchedEffect(exportDataStatus) {
        when (val exportDataStatus = exportDataStatus) {
            is SettingsViewModel.ExportDataStatus.Processing -> {
                // Do nothing
            }

            is SettingsViewModel.ExportDataStatus.PermissionsNotGranted -> {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.export_data_missing_permissions),
                    withDismissAction = true,
                )
            }

            is SettingsViewModel.ExportDataStatus.Done -> when (exportDataStatus.result) {
                is Result.Success -> {
                    when (
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.export_data_success),
                            actionLabel = context.getString(R.string.export_data_open_file),
                            withDismissAction = true,
                        )
                    ) {
                        SnackbarResult.ActionPerformed -> {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        exportDataStatus.result.data,
                                    ),
                                    context.getString(
                                        R.string.export_data_open_file,
                                    ),
                                )
                            )
                        }

                        SnackbarResult.Dismissed -> Unit
                    }
                }

                is Result.Error -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(
                            R.string.export_data_error,
                            exportDataStatus.result.error.name,
                        ),
                        withDismissAction = true,
                    )
                }
            }

            null -> Unit
        }
    }
}

@Composable
private fun AboutCard() {
    PreferenceCategoryCard(
        titleStringResId = R.string.about,
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(R.string.about_developer),
                )
            },
            supportingContent = {
                Text(
                    text = stringResource(R.string.developer_name),
                )
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AboutLinkIconButton(
                        nameStringResId = R.string.github,
                        iconDrawableResId = R.drawable.ic_github,
                        linkStringResId = R.string.about_developer_github_link,
                    )
                    AboutLinkIconButton(
                        nameStringResId = R.string.twitter,
                        iconDrawableResId = R.drawable.ic_twitter,
                        linkStringResId = R.string.about_developer_twitter_link,
                    )
                    AboutLinkIconButton(
                        nameStringResId = R.string.mastodon,
                        iconDrawableResId = R.drawable.ic_mastodon,
                        linkStringResId = R.string.about_developer_mastodon_link,
                    )
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
        )

        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(R.string.about_application),
                )
            },
            supportingContent = {
                Text(
                    text = stringResource(R.string.app_name),
                )
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AboutLinkIconButton(
                        nameStringResId = R.string.about_application_website,
                        iconDrawableResId = R.drawable.ic_globe,
                        linkStringResId = R.string.about_application_website_link,
                    )
                    AboutLinkIconButton(
                        nameStringResId = R.string.about_application_repository,
                        iconDrawableResId = R.drawable.ic_github,
                        linkStringResId = R.string.about_application_repository_link,
                    )
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun AboutLinkIconButton(
    @StringRes nameStringResId: Int,
    @DrawableRes iconDrawableResId: Int,
    @StringRes linkStringResId: Int,
) {
    val context = LocalContext.current

    fun openLink(@StringRes link: Int) {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                context.getString(link).toUri(),
            )
        )
    }

    IconButton(
        onClick = { openLink(linkStringResId) },
    ) {
        Icon(
            painter = painterResource(iconDrawableResId),
            contentDescription = stringResource(nameStringResId),
        )
    }
}