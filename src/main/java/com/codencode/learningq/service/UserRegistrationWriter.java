package com.codencode.learningq.service;

import java.time.Clock;

import com.codencode.learningq.entity.User;
import com.codencode.learningq.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationWriter {
    private final UserRepository users;
    private final Clock clock;

    public UserRegistrationWriter(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public void create(String email, String emailNormalized, String encodedPassword, String displayName) {
        users.saveAndFlush(User.create(email, emailNormalized, encodedPassword, displayName, clock.instant()));
    }
}
