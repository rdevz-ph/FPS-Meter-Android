package com.rdevzph.fpsmeter.ui.crash

import androidx.compose.ui.res.stringResource
import com.rdevzph.fpsmeter.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rdevzph.fpsmeter.MainActivity
import com.rdevzph.fpsmeter.ui.theme.FpsMeterTheme
import com.rdevzph.fpsmeter.util.LocaleHelper
import java.io.File

class CrashActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    companion object {
        const val EXTRA_CRASH_REPORT_PATH = "extra_crash_report_path"
        const val EXTRA_ERROR_NAME = "extra_error_name"
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
        const val EXTRA_APP_VERSION = "extra_app_version"
        private const val GITHUB_ISSUES_URL = "https://github.com/rdevz-ph/FPS-Meter-Android/issues/new"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val reportPath = intent.getStringExtra(EXTRA_CRASH_REPORT_PATH)
        val errorName = intent.getStringExtra(EXTRA_ERROR_NAME) ?: getString(R.string.default_unexpected_exception)
        val errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE) ?: getString(R.string.default_no_message)
        val appVersion = intent.getStringExtra(EXTRA_APP_VERSION) ?: "1.6"

        val reportContent = try {
            if (!reportPath.isNullOrEmpty()) {
                val file = File(reportPath)
                if (file.exists()) file.readText() else "Report file not found."
            } else {
                "No report file path provided."
            }
        } catch (e: Exception) {
            "Failed to load crash report: ${e.message}"
        }

        setContent {
            FpsMeterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CrashScreen(
                        errorName = errorName,
                        errorMessage = errorMessage,
                        appVersion = appVersion,
                        reportContent = reportContent,
                        onCopyReport = { copyToClipboard(reportContent, getString(R.string.crash_report_copied)) },
                        onShareReport = { shareCrashReport(errorName, reportContent) },
                        onFileIssue = { openGitHubIssue(errorName, errorMessage, appVersion, reportContent) },
                        onRestartApp = { restartApp() }
                    )
                }
            }
        }
    }

    private fun copyToClipboard(text: String, toastMessage: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.clipboard_crash_label), text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
    }

    private fun shareCrashReport(errorName: String, report: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${getString(R.string.clipboard_crash_label)}: $errorName")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        val chooser = Intent.createChooser(sendIntent, getString(R.string.share_crash_report))
        startActivity(chooser)
    }

    private fun openGitHubIssue(
        errorName: String,
        errorMessage: String,
        appVersion: String,
        fullReport: String
    ) {
        // Automatically copy full report to clipboard so user has it ready
        copyToClipboard(fullReport, getString(R.string.report_copied_opening))

        val cleanMsg = errorMessage.replace("\n", " ").trim()
        val titleSnippet = if (cleanMsg.length > 50) cleanMsg.take(50) + "..." else cleanMsg
        val issueTitle = "[Crash] $errorName: $titleSnippet"

        // Keep body within browser URL length limits (~2500 chars for trace)
        val traceForUrl = if (fullReport.length > 2500) {
            fullReport.take(2500) + "\n\n... [Trace truncated for URL length. Full report is in your clipboard]"
        } else {
            fullReport
        }

        val issueBody = buildString {
            appendLine("### Crash Summary")
            appendLine("An unexpected crash occurred in FPS Meter.")
            appendLine()
            appendLine("### Environment")
            appendLine("- **App Version:** $appVersion")
            appendLine("- **Device:** ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("- **Android OS:** ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("### Stack Trace")
            appendLine("```")
            appendLine(traceForUrl)
            appendLine("```")
            appendLine()
            appendLine("### Steps to Reproduce")
            appendLine("1. ")
            appendLine("2. ")
        }

        val fullUrl = "$GITHUB_ISSUES_URL?title=${Uri.encode(issueTitle)}&body=${Uri.encode(issueBody)}"
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
            startActivity(browserIntent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.could_not_open_browser_issue), Toast.LENGTH_LONG).show()
            copyToClipboard(fullUrl, getString(R.string.github_issue_link_copied))
        }
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}

@Composable
fun CrashScreen(
    errorName: String,
    errorMessage: String,
    appVersion: String,
    reportContent: String,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit,
    onFileIssue: () -> Unit,
    onRestartApp: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Warning Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = stringResource(R.string.crash_warning_desc),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.crash_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.crash_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Diagnostic Chips / Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = stringResource(R.string.label_exception),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = errorName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = stringResource(R.string.label_version),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "v$appVersion",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = stringResource(R.string.label_android),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }

            // Error Message Box
            if (errorMessage.isNotBlank() && errorMessage != "None") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.common_details),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Primary Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // File Issue on GitHub (Highlight Button)
                Button(
                    onClick = onFileIssue,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = stringResource(R.string.file_issue_github),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.file_issue_github),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Copy & Share in a Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyReport,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.copy_report),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.copy_report), maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onShareReport,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Report",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.common_share), maxLines = 1)
                    }
                }

                // Restart App Button
                FilledTonalButton(
                    onClick = onRestartApp,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = stringResource(R.string.restart_app),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.restart_app), fontWeight = FontWeight.SemiBold)
                }
            }

            // Stack Trace Log Box
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.diagnostic_log),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = onCopyReport,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.copy_log),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                val logHorizontalScroll = rememberScrollState()

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F140F),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 340.dp)
                ) {
                    SelectionContainer {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .horizontalScroll(logHorizontalScroll)
                        ) {
                            Text(
                                text = reportContent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = Color(0xFFBDCAB9)
                            )
                        }
                    }
                }
            }
        }
    }
}
