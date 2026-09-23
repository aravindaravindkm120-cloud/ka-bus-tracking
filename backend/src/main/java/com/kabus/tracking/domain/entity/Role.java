package com.kabus.tracking.domain.entity;

import com.kabus.tracking.domain.enums.RoleCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role extends BaseEntity {

    @Column(name = "code", nullable = false, length = 40, unique = true)
    @Enumerated(EnumType.STRING)
    private RoleCode code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    public Role(RoleCode code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }
}