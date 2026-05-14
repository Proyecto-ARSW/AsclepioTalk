package arsw.asclepio.talk.domain.conversation;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversations", schema = "talk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Daniel Useche
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConversationType type;

    @Column(length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "hospital_id", nullable = false)
    private Integer hospitalId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "is_anonymous", nullable = false)
    @Builder.Default
    private boolean anonymous = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Participant> participants = new ArrayList<>();

    // Garantiza que updatedAt nunca sea null en el INSERT. Hibernate
    // @UpdateTimestamp lo establece al guardar por primera vez, pero algunas
    // combinaciones de dialecto/driver lo dejan null hasta el primer UPDATE,
    // provocando que el frontend recibiera updatedAt vacío y mostrara
    // "Invalid date" hasta que el grupo se editaba por primera vez.
    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
}
