package org.timmy.knowcommute.figure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "bookmark_figures")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookmarkFigure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bookmark_id", nullable = false)
    private UUID bookmarkId;

    @Column(nullable = false)
    private int ordinal;

    @Column(name = "image_url", nullable = false, columnDefinition = "text")
    private String imageUrl;

    @Column(name = "alt_text", columnDefinition = "text")
    private String altText;

    @Column(columnDefinition = "text")
    private String caption;

    @Column(name = "preceding_paragraph", columnDefinition = "text")
    private String precedingParagraph;

    @Column(name = "following_paragraph", columnDefinition = "text")
    private String followingParagraph;

    public BookmarkFigure(UUID bookmarkId, int ordinal, String imageUrl, String altText, String caption,
                          String precedingParagraph, String followingParagraph) {
        this.bookmarkId = bookmarkId;
        this.ordinal = ordinal;
        this.imageUrl = imageUrl;
        this.altText = altText;
        this.caption = caption;
        this.precedingParagraph = precedingParagraph;
        this.followingParagraph = followingParagraph;
    }
}
