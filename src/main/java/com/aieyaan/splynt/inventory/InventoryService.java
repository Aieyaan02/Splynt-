package com.aieyaan.splynt.inventory;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieyaan.splynt.inventory.dto.InventoryChangeRequest;
import com.aieyaan.splynt.inventory.dto.InventoryMovementResponse;
import com.aieyaan.splynt.inventory.dto.InventoryTransactionResponse;
import com.aieyaan.splynt.product.Product;
import com.aieyaan.splynt.product.ProductRepository;
import com.aieyaan.splynt.product.dto.ProductResponse;
import com.aieyaan.splynt.product.exception.ProductNotFoundException;

@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryMovementRepository movementRepository;

    public InventoryService(
            ProductRepository productRepository,
            InventoryMovementRepository movementRepository) {

        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public InventoryTransactionResponse recordSale(
            Long storeId,
            Long productId,
            InventoryChangeRequest request) {

        Product product = findActiveProduct(
                storeId,
                productId, request.requestId() != null
        );

        if (product.getSource() == com.aieyaan.splynt.product.ProductSource.CLOVER)
            throw new IllegalArgumentException("Manage Clover stock in Clover, then sync Splynt. This prevents changes being overwritten.");
        InventoryTransactionResponse replay = replay(product, storeId, productId, request, InventoryMovementType.SALE);
        if (replay != null) return replay;
        java.math.BigDecimal quantityBefore = product.getQuantity();

        product.recordSale(request.quantity());

        InventoryMovement movement = new InventoryMovement(
                product,
                InventoryMovementType.SALE,
                request.quantity().negate(),
                quantityBefore,
                product.getQuantity(),
                InventoryMovementSource.MANUAL,
                request.note(),
                requestReference(storeId, productId, request.requestId())
        );

        productRepository.save(product);

        InventoryMovement savedMovement =
                movementRepository.saveAndFlush(movement);

        return new InventoryTransactionResponse(
                ProductResponse.from(product),
                InventoryMovementResponse.from(savedMovement)
        );
    }

    @Transactional
    public InventoryTransactionResponse recordRestock(
            Long storeId,
            Long productId,
            InventoryChangeRequest request) {

        Product product = findActiveProduct(
                storeId,
                productId, request.requestId() != null
        );

        if (product.getSource() == com.aieyaan.splynt.product.ProductSource.CLOVER)
            throw new IllegalArgumentException("Manage Clover stock in Clover, then sync Splynt. This prevents changes being overwritten.");
        InventoryTransactionResponse replay = replay(product, storeId, productId, request, InventoryMovementType.RESTOCK);
        if (replay != null) return replay;
        java.math.BigDecimal quantityBefore = product.getQuantity();

        product.restock(request.quantity());

        InventoryMovement movement = new InventoryMovement(
                product,
                InventoryMovementType.RESTOCK,
                request.quantity(),
                quantityBefore,
                product.getQuantity(),
                InventoryMovementSource.MANUAL,
                request.note(),
                requestReference(storeId, productId, request.requestId())
        );

        productRepository.save(product);

        InventoryMovement savedMovement =
                movementRepository.saveAndFlush(movement);

        return new InventoryTransactionResponse(
                ProductResponse.from(product),
                InventoryMovementResponse.from(savedMovement)
        );
    }

    @Transactional(readOnly = true)
    public List<InventoryMovementResponse> getMovementHistory(
            Long storeId,
            Long productId) {

        // Archived products retain readable inventory history.
        productRepository.findByStoreIdAndId(storeId, productId)
                .orElseThrow(() -> new ProductNotFoundException("Product was not found in this store"));

        return movementRepository
                .findAllByProduct_Store_IdAndProduct_IdOrderByCreatedAtDesc(
                        storeId,
                        productId
                )
                .stream()
                .map(InventoryMovementResponse::from)
                .toList();
    }

    private String requestReference(Long storeId, Long productId, String id) {
        return id == null ? null : "request:" + storeId + ":" + productId + ":" + java.util.UUID.fromString(id);
    }
    private InventoryTransactionResponse replay(Product product, Long storeId, Long productId,
            InventoryChangeRequest request, InventoryMovementType type) {
        String reference = requestReference(storeId, productId, request.requestId());
        if (reference == null) return null;
        var prior = movementRepository.findBySourceAndExternalReference(InventoryMovementSource.MANUAL, reference);
        if (prior.isEmpty()) return null;
        var movement = prior.get();
        String note = request.note() == null || request.note().trim().isEmpty() ? null : request.note().trim();
        if (movement.getMovementType() != type || movement.getQuantityChange().abs().compareTo(request.quantity()) != 0
                || !java.util.Objects.equals(movement.getNote(), note))
            throw new IllegalArgumentException("This request ID was already used for a different inventory change. Refresh inventory before starting a new change.");
        return new InventoryTransactionResponse(ProductResponse.from(product), InventoryMovementResponse.from(movement));
    }

    private Product findActiveProduct(
            Long storeId,
            Long productId, boolean lock) {

        return (lock ? productRepository.findLockedByStoreIdAndId(storeId, productId) : productRepository.findByStoreIdAndId(storeId, productId))
                .filter(Product::isActive)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product with ID "
                                + productId
                                + " was not found in store "
                                + storeId
                ));
    }
}