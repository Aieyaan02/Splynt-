package com.aieyaan.splynt.product;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aieyaan.splynt.product.dto.CreateProductRequest;
import com.aieyaan.splynt.product.dto.ProductResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/stores/{storeId}/products")
@PreAuthorize(
        "@storeAuthorizationService.canAccess("
                + "authentication, #storeId)"
)
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(
            @PathVariable Long storeId,
            @Valid @RequestBody CreateProductRequest request) {

        return productService.createProduct(storeId, request);
    }

    @GetMapping
    public List<ProductResponse> getActiveProducts(
            @PathVariable Long storeId) {

        return productService.getActiveProducts(storeId);
    }

    @GetMapping("/low-stock")
    public List<ProductResponse> getLowStockProducts(
            @PathVariable Long storeId) {

        return productService.getLowStockProducts(storeId);
    }

    @GetMapping("/{productId}")
    public ProductResponse getProductById(
            @PathVariable Long storeId,
            @PathVariable Long productId) {

        return productService.getProductById(
                storeId,
                productId
        );
    }

    @GetMapping("/barcode/{barcode}")
    public ProductResponse getProductByBarcode(
            @PathVariable Long storeId,
            @PathVariable String barcode) {

        return productService.getProductByBarcode(
                storeId,
                barcode
        );
    }
}