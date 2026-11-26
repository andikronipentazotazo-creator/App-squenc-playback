package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.StudioTransportHeader
import com.example.ui.screens.AutomationScreen
import com.example.ui.screens.ClickAndCueScreen
import com.example.ui.screens.MixerScreen
import com.example.ui.screens.RoutingScreen
import com.example.ui.screens.TimelineScreen
import com.example.ui.theme.ClickCyan
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.CuePanTheme
import com.example.ui.theme.StripElevated
import com.example.ui.theme.StudioBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.MixerViewModel

private enum class StudioTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    MIXER("Mixer", Icons.Default.Tune, Icons.Outlined.Tune, "nav_tab_mixer"),
    TIMELINE("Timeline", Icons.Default.ViewWeek, Icons.Outlined.ViewWeek, "nav_tab_timeline"),
    CLICK_CUE("Click & Cue", Icons.Default.GraphicEq, Icons.Outlined.GraphicEq, "nav_tab_click_cue"),
    AUTOMATION("Automation", Icons.Default.Timeline, Icons.Outlined.Timeline, "nav_tab_automation"),
    ROUTING("Routing", Icons.Default.Cable, Icons.Outlined.Cable, "nav_tab_routing")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CuePanTheme {
                CuePanStudioApp()
            }
        }
    }
}

@Composable
fun CuePanStudioApp(viewModel: MixerViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val transport by viewModel.transportState.collectAsStateWithLifecycle()
    val meters by viewModel.meterLevels.collectAsStateWithLifecycle()
    val hardware by viewModel.hardwareCapability.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var currentTab by rememberSaveable { mutableStateOf(StudioTab.MIXER) }
    val snackbarHostState = remember { SnackbarHostState() }

    if (currentTab != StudioTab.MIXER) {
        BackHandler { currentTab = StudioTab.MIXER }
    }

    LaunchedEffect(uiState.statusBanner) {
        val msg = uiState.statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBg)
    ) {
        val isExpandedWidth = maxWidth >= 720.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = StudioBg,
            contentWindowInsets = WindowInsets.navigationBars,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isExpandedWidth) {
                    NavigationBar(
                        containerColor = ConsoleSurface,
                        contentColor = TextPrimary,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        StudioTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ClickCyan,
                                    selectedTextColor = ClickCyan,
                                    indicatorColor = StripElevated,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedWidth) {
                    NavigationRail(
                        containerColor = ConsoleSurface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding()
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "CUEPAN",
                            style = MaterialTheme.typography.labelSmall,
                            color = ClickCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        StudioTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ClickCyan,
                                    selectedTextColor = ClickCyan,
                                    indicatorColor = StripElevated,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    StudioTransportHeader(
                        projects = uiState.projects,
                        songs = uiState.songsInActiveProject,
                        activeProject = uiState.activeProject,
                        activeSong = uiState.activeSong,
                        transport = transport,
                        meters = meters,
                        isImportingAudio = uiState.isImportingAudio,
                        resolveFileName = { uri -> viewModel.resolveFileDisplayName(context, uri) },
                        onSelectProject = viewModel::selectProject,
                        onSelectSong = viewModel::selectSong,
                        onCreateProject = viewModel::createProject,
                        onCreateSong = viewModel::createSong,
                        onImportSong = { uri, title, bpm, asNew ->
                            viewModel.importSongFromUri(context, uri, title, bpm, asNew)
                        },
                        onTogglePlay = viewModel::togglePlayPause,
                        onPlayWithCountIn = viewModel::playWithCountIn,
                        onStop = viewModel::stopPlayback,
                        onChangeBpm = viewModel::setBpm
                    )

                    val activeSong = uiState.activeSong
                    if (uiState.isLoading || activeSong == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ClickCyan)
                        }
                    } else {
                        when (currentTab) {
                            StudioTab.MIXER -> MixerScreen(
                                song = activeSong,
                                transport = transport,
                                meters = meters,
                                hardware = hardware,
                                onVolumeChange = viewModel::updateChannelVolume,
                                onPanChange = viewModel::updateChannelPan,
                                onToggleMute = viewModel::toggleChannelMute,
                                onToggleSolo = viewModel::toggleChannelSolo,
                                onOutputRouteChange = viewModel::updateChannelOutputRoute,
                                onApplyPreset = viewModel::applyLiveStereoPreset,
                                onAddTrack = viewModel::addAdditionalTrack,
                                onRemoveTrack = viewModel::removeAdditionalTrack,
                                onImportWav = { chId, uri -> viewModel.importChannelWav(context, chId, uri) },
                                onSeekToBar = viewModel::seekToBar,
                                onAuditionCue = viewModel::auditionVoiceCue
                            )

                            StudioTab.TIMELINE -> TimelineScreen(
                                song = activeSong,
                                transport = transport,
                                onSeekToBar = viewModel::seekToBar,
                                onAuditionCue = viewModel::auditionVoiceCue,
                                onUpdateTotalBars = viewModel::updateTotalBars,
                                onUpdateSectionRange = viewModel::updateSectionRange,
                                onAddCueAtBar = { bar, section ->
                                    viewModel.addVoiceCue(
                                        label = "${section.displayName} Cue B$bar",
                                        phrase = section.displayName,
                                        section = section,
                                        bar = bar,
                                        beat = 1,
                                        pan = activeSong.sections.firstOrNull { it.type == section }?.defaultCuePan ?: 0f
                                    )
                                }
                            )

                            StudioTab.CLICK_CUE -> ClickAndCueScreen(
                                song = activeSong,
                                onSelectMetronomeSound = viewModel::selectMetronomeSound,
                                onUpdateMetronomePanForSound = viewModel::updateMetronomePanForSound,
                                onAuditionMetronome = viewModel::auditionMetronome,
                                onTogglePerCuePan = viewModel::togglePerCuePanEnabled,
                                onUpdateCuePan = viewModel::updateCuePan,
                                onUpdateCueProfile = viewModel::updateCueSoundProfile,
                                onUpdateCueProfilePan = viewModel::updateCueSoundProfilePan,
                                onUpdateSectionCuePan = viewModel::updateSectionCuePan,
                                onAuditionCue = viewModel::auditionVoiceCue,
                                onAddCue = viewModel::addVoiceCue,
                                onDeleteCue = viewModel::deleteVoiceCue,
                                onImportCueProfileWav = { profileId, uri ->
                                    viewModel.importCueProfileWav(context, profileId, uri)
                                }
                            )

                            StudioTab.AUTOMATION -> AutomationScreen(
                                song = activeSong,
                                transport = transport,
                                onToggleAutomation = viewModel::toggleAutomationEnabled,
                                onAddPoint = viewModel::addAutomationPoint,
                                onUpdatePoint = viewModel::updateAutomationPoint,
                                onDeletePoint = viewModel::deleteAutomationPoint,
                                onApplySweepPreset = viewModel::applySweepAutomationPreset,
                                onApplyChorusJump = viewModel::applyChorusJumpAutomation,
                                onSeekToBar = viewModel::seekToBar
                            )

                            StudioTab.ROUTING -> RoutingScreen(
                                song = activeSong,
                                hardware = hardware,
                                onRefreshDevices = viewModel::refreshAudioDevices,
                                onApplyPreset = viewModel::applyLiveStereoPreset,
                                onOutputRouteChange = viewModel::updateChannelOutputRoute
                            )
                        }
                    }
                }
            }
        }
    }
}
