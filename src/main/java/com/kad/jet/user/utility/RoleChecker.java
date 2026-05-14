package com.kad.jet.user.utility;

import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import org.springframework.stereotype.Service;

public class RoleChecker {

    public static boolean is_superAdmin(_User user){
        return user != null && user.getUserRole() == UserRole.SUPER_ADMIN;
    }

    public static boolean is_itAdmin(_User user){
        return user != null && user.getUserRole() == UserRole.IT_ADMIN;
    }

    public static boolean is_registrar(_User user){
        return user != null && user.getUserRole() == UserRole.REGISTRAR;
    }

    public static boolean is_dean(_User user){
        return user != null && user.getUserRole() == UserRole.DEAN;
    }

    public static boolean is_hod(_User user){
        return user != null && user.getUserRole() == UserRole.HOD;
    }

    public static boolean is_bursary(_User user){
        return user != null && user.getUserRole() == UserRole.BURSAR;
    }

    public static boolean is_accountant(_User user){
        return user != null && user.getUserRole() == UserRole.ACCOUNTANT;
    }

    public static boolean is_student(_User user){
        return user != null && user.getUserRole() == UserRole.STUDENT;
    }

    public static boolean hasAnyRole(_User user, UserRole... roles){
        if (user == null || user.getUserRole() == null){
            return false;
        }

        for (UserRole role : roles){
            if (user.getUserRole() == role){
                return true;
            }
        }
        return false;
    }
}

