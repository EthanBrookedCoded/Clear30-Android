package org.clear30.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** App modes — ported from `AppMode` (UserInfo.swift). */
@Serializable
enum class AppMode {
    @SerialName("counselor") COUNSELOR,
    @SerialName("b2b") B2B,
    @SerialName("nys") NYS,
    @SerialName("adolescent") ADOLESCENT,
}

/** Sign-up channel — ported from `SignUpType`. */
@Serializable
enum class SignUpType {
    @SerialName("phone") PHONE,
    @SerialName("email") EMAIL,
    @SerialName("apple") APPLE,
}

/** Paid tier — ported from `EntitlementType`. */
@Serializable
enum class EntitlementType {
    @SerialName("Core") CORE,
    @SerialName("Plus") PLUS;

    companion object {
        val DEFAULT = PLUS
    }
}
