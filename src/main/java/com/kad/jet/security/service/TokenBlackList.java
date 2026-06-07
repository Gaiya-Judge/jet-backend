package com.kad.jet.security.service;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlackList {

    Set<String> blacklist = ConcurrentHashMap.newKeySet();

    public void blackListToken(String token){
        blacklist.add(token);
    }

    public boolean isTokenValid(String token){
        return !blacklist.contains(token);
    }
}
