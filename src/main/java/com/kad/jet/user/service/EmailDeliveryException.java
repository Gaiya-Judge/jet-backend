package com.kad.jet.user.service;

public class EmailDeliveryException extends RuntimeException {

    private final boolean permanent;
    private final String errorCode;

    public EmailDeliveryException(boolean permanent, String errorCode) {
        super(errorCode);
        this.permanent = permanent;
        this.errorCode = errorCode;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
