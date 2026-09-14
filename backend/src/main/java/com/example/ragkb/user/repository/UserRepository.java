package com.example.ragkb.user.repository;

import com.example.ragkb.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    Optional<User> findByUsername(String username);
}
