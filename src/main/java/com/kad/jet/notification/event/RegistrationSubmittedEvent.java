package com.kad.jet.notification.event;

import com.kad.jet.registration.model.Registration;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

public record RegistrationSubmittedEvent(UUID registrationId, UUID studentId){
}
