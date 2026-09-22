package com.aieyaan.splynt.inventory;

import com.aieyaan.splynt.inventory.dto.InventoryChangeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

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
                eq(1L),
                any(InventoryChangeRequest.class)
        )).thenReturn(null);

        mockMvc.perform(
                        post("/api/products/1/sales")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "quantity": 3,
                                          "note": "Customer purchase"
                                        }
                                        """)
                )
                .andExpect(status().isCreated());

        verify(inventoryService).recordSale(
                eq(1L),
                any(InventoryChangeRequest.class)
        );
    }

    @Test
    void recordRestockReturnsCreated() throws Exception {
        when(inventoryService.recordRestock(
                eq(1L),
                any(InventoryChangeRequest.class)
        )).thenReturn(null);

        mockMvc.perform(
                        post("/api/products/1/restocks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "quantity": 12,
                                          "note": "Supplier delivery"
                                        }
                                        """)
                )
                .andExpect(status().isCreated());

        verify(inventoryService).recordRestock(
                eq(1L),
                any(InventoryChangeRequest.class)
        );
    }

    @Test
    void getMovementHistoryReturnsOkAndJsonArray() throws Exception {
        when(inventoryService.getMovementHistory(1L))
                .thenReturn(List.of());

        mockMvc.perform(
                        get("/api/products/1/movements")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(inventoryService).getMovementHistory(1L);
    }

    @Test
    void recordSaleRejectsZeroQuantity() throws Exception {
        mockMvc.perform(
                        post("/api/products/1/sales")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "quantity": 0,
                                          "note": "Invalid sale"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verify(inventoryService, never()).recordSale(
                eq(1L),
                any(InventoryChangeRequest.class)
        );
    }
}