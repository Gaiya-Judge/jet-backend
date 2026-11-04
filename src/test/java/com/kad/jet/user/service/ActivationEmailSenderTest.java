package com.kad.jet.user.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActivationEmailSenderTest {

    @Test
    void sendsConfiguredActivationEmailWithoutLoggingOrReturningLink() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(provider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(message);

        ActivationEmailSender sender = new ActivationEmailSender(provider);
        ReflectionTestUtils.setField(sender, "fromAddress", "no-reply@example.test");
        ReflectionTestUtils.setField(sender, "senderName", "JET Test");
        ReflectionTestUtils.setField(sender, "frontendBaseUrl", "https://frontend.example.test");

        sender.sendActivationEmail("user@example.test", "test-activation-token");

        verify(mailSender).send(message);
        assertEquals("user@example.test", message.getAllRecipients()[0].toString());
        String body = (String) message.getContent();
        assertTrue(body.contains("https://frontend.example.test/activate?token=test-activation-token"));
        assertTrue(body.contains("can only be used once"));
    }
}
