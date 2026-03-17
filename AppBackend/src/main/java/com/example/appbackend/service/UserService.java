package com.example.appbackend.service;

import com.example.appbackend.dto.*;
import jakarta.validation.Valid;

public interface UserService {

    UserResponse register(RegisterRequest request);



    UserResponse applogin(@Valid LoginRequest request);

    UserResponse weblogin(@Valid LoginRequest request);

    UserResponse current(Long id);

    void password(String username, @Valid PasswordChangeRequest request);

    PageResponse<UserListItem> getUserList(Integer page, Integer size, String username, String role, Integer status);

    void updateUser(Long id, UserUpdateRequest request);

    void enableUser(Long id);

    void disableUser(Long id);

    void resetPassword(Long id, String newPassword);
}
