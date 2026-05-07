package com.vellum.category.repository;

import com.vellum.category.entity.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Integer> {

    Optional<Tag> findBySlug(String slug);

    Optional<Tag> findByName(String name);

    boolean existsBySlug(String slug);

    boolean existsByName(String name);

    @Query("SELECT t FROM Tag t " +
           "WHERE t.postCount > 0 " +
           "ORDER BY t.postCount DESC")
    List<Tag> findTrendingTags(Pageable pageable);

    List<Tag> findAllByOrderByNameAsc();

    @Query("SELECT t FROM Tag t " +
           "WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "ORDER BY t.postCount DESC")
    List<Tag> searchByName(@Param("query") String query);

    @Query("SELECT t FROM Tag t WHERE t.tagId IN :ids")
    List<Tag> findByIds(@Param("ids") List<Integer> ids);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Tag t " +
           "SET t.postCount = t.postCount + 1 " +
           "WHERE t.tagId = :tagId")
    void incrementPostCount(@Param("tagId") Integer tagId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Tag t " +
           "SET t.postCount = GREATEST(t.postCount - 1, 0) " +
           "WHERE t.tagId = :tagId")
    void decrementPostCount(@Param("tagId") Integer tagId);
}
