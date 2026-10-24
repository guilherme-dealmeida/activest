package com.activest.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Usuários. O e-mail é gravado sempre normalizado (minúsculas, sem espaços — ver {@code Emails} na
 * app-api), então a busca exata basta; {@code uk_users_email_lower} (V41) garante no banco.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    boolean existsByPhoneAndIdNot(String phone, UUID id);
    boolean existsByCpfHash(String cpfHash);
    boolean existsByCpfHashAndIdNot(String cpfHash, UUID id);
}
