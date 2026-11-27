package com.kmail.thekami.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmail.thekami.R
import com.kmail.thekami.ui.support.GithubUpdates
import com.kmail.thekami.ui.support.openReleasePage

private val UpdateRed = Color(0xFFE53935)

/** Bottom of the drawer: Thekami logo on the left, app version on the right with a red dot when an update is out. */
@Composable
fun DrawerFooter() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val update by GithubUpdates.update.collectAsStateWithLifecycle()
    val pending = update
    val version = remember { GithubUpdates.currentName(context) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.thekami_logo),
            contentDescription = "Thekami",
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier.weight(1f).height(28.dp),
        )
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .then(if (pending != null) Modifier.clickable { openReleasePage(context, pending.url) } else Modifier)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "v$version",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
                if (pending != null) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(UpdateRed))
                }
            }
            if (pending != null) {
                Text(
                    text = stringResource(R.string.drawer_update_available) + " v" + pending.versionName,
                    style = MaterialTheme.typography.labelSmall,
                    color = UpdateRed,
                )
            }
        }
    }
}
