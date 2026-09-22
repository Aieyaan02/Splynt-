package com.aieyaan.splynt.inventory;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aieyaan.splynt.inventory.dto.InventoryChangeRequest;
import com.aieyaan.splynt.inventory.dto.InventoryMovementResponse;
import com.aieyaan.splynt.inventory.dto.InventoryTransactionResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping(
        "/api/stores/{storeId}/products/{productId}/inventory"
)
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(
            InventoryService inventoryService) {

        this.inventoryService = inventoryService;
    }

    @PostMapping("/sales")
    public ResponseEntity<InventoryTransactionResponse> recordSale(
            @PathVariable Long storeId,
            @PathVariable Long productId,
            @Valid @RequestBody InventoryChangeRequest request) {

        InventoryTransactionResponse response =
                inventoryService.recordSale(
                        storeId,
                        productId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/restocks")
    public ResponseEntity<InventoryTransactionResponse> recordRestock(
            @PathVariable Long storeId,
            @PathVariable Long productId,
            @Valid @RequestBody InventoryChangeRequest request) {

        InventoryTransactionResponse response =
                inventoryService.recordRestock(
                        storeId,
                        productId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/movements")
    public ResponseEntity<List<InventoryMovementResponse>>
            getMovementHistory(
                    @PathVariable Long storeId,
                    @PathVariable Long productId) {

        List<InventoryMovementResponse> movements =
                inventoryService.getMovementHistory(
                        storeId,
                        productId
                );

        return ResponseEntity.ok(movements);
    }
}