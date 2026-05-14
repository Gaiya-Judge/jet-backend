package com.kad.jet.security.service;

import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {

        if (identifier.contains("@")) {
            return userRepository.findByEmailIgnoreCase(identifier)
                    .orElseThrow(() -> new UsernameNotFoundException(
                            "User not found: " + identifier));
        }

        return studentProfileRepository
                .findByMatricNumber(identifier)
                .map(profile -> profile.getUser())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Student not found: " + identifier));

    }
}
