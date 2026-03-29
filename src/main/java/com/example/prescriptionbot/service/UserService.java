package com.example.prescriptionbot.service;

import com.example.prescriptionbot.entity.User;
import com.example.prescriptionbot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public User getOrCreateUser(String telegramUserId, String username) {
        log.info("Looking up or creating user for telegramUserId={}", telegramUserId);
        return userRepository.findByTelegramUserId(telegramUserId)
                .orElseGet(() -> {
                    log.info("No user found, creating new user for telegramUserId={}", telegramUserId);
                    User newUser = User.builder()
                            .telegramUserId(telegramUserId)
                            .username(username)
                            .premium(false)
                            .build();
                    User saved = userRepository.save(newUser);
                    log.info("Created new user with id={}", saved.getId());
                    return saved;
                });
    }

    @Transactional
    public User getOrCreateUser(String telegramUserId) {
        return getOrCreateUser(telegramUserId, null);
    }
}
