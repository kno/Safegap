package com.safegap.core.model

enum class AlertLevel(val severity: Int) {
    SAFE(0),
    WARNING(1),
    CRITICAL(2),
}
