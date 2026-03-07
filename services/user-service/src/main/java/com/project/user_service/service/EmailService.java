package com.project.user_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {
    public void sendVerificationEmail(String to, String verificationLink) {
        log.info("Sending verification email to {}: {}", to, verificationLink);
    }
}