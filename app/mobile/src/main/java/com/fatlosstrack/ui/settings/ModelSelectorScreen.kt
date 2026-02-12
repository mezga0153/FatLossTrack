package com.fatlosstrack.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.ui.theme.Accent
import com.fatlosstrack.ui.theme.CardSurface
import com.fatlosstrack.ui.theme.OnSurfaceVariant
import com.fatlosstrack.ui.theme.Primary
import com.fatlosstrack.ui.theme.Secondary
import com.fatlosstrack.ui.theme.SurfaceVariant
import kotlinx.coroutines.launch

// ── Data ────────────────────────────────────────────────────────────────

private enum class BadgeStyle { PRIMARY, SECONDARY, ACCENT }

private data class ModelOption(
    val id: String,
    val name: String,
    val badge: String?,
    val badgeStyle: BadgeStyle = BadgeStyle.PRIMARY,
    val inputPrice: String,
    val outputPrice: String,
    val recommended: Boolean = false,
)

private val MODEL_OPTIONS = listOf(
    ModelOption("gpt-5.2", "GPT-5.2", "Best", BadgeStyle.PRIMARY, "$1.75", "$14"),
    ModelOption("gpt-5-mini", "GPT-5 mini", "Fast", BadgeStyle.SECONDARY, "$0.25", "$2", recommended = true),
    ModelOption("gpt-5-nano", "GPT-5 nano", "Cheapest", BadgeStyle.SECONDARY, "$0.05", "$0.40"),
    ModelOption("gpt-4.1", "GPT-4.1", null, inputPrice = "$2", outputPrice = "$8"),
    ModelOption("gpt-4.1-mini", "GPT-4.1 mini", null, inputPrice = "$0.40", outputPrice = "$1.60"),
    ModelOption("gpt-4o-mini", "GPT-4o mini", null, inputPrice = "$0.15", outputPrice = "$0.60"),
    ModelOption("o4-mini", "o4-mini", "Reasoning", BadgeStyle.ACCENT, "$1.10", "$4.40"),
    ModelOption("gpt-5.2-pro", "GPT-5.2 Pro", "Smartest", BadgeStyle.ACCENT, "$21", "$168"),
)

/** Resolve a model id to its display name. */
internal fun modelDisplayName(id: String): String =
    MODEL_OPTIONS.firstOrNull { it.id == id }?.name ?: id

// ── Screen ──────────────────────────────────────────────────────────────

@Composable
fun ModelSelectorScreen(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val storedModel by preferencesManager.openAiModel.collectAsState(initial = "gpt-5-mini")
    var selectedModel by remember(storedModel) { mutableStateOf(storedModel) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // ── Top bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.model_selector_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // ── Pricing hint ──
        Text(
            text = stringResource(R.string.model_selector_pricing_hint),
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // ── Model cards ──
        MODEL_OPTIONS.forEach { model ->
            ModelCard(
                model = model,
                isSelected = model.id == selectedModel,
            ) {
                selectedModel = model.id
                scope.launch { preferencesManager.setOpenAiModel(model.id) }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Card ────────────────────────────────────────────────────────────────

@Composable
private fun ModelCard(model: ModelOption, isSelected: Boolean, onClick: () -> Unit) {
    val borderMod = if (isSelected) {
        Modifier.border(1.5.dp, Primary, RoundedCornerShape(12.dp))
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderMod)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Primary.copy(alpha = 0.08f) else SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Radio dot
        RadioButton(
            selected = isSelected,
            onClick = null,
            modifier = Modifier.size(20.dp),
            colors = RadioButtonDefaults.colors(selectedColor = Primary, unselectedColor = OnSurfaceVariant),
        )
        Spacer(Modifier.width(10.dp))

        // Name + badges
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = if (isSelected) Primary else MaterialTheme.colorScheme.onSurface,
                )
                if (model.badge != null) {
                    Spacer(Modifier.width(6.dp))
                    val badgeColor = when (model.badgeStyle) {
                        BadgeStyle.PRIMARY -> Primary
                        BadgeStyle.SECONDARY -> Secondary
                        BadgeStyle.ACCENT -> Accent
                    }
                    Text(
                        text = model.badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (model.recommended) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.model_recommended),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        // Pricing
        Column(horizontalAlignment = Alignment.End) {
            Text("↑ ${model.inputPrice}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            Text("↓ ${model.outputPrice}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        }
    }
}
