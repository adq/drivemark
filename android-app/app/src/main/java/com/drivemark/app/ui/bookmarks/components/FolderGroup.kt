package com.drivemark.app.ui.bookmarks.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drivemark.app.domain.model.Bookmark
import com.drivemark.app.ui.bookmarks.FolderNode

@Composable
fun FolderGroup(
    folderName: String,
    totalCount: Int,
    bookmarks: List<Bookmark>,
    children: List<FolderNode>,
    isExpanded: Boolean,
    expandedFolders: Set<String>,
    depth: Int,
    onToggle: () -> Unit,
    onToggleChild: (String) -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = (16 + depth * 16).dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown
                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = folderName,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$totalCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                // Child folders first
                children.forEach { child ->
                    FolderGroup(
                        folderName = child.name,
                        totalCount = child.totalCount,
                        bookmarks = child.bookmarks,
                        children = child.children,
                        isExpanded = child.fullPath in expandedFolders,
                        expandedFolders = expandedFolders,
                        depth = depth + 1,
                        onToggle = { onToggleChild(child.fullPath) },
                        onToggleChild = onToggleChild,
                        onBookmarkClick = onBookmarkClick,
                    )
                }
                // Then bookmarks at this level
                if (bookmarks.isNotEmpty()) {
                    Column(modifier = Modifier.padding(start = (16 + depth * 16).dp, end = 16.dp, bottom = 8.dp)) {
                        bookmarks.forEach { bookmark ->
                            BookmarkCard(
                                bookmark = bookmark,
                                onClick = { onBookmarkClick(bookmark) },
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
