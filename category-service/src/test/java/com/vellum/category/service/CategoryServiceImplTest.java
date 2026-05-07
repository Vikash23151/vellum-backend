package com.vellum.category.service;

import com.vellum.category.dto.*;
import com.vellum.category.entity.Category;
import com.vellum.category.entity.PostTagAssociation;
import com.vellum.category.entity.Tag;
import com.vellum.category.exception.CustomException;
import com.vellum.category.repository.CategoryRepository;
import com.vellum.category.repository.PostTagAssociationRepository;
import com.vellum.category.repository.TagRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryServiceImpl Unit Tests")
class CategoryServiceImplTest {

    @Mock private CategoryRepository           categoryRepository;
    @Mock private TagRepository                tagRepository;
    @Mock private PostTagAssociationRepository postTagRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private Category techCategory;
    private Tag      javaTag;

    @BeforeEach
    void setUp() {
        techCategory = Category.builder()
                .categoryId(1)
                .name("Technology")
                .slug("technology")
                .parentCategoryId(null)
                .postCount(5)
                .build();

        javaTag = Tag.builder()
                .tagId(1)
                .name("java")
                .slug("java")
                .postCount(10)
                .build();
    }

    @Test
    @DisplayName("createCategory: success creates new category")
    void createCategory_success() {
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Technology");

        when(categoryRepository.existsByName("Technology"))
                .thenReturn(false);
        when(categoryRepository.existsBySlug(anyString()))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class)))
                .thenReturn(techCategory);

        CategoryResponse result =
                categoryService.createCategory(request);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Technology");
        verify(categoryRepository, times(1)).save(any(Category.class));
    }

    @Test
    @DisplayName("createCategory: throws 409 when name already exists")
    void createCategory_duplicateName_throwsConflict() {
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Technology");

        when(categoryRepository.existsByName("Technology"))
                .thenReturn(true);

        assertThatThrownBy(() ->
                categoryService.createCategory(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(
                        ((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("getCategoryTree: builds correct tree structure")
    void getCategoryTree_buildsTree() {
        Category backend = Category.builder()
                .categoryId(2)
                .name("Backend")
                .slug("backend")
                .parentCategoryId(1)
                .postCount(3)
                .build();

        when(categoryRepository.findAllOrdered())
                .thenReturn(List.of(techCategory, backend));

        List<CategoryResponse> tree =
                categoryService.getCategoryTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getName()).isEqualTo("Technology");
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getName())
                .isEqualTo("Backend");
    }

    @Test
    @DisplayName("deleteCategory: throws when category has children")
    void deleteCategory_hasChildren_throwsBadRequest() {
        when(categoryRepository.findById(1))
                .thenReturn(Optional.of(techCategory));
        when(categoryRepository.existsByParentCategoryId(1))
                .thenReturn(true);

        assertThatThrownBy(() ->
                categoryService.deleteCategory(1))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("subcategories")
                .satisfies(ex -> assertThat(
                        ((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteCategory: success when no children")
    void deleteCategory_noChildren_deletes() {
        when(categoryRepository.findById(1))
                .thenReturn(Optional.of(techCategory));
        when(categoryRepository.existsByParentCategoryId(1))
                .thenReturn(false);

        assertThatCode(() -> categoryService.deleteCategory(1))
                .doesNotThrowAnyException();

        verify(categoryRepository, times(1)).delete(techCategory);
    }

    @Test
    @DisplayName("addTagToPost: success adds association and increments count")
    void addTagToPost_success() {
        when(tagRepository.findById(1))
                .thenReturn(Optional.of(javaTag));
        when(postTagRepository.existsByPostIdAndTagId(100, 1))
                .thenReturn(false);

        categoryService.addTagToPost(100, 1);

        verify(postTagRepository, times(1))
                .save(any(PostTagAssociation.class));
        verify(tagRepository, times(1)).incrementPostCount(1);
    }

    @Test
    @DisplayName("addTagToPost: throws 409 when already tagged")
    void addTagToPost_alreadyTagged_throwsConflict() {
        when(tagRepository.findById(1))
                .thenReturn(Optional.of(javaTag));
        when(postTagRepository.existsByPostIdAndTagId(100, 1))
                .thenReturn(true);

        assertThatThrownBy(() ->
                categoryService.addTagToPost(100, 1))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(
                        ((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(postTagRepository, never()).save(any());
    }

    @Test
    @DisplayName("getTagsByPost: returns tags for a post")
    void getTagsByPost_returnsTags() {
        when(postTagRepository.findTagIdsByPostId(100))
                .thenReturn(List.of(1));
        when(tagRepository.findByIds(List.of(1)))
                .thenReturn(List.of(javaTag));

        List<TagResponse> tags = categoryService.getTagsByPost(100);

        assertThat(tags).hasSize(1);
        assertThat(tags.get(0).getName()).isEqualTo("java");
    }
}
