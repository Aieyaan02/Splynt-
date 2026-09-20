package com.aieyaan.splynt.product;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieyaan.splynt.product.dto.CreateProductRequest;
import com.aieyaan.splynt.product.dto.ProductResponse;
import com.aieyaan.splynt.product.exception.DuplicateProductException;
import com.aieyaan.splynt.product.exception.ProductNotFoundException;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        validateInventoryLevels(request);

        String barcode = request.barcode().trim();

        if (productRepository.existsByBarcode(barcode)) {
            throw new DuplicateProductException(
                    "A product with barcode " + barcode + " already exists");
        }

        Product product = new Product(
                barcode,
                request.name().trim(),
                normalizeOptionalText(request.brand()),
                normalizeOptionalText(request.category()),
                request.quantity(),
                request.reorderLevel(),
                request.targetStock(),
                request.unitCost(),
                ProductSource.MANUAL);

        Product savedProduct = productRepository.save(product);

        return ProductResponse.from(savedProduct);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getActiveProducts() {
        return productRepository.findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product with ID " + id + " was not found"));

        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductByBarcode(String barcode) {
        String normalizedBarcode = barcode.trim();

        Product product = productRepository.findByBarcode(normalizedBarcode)
                .filter(Product::isActive)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product with barcode "
                                + normalizedBarcode
                                + " was not found"));

        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getLowStockProducts() {
        return productRepository.findLowStockProducts()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    private void validateInventoryLevels(CreateProductRequest request) {
        if (request.targetStock() < request.reorderLevel()) {
            throw new IllegalArgumentException(
                    "Target stock cannot be lower than the reorder level");
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}