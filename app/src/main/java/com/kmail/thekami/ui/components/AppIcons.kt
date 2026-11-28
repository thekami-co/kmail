package com.kmail.thekami.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.ReplyAll
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Every icon in the app, in one place. To change an icon, change its line here.
 *
 * Icons are Material Symbols (Apache 2.0): outlined for content, rounded for arrows and controls.
 * Back, chevron, send, reply and open-in-new flip in right-to-left languages.
 */
object AppIcons {
    val Back: ImageVector get() = Icons.AutoMirrored.Rounded.ArrowBack
    val Menu: ImageVector get() = Icons.Rounded.Menu
    val More: ImageVector get() = Icons.Rounded.MoreVert
    val Close: ImageVector get() = Icons.Rounded.Close
    val Check: ImageVector get() = Icons.Rounded.Check
    val Search: ImageVector get() = Icons.Rounded.Search
    val ChevronRight: ImageVector get() = Icons.AutoMirrored.Rounded.KeyboardArrowRight
    val ChevronDown: ImageVector get() = Icons.Rounded.KeyboardArrowDown
    val ChevronUp: ImageVector get() = Icons.Rounded.KeyboardArrowUp
    val OpenInNew: ImageVector get() = Icons.AutoMirrored.Rounded.OpenInNew
    val Trash: ImageVector get() = Icons.Outlined.Delete
    val Archive: ImageVector get() = Icons.Outlined.Archive
    val StarOutline: ImageVector get() = Icons.Outlined.StarBorder
    val Star: ImageVector get() = Icons.Rounded.Star
    val Reply: ImageVector get() = Icons.AutoMirrored.Outlined.Reply
    val ReplyAll: ImageVector get() = Icons.AutoMirrored.Outlined.ReplyAll
    val Send: ImageVector get() = Icons.AutoMirrored.Outlined.Send
    val MarkUnread: ImageVector get() = Icons.Outlined.MarkEmailUnread
    val Pencil: ImageVector get() = Icons.Outlined.Edit
    val Attachment: ImageVector get() = Icons.Outlined.AttachFile
    val Download: ImageVector get() = Icons.Outlined.Download
    val Inbox: ImageVector get() = Icons.Outlined.Inbox
    val InboxAll: ImageVector get() = Icons.Outlined.AllInbox
    val Mail: ImageVector get() = Icons.Outlined.Mail
    val MailOpen: ImageVector get() = Icons.Outlined.Drafts
    val NoteEdit: ImageVector get() = Icons.Outlined.EditNote
    val Folder: ImageVector get() = Icons.Outlined.Folder
    val FolderOpen: ImageVector get() = Icons.Outlined.FolderOpen
    val FolderZip: ImageVector get() = Icons.Outlined.FolderZip
    val Spam: ImageVector get() = Icons.Outlined.Report
    val AddAccount: ImageVector get() = Icons.Outlined.PersonAdd
    val Image: ImageVector get() = Icons.Outlined.Image
    val FilePdf: ImageVector get() = Icons.Outlined.PictureAsPdf
    val Document: ImageVector get() = Icons.Outlined.Description
    val File: ImageVector get() = Icons.Outlined.InsertDriveFile
    val Settings: ImageVector get() = Icons.Outlined.Settings
    val AccountCircle: ImageVector get() = Icons.Outlined.AccountCircle
    val Palette: ImageVector get() = Icons.Outlined.Palette
    val Language: ImageVector get() = Icons.Outlined.Language
    val Sync: ImageVector get() = Icons.Outlined.Sync
    val Info: ImageVector get() = Icons.Outlined.Info
    val QrCode: ImageVector get() = Icons.Outlined.QrCode2
    val QrScan: ImageVector get() = Icons.Outlined.QrCodeScanner
    val Lock: ImageVector get() = Icons.Outlined.Lock
    val Eye: ImageVector get() = Icons.Outlined.Visibility
    val EyeOff: ImageVector get() = Icons.Outlined.VisibilityOff
    val At: ImageVector get() = Icons.Outlined.AlternateEmail
    val Fingerprint: ImageVector get() = Icons.Outlined.Fingerprint
    val Server: ImageVector get() = Icons.Outlined.Dns
    val Account: ImageVector get() = Icons.Outlined.Person

    /** reply, turned around. Material has no mirrored forward arrow, so this one keeps its own path. */
    val Forward: ImageVector by lazy {
        ImageVector.Builder(
            name = "Forward",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        ).addGroup(scaleX = -1f, pivotX = 12f)
            .addPath(
                pathData = addPathNodes(
                    "M8 9.8v.9l1.7.3c2.6.4 4.5 1.4 5.9 2.7-1.7-.5-3.5-.8-5.6-.8H8v1.3L5.8 12zM10 5l-7 7 7 7v-4.1c5 0 8.5 1.6 11 5.1-1-5-4-10-11-11",
                ),
                fill = SolidColor(Color.Black),
            )
            .clearGroup()
            .build()
    }
}
