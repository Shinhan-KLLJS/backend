package com.shinhan.klljs.domain.user.repository;

import com.shinhan.klljs.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
