package com.vellum.category.repository;

import com.vellum.category.entity.Category;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("CategoryRepository Tests")
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    private Category rootCategory;
    private Category childCategory;

    @BeforeEach
    void setUp() {
        rootCategory = categoryRepository.save(Category.builder()
                .name("Technology")
                .slug("technology")
                .description("Tech articles")
                .parentCategoryId(null)
                .postCount(5)
                .build());

        childCategory = categoryRepository.save(Category.builder()
                .name("Backend")
                .slug("backend")
                .description("Backend development")
                .parentCategoryId(rootCategory.getCategoryId())
                .postCount(3)
                .build());
    }

    @Test
    @DisplayName("findBySlug: returns category when exists")
    void findBySlug_exists_returnsCategory() {
        Optional<Category> result =
                categoryRepository.findBySlug("technology");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Technology");
    }

    @Test
    @DisplayName("findRootCategories: returns only top-level categories")
    void findRootCategories_returnsOnlyRoots() {
        List<Category> roots =
                categoryRepository.findRootCategories();

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).getName()).isEqualTo("Technology");
        assertThat(roots.get(0).getParentCategoryId()).isNull();
    }

    @Test
    @DisplayName("findByParentCategoryId: returns children of a parent")
    void findByParentCategoryId_returnsChildren() {
        List<Category> children = categoryRepository
                .findByParentCategoryIdOrderByNameAsc(
                        rootCategory.getCategoryId());

        assertThat(children).hasSize(1);
        assertThat(children.get(0).getName()).isEqualTo("Backend");
    }

    @Test
    @DisplayName("existsByParentCategoryId: true when children exist")
    void existsByParentCategoryId_hasChildren_returnsTrue() {
        assertThat(categoryRepository.existsByParentCategoryId(
                rootCategory.getCategoryId())).isTrue();
    }

    @Test
    @DisplayName("existsByParentCategoryId: false when no children")
    void existsByParentCategoryId_noChildren_returnsFalse() {
        assertThat(categoryRepository.existsByParentCategoryId(
                childCategory.getCategoryId())).isFalse();
    }

    @Test
    @DisplayName("findByIds: returns categories matching given IDs")
    void findByIds_returnsMatchingCategories() {
        List<Category> result = categoryRepository.findByIds(
                List.of(rootCategory.getCategoryId(),
                        childCategory.getCategoryId()));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("incrementPostCount: atomically increments")
    void incrementPostCount_increments() {
        int before = rootCategory.getPostCount();

        categoryRepository.incrementPostCount(
                rootCategory.getCategoryId());

        Category updated = categoryRepository
                .findById(rootCategory.getCategoryId()).get();
        assertThat(updated.getPostCount()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("decrementPostCount: does not go below zero")
    void decrementPostCount_noNegative() {
        Category zeroCount = categoryRepository.save(Category.builder()
                .name("Empty Category")
                .slug("empty-category")
                .parentCategoryId(null)
                .postCount(0)
                .build());

        categoryRepository.decrementPostCount(zeroCount.getCategoryId());

        Category updated = categoryRepository
                .findById(zeroCount.getCategoryId()).get();
        assertThat(updated.getPostCount()).isEqualTo(0);
    }
}
