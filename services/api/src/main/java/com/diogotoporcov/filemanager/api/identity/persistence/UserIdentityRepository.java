package com.diogotoporcov.filemanager.api.identity.persistence;

import com.diogotoporcov.filemanager.api.identity.domain.UserIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {
    Optional<UserIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
