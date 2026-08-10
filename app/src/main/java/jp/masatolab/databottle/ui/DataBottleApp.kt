package jp.masatolab.databottle.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.masatolab.databottle.data.AppSettings
import jp.masatolab.databottle.data.BottleType
import jp.masatolab.databottle.data.DataRepository
import jp.masatolab.databottle.data.MetricResult
import jp.masatolab.databottle.sensor.GravityVector
import jp.masatolab.databottle.sensor.rememberGravityVector
import jp.masatolab.databottle.widget.BottleWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun DataBottleApp() {
    val context = LocalContext.current
    val settings = remember { AppSettings(context.applicationContext) }
    val repository = remember { DataRepository(context.applicationContext, settings) }

    var order by remember { mutableStateOf(settings.order()) }
    var enabled by remember { mutableStateOf(settings.enabled()) }
    var mobileLimitGb by remember { mutableStateOf(settings.mobileLimitGb()) }
    var cycleDay by remember { mutableStateOf(settings.cycleDay()) }
    var openAiFallbackLimitUsd by remember { mutableStateOf(settings.openAiFallbackLimitUsd()) }
    var openAiKeyConfigured by remember { mutableStateOf(settings.hasOpenAiAdminKey()) }
    var showSettings by remember { mutableStateOf(false) }
    var currentType by remember { mutableStateOf(settings.lastViewed()) }

    val visible = order.filter { it in enabled }.ifEmpty { listOf(BottleType.BATTERY) }

    BackHandler(enabled = showSettings) { showSettings = false }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BottleBackground,
        contentColor = BottleText
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            if (showSettings) {
                SettingsScreen(
                    order = order,
                    enabled = enabled,
                    mobileLimitGb = mobileLimitGb,
                    cycleDay = cycleDay,
                    openAiFallbackLimitUsd = openAiFallbackLimitUsd,
                    openAiKeyConfigured = openAiKeyConfigured,
                    repository = repository,
                    onToggle = { type, checked ->
                        val next = enabled.toMutableSet().apply {
                            if (checked) add(type) else remove(type)
                        }
                        if (next.isNotEmpty()) {
                            enabled = next
                            settings.saveEnabled(next)
                        }
                    },
                    onMove = { from, to ->
                        if (from in order.indices && to in order.indices) {
                            val next = order.toMutableList()
                            val item = next.removeAt(from)
                            next.add(to, item)
                            order = next
                            settings.saveOrder(next)
                        }
                    },
                    onMobileLimitChange = {
                        mobileLimitGb = it
                        settings.saveMobileLimitGb(it)
                    },
                    onCycleDayChange = {
                        cycleDay = it
                        settings.saveCycleDay(it)
                    },
                    onOpenAiFallbackLimitChange = {
                        openAiFallbackLimitUsd = it
                        settings.saveOpenAiFallbackLimitUsd(it)
                    },
                    onSaveOpenAiKey = { key ->
                        settings.saveOpenAiAdminKey(key)
                        openAiKeyConfigured = settings.hasOpenAiAdminKey()
                        BottleWidgetProvider.updateAllAsync(context.applicationContext)
                    },
                    onClearOpenAiKey = {
                        settings.clearOpenAiAdminKey()
                        openAiKeyConfigured = false
                        BottleWidgetProvider.updateAllAsync(context.applicationContext)
                    },
                    onOpenUsageAccess = { openUsageAccessSettings(context) },
                    onDone = { showSettings = false }
                )
            } else {
                val visibleKey = visible.joinToString("|") { it.name }
                key(visibleKey) {
                    MainBottleScreen(
                        types = visible,
                        initialType = currentType.takeIf { it in visible } ?: visible.first(),
                        repository = repository,
                        onCurrentType = { type ->
                            currentType = type
                            settings.saveLastViewed(type)
                            BottleWidgetProvider.updateAllAsync(context.applicationContext)
                        },
                        onSettings = { showSettings = true },
                        onOpenUsageAccess = { openUsageAccessSettings(context) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainBottleScreen(
    types: List<BottleType>,
    initialType: BottleType,
    repository: DataRepository,
    onCurrentType: (BottleType) -> Unit,
    onSettings: () -> Unit,
    onOpenUsageAccess: () -> Unit
) {
    val initialIndex = types.indexOf(initialType).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex) { types.size }
    val gravity = rememberGravityVector()

    LaunchedEffect(pagerState, types) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> onCurrentType(types[page]) }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DATA BOTTLE",
                style = MaterialTheme.typography.titleMedium,
                color = BottleText,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onSettings) { Text("SET") }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            MetricBottlePage(
                type = types[page],
                repository = repository,
                gravity = gravity,
                onSettings = onSettings,
                onOpenUsageAccess = onOpenUsageAccess
            )
        }

        Text(
            text = "%02d / %02d".format(pagerState.currentPage + 1, types.size),
            fontSize = 11.sp,
            color = BottleMuted.copy(alpha = 0.62f),
            letterSpacing = 0.8f.sp
        )
        Text(
            text = if (types.size > 1) "SWIPE" else "",
            fontSize = 9.sp,
            color = BottleMuted.copy(alpha = 0.55f),
            letterSpacing = 1.6f.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )
    }
}

@Composable
private fun MetricBottlePage(
    type: BottleType,
    repository: DataRepository,
    gravity: GravityVector,
    onSettings: () -> Unit,
    onOpenUsageAccess: () -> Unit
) {
    var metric by remember(type) {
        mutableStateOf(
            MetricResult(
                type = type,
                ratio = 0f,
                headline = "--",
                detail = "READING…",
                available = false
            )
        )
    }

    LaunchedEffect(type) {
        while (isActive) {
            metric = withContext(Dispatchers.IO) { repository.read(type) }
            val delayMs = when {
                metric.needsUsageAccess -> 2500L
                metric.needsOpenAiKey -> 10_000L
                type == BottleType.MOBILE_DATA -> 30_000L
                type == BottleType.OPENAI_API -> 300_000L
                else -> 2500L
            }
            delay(delayMs)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = type.label,
            fontSize = 16.sp,
            color = BottlePrimary.copy(alpha = 0.92f),
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4f.sp
        )

        Spacer(Modifier.height(14.dp))

        DotBottle(
            type = type,
            ratio = metric.ratio,
            gravity = gravity
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = metric.headline,
            fontSize = 36.sp,
            color = BottleText,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5f).sp
        )
        Text(
            text = metric.detail,
            fontSize = 14.sp,
            color = if (metric.needsUsageAccess || metric.needsOpenAiKey) BottleOverflow else BottleMuted.copy(alpha = 0.90f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp)
        )

        if (metric.needsUsageAccess) {
            Spacer(Modifier.height(14.dp))
            Button(onClick = onOpenUsageAccess) { Text("GRANT USAGE ACCESS") }
        }
        if (metric.needsOpenAiKey) {
            Spacer(Modifier.height(14.dp))
            Button(onClick = onSettings) { Text("OPEN SETTINGS") }
        }
    }
}

private fun openUsageAccessSettings(context: Context) {
    val targeted = Intent(
        Settings.ACTION_USAGE_ACCESS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(targeted) }
        .onFailure {
            val fallback = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
}
