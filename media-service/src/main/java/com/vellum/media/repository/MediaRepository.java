package com.vellum.media.repository;

import com.vellum.media.entity.Media;
import org.springframework.data.domain.Page;
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
public interface MediaRepository extends JpaRepository<Media, Integer> {

    List<Media> findByUploaderIdAndIsDeletedFalseOrderByUploadedAtDesc(
            Integer uploaderId);

    List<Media> findByLinkedPostIdAndIsDeletedFalse(
            Integer linkedPostId);

    @Query("SELECT m FROM Media m " +
            "WHERE m.uploaderId = :uploaderId " +
            "AND m.mimeType LIKE CONCAT(:typePrefix, '%') " +
            "AND m.isDeleted = false " +
            "ORDER BY m.uploadedAt DESC")
    List<Media> findByUploaderIdAndMimeTypePrefix(
            @Param("uploaderId") Integer uploaderId,
            @Param("typePrefix") String typePrefix);

    Page<Media> findByIsDeletedFalseOrderByUploadedAtDesc(
            Pageable pageable);

    List<Media> findByIsDeletedTrue();

    @Query("SELECT COALESCE(SUM(m.sizeKb), 0) " +
            "FROM Media m " +
            "WHERE m.uploaderId = :uploaderId " +
            "AND m.isDeleted = false")
    Long getTotalSizeKbByUploader(@Param("uploaderId") Integer uploaderId);

    long countByUploaderIdAndIsDeletedFalse(Integer uploaderId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Media m " +
            "SET m.isDeleted = true " +
            "WHERE m.mediaId = :mediaId")
    void softDelete(@Param("mediaId") Integer mediaId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Media m " +
            "SET m.altText = :altText " +
            "WHERE m.mediaId = :mediaId")
    void updateAltText(
            @Param("mediaId") Integer mediaId,
            @Param("altText") String altText);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Media m " +
            "SET m.linkedPostId = :postId " +
            "WHERE m.mediaId = :mediaId")
    void linkToPost(
            @Param("mediaId") Integer mediaId,
            @Param("postId") Integer postId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Media m " +
            "SET m.linkedPostId = null " +
            "WHERE m.mediaId = :mediaId")
    void unlinkFromPost(@Param("mediaId") Integer mediaId);

    Optional<Media> findByMediaIdAndIsDeletedFalse(Integer mediaId);
}