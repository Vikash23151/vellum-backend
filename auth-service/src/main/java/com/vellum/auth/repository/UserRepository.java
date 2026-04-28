package com.vellum.auth.repository;

import com.vellum.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<User> findAllByRole(User.Role role);

    List<User> findAllByIsActive(boolean isActive);

    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.email)    LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> searchUsers(@Param("query") String query);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.isActive = false WHERE u.userId = :userId")
    void softDeleteUser(@Param("userId") Integer userId);

    List<User> findAllByRoleOrderByCreatedAtDesc(User.Role role);

    long countByRole(User.Role role);

    long countByIsActive(boolean isActive);
}