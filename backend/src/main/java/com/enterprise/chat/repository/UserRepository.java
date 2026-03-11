package com.enterprise.chat.repository;

import com.enterprise.chat.model.User;
import com.enterprise.chat.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    List<User> findByRole(UserRole role);
    List<User> findByRoleAndAvailable(UserRole role, boolean available);
}
