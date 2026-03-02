package com.company.common.model;

public enum Severity {
    P1_CRITICAL,   // Full outage, immediate action
    P2_HIGH,       // Major degradation, urgent
    P3_MEDIUM,     // Partial degradation, investigate
    P4_LOW;        // Minor issue, monitor

    public boolean isHighPriority() {
        return this == P1_CRITICAL || this == P2_HIGH;
    }
}
