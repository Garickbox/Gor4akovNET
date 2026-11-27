package ru.garickbox.gor4akovnet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

// Справочник NDMS 5.1: service ssh/ftp/cifs/dlna/upnp/torrent + ip http webdav enable.
// Команды плоские через RCI: [{parse:"service X"}] / [{parse:"no service X"}].
private val NDMS_SVC_ITEMS = listOf(
    "ssh" to "SSH :22",
    "ftp" to "FTP :21",
    "cifs" to "CIFS / SMB :445",
    "dlna" to "DLNA :8200",
    "upnp" to "UPnP :5000",
    "torrent" to "Torrent :51413",
    "webdav" to "WebDAV :80"
)

@Composable
fun NdmsServicesTogglesCard(
    toggles: JSONObject?,
    busy: Boolean,
    onToggle: (String, Boolean) -> Unit
) {
    if (toggles == null) return
    MiniCard("Службы NDMS — тумблеры (3.15x-3.17x)") {
        for ((key, label) in NDMS_SVC_ITEMS) {
            val on = toggles.optBoolean(key, false)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = DeckColors.Text, fontSize = 12.sp)
                Text(
                    if (on) "вкл" else "выкл",
                    color = if (on) DeckColors.Emerald else DeckColors.Muted,
                    fontSize = 10.5.sp
                )
                Switch(
                    checked = on,
                    onCheckedChange = { v -> onToggle(key, v) },
                    enabled = !busy
                )
            }
        }
    }
}