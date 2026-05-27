package com.filemanager.api.repository;

import com.filemanager.api.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {
    Optional<UserIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
