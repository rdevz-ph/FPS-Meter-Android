package com.rdevzph.fpsmeter.ui.screen

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.rdevzph.fpsmeter.R

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rdevzph.fpsmeter.ui.theme.AppThemeMode
import com.rdevzph.fpsmeter.ui.theme.AppThemeSettings
import com.rdevzph.fpsmeter.util.LocaleHelper

private const val REPO_URL = "https://github.com/rdevz-ph/FPS-Meter-Android"
private const val ISSUES_URL = "https://github.com/rdevz-ph/FPS-Meter-Android/issues"
private const val PORTFOLIO_URL = "https://romel-portfolio.vercel.app/"
private const val PRIVACY_POLICY_URL = "https://rdevz-ph.github.io/FPS-Meter-Android/privacy.html"

private fun openBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.unable_to_open_browser), Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeSettings: AppThemeSettings,
    currentLanguage: String,
    onThemeChange: (AppThemeSettings) -> Unit,
    onLanguageChange: (String) -> Unit,
    onOpenDonation: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDiagnosticsOpen by remember { mutableStateOf(false) }

    if (isDiagnosticsOpen) {
        DeviceDiagnosticsScreen(onBack = { isDiagnosticsOpen = false })
        return
    }

    val context = LocalContext.current
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.common_settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Section 1: App Theme Chooser
            SettingsSectionHeader(
                title = stringResource(R.string.section_app_theme),
                icon = Icons.Default.Palette
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    AppThemeMode.values().forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeChange(themeSettings.copy(themeMode = mode))
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = themeSettings.themeMode == mode,
                                onClick = {
                                    onThemeChange(themeSettings.copy(themeMode = mode))
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(mode.titleRes),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Dark AMOLED toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onThemeChange(themeSettings.copy(amoledDark = !themeSettings.amoledDark))
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.dark_amoled),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.dark_amoled_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = themeSettings.amoledDark,
                            onCheckedChange = {
                                onThemeChange(themeSettings.copy(amoledDark = it))
                            }
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeChange(themeSettings.copy(dynamicColor = !themeSettings.dynamicColor))
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.dynamic_colors),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    stringResource(R.string.dynamic_colors_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = themeSettings.dynamicColor,
                                onCheckedChange = {
                                    onThemeChange(themeSettings.copy(dynamicColor = it))
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Section: Language
            SettingsSectionHeader(
                title = stringResource(R.string.section_language),
                icon = Icons.Default.Language
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    val languageOptions = listOf(
                        LocaleHelper.LANG_SYSTEM to R.string.language_system,
                        LocaleHelper.LANG_ENGLISH to R.string.language_english,
                        LocaleHelper.LANG_CHINESE to R.string.language_chinese
                    )
                    languageOptions.forEach { (value, labelRes) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (currentLanguage != value) onLanguageChange(value)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = currentLanguage == value,
                                onClick = {
                                    if (currentLanguage != value) onLanguageChange(value)
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(labelRes),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Section: Diagnostics
            SettingsSectionHeader(
                title = stringResource(R.string.section_diagnostics),
                icon = Icons.Default.Analytics
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { isDiagnosticsOpen = true }
            ) {
                SettingsActionRow(
                    icon = Icons.Default.PhoneAndroid,
                    title = stringResource(R.string.device_diagnostics),
                    subtitle = stringResource(R.string.device_diagnostics_desc),
                    onClick = { isDiagnosticsOpen = true }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Section 2: Developer Infos
            SettingsSectionHeader(
                title = stringResource(R.string.section_developer),
                icon = Icons.Default.Person
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { openBrowser(context, PORTFOLIO_URL) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Romel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.developer_role_short),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.content_desc_portfolio),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Section 3: GitHub, Star & Issue Tracker
            SettingsSectionHeader(
                title = stringResource(R.string.section_github),
                iconPainter = painterResource(R.drawable.ic_github)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsActionRow(
                        iconPainter = painterResource(R.drawable.ic_github),
                        title = stringResource(R.string.github_repository),
                        subtitle = "github.com/rdevz-ph/FPS-Meter-Android",
                        onClick = { openBrowser(context, REPO_URL) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsActionRow(
                        icon = Icons.Default.Star,
                        iconTint = Color(0xFFFFB300),
                        title = stringResource(R.string.github_star),
                        subtitle = stringResource(R.string.github_star_desc),
                        onClick = { openBrowser(context, REPO_URL) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsActionRow(
                        icon = Icons.Default.BugReport,
                        title = stringResource(R.string.github_report_issue),
                        subtitle = stringResource(R.string.github_report_issue_desc),
                        onClick = { openBrowser(context, ISSUES_URL) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsActionRow(
                        icon = Icons.Default.Security,
                        title = stringResource(R.string.privacy_policy),
                        subtitle = stringResource(R.string.privacy_policy_desc),
                        onClick = { openBrowser(context, PRIVACY_POLICY_URL) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Section 4: Support & Donation
            SettingsSectionHeader(
                title = stringResource(R.string.section_support),
                icon = Icons.Default.Favorite
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.support_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onOpenDonation,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.donate_support),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    icon: ImageVector? = null,
    iconPainter: Painter? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        if (iconPainter != null) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (iconPainter != null) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
    }
}
