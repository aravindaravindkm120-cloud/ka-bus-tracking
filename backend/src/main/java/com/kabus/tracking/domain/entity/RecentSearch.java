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
@Table(name = "recent_searches")
@Getter
@Setter
@NoArgsConstructor
public class RecentSearch extends BaseEntity {

    @Column(name = "device_id", nullable = false, length = 80)
    private String deviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "from_name", nullable = false, length = 160)
    private String fromName;

    @Column(name = "to_name", nullable = false, length = 160)
    private String toName;
}