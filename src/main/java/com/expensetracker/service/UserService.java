package com.expensetracker.service;

import com.expensetracker.model.User;
import com.expensetracker.repository.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    UserRepo userRepo;

    public User getUser(Long id) {
        return userRepo.getReferenceById(id);
    }

    public Optional<User> findUser(Long id) {
        return userRepo.findById(id);
    }
}
