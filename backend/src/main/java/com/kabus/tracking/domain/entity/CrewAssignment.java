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
@Table(name = "crew_assignments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_crew_assign_active", columnNames = {"trip_id", "crew_id", "status"})
})
@Getter
@Setter
@NoArgsConstructor
public class CrewAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crew_id", nullable = false)
    private Crew crew;

    @Column(name = "crew_type", nullable = false, length = 40)
    private String crewType;

    @Column(name = "assigned_from")
    private LocalDateTime assignedFrom;

    @Column(name = "assigned_to")
    private LocalDateTime assignedTo;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";
}