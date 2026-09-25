package com.aieyaan.splynt.clover;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CloverOAuthCredentialRepository
        extends JpaRepository<CloverOAuthCredential, Long> {

    Optional<CloverOAuthCredential> findByMerchantId(
            String merchantId
    );
}
