package com.aieyaan.splynt.inventory;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository
        extends JpaRepository<InventoryMovement, Long> {

    List<InventoryMovement>
            findAllByProduct_Store_IdAndProduct_IdOrderByCreatedAtDesc(
                    Long storeId,
                    Long productId
            );

    Optional<InventoryMovement>
            findBySourceAndExternalReference(
                    InventoryMovementSource source,
                    String externalReference
            );

    boolean existsBySourceAndExternalReference(
            InventoryMovementSource source,
            String externalReference
    );
}