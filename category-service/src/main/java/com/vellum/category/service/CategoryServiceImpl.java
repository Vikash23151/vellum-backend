package com.vellum.category.service;

import com.vellum.category.dto.*;
import com.vellum.category.entity.Category;
import com.vellum.category.entity.PostTagAssociation;
import com.vellum.category.entity.Tag;
import com.vellum.category.exception.CustomException;
import com.vellum.category.repository.CategoryRepository;
import com.vellum.category.repository.PostTagAssociationRepository;
import com.vellum.category.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository           categoryRepository;
    private final TagRepository                tagRepository;
    private final PostTagAssociationRepository postTagRepository;

    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        log.info("📁 Creating category: {}", request.getName());

        if (categoryRepository.existsByName(request.getName())) {
            throw new CustomException(
                "Category '" + request.getName() + "' already exists",
                HttpStatus.CONFLICT);
        }

        if (request.getParentCategoryId() != null) {
            categoryRepository.findById(request.getParentCategoryId())
                    .orElseThrow(() -> new CustomException(
                        "Parent category not found: " +
                        request.getParentCategoryId(),
                        HttpStatus.NOT_FOUND));
        }

        String slug = generateUniqueSlug(
                request.getName(), categoryRepository);

        Category category = Category.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .parentCategoryId(request.getParentCategoryId())
                .postCount(0)
                .build();

        Category saved = categoryRepository.save(category);
        log.info("✅ Category created: id={} slug={}", saved.getCategoryId(), slug);
        return CategoryResponse.fromEntity(saved);
    }

    @Override
    public CategoryResponse getCategoryById(Integer categoryId) {
        Category category = findCategoryOrThrow(categoryId);
        return enrichWithParentName(CategoryResponse.fromEntity(category));
    }

    @Override
    public CategoryResponse getCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new CustomException(
                    "Category not found: " + slug, HttpStatus.NOT_FOUND));
        return enrichWithParentName(CategoryResponse.fromEntity(category));
    }

    @Override
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAllOrdered()
                .stream()
                .map(CategoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryResponse> getCategoryTree() {
        List<Category> all = categoryRepository.findAllOrdered();

        Map<Integer, List<CategoryResponse>> childrenMap = all.stream()
                .filter(c -> c.getParentCategoryId() != null)
                .collect(Collectors.groupingBy(
                        Category::getParentCategoryId,
                        Collectors.mapping(
                                CategoryResponse::fromEntity,
                                Collectors.toList())));

        return all.stream()
                .filter(c -> c.getParentCategoryId() == null)
                .map(root -> {
                    CategoryResponse response =
                            CategoryResponse.fromEntity(root);
                    response.setChildren(
                            childrenMap.getOrDefault(
                                    root.getCategoryId(),
                                    new ArrayList<>()));
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryResponse> getChildCategories(Integer parentId) {
        findCategoryOrThrow(parentId);
        return categoryRepository
                .findByParentCategoryIdOrderByNameAsc(parentId)
                .stream()
                .map(CategoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryResponse> getCategoriesByIds(List<Integer> ids) {
        return categoryRepository.findByIds(ids)
                .stream()
                .map(CategoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Integer categoryId,
                                            UpdateCategoryRequest request) {
        Category category = findCategoryOrThrow(categoryId);

        if (request.getName() != null &&
                !request.getName().equals(category.getName())) {
            if (categoryRepository.existsByName(request.getName())) {
                throw new CustomException(
                    "Category name already taken: " + request.getName(),
                    HttpStatus.CONFLICT);
            }
            category.setName(request.getName());
            category.setSlug(generateUniqueSlug(
                    request.getName(), categoryRepository));
        }

        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }

        if (request.getParentCategoryId() != null) {
            if (request.getParentCategoryId().equals(categoryId)) {
                throw new CustomException(
                    "A category cannot be its own parent",
                    HttpStatus.BAD_REQUEST);
            }
            category.setParentCategoryId(request.getParentCategoryId());
        }

        Category updated = categoryRepository.save(category);
        log.info("✏️ Category updated: id={}", categoryId);
        return CategoryResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void deleteCategory(Integer categoryId) {
        Category category = findCategoryOrThrow(categoryId);

        if (categoryRepository.existsByParentCategoryId(categoryId)) {
            throw new CustomException(
                "Cannot delete a category that has subcategories. " +
                "Please delete or reassign subcategories first.",
                HttpStatus.BAD_REQUEST);
        }

        categoryRepository.delete(category);
        log.info("🗑️ Category deleted: id={}", categoryId);
    }

    @Override
    @Transactional
    public TagResponse createTag(CreateTagRequest request) {
        log.info("🏷️ Creating tag: {}", request.getName());

        if (tagRepository.existsByName(request.getName())) {
            throw new CustomException(
                "Tag '" + request.getName() + "' already exists",
                HttpStatus.CONFLICT);
        }

        String slug = generateTagSlug(request.getName());

        if (tagRepository.existsBySlug(slug)) {
            slug = slug + "-" + System.currentTimeMillis();
        }

        Tag tag = Tag.builder()
                .name(request.getName())
                .slug(slug)
                .postCount(0)
                .build();

        Tag saved = tagRepository.save(tag);
        log.info("✅ Tag created: id={} slug={}", saved.getTagId(), slug);
        return TagResponse.fromEntity(saved);
    }

    @Override
    public TagResponse getTagById(Integer tagId) {
        return TagResponse.fromEntity(findTagOrThrow(tagId));
    }

    @Override
    public TagResponse getTagBySlug(String slug) {
        Tag tag = tagRepository.findBySlug(slug)
                .orElseThrow(() -> new CustomException(
                    "Tag not found: " + slug, HttpStatus.NOT_FOUND));
        return TagResponse.fromEntity(tag);
    }

    @Override
    public List<TagResponse> getAllTags() {
        return tagRepository.findAllByOrderByNameAsc()
                .stream()
                .map(TagResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<TagResponse> getTrendingTags(int limit) {
        return tagRepository
                .findTrendingTags(PageRequest.of(0, limit))
                .stream()
                .map(TagResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<TagResponse> searchTags(String query) {
        return tagRepository.searchByName(query)
                .stream()
                .map(TagResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteTag(Integer tagId) {
        Tag tag = findTagOrThrow(tagId);

        postTagRepository.findByTagId(tagId)
                .forEach(a -> postTagRepository
                        .deleteByPostIdAndTagId(a.getPostId(), tagId));

        tagRepository.delete(tag);
        log.info("🗑️ Tag deleted: id={}", tagId);
    }

    @Override
    @Transactional
    public void addTagToPost(Integer postId, Integer tagId) {
        findTagOrThrow(tagId);

        if (postTagRepository.existsByPostIdAndTagId(postId, tagId)) {
            throw new CustomException(
                "Post already has this tag", HttpStatus.CONFLICT);
        }

        PostTagAssociation association = PostTagAssociation.builder()
                .postId(postId)
                .tagId(tagId)
                .build();

        postTagRepository.save(association);
        tagRepository.incrementPostCount(tagId);

        log.debug("🏷️ Tag {} added to post {}", tagId, postId);
    }

    @Override
    @Transactional
    public void removeTagFromPost(Integer postId, Integer tagId) {
        if (!postTagRepository.existsByPostIdAndTagId(postId, tagId)) {
            throw new CustomException(
                "Post does not have this tag", HttpStatus.NOT_FOUND);
        }

        postTagRepository.deleteByPostIdAndTagId(postId, tagId);
        tagRepository.decrementPostCount(tagId);

        log.debug("🏷️ Tag {} removed from post {}", tagId, postId);
    }

    @Override
    public List<TagResponse> getTagsByPost(Integer postId) {
        List<Integer> tagIds =
                postTagRepository.findTagIdsByPostId(postId);

        if (tagIds.isEmpty()) {
            return new ArrayList<>();
        }

        return tagRepository.findByIds(tagIds)
                .stream()
                .map(TagResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void removeAllTagsFromPost(Integer postId) {
        List<Integer> tagIds =
                postTagRepository.findTagIdsByPostId(postId);
        tagIds.forEach(tagRepository::decrementPostCount);

        postTagRepository.deleteByPostId(postId);
        log.info("🏷️ All tags removed from post {}", postId);
    }

    private Category findCategoryOrThrow(Integer categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(
                    "Category not found: " + categoryId,
                    HttpStatus.NOT_FOUND));
    }

    private Tag findTagOrThrow(Integer tagId) {
        return tagRepository.findById(tagId)
                .orElseThrow(() -> new CustomException(
                    "Tag not found: " + tagId,
                    HttpStatus.NOT_FOUND));
    }

    private CategoryResponse enrichWithParentName(
            CategoryResponse response) {
        if (response.getParentCategoryId() != null) {
            categoryRepository.findById(
                            response.getParentCategoryId())
                    .ifPresent(parent ->
                            response.setParentName(parent.getName()));
        }
        return response;
    }

    private String generateUniqueSlug(String name,
                                       CategoryRepository repo) {
        String baseSlug = name.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        String slug = baseSlug;
        int counter = 2;
        while (repo.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    private String generateTagSlug(String name) {
        return name.toLowerCase()
                .trim()
                .replace("++", "-plus-plus")
                .replace("+", "-plus")
                .replace(".", "-dot-")
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
