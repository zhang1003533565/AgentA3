package com.example.appbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserResponse {

    private String token;
    private String username;
    private String role;
    private String phone;
}
