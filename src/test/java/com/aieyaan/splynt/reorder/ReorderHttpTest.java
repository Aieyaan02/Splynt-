package com.aieyaan.splynt.reorder;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import com.aieyaan.splynt.tenant.*;
import com.aieyaan.splynt.product.*;

@SpringBootTest @Transactional
class ReorderHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired ProductRepository products;
    @Autowired OrganizationRepository organizations;
    @Autowired StoreRepository stores;
    @Autowired AppUserRepository users;
    @Autowired OrganizationMembershipRepository memberships;
    @Autowired ReorderDraftRepository drafts;
    MockMvc mvc; Store store; AppUser owner; Product product;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String suffix = UUID.randomUUID().toString();
        var organization = organizations.saveAndFlush(new Organization("Store", suffix));
        store = stores.saveAndFlush(new Store(organization, "Store", suffix));
        owner = users.saveAndFlush(new AppUser(suffix + "@example.com", "hash", "Owner", "One"));
        memberships.saveAndFlush(new OrganizationMembership(organization, owner, MembershipRole.OWNER));
        product = products.saveAndFlush(new Product(store, "ABC", "Coffee", "Brand", "Pantry", 4, 5, 10, null, ProductSource.MANUAL));
    }
    String path() { return "/api/stores/" + store.getId() + "/reorder-drafts"; }
    String body(UUID id, long version, long productId, String packs, String size) {
        return "{\"requestId\":\""+id+"\",\"version\":"+version+",\"supplier\":\"Local supplier\",\"supplierContact\":\"Account 123\",\"notes\":\"Test draft\",\"items\":[{\"productId\":"+productId+",\"packs\":"+packs+",\"unitsPerPack\":"+size+"}]}";
    }
    void create(UUID id) throws Exception {
        mvc.perform(post(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString()))).contentType("application/json").content(body(id,0,product.getId(),"3","2.5")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(7.5)).andExpect(jsonPath("$.items[0].name").value("Coffee"));
    }
    @Test void persistsSnapshotAndRetriesCreateWithoutDuplicates() throws Exception {
        UUID id=UUID.randomUUID();create(id);create(id);
        mvc.perform(get(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].supplier").value("Local supplier"));
        assertEquals(4,product.getQuantity().intValueExact());
    }
    @Test void editsThenMarksOrderedWithoutChangingStockAndRejectsStaleEdits() throws Exception {
        UUID id=UUID.randomUUID();create(id);
        mvc.perform(put(path()+"/"+id).with(jwt().jwt(j -> j.subject(owner.getId().toString()))).contentType("application/json").content(body(id,0,product.getId(),"2","12")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.items[0].quantity").value(24));
        mvc.perform(put(path()+"/"+id).with(jwt().jwt(j -> j.subject(owner.getId().toString()))).contentType("application/json").content(body(id,0,product.getId(),"1","1"))).andExpect(status().isConflict());
        mvc.perform(post(path()+"/"+id+"/ordered").with(jwt().jwt(j -> j.subject(owner.getId().toString()))).contentType("application/json").content("{\"version\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ORDERED")).andExpect(jsonPath("$.orderedAt").isNotEmpty());
        mvc.perform(post(path()+"/"+id+"/ordered").with(jwt().jwt(j -> j.subject(owner.getId().toString()))).contentType("application/json").content("{\"version\":1}")).andExpect(status().isOk());
        mvc.perform(put(path()+"/"+id).with(jwt().jwt(j -> j.subject(owner.getId().toString()))).contentType("application/json").content(body(id,2,product.getId(),"1","1"))).andExpect(status().isConflict());
        assertEquals(4,product.getQuantity().intValueExact());
    }
    @Test void outsiderCannotReadCreateEditOrMarkOrdered() throws Exception {
        UUID id=UUID.randomUUID();create(id);
        var outsider=jwt().jwt(j -> j.subject("999999"));
        mvc.perform(get(path()).with(outsider)).andExpect(status().isForbidden());
        mvc.perform(post(path()).with(outsider).contentType("application/json").content(body(UUID.randomUUID(),0,product.getId(),"1","1"))).andExpect(status().isForbidden());
        mvc.perform(put(path()+"/"+id).with(outsider).contentType("application/json").content(body(id,0,product.getId(),"1","1"))).andExpect(status().isForbidden());
        mvc.perform(post(path()+"/"+id+"/ordered").with(outsider).contentType("application/json").content("{\"version\":0}")).andExpect(status().isForbidden());
    }
    @Test void employeeCanReadButCannotCreateOrOrder() throws Exception {
        UUID id=UUID.randomUUID();create(id);
        var employee=users.saveAndFlush(new AppUser(UUID.randomUUID()+"@example.com","hash","Employee","One"));
        memberships.saveAndFlush(new OrganizationMembership(store.getOrganization(),employee,MembershipRole.EMPLOYEE));
        var auth=jwt().jwt(j -> j.subject(employee.getId().toString()));
        mvc.perform(get(path()).with(auth)).andExpect(status().isOk());
        mvc.perform(post(path()).with(auth).contentType("application/json").content(body(UUID.randomUUID(),0,product.getId(),"1","1"))).andExpect(status().isForbidden());
        mvc.perform(post(path()+"/"+id+"/ordered").with(auth).contentType("application/json").content("{\"version\":0}")).andExpect(status().isForbidden());
    }
    @Test void rejectsCrossStoreProductsAndDraftIdentifiersEvenForSharedOwner() throws Exception {
        UUID id=UUID.randomUUID();create(id);
        var other=stores.saveAndFlush(new Store(store.getOrganization(),"Second",UUID.randomUUID().toString()));
        String otherPath="/api/stores/"+other.getId()+"/reorder-drafts";
        var auth=jwt().jwt(j -> j.subject(owner.getId().toString()));
        mvc.perform(post(otherPath).with(auth).contentType("application/json").content(body(UUID.randomUUID(),0,product.getId(),"1","1"))).andExpect(status().isBadRequest());
        mvc.perform(put(otherPath+"/"+id).with(auth).contentType("application/json").content(body(id,0,product.getId(),"1","1"))).andExpect(status().isNotFound());
        mvc.perform(post(otherPath+"/"+id+"/ordered").with(auth).contentType("application/json").content("{\"version\":0}")).andExpect(status().isNotFound());
        mvc.perform(get(otherPath).with(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void rejectsInvalidPackQuantities() throws Exception {
        for (String packs : new String[]{"0","-1","1.5","1000001"}) {
            mvc.perform(post(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString()))).contentType("application/json").content(body(UUID.randomUUID(),0,product.getId(),packs,"1"))).andExpect(status().isBadRequest());
        }
        for (String size : new String[]{"0","-1","0.0000001","1000000"}) {
            mvc.perform(post(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString()))).contentType("application/json").content(body(UUID.randomUUID(),0,product.getId(),"1",size))).andExpect(status().isBadRequest());
        }
    }
    @Test void rejectsEmptyOrDuplicateItems() throws Exception {
        UUID id = UUID.randomUUID();
        String payload = body(id, 0, product.getId(), "1", "1");
        String line = "{\"productId\":"+product.getId()+",\"packs\":1,\"unitsPerPack\":1}";
        for (String invalid : new String[]{payload.replace(line, ""), payload.replace(line, line+","+line)}) {
            mvc.perform(post(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString()))).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
    }

}
