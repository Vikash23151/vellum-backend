package com.vellum.category.service;

import com.vellum.category.dto.*;

import java.util.List;

public interface CategoryService {

    CategoryResponse createCategory(CreateCategoryRequest request);

    CategoryResponse getCategoryById(Integer categoryId);

    CategoryResponse getCategoryBySlug(String slug);

    List<CategoryResponse> getAllCategories();

    List<CategoryResponse> getCategoryTree();

    List<CategoryResponse> getChildCategories(Integer parentId);

    List<CategoryResponse> getCategoriesByIds(List<Integer> ids);

    CategoryResponse updateCategory(Integer categoryId,
                                    UpdateCategoryRequest request);

    void deleteCategory(Integer categoryId);

    TagResponse createTag(CreateTagRequest request);

    TagResponse getTagById(Integer tagId);

    TagResponse getTagBySlug(String slug);

    List<TagResponse> getAllTags();

    List<TagResponse> getTrendingTags(int limit);

    List<TagResponse> searchTags(String query);

    void deleteTag(Integer tagId);

    void addTagToPost(Integer postId, Integer tagId);

    void removeTagFromPost(Integer postId, Integer tagId);

    List<TagResponse> getTagsByPost(Integer postId);

    void removeAllTagsFromPost(Integer postId);
}
