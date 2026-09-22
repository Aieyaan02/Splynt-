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
                productId
        );

        int quantityBefore = product.getQuantity();

        product.recordSale(request.quantity());

        InventoryMovement movement = new InventoryMovement(
                product,
                InventoryMovementType.SALE,
                -request.quantity(),
                quantityBefore,
                product.getQuantity(),
                InventoryMovementSource.MANUAL,
                request.note(),
                null
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
                productId
        );

        int quantityBefore = product.getQuantity();

        product.restock(request.quantity());

        InventoryMovement movement = new InventoryMovement(
                product,
                InventoryMovementType.RESTOCK,
                request.quantity(),
                quantityBefore,
                product.getQuantity(),
                InventoryMovementSource.MANUAL,
                request.note(),
                null
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

        findActiveProduct(storeId, productId);

        return movementRepository
                .findAllByProduct_Store_IdAndProduct_IdOrderByCreatedAtDesc(
                        storeId,
                        productId
                )
                .stream()
                .map(InventoryMovementResponse::from)
                .toList();
    }

    private Product findActiveProduct(
            Long storeId,
            Long productId) {

        return productRepository
                .findByStoreIdAndId(storeId, productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product with ID "
                                + productId
                                + " was not found in store "
                                + storeId
                ));
    }
}