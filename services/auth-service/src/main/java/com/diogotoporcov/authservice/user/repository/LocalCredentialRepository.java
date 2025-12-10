package com.diogotoporcov.authservice.user.repository;

import com.diogotoporcov.authservice.user.entity.LocalCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LocalCredentialRepository extends JpaRepository<LocalCredential, UUID> {
}
