package com.vellum.category.repository;

import com.vellum.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByName(String name);

    @Query("SELECT c FROM Category c " +
           "WHERE c.parentCategoryId IS NULL " +
           "ORDER BY c.name ASC")
    List<Category> findRootCategories();

    List<Category> findByParentCategoryIdOrderByNameAsc(
            Integer parentCategoryId);

    @Query("SELECT c FROM Category c " +
           "ORDER BY c.parentCategoryId ASC NULLS FIRST, c.name ASC")
    List<Category> findAllOrdered();

    @Query("SELECT c FROM Category c WHERE c.categoryId IN :ids")
    List<Category> findByIds(@Param("ids") List<Integer> ids);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Category c " +
           "SET c.postCount = c.postCount + 1 " +
           "WHERE c.categoryId = :categoryId")
    void incrementPostCount(@Param("categoryId") Integer categoryId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Category c " +
           "SET c.postCount = GREATEST(c.postCount - 1, 0) " +
           "WHERE c.categoryId = :categoryId")
    void decrementPostCount(@Param("categoryId") Integer categoryId);

    boolean existsByParentCategoryId(Integer parentCategoryId);
}
