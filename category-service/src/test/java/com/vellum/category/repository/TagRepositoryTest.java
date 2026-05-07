package com.vellum.category.repository;

import com.vellum.category.entity.Tag;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("TagRepository Tests")
class TagRepositoryTest {

    @Autowired
    private TagRepository tagRepository;

    @BeforeEach
    void setUp() {
        tagRepository.save(Tag.builder()
                .name("java").slug("java").postCount(10).build());
        tagRepository.save(Tag.builder()
                .name("spring-boot").slug("spring-boot")
                .postCount(8).build());
        tagRepository.save(Tag.builder()
                .name("python").slug("python").postCount(5).build());
        tagRepository.save(Tag.builder()
                .name("tutorial").slug("tutorial").postCount(0).build());
    }

    @Test
    @DisplayName("findTrendingTags: returns tags ordered by postCount")
    void findTrendingTags_orderedByPostCount() {
        List<Tag> trending =
                tagRepository.findTrendingTags(PageRequest.of(0, 10));

        assertThat(trending).hasSize(3);
        assertThat(trending.get(0).getName()).isEqualTo("java");
        assertThat(trending.get(0).getPostCount()).isEqualTo(10);
        assertThat(trending.get(1).getName()).isEqualTo("spring-boot");
    }

    @Test
    @DisplayName("findTrendingTags: limits results to page size")
    void findTrendingTags_limitedByPageSize() {
        List<Tag> trending =
                tagRepository.findTrendingTags(PageRequest.of(0, 2));

        assertThat(trending).hasSize(2);
    }

    @Test
    @DisplayName("searchByName: case-insensitive search")
    void searchByName_caseInsensitive() {
        List<Tag> results = tagRepository.searchByName("JAVA");

        assertThat(results).anyMatch(t -> t.getName().equals("java"));
    }

    @Test
    @DisplayName("findAllByOrderByNameAsc: returns alphabetical order")
    void findAllByOrderByNameAsc_alphabetical() {
        List<Tag> tags = tagRepository.findAllByOrderByNameAsc();

        assertThat(tags.get(0).getName()).isEqualTo("java");
        assertThat(tags.get(1).getName()).isEqualTo("python");
        assertThat(tags.get(2).getName()).isEqualTo("spring-boot");
    }

    @Test
    @DisplayName("incrementPostCount: atomically increments")
    void incrementPostCount_increments() {
        Tag java = tagRepository.findByName("java").get();
        int before = java.getPostCount();

        tagRepository.incrementPostCount(java.getTagId());

        Tag updated = tagRepository.findById(java.getTagId()).get();
        assertThat(updated.getPostCount()).isEqualTo(before + 1);
    }
}
