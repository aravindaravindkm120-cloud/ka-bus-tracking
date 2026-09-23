package com.kabus.tracking.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "crew")
@Getter
@Setter
@NoArgsConstructor
public class Crew extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "badge_no", nullable = false, length = 30, unique = true)
    private String badgeNo;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "crew_type", nullable = false, length = 40)
    private String crewType;

    @Column(name = "license_no", length = 40)
    private String licenseNo;

    @Column(name = "license_expiry")
    private LocalDate licenseExpiry;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "duty_status", nullable = false, length = 20)
    private String dutyStatus = "OFF_DUTY";
}