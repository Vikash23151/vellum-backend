package com.vellum.category.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vellum.category.dto.*;
import com.vellum.category.exception.GlobalExceptionHandler;
import com.vellum.category.service.CategoryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryResource Controller Tests")
class CategoryResourceTest {

    private MockMvc mockMvc;

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private CategoryResource categoryResource;

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    private CategoryResponse sampleCategory;
    private TagResponse      sampleTag;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(categoryResource)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleCategory = CategoryResponse.builder()
                .categoryId(1)
                .name("Technology")
                .slug("technology")
                .postCount(5)
                .build();

        sampleTag = TagResponse.builder()
                .tagId(1)
                .name("java")
                .slug("java")
                .postCount(10)
                .build();
    }

    @Test
    @DisplayName("GET /api/categories: 200 OK returns list")
    void getAllCategories_returns200() throws Exception {
        when(categoryService.getAllCategories())
                .thenReturn(List.of(sampleCategory));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Technology"));
    }

    @Test
    @DisplayName("POST /api/categories: 201 Created for ADMIN")
    void createCategory_admin_returns201() throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Technology");

        when(categoryService.createCategory(
                any(CreateCategoryRequest.class)))
                .thenReturn(sampleCategory);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Role", "ADMIN")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Technology"));
    }
}
