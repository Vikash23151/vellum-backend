package com.vellum.auth.repository;

import com.vellum.auth.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository Tests")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    // Test data
    private User savedUser;

    @BeforeEach
    void setUp() {
        savedUser = userRepository.save(User.builder()
                .username("johndoe")
                .email("john@vellum.com")
                .passwordHash("$2a$10$hashedpassword")
                .fullName("John Doe")
                .role(User.Role.READER)
                .provider(User.Provider.LOCAL)
                .isActive(true)
                .build());
    }

    // findByEmail tests

    @Test
    @DisplayName("findByEmail should return user when email exists")
    void findByEmail_whenEmailExists_returnsUser() {
        // ACT
        Optional<User> result = userRepository.findByEmail("john@vellum.com");

        // ASSERT
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("john@vellum.com");
        assertThat(result.get().getUsername()).isEqualTo("johndoe");
    }

    @Test
    @DisplayName("findByEmail should return empty when email does not exist")
    void findByEmail_whenEmailNotExists_returnsEmpty() {
        Optional<User> result = userRepository.findByEmail("nobody@vellum.com");
        assertThat(result).isEmpty();
    }

    // existsByEmail tests

    @Test
    @DisplayName("existsByEmail should return true when email exists")
    void existsByEmail_whenEmailExists_returnsTrue() {
        assertThat(userRepository.existsByEmail("john@vellum.com")).isTrue();
    }

    @Test
    @DisplayName("existsByEmail should return false when email does not exist")
    void existsByEmail_whenEmailNotExists_returnsFalse() {
        assertThat(userRepository.existsByEmail("ghost@vellum.com")).isFalse();
    }

    // existsByUsername tests

    @Test
    @DisplayName("existsByUsername should return true for existing username")
    void existsByUsername_whenExists_returnsTrue() {
        assertThat(userRepository.existsByUsername("johndoe")).isTrue();
    }

    @Test
    @DisplayName("existsByUsername should return false for non-existent username")
    void existsByUsername_whenNotExists_returnsFalse() {
        assertThat(userRepository.existsByUsername("janedoe")).isFalse();
    }

    // findAllByRole tests

    @Test
    @DisplayName("findAllByRole should return only users with matching role")
    void findAllByRole_returnsOnlyMatchingRole() {
        // Add an AUTHOR user
        userRepository.save(User.builder()
                .username("authoruser")
                .email("author@vellum.com")
                .passwordHash("hash")
                .role(User.Role.AUTHOR)
                .provider(User.Provider.LOCAL)
                .isActive(true)
                .build());

        // ACT
        List<User> readers = userRepository.findAllByRole(User.Role.READER);
        List<User> authors = userRepository.findAllByRole(User.Role.AUTHOR);

        // ASSERT
        assertThat(readers).hasSize(1);
        assertThat(readers.get(0).getRole()).isEqualTo(User.Role.READER);

        assertThat(authors).hasSize(1);
        assertThat(authors.get(0).getRole()).isEqualTo(User.Role.AUTHOR);
    }

    // searchUsers tests

    @Test
    @DisplayName("searchUsers should find by username (case insensitive)")
    void searchUsers_byUsername_caseInsensitive() {
        List<User> results = userRepository.searchUsers("JOHN");

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getUsername()).isEqualTo("johndoe");
    }

    @Test
    @DisplayName("searchUsers should find by full name")
    void searchUsers_byFullName() {
        List<User> results = userRepository.searchUsers("John Doe");

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getFullName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("searchUsers should return empty for no match")
    void searchUsers_noMatch_returnsEmpty() {
        List<User> results = userRepository.searchUsers("xyz123nonexistent");
        assertThat(results).isEmpty();
    }

    // countByRole tests

    @Test
    @DisplayName("countByRole should return correct count")
    void countByRole_returnsCorrectCount() {
        assertThat(userRepository.countByRole(User.Role.READER)).isEqualTo(1);
        assertThat(userRepository.countByRole(User.Role.ADMIN)).isEqualTo(0);
    }
}