package com.aieyaan.splynt.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.aieyaan.splynt.common.error.GlobalExceptionHandler;
import com.aieyaan.splynt.product.dto.CreateProductRequest;
import com.aieyaan.splynt.product.dto.ProductResponse;
import com.aieyaan.splynt.product.exception.ProductNotFoundException;
import com.aieyaan.splynt.tenant.exception.StoreNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductController(productService)
                )
                .setControllerAdvice(
                        new GlobalExceptionHandler()
                )
                .build();

        objectMapper =
                new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void createsProductForStoreAndReturnsCreatedStatus()
            throws Exception {

        CreateProductRequest request =
                new CreateProductRequest(
                        "123456789012",
                        "Orange Juice",
                        "Example Brand",
                        "Beverages",
                        10,
                        5,
                        20,
                        new BigDecimal("2.50")
                );

        ProductResponse response = new ProductResponse(
                1L,
                0L,
                10L,
                "123456789012",
                "Orange Juice",
                "Example Brand",
                "Beverages",
                10,
                5,
                20,
                new BigDecimal("2.50"),
                null,
                ProductSource.MANUAL,
                true,
                false,
                10,
                OffsetDateTime.parse(
                        "2026-09-20T17:00:00-04:00"
                ),
                OffsetDateTime.parse(
                        "2026-09-20T17:00:00-04:00"
                )
        );

        when(productService.createProduct(
                eq(10L),
                any(CreateProductRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/stores/10/products")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper
                                                .writeValueAsString(
                                                        request
                                                )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.storeId").value(10))
                .andExpect(
                        jsonPath("$.barcode")
                                .value("123456789012")
                )
                .andExpect(
                        jsonPath("$.name")
                                .value("Orange Juice")
                )
                .andExpect(
                        jsonPath("$.source")
                                .value("MANUAL")
                );
    }

    @Test
    void rejectsInvalidProductRequest() throws Exception {
        String invalidRequest = """
                {
                  "barcode": "",
                  "name": "",
                  "brand": "Example Brand",
                  "category": "Beverages",
                  "quantity": -1,
                  "reorderLevel": 5,
                  "targetStock": 20,
                  "unitCost": -2.50
                }
                """;

        mockMvc.perform(
                        post("/api/stores/10/products")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(invalidRequest)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Request validation failed"
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.validationErrors.barcode"
                        ).value("Barcode is required")
                )
                .andExpect(
                        jsonPath(
                                "$.validationErrors.name"
                        ).value(
                                "Product name is required"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.validationErrors.quantity"
                        ).value(
                                "Quantity cannot be negative"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.validationErrors.unitCost"
                        ).value(
                                "Unit cost cannot be negative"
                        )
                );
    }

    @Test
    void returnsNotFoundForMissingProduct()
            throws Exception {

        when(productService.getProductById(10L, 999L))
                .thenThrow(new ProductNotFoundException(
                        "Product with ID 999 was not found "
                                + "in store 10"
                ));

        mockMvc.perform(
                        get("/api/stores/10/products/999")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.error")
                                .value("Not Found")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Product with ID 999 "
                                                + "was not found "
                                                + "in store 10"
                                )
                )
                .andExpect(
                        jsonPath("$.path")
                                .value(
                                        "/api/stores/10/products/999"
                                )
                );
    }

    @Test
    void returnsNotFoundForMissingStore()
            throws Exception {

        when(productService.getActiveProducts(999L))
                .thenThrow(new StoreNotFoundException(
                        "Active store with ID 999 was not found"
                ));

        mockMvc.perform(
                        get("/api/stores/999/products")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Active store with ID 999 "
                                                + "was not found"
                                )
                );
    }

    @Test
    void archivesProductAndReturnsNoContent()
            throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/stores/10/products/25"
                        )
                )
                .andExpect(status().isNoContent());

        verify(productService)
                .archiveProduct(10L, 25L);
    }

    @Test
    void returnsNotFoundWhenArchivingMissingProduct()
            throws Exception {

        whenArchiveMissingProduct();

        mockMvc.perform(
                        delete(
                                "/api/stores/10/products/999"
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.message").value(
                                "Product with ID 999 "
                                        + "was not found in store 10"
                        )
                );
    }

    private void whenArchiveMissingProduct() {
        org.mockito.Mockito.doThrow(
                        new ProductNotFoundException(
                                "Product with ID 999 "
                                        + "was not found in store 10"
                        )
                )
                .when(productService)
                .archiveProduct(10L, 999L);
    }
}