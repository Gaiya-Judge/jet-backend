//package com.kad.jet.security.service;
//
//import com.kad.jet.user.model._User;
//import org.springframework.security.core.GrantedAuthority;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.core.userdetails.UserDetails;
//
//import java.util.Collection;
//import java.util.List;
//import java.util.Set;
//
//public class CustomUserDetails implements UserDetails {
//
//    private final  _User user;
//
//    public CustomUserDetails(_User user) {
//        this.user = user;
//    }
//
//    @Override
//    public Collection<? extends GrantedAuthority> getAuthorities() {
//        return Set.of(
//                new SimpleGrantedAuthority("ROLE_" + user.getUserRole().name())
//        );
//    }
//
//    @Override
//    public String getPassword() {
//        return user.getPassword();
//    }
//
//    @Override
//    public String getUsername() {
//        return user.getEmail();
//    }
//
//    @Override
//    public boolean isAccountNonExpired() {
//        return true;
//    }
//
//    @Override
//    public boolean isAccountNonLocked() {
//        return !user.getIsBanned();
//    }
//
//    @Override
//    public boolean isCredentialsNonExpired() {
//        return true;
//    }
//
//    @Override
//    public boolean isEnabled() {
//        return user.getIsActive();
//    }
//
//    public _User getUser() {
//        return user;
//    }
//}
