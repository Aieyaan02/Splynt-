package com.aieyaan.splynt.reorder;

import java.util.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.aieyaan.splynt.product.ProductRepository;
import com.aieyaan.splynt.tenant.StoreRepository;
import tools.jackson.databind.json.JsonMapper;

@Service @Transactional
public class ReorderService {
    private final ReorderDraftRepository drafts;
    private final ProductRepository products;
    private final StoreRepository stores;
    private final JsonMapper json = new JsonMapper();
    public ReorderService(ReorderDraftRepository drafts, ProductRepository products, StoreRepository stores) {
        this.drafts = drafts; this.products = products; this.stores = stores;
    }
    public record ItemInput(@NotNull Long productId, @NotNull @DecimalMin("1") @DecimalMax("1000000") @Digits(integer=7, fraction=0) BigDecimal packs,
        @NotNull @DecimalMin("0.000001") @DecimalMax("999999") @Digits(integer=6, fraction=6) BigDecimal unitsPerPack) {}
    public record Input(@NotNull UUID requestId, @Min(0) long version,
        @NotNull @Size(max=150) String supplier, @NotNull @Size(max=255) String supplierContact,
        @NotNull @Size(max=1000) String notes, @NotEmpty @Size(max=200) List<@NotNull @Valid ItemInput> items) {}
    public record Item(Long productId, String name, String barcode, String unit, int packs, BigDecimal unitsPerPack, BigDecimal quantity) {}
    public record View(UUID id, long version, String supplier, String supplierContact, String notes, String status,
        OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime orderedAt, List<Item> items) {}
    public record Version(@Min(0) long version) {}

    @Transactional(readOnly=true)
    public List<View> list(Long storeId) { return drafts.findTop20ByStoreIdOrderByCreatedAtDesc(storeId).stream().map(this::view).toList(); }
    public View create(Long storeId, Input input) {
        // Serializes repeated create requests without ever making duplicate purchase records.
        stores.findLockedById(storeId).filter(s -> s.isActive()).orElseThrow(() -> missing());
        var existing = drafts.findById(input.requestId());
        if (existing.isPresent()) {
            if (!existing.get().storeId.equals(storeId)) throw missing();
            return view(existing.get());
        }
        ReorderDraft draft = new ReorderDraft();
        draft.id = input.requestId(); draft.storeId = storeId; draft.status = "DRAFT";
        draft.createdAt = OffsetDateTime.now();
        apply(draft, input);
        return view(drafts.saveAndFlush(draft));
    }
    public View update(Long storeId, UUID id, Input input) {
        var draft = drafts.findLocked(storeId, id).orElseThrow(() -> missing());
        checkVersion(draft, input.version());
        if (!"DRAFT".equals(draft.status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ordered drafts cannot be edited.");
        apply(draft, input);
        return view(drafts.saveAndFlush(draft));
    }
    public View ordered(Long storeId, UUID id, long version) {
        var draft = drafts.findLocked(storeId, id).orElseThrow(() -> missing());
        if ("ORDERED".equals(draft.status)) return view(draft); // retry after lost response
        checkVersion(draft, version);
        draft.status = "ORDERED"; draft.orderedAt = OffsetDateTime.now(); draft.updatedAt = draft.orderedAt;
        return view(drafts.saveAndFlush(draft));
    }
    private void checkVersion(ReorderDraft draft, long version) {
        if (draft.version != version) throw new ResponseStatusException(HttpStatus.CONFLICT, "This draft changed. Reload before saving.");
    }
    private void apply(ReorderDraft draft, Input input) {
        Set<Long> seen = new HashSet<>();
        List<Item> items = new ArrayList<>();
        for (var line : input.items()) {
            if (!seen.add(line.productId())) throw new IllegalArgumentException("Each product may appear only once.");
            var product = products.findByStoreIdAndId(draft.storeId, line.productId()).filter(p -> p.isActive()).orElseThrow(() -> new IllegalArgumentException("A product is unavailable in this store. Remove it and reload the catalog."));
            String unit = product.getCloverDetails() == null || product.getCloverDetails().unitName() == null ? "units" : product.getCloverDetails().unitName();
            items.add(new Item(product.getId(), product.getName(), product.getBarcode(), unit, line.packs().intValueExact(), line.unitsPerPack(), line.unitsPerPack().multiply(line.packs())));
        }
        draft.supplier = input.supplier().trim(); draft.supplierContact = input.supplierContact().trim(); draft.notes = input.notes().trim();
        draft.itemsJson = json.writeValueAsString(items); draft.updatedAt = OffsetDateTime.now();
    }
    private View view(ReorderDraft draft) {
        return new View(draft.id, draft.version, draft.supplier, draft.supplierContact, draft.notes, draft.status,
            draft.createdAt, draft.updatedAt, draft.orderedAt, Arrays.asList(json.readValue(draft.itemsJson, Item[].class)));
    }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft or store not found."); }
}
