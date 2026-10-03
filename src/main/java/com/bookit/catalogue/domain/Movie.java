package com.bookit.catalogue.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "movie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movie {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "synopsis", columnDefinition = "TEXT")
    private String synopsis;

    @Column(name = "runtime_minutes", nullable = false)
    private int runtimeMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate", nullable = false, length = 10)
    @Builder.Default
    private MovieCertificate certificate = MovieCertificate.UA;

    @Column(name = "release_date", nullable = false)
    private LocalDate releaseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MovieStatus status = MovieStatus.RELEASED;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "movie_language", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "language_code")
    @Builder.Default
    private Set<String> languages = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "movie_genre", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "genre")
    @Builder.Default
    private Set<String> genres = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "movie_supported_format", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "format_id")
    @Builder.Default
    private Set<UUID> supportedFormatIds = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}