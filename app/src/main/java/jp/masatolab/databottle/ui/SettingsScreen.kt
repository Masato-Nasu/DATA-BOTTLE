package jp.masatolab.databottle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import jp.masatolab.databottle.data.BottleType
import jp.masatolab.databottle.data.DataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    order: List<BottleType>,
    enabled: Set<BottleType>,
    mobileLimitGb: Float,
    cycleDay: Int,
    openAiFallbackLimitUsd: Float,
    openAiKeyConfigured: Boolean,
    repository: DataRepository,
    onToggle: (BottleType, Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
    onMobileLimitChange: (Float) -> Unit,
    onCycleDayChange: (Int) -> Unit,
    onOpenAiFallbackLimitChange: (Float) -> Unit,
    onSaveOpenAiKey: (String) -> Unit,
    onClearOpenAiKey: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onDone: () -> Unit
) {
    var limitText by remember(mobileLimitGb) {
        mutableStateOf(if (mobileLimitGb % 1f == 0f) mobileLimitGb.toInt().toString() else mobileLimitGb.toString())
    }
    var cycleText by remember(cycleDay) { mutableStateOf(cycleDay.toString()) }
    var openAiLimitText by remember(openAiFallbackLimitUsd) {
        mutableStateOf(
            if (openAiFallbackLimitUsd % 1f == 0f) openAiFallbackLimitUsd.toInt().toString()
            else openAiFallbackLimitUsd.toString()
        )
    }
    var openAiKeyText by remember { mutableStateOf("") }
    var openAiTestStatus by remember { mutableStateOf<String?>(null) }
    var openAiTestOk by remember { mutableStateOf(false) }
    var usageAccess by remember { mutableStateOf(repository.hasUsageAccess()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            usageAccess = repository.hasUsageAccess()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
            .padding(top = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.titleLarge,
                color = BottleText,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onDone) { Text("DONE") }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                SectionLabel("BOTTLES")
                Text(
                    text = "Choose what appears when you swipe. Use the arrows to change the order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BottleMuted
                )
                Spacer(Modifier.height(10.dp))
            }

            itemsIndexed(order, key = { _, item -> item.name }) { index, type ->
                BottleSettingRow(
                    type = type,
                    checked = type in enabled,
                    canMoveUp = index > 0,
                    canMoveDown = index < order.lastIndex,
                    onCheckedChange = { onToggle(type, it) },
                    onMoveUp = { onMove(index, index - 1) },
                    onMoveDown = { onMove(index, index + 1) }
                )
                HorizontalDivider(color = BottleMuted.copy(alpha = 0.18f))
            }

            item {
                Spacer(Modifier.height(28.dp))
                SectionLabel("MOBILE DATA")
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = limitText,
                    onValueChange = { value ->
                        limitText = value
                        value.toFloatOrNull()?.takeIf { it > 0f }?.let(onMobileLimitChange)
                    },
                    label = { Text("Monthly limit (GB)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = cycleText,
                    onValueChange = { value ->
                        cycleText = value.filter(Char::isDigit).take(2)
                        cycleText.toIntOrNull()?.takeIf { it in 1..31 }?.let(onCycleDayChange)
                    },
                    label = { Text("Billing cycle starts on day") },
                    supportingText = { Text("1–31. Short months use their last day.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("USAGE ACCESS", color = BottleText, fontWeight = FontWeight.Medium)
                        Text(
                            if (usageAccess) "GRANTED" else "REQUIRED FOR MOBILE DATA",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (usageAccess) BottlePrimary else BottleOverflow
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = onOpenUsageAccess) {
                        Text(if (usageAccess) "OPEN" else "GRANT")
                    }
                }

                Spacer(Modifier.height(30.dp))
                SectionLabel("OPENAI API · BYOK")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Monthly cost is read from OpenAI's organization Costs API. This requires an OpenAI Admin API key; a normal project API key cannot read organization costs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BottleMuted
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = openAiKeyText,
                    onValueChange = {
                        openAiKeyText = it.trim()
                        openAiTestStatus = null
                    },
                    label = { Text("OpenAI Admin API key") },
                    placeholder = {
                        Text(if (openAiKeyConfigured) "SAVED · paste a new key to replace" else "Paste Admin API key")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        enabled = openAiKeyText.isNotBlank(),
                        onClick = {
                            onSaveOpenAiKey(openAiKeyText)
                            openAiKeyText = ""
                            openAiTestStatus = "KEY SAVED"
                            openAiTestOk = true
                        }
                    ) { Text("SAVE") }
                    TextButton(
                        enabled = openAiKeyConfigured,
                        onClick = {
                            onClearOpenAiKey()
                            openAiKeyText = ""
                            openAiTestStatus = "KEY CLEARED"
                            openAiTestOk = false
                        }
                    ) { Text("CLEAR") }
                    TextButton(
                        enabled = openAiKeyConfigured,
                        onClick = {
                            openAiTestStatus = "CHECKING…"
                            scope.launch {
                                val metric = withContext(Dispatchers.IO) {
                                    repository.read(BottleType.OPENAI_API)
                                }
                                openAiTestOk = metric.available
                                openAiTestStatus = if (metric.available) {
                                    "CONNECTED · ${metric.detail}"
                                } else {
                                    metric.detail
                                }
                            }
                        }
                    ) { Text("TEST") }
                }

                openAiTestStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (openAiTestOk) BottlePrimary else BottleOverflow,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = openAiLimitText,
                    onValueChange = { value ->
                        openAiLimitText = value
                        value.toFloatOrNull()?.takeIf { it > 0f }?.let(onOpenAiFallbackLimitChange)
                    },
                    label = { Text("Fallback monthly limit (USD)") },
                    supportingText = {
                        Text("Used only if the organization hard spend limit cannot be read.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "The key is encrypted with Android Keystore and stored only on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BottleMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(Modifier.height(30.dp))
                SectionLabel("WIDGET")
                Text(
                    text = "The home-screen widget shows the last bottle you viewed. It is intentionally still; open DATA BOTTLE to make the liquid react to gravity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BottleMuted
                )
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun BottleSettingRow(
    type: BottleType,
    checked: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = type.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = BottleText,
            fontWeight = FontWeight.Medium
        )
        TextButton(onClick = onMoveUp, enabled = canMoveUp) { Text("↑") }
        TextButton(onClick = onMoveDown, enabled = canMoveDown) { Text("↓") }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = BottlePrimary,
        fontWeight = FontWeight.Bold
    )
}
