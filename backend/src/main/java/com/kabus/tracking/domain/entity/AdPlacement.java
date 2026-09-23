package com.kabus.tracking.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ad_placements")
@Getter
@Setter
@NoArgsConstructor
public class AdPlacement extends BaseEntity {

    @Column(name = "code", nullable = false, length = 60, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds = 5;

    @Column(name = "frequency_seconds", nullable = false)
    private Integer frequencySeconds = 0;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}