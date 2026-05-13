package com.vellum.newsletter.repository;

import com.vellum.newsletter.entity.Subscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriberRepository
        extends JpaRepository<Subscriber, Integer> {

    Optional<Subscriber> findByEmail(String email);

    Optional<Subscriber> findByToken(String token);

    Optional<Subscriber> findByUserId(Integer userId);

    boolean existsByEmail(String email);

    /*
     * Get all ACTIVE subscribers for newsletter sending.
     * Used for: full campaign blast.
     */
    List<Subscriber> findByStatus(Subscriber.SubscriberStatus status);

    /*
     * Get active subscribers with specific preference tag.
     * Uses LIKE to check if preference exists in comma-separated string.
     */
    @Query("SELECT s FROM Subscriber s " +
            "WHERE s.status = 'ACTIVE' " +
            "AND s.preferences LIKE CONCAT('%', :preference, '%')")
    List<Subscriber> findActiveByPreference(
            @Param("preference") String preference);

    // Count subscribers by status (for analytics)
    long countByStatus(Subscriber.SubscriberStatus status);

    /*
     * Update subscriber status.
     * Used for: confirming subscription, unsubscribing.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Subscriber s SET s.status = :status " +
            "WHERE s.subscriberId = :id")
    void updateStatus(
            @Param("id") Integer id,
            @Param("status") Subscriber.SubscriberStatus status);

    /*
     * Update preferences for a subscriber.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Subscriber s SET s.preferences = :preferences " +
            "WHERE s.subscriberId = :id")
    void updatePreferences(
            @Param("id") Integer id,
            @Param("preferences") String preferences);
}