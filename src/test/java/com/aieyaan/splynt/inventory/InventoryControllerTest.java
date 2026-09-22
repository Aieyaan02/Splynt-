package com.aieyaan.splynt.inventory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.aieyaan.splynt.inventory.dto.InventoryChangeRequest;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    @Mock
    private InventoryService inventoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InventoryController inventoryController =
                new InventoryController(inventoryService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(inventoryController)
                .build();
    }

    @Test
    void recordSaleReturnsCreated() throws Exception {
        when(inventoryService.recordSale(
                eq(10L),
                eq(1L),
                any(InventoryChangeRequest.class)
        )).thenReturn(null);

        mockMvc.perform(
                        post(
                                "/api/stores/10/products/1"
                                        + "/inventory/sales"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "quantity": 3,
                                          "note": "Customer purchase"
                                        }
                                        """)
                )
                .andExpect(status().isCreated());

        verify(inventoryService).recordSale(
                eq(10L),
                eq(1L),
                any(InventoryChangeRequest.class)
        );
    }

    @Test
    void recordRestockReturnsCreated() throws Exception {
        when(inventoryService.recordRestock(
                eq(10L),
                eq(1L),
                any(InventoryChangeRequest.class)
        )).thenReturn(null);

        mockMvc.perform(
                        post(
                                "/api/stores/10/products/1"
                                        + "/inventory/restocks"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "quantity": 12,
                                          "note": "Supplier delivery"
                                        }
                                        """)
                )
                .andExpect(status().isCreated());

        verify(inventoryService).recordRestock(
                eq(10L),
                eq(1L),
                any(InventoryChangeRequest.class)
        );
    }

    @Test
    void getMovementHistoryReturnsOkAndJsonArray()
            throws Exception {

        when(inventoryService.getMovementHistory(
                10L,
                1L
        )).thenReturn(List.of());

        mockMvc.perform(
                        get(
                                "/api/stores/10/products/1"
                                        + "/inventory/movements"
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(inventoryService)
                .getMovementHistory(10L, 1L);
    }

    @Test
    void recordSaleRejectsZeroQuantity() throws Exception {
        mockMvc.perform(
                        post(
                                "/api/stores/10/products/1"
                                        + "/inventory/sales"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "quantity": 0,
                                          "note": "Invalid sale"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verify(inventoryService, never()).recordSale(
                eq(10L),
                eq(1L),
                any(InventoryChangeRequest.class)
        );
    }
}