package com.offerpk.photoresizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Portrait
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offerpk.photoresizer.ads.BannerAd
import com.offerpk.photoresizer.ui.Routes

private data class Tool(val route: String, val title: String, val subtitle: String, val icon: ImageVector, val tint: Color, val bg: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onTool: (String) -> Unit, onAbout: () -> Unit) {
    val tools = listOf(
        Tool(Routes.COMPRESS, "Compress to KB", "20, 50, 100 KB or any size", Icons.Filled.Compress, Color(0xFF0B5CAD), Color(0xFFD6E4FF)),
        Tool(Routes.RESIZE, "Resize photo", "Pixels, percent or presets", Icons.Filled.AspectRatio, Color(0xFF00796B), Color(0xFFC8F0EA)),
        Tool(Routes.PASSPORT, "Passport photo", "35×45 mm, 2×2 in, 4×6 sheet", Icons.Filled.Portrait, Color(0xFFD35400), Color(0xFFFFE0C7)),
        Tool(Routes.SIGNATURE, "Signature", "Clean ink, transparent PNG", Icons.Filled.Draw, Color(0xFF6A3FB5), Color(0xFFE7DDFB)),
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Photo Resizer", style = MaterialTheme.typography.titleLarge)
                        Text("KB · Passport · Signature", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { IconButton(onClick = onAbout) { Icon(Icons.Filled.Info, contentDescription = "About & privacy") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            // Banner sits at the very bottom, well away from the tool buttons.
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 12.dp)) { BannerAd() }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Hero()
            tools.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { t -> ToolCard(t, Modifier.weight(1f)) { onTool(t.route) } }
                }
            }
            HowItWorks()
        }
    }
}

@Composable
private fun Hero() {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1565C0), Color(0xFF083480))))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Exact size photos for online forms", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text("Hit a KB limit, make passport-size prints and clean signatures in seconds.",
                color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Shield, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("100% offline · photos never leave your phone", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ToolCard(t: Tool, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier.height(156.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(t.bg), contentAlignment = Alignment.Center) {
                Icon(t.icon, null, tint = t.tint, modifier = Modifier.size(26.dp))
            }
            Column {
                Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(t.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

@Composable
private fun HowItWorks() {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Pick → adjust → save", fontWeight = FontWeight.SemiBold)
                Text("Choose a tool, pick a photo with the system photo picker, then save to your gallery or share.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
