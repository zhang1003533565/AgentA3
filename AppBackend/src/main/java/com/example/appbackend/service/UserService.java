package com.example.appbackend.service;

import com.example.appbackend.dto.UserResponse;
import com.example.appbackend.dto.LoginRequest;
import com.example.appbackend.dto.RegisterRequest;

public interface UserService {

    UserResponse register(RegisterRequest request);

    UserResponse login(LoginRequest request);
}
