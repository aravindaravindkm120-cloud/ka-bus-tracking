package com.kabus.tracking.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "bus_assignments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_bus_assign_active", columnNames = {"trip_id", "bus_id", "status"})
})
@Getter
@Setter
@NoArgsConstructor
public class BusAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bus_id", nullable = false)
    private Bus bus;

    @Column(name = "assigned_from")
    private LocalDateTime assignedFrom;

    @Column(name = "assigned_to")
    private LocalDateTime assignedTo;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";
}