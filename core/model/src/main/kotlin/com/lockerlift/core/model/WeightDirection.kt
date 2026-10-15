package com.lockerlift.core.model

import kotlinx.serialization.Serializable

/**
 * Direction of progress for a machine's weight stack (Issue #35).
 *
 * NORMAL: the classic case - more selected weight means more load and
 * therefore more progress (e.g. leg press, bench press).
 *
 * INVERSE: assisted machines (e.g. Assist-Dip, Assist-Chin) where the
 * selected plate weight counterbalances the user's body weight, so less
 * selected weight means more load and therefore more progress. Progression
 * on such machines means decreasing the selected assistance weight.
 */
@Serializable
enum class WeightDirection {
    NORMAL,
    INVERSE
}
