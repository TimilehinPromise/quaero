package org.timmy.knowcommute.figure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "figure_descriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FigureDescription {

    @Id
    @Column(name = "image_hash", length = 64)
    private String imageHash;

    @Column(columnDefinition = "text")
    private String knowledge;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public FigureDescription(String imageHash, String knowledge) {
        this.imageHash = imageHash;
        this.knowledge = knowledge;
    }

    @PrePersist
    void onInsert() {
        createdAt = Instant.now();
    }
}
