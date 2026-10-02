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

/**
 * A bus number: the service number a depot runs, independent of any one
 * physical vehicle. {@link Bus} records reference a bus number rather than
 * carrying the number inline, so the depot's bus-number list is maintained
 * once and shared by every vehicle that runs on it.
 */
@Entity
@Table(name = "bus_numbers")
@Getter
@Setter
@NoArgsConstructor
public class BusNumber extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "town_id", nullable = false)
    private Town town;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "division_id", nullable = false)
    private Division division;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corporation_id", nullable = false)
    private Corporation corporation;

    @Column(name = "bus_number", nullable = false, length = 20)
    private String busNumber;

    @Column(name = "bus_type", nullable = false, length = 40)
    private String busType = "ORDINARY";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
