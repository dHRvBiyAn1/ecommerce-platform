package com.project.notification.config;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class MockJavaMailSender implements JavaMailSender {

    @Override
    public MimeMessage createMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
        try {
            return new MimeMessage(Session.getInstance(new Properties()), contentStream);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create MimeMessage from stream", e);
        }
    }

    @Override
    public void send(MimeMessage mimeMessage) throws MailException {
        try {
            String from = "unknown";
            if (mimeMessage.getFrom() != null && mimeMessage.getFrom().length > 0) {
                from = mimeMessage.getFrom()[0].toString();
            }
            String to = "unknown";
            if (mimeMessage.getAllRecipients() != null && mimeMessage.getAllRecipients().length > 0) {
                to = mimeMessage.getAllRecipients()[0].toString();
            }
            log.info("[SPRING MAIL MOCK] Outbound email intercepted: from='{}', to='{}', subject='{}'", 
                    from, to, mimeMessage.getSubject());
        } catch (Exception e) {
            log.error("Failed to intercept MimeMessage in Mock JavaMailSender: {}", e.getMessage());
        }
    }

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {
        for (MimeMessage msg : mimeMessages) {
            send(msg);
        }
    }

    @Override
    public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
        MimeMessage mimeMessage = createMimeMessage();
        try {
            mimeMessagePreparator.prepare(mimeMessage);
            send(mimeMessage);
        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare MimeMessage", e);
        }
    }

    @Override
    public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
        for (MimeMessagePreparator prep : mimeMessagePreparators) {
            send(prep);
        }
    }

    @Override
    public void send(SimpleMailMessage simpleMessage) throws MailException {
        log.info("[SPRING MAIL MOCK] Outbound simple email intercepted: from='{}', to='{}', subject='{}', body='{}'",
                simpleMessage.getFrom(),
                simpleMessage.getTo() != null && simpleMessage.getTo().length > 0 ? simpleMessage.getTo()[0] : "unknown",
                simpleMessage.getSubject(),
                simpleMessage.getText());
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
        for (SimpleMailMessage msg : simpleMessages) {
            send(msg);
        }
    }
}
