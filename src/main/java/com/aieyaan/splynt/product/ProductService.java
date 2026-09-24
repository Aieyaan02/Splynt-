package com.aieyaan.splynt.product;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieyaan.splynt.product.dto.CreateProductRequest;
import com.aieyaan.splynt.product.dto.ProductResponse;
import com.aieyaan.splynt.product.exception.DuplicateProductException;
import com.aieyaan.splynt.product.exception.ProductNotFoundException;
import com.aieyaan.splynt.tenant.Store;
import com.aieyaan.splynt.tenant.StoreRepository;
import com.aieyaan.splynt.tenant.exception.StoreNotFoundException;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;

    public ProductService(
            ProductRepository productRepository,
            StoreRepository storeRepository) {

        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
    }

    @Transactional
    public ProductResponse createProduct(
            Long storeId,
            CreateProductRequest request) {

        validateInventoryLevels(request);

        Store store = getActiveStore(storeId);
        String barcode = request.barcode().trim();

        if (productRepository.existsByStoreIdAndBarcode(
                storeId,
                barcode)) {

            Optional<Product> existingProduct =
                    productRepository.findByStoreIdAndBarcode(
                            storeId,
                            barcode
                    );

            if (existingProduct.isPresent()
                    && !existingProduct.get().isActive()) {

                throw new DuplicateProductException(
                        "An archived product with barcode "
                                + barcode
                                + " already exists. Restore the "
                                + "archived product instead."
                );
            }

            throw new DuplicateProductException(
                    "A product with barcode "
                            + barcode
                            + " already exists in store "
                            + storeId
            );
        }

        Product product = new Product(
                store,
                barcode,
                request.name().trim(),
                normalizeOptionalText(request.brand()),
                normalizeOptionalText(request.category()),
                request.quantity(),
                request.reorderLevel(),
                request.targetStock(),
                request.unitCost(),
                ProductSource.MANUAL
        );

        Product savedProduct = productRepository.save(product);

        return ProductResponse.from(savedProduct);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getActiveProducts(Long storeId) {
        getActiveStore(storeId);

        return productRepository
                .findAllByStoreIdAndActiveTrueOrderByNameAsc(storeId)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getArchivedProducts(Long storeId) {
        getActiveStore(storeId);

        return productRepository
                .findAllByStoreIdAndActiveFalseOrderByNameAsc(storeId)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(
            Long storeId,
            Long productId) {

        Product product = getActiveProduct(
                storeId,
                productId
        );

        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductByBarcode(
            Long storeId,
            String barcode) {

        getActiveStore(storeId);

        String normalizedBarcode = barcode.trim();

        Product product = productRepository
                .findByStoreIdAndBarcode(
                        storeId,
                        normalizedBarcode
                )
                .filter(Product::isActive)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product with barcode "
                                + normalizedBarcode
                                + " was not found in store "
                                + storeId
                ));

        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getLowStockProducts(
            Long storeId) {

        getActiveStore(storeId);

        return productRepository
                .findLowStockProductsByStoreId(storeId)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional
    public void archiveProduct(
            Long storeId,
            Long productId) {

        Product product = getActiveProduct(
                storeId,
                productId
        );

        product.setActive(false);
    }

    @Transactional
    public ProductResponse restoreProduct(
            Long storeId,
            Long productId) {

        getActiveStore(storeId);

        Product product = productRepository
                .findByStoreIdAndId(storeId, productId)
                .filter(existingProduct ->
                        !existingProduct.isActive())
                .orElseThrow(() -> new ProductNotFoundException(
                        "Archived product with ID "
                                + productId
                                + " was not found in store "
                                + storeId
                ));

        product.setActive(true);

        return ProductResponse.from(product);
    }

    private Product getActiveProduct(
            Long storeId,
            Long productId) {

        getActiveStore(storeId);

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

    private Store getActiveStore(Long storeId) {
        return storeRepository.findById(storeId)
                .filter(Store::isActive)
                .orElseThrow(() -> new StoreNotFoundException(
                        "Active store with ID "
                                + storeId
                                + " was not found"
                ));
    }

    private void validateInventoryLevels(
            CreateProductRequest request) {

        if (request.targetStock() < request.reorderLevel()) {
            throw new IllegalArgumentException(
                    "Target stock cannot be lower than the reorder level"
            );
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}