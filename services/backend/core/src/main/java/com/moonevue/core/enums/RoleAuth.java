package com.moonevue.core.enums;

import lombok.Getter;

@Getter
public enum RoleAuth {
    ADMIN_TENANT(1, "ADMIN_TENANT"),
    ADMIN(2, "ADMIN"),
    EMPLOYED(3, "EMPLOYED"),
    FINANCE(4, "FINANCE"),
    SUPPORT(5, "SUPPORT"),
    USER(6, "USER");

    public final long id;
    public final String name;

    RoleAuth(long id, String name) {
        this.id = id;
        this.name = name;
    }

}
