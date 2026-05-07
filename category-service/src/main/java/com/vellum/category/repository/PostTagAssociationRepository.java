package com.vellum.category.repository;

import com.vellum.category.entity.PostTagAssociation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface PostTagAssociationRepository
        extends JpaRepository<PostTagAssociation, Integer> {

    List<PostTagAssociation> findByPostId(Integer postId);

    List<PostTagAssociation> findByTagId(Integer tagId);

    boolean existsByPostIdAndTagId(Integer postId, Integer tagId);

    @Modifying
    @Transactional
    void deleteByPostIdAndTagId(Integer postId, Integer tagId);

    @Modifying
    @Transactional
    void deleteByPostId(Integer postId);

    @Query("SELECT a.tagId FROM PostTagAssociation a " +
           "WHERE a.postId = :postId")
    List<Integer> findTagIdsByPostId(@Param("postId") Integer postId);

    long countByTagId(Integer tagId);
}
