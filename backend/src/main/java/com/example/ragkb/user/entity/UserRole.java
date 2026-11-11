package com.example.ragkb.user.entity;

public enum UserRole {
    USER(0),
    ADMIN(1),
    SUPER_ADMIN(2);

    private final int level;

    UserRole(int level) {
        this.level = level;
    }

    public boolean isAdmin() {
        return this != USER;
    }

    public boolean outranks(UserRole other) {
        return level > other.level;
    }
}
