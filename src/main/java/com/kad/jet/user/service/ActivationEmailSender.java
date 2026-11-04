package com.kad.jet.user.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class ActivationEmailSender {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${activation.email.from:}")
    private String fromAddress;

    @Value("${activation.email.sender-name:JET}")
    private String senderName;

    @Value("${activation.frontend-base-url:}")
    private String frontendBaseUrl;

    public void sendActivationEmail(String recipient, String rawToken) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || fromAddress == null || fromAddress.isBlank()
                || frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            throw new EmailDeliveryException(true, "SMTP_CONFIGURATION_MISSING");
        }

        String baseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        String activationLink = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/activate")
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress, senderName);
            helper.setTo(recipient);
            helper.setSubject("Activate your JET account");
            helper.setText("Your JET account is ready. Create your password and activate it using this link:\n\n"
                    + activationLink + "\n\nThis link expires after the configured activation period " +
                    "and can only be used once.");
            mailSender.send(message);
        } catch (MailException e) {
            throw classify(e);
        } catch (Exception e) {
            throw new EmailDeliveryException(true, "EMAIL_MESSAGE_INVALID");
        }
    }

    private EmailDeliveryException classify(MailException exception) {
        if (exception instanceof MailSendException sendException
                && sendException.getFailedMessages() != null
                && !sendException.getFailedMessages().isEmpty()) {
            for (Exception failure : sendException.getFailedMessages().values()) {
                Integer returnCode = smtpReturnCode(failure);
                if (returnCode != null && returnCode >= 500) {
                    return new EmailDeliveryException(true, "SMTP_PERMANENT_FAILURE");
                }
            }
            return new EmailDeliveryException(false, "SMTP_DELIVERY_FAILED");
        }
        return new EmailDeliveryException(false, "SMTP_TEMPORARY_FAILURE");
    }

    private Integer smtpReturnCode(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (!cause.getClass().getSimpleName().startsWith("SMTP")) {
                continue;
            }
            try {
                Object code = cause.getClass().getMethod("getReturnCode").invoke(cause);
                if (code instanceof Integer integer) {
                    return integer;
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall through to the bounded retry policy when the SMTP code is unavailable.
            }
        }
        return null;
    }
}
