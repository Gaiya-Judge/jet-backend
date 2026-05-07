package com.kad.jet.user.mapper;

import com.kad.jet.user.dto.UserResponse;
import com.kad.jet.user.model._User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public static UserResponse toDto(_User user){
        return UserResponse
                .builder()
                .userRole(user.getUserRole().name())
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }
}
