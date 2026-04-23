package com.kad.jet.security.service;

import com.kad.jet.student.model.StudentProfile;
import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return (UserDetails) userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(()->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
    }
}
