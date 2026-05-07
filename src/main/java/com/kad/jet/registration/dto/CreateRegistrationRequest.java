package com.kad.jet.registration.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateRegistrationRequest {

    private UUID studentId;

    private String assistReason;
}
