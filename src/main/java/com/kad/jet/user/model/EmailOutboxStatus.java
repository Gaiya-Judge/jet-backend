package com.kad.jet.user.model;

public enum EmailOutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    CANCELLED,
    FAILED
}
