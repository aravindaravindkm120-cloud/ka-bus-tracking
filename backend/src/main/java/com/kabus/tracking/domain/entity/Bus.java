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

@Entity
@Table(name = "buses")
@Getter
@Setter
@NoArgsConstructor
public class Bus extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "town_id", nullable = false)
    private Town town;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    @Column(name = "registration_no", nullable = false, length = 20, unique = true)
    private String registrationNo;

    @Column(name = "bus_type", nullable = false, length = 40)
    private String busType = "ORDINARY";

    @Column(name = "capacity", nullable = false)
    private Integer capacity = 40;

    @Column(name = "fuel_type", nullable = false, length = 20)
    private String fuelType = "DIESEL";

    @Column(name = "make_model", length = 80)
    private String makeModel;

    @Column(name = "manufacture_year")
    private Integer manufactureYear;

    @Column(name = "gps_device_id", length = 60)
    private String gpsDeviceId;

    @Column(name = "gps_enabled", nullable = false)
    private boolean gpsEnabled = true;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}