package com.lockerlift.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SetType {
    WARMUP,
    NORMAL,
    DROPSET,
    MYOREPS,
    FAILURE
}
