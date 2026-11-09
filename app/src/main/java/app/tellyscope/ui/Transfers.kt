// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.tellyscope.R
import app.tellyscope.adb.InstallKind
import app.tellyscope.adb.TvFile
import app.tellyscope.adb.TvFolder
import app.tellyscope.data.AppInfo
import app.tellyscope.data.resolve
import app.tellyscope.ui.theme.SmallNumber

/**
 * Both dialogs here close only through their own buttons: a tap outside or Back would otherwise
 * drop an install halfway through its questions, or look like it cancelled a copy.
 */
internal val Sticky = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)

/**
 * The three things people come to a TV's page for, as big tiles under the system widget.
 * Debloat is filled, with a count, while it has something to do; after that it looks like its
 * neighbours and [onDebloat] leads somewhere quieter.
 */
@Composable
fun ActionTiles(actions: DeviceActions, count: Int, todo: Boolean, disabling: Boolean, onDebloat: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile(Icons.Default.Add, stringResource(R.string.transfers_install_app), actions.pickApp, Modifier.weight(1f))
        Tile(
            Broom, if (disabling) stringResource(R.string.transfers_disabling) else stringResource(R.string.transfers_debloat), onDebloat, Modifier.weight(1f),
            enabled = !disabling, primary = todo, badge = count.takeIf { todo && it > 0 },
        )
        // Where the file lands is the user's choice, every time, not whatever folder was last shown.
        var choosing by remember { mutableStateOf(false) }
        Box(Modifier.weight(1f)) {
            @Suppress("DEPRECATION") // the AutoMirrored set isn't in icons-core
            Tile(Icons.Default.Send, stringResource(R.string.transfers_send_file), { choosing = true }, Modifier.fillMaxWidth())
            DropdownMenu(choosing, onDismissRequest = { choosing = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.transfers_to_current_folder)) },
                    onClick = { choosing = false; actions.pickFile(TvFolder.DOWNLOAD) },
                )
                HorizontalDivider()
                TvFolder.entries.forEach { f ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.transfers_to_folder, Copy.folder(f).resolve())) },
                        onClick = { choosing = false; actions.pickFile(f) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Tile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    badge: Int? = null,
) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        // Material's disabled colours, so a greyed-out Debloat doesn't still read as the main button.
        color = when {
            !enabled -> c.onSurface.copy(alpha = 0.12f)
            primary -> c.primary
            else -> c.secondaryContainer
        },
        contentColor = when {
            !enabled -> c.onSurface.copy(alpha = 0.38f)
            primary -> c.onPrimary
            else -> c.onSecondaryContainer
        },
    ) {
        Column(
            Modifier.padding(vertical = 20.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BadgedBox(badge = { if (badge != null) Badge { Text(badge.toString()) } }) {
                Icon(icon, contentDescription = null, Modifier.size(28.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Material Icons "cleaning_services" (Apache-2.0, see NOTICE); icons-core has no broom. */
internal val Broom = ImageVector.Builder("Broom", 24.dp, 24.dp, 24f, 24f)
    .addPath(
        PathParser().parsePathString(
            "M16 11h-1V3c0-1.1-.9-2-2-2h-2c-1.1 0-2 .9-2 2v8H8c-2.76 0-5 2.24-5 5v7h18v-7c0-2.76-2.24-5-5-5z" +
                "m3 10h-2v-3c0-.55-.45-1-1-1s-1 .45-1 1v3h-2v-3c0-.55-.45-1-1-1s-1 .45-1 1v3H9v-3c0-.55-.45-1-1-1s-1 .45-1 1v3H5v-5" +
                "c0-1.65 1.35-3 3-3h8c1.65 0 3 1.35 3 3v5z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    )
    .build()



private val CutIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ContentCut",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M9.64 7.64c.23-.5.36-1.05.36-1.64 0-2.21-1.79-4-4-4S2 3.79 2 6s1.79 4 4 4c.59 0 1.14-.13 1.64-.36L10 12l-2.36 2.36C7.14 14.13 6.59 14 6 14c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4c0-.59-.13-1.14-.36-1.64L12 14l7 7h3v-1L9.64 7.64zM6 8c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm0 12c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm6-7.5c-.28 0-.5-.22-.5-.5s.22-.5.5-.5.5.22.5.5-.22.5-.5.5zM19 3l-6 6 2 2 7-7V3h-3z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()

private val FolderIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Folder",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()

private val DownloadIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Download",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()

private val InfoIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Info",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()

private val OpenWithIcon: ImageVector
    get() = ImageVector.Builder(
        name = "OpenInNew",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenWithDialog(
    openWith: OpenWithUi,
    onSelect: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            val ext = openWith.file.name.substringAfterLast('.', "file").uppercase()
            Text(
                stringResource(R.string.transfers_open_with_title, openWith.file.name),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.transfers_open_with_caption, ext),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            val launchableApps = remember(openWith.apps) { openWith.apps.filter { it.launchable } }
            if (launchableApps.isEmpty()) {
                Text(
                    stringResource(R.string.transfers_no_apps_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(280.dp),
                ) {
                    items(launchableApps) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onSelect(app) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app, Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilePropertiesDialog(f: TvFile, currentPath: String, now: Long, onDismiss: () -> Unit) {
    val fullPath = f.path.ifEmpty { "${currentPath.trimEnd('/')}/${f.name}" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.transfers_properties_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Name: ${f.name}", style = MaterialTheme.typography.bodyMedium)
                Text("Location: $fullPath", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Type: ${if (f.isDir) "Folder" else "File"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!f.isDir) {
                    Text("Size: ${Copy.bytes(f.size).resolve()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Modified: ${Copy.ago(f.modifiedMillis, now).resolve()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.transfers_ok))
            }
        },
    )
}



/** Body of the Files tab, under its folder tabs: tree structure viewing & file management. */
@Composable
fun FilesBlock(files: FilesUi, now: Long, actions: DeviceActions) {
    val list = files.list
    val clip = files.fileClip ?: files.copiedFile?.let { FileClip(it.file.name, "${it.folder.path}/${it.file.name}", FileOpMode.COPY) }
    var storageDropdownOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Quick Picks Row: Download & Movies
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.transfers_quick_picks), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { actions.folder(TvFolder.DOWNLOAD) },
                    label = { Text(Copy.folder(TvFolder.DOWNLOAD).resolve()) },
                    leadingIcon = { Icon(FolderIcon, null, Modifier.size(16.dp)) },
                )
                AssistChip(
                    onClick = { actions.folder(TvFolder.MOVIES) },
                    label = { Text(Copy.folder(TvFolder.MOVIES).resolve()) },
                    leadingIcon = { Icon(FolderIcon, null, Modifier.size(16.dp)) },
                )
            }
        }

        // Storage Selector Dropdown / Row
        if (files.storages.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    TextButton(onClick = { storageDropdownOpen = true }) {
                        Icon(FolderIcon, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            files.storages.firstOrNull { files.currentPath.startsWith(it.path) }?.name ?: "Storage",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    DropdownMenu(expanded = storageDropdownOpen, onDismissRequest = { storageDropdownOpen = false }) {
                        files.storages.forEach { st ->
                            DropdownMenuItem(
                                text = { Text("${st.name} (${st.path})") },
                                leadingIcon = { Icon(FolderIcon, null) },
                                onClick = {
                                    storageDropdownOpen = false
                                    actions.selectStorage(st.path)
                                },
                            )
                        }
                    }
                }
            }
        }

        // Path Navigation Bar (Breadcrumb / Parent directory)
        val currentPath = files.currentPath.trimEnd('/')
        val parentPath = currentPath.substringBeforeLast('/', "").ifEmpty { "/sdcard" }
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { actions.navigatePath(parentPath) },
                enabled = currentPath.contains('/') && currentPath != "/sdcard" && currentPath != "/storage",
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up", modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                currentPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Cut / Copy Paste Action Banner
        if (clip != null) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (clip.mode == FileOpMode.CUT) CutIcon else CopyIcon,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.transfers_copied, clip.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Button(
                        onClick = { actions.pastePath(files.currentPath) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(stringResource(R.string.transfers_paste))
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = actions.clearCopiedFile, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Directory Tree Contents
        when {
            list == null -> Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                LoadingIndicator(Modifier.size(32.dp))
            }

            files.error != null -> Muted(files.error.resolve())
            else -> {
                if (list.isEmpty()) {
                    Muted(stringResource(R.string.tabs_folder_empty_line))
                }
                list.forEach { f ->
                    FileRow(
                        f,
                        now,
                        currentPath = files.currentPath,
                        onOpenDir = { actions.navigatePath(f.path) },
                        onOpenWith = { actions.openFilePicker(f) },
                        onSave = { actions.saveFile(f) },
                        onCut = { actions.cutPath(f.name, f.path) },
                        onCopy = { actions.copyPath(f.name, f.path) },
                        onDelete = { actions.deletePath(f.path) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    f: TvFile,
    now: Long,
    currentPath: String,
    onOpenDir: () -> Unit,
    onOpenWith: () -> Unit,
    onSave: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showProperties by remember { mutableStateOf(false) }

    if (showProperties) {
        FilePropertiesDialog(f, currentPath, now, onDismiss = { showProperties = false })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.transfers_confirm_delete, f.name)) },
            confirmButton = {
                Button(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.transfers_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.transfers_cancel))
                }
            },
        )
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = { if (f.isDir) onOpenDir() else onOpenWith() })
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (f.isDir) {
            Icon(FolderIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(f.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            if (!f.isDir) {
                Text(
                    "${Copy.bytes(f.size).resolve()} · ${Copy.ago(f.modifiedMillis, now).resolve()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (!f.isDir) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.transfers_open_with)) },
                        leadingIcon = { Icon(OpenWithIcon, null) },
                        onClick = { menuOpen = false; onOpenWith() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.transfers_save)) },
                        leadingIcon = { Icon(DownloadIcon, null) },
                        onClick = { menuOpen = false; onSave() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.transfers_cut)) },
                    leadingIcon = { Icon(CutIcon, null) },
                    onClick = { menuOpen = false; onCut() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.transfers_copy)) },
                    leadingIcon = { Icon(CopyIcon, null) },
                    onClick = { menuOpen = false; onCopy() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.transfers_properties)) },
                    leadingIcon = { Icon(InfoIcon, null) },
                    onClick = { menuOpen = false; showProperties = true },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.transfers_delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { menuOpen = false; confirmDelete = true },
                )
            }
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SideloadDialog(s: SideloadUi, actions: DeviceActions) {
    val app = s.app
    val kind = s.kind
    AlertDialog(
        onDismissRequest = {},
        properties = Sticky,
        icon = when (s.step) {
            SideloadUi.Step.Installed -> ({ Icon(Icons.Default.CheckCircle, null) })
            SideloadUi.Step.Failed -> ({ Icon(Icons.Default.Warning, null) })
            else -> null
        },
        title = {
            Text(
                when (s.step) {
                    SideloadUi.Step.Reading -> stringResource(R.string.transfers_reading)
                    SideloadUi.Step.Failed -> app?.let { stringResource(R.string.transfers_install_failed, it.label) } ?: stringResource(R.string.transfers_cant_install)
                    SideloadUi.Step.Installed -> Copy.installed(kind ?: InstallKind.NEW, app?.label.orEmpty()).resolve()
                    else -> app?.label.orEmpty()
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.step == SideloadUi.Step.Reading) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { LoadingIndicator() }
                }
                if (app != null && s.step != SideloadUi.Step.Failed) {
                    Column {
                        Text(app.info.packageName, style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            listOfNotNull(
                                stringResource(R.string.transfers_version, app.info.versionName ?: app.info.versionCode),
                                Copy.bytes(app.sizeBytes).resolve(),
                                if (app.files.size > 1) pluralStringResource(R.plurals.transfers_parts, app.files.size, app.files.size) else null,
                            ).joinToString(" · "),
                            style = SmallNumber,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when (s.step) {
                    SideloadUi.Step.Ready -> if (kind != null) {
                        Text(Copy.installKind(kind, s.installed?.versionName, app?.info?.versionName).resolve(), style = MaterialTheme.typography.bodyMedium)
                        if (kind == InstallKind.DOWNGRADE) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { actions.allowDowngrade(!s.allowDowngrade) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(s.allowDowngrade, onCheckedChange = null)
                                Spacer(Modifier.width(12.dp))
                                Text(Copy.ALLOW_DOWNGRADE.resolve(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    SideloadUi.Step.Installing -> {
                        val f = s.progress?.fraction
                        // Past the last byte the TV is still unpacking: say so rather than sit at 100%.
                        val sending = f != null && f < 1f
                        if (sending) LinearWavyProgressIndicator(progress = { f }, Modifier.fillMaxWidth())
                        else LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            if (sending) stringResource(R.string.transfers_sending_percent, (f * 100).toInt()) else stringResource(R.string.transfers_installing),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Muted(Copy.INSTALL_KEEP_OPEN.resolve())
                    }
                    SideloadUi.Step.Failed -> {
                        Text(s.error?.resolve().orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        s.detail?.takeIf { it.isNotBlank() }?.let {
                            Text(stringResource(R.string.transfers_tv_said, it), style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (s.step) {
                SideloadUi.Step.Ready -> if (kind != null) Button(
                    onClick = actions.install,
                    enabled = kind != InstallKind.DOWNGRADE || s.allowDowngrade,
                ) { Text(Copy.installButton(kind).resolve()) }
                SideloadUi.Step.Installed -> Button(onClick = actions.openInstalled) { Text(stringResource(R.string.transfers_open_on_tv)) }
                SideloadUi.Step.Failed -> Button(onClick = actions.closeSideload) { Text(stringResource(R.string.transfers_ok)) }
                else -> Unit
            }
        },
        dismissButton = {
            when (s.step) {
                SideloadUi.Step.Reading, SideloadUi.Step.Ready -> TextButton(onClick = actions.closeSideload) { Text(stringResource(R.string.transfers_cancel)) }
                SideloadUi.Step.Installed -> TextButton(onClick = actions.closeSideload) { Text(stringResource(R.string.transfers_done)) }
                SideloadUi.Step.Failed -> TextButton(onClick = { actions.reportInstall(s) }) { Text(stringResource(R.string.transfers_report)) }
                else -> Unit
            }
        },
    )
}

/** A copy in either direction. Cancel is the only way out before it's done. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransferDialog(t: TransferUi, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = Sticky,
        title = { Text(if (t.toTv) stringResource(R.string.transfers_sending_to, Copy.folder(t.folder).resolve()) else stringResource(R.string.transfers_saving)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                val f = t.progress?.fraction
                if (f != null) LinearWavyProgressIndicator(progress = { f }, Modifier.fillMaxWidth())
                else LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    t.progress?.let { p -> p.total?.let { stringResource(R.string.transfers_progress, Copy.bytes(p.done).resolve(), Copy.bytes(it).resolve()) } ?: Copy.bytes(p.done).resolve() } ?: stringResource(R.string.transfers_starting),
                    style = SmallNumber,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.transfers_cancel)) } },
    )
}
