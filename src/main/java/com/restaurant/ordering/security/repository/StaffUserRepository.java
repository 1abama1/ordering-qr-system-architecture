package com.restaurant.ordering.security.repository;
import com.restaurant.ordering.security.entity.StaffUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {
    Optional<StaffUser> findByUsername(String username);
}
