package com.kad.jet.security;

import com.kad.jet.student.model.StudentProfile;
import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.user.model._user;
import com.kad.jet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetailsService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        _user user;

        if (identifier.contains("@")){
            user = userRepository.findByEmail(identifier)
                    .orElseThrow(()-> new UsernameNotFoundException(
                            "user not found " + identifier
                    ));
        }else {
            user = studentProfileRepository.findByMatricNumber(identifier)
                    .map(StudentProfile::getUser)
                    .orElseThrow(()-> new UsernameNotFoundException(
                            "student not found " + identifier
                    ));
        }

        return new User(
                user.getEmail(),
                user.getPasswordHashed(),
                List.of(new SimpleGrantedAuthority(
                        "ROLE_" + user.getUserRole().name()
                ))
        );
    }
}
