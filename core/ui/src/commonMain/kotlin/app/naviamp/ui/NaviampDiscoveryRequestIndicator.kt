package app.naviamp.ui

import androidx.compose.ui.graphics.vector.ImageVector
import app.naviamp.domain.provider.*
import org.jetbrains.compose.resources.StringResource

/** A submitted request is never presented as a fresh download, nor as proven library ownership. */
internal data class DiscoveryRequestIndicator(val icon: ImageVector, val label: StringResource)

internal fun discoveryRequestIndicator(
    operation: ProviderDiscoveryOperation?,
    acquisition: DiscoveryAcquisitionRecord?,
): DiscoveryRequestIndicator? {
    val phase = acquisition?.phase ?: operation?.acquisitionPhase() ?: return null
    val icon = when (phase) {
        DiscoveryAcquisitionPhase.Queued, DiscoveryAcquisitionPhase.Searching,
        DiscoveryAcquisitionPhase.Processing, DiscoveryAcquisitionPhase.Downloading,
        DiscoveryAcquisitionPhase.Importing, DiscoveryAcquisitionPhase.WaitingForRelease -> NaviampIcons.Clock
        DiscoveryAcquisitionPhase.Available -> NaviampIcons.Library
        DiscoveryAcquisitionPhase.Complete -> NaviampIcons.Check
        DiscoveryAcquisitionPhase.Saved -> NaviampIcons.Cache
        else -> NaviampIcons.Info
    }
    return DiscoveryRequestIndicator(icon, acquisition?.let { acquisitionPhaseMessage(it.phase) }
        ?: discoveryOperationMessage(checkNotNull(operation).status))
}
