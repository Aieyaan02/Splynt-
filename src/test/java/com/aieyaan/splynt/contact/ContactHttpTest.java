package com.aieyaan.splynt.contact;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
@SpringBootTest @Transactional
class ContactHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired ContactInquiryRepository inquiries;
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @Test void publicSubmissionPersistsButPrivateResourcesStayPrivate() throws Exception {
        long before = inquiries.count();
        mvc.perform(post("/api/public/inquiries").contentType("application/json")
                .content("{\"name\":\"Alex\",\"email\":\"alex@example.com\",\"message\":\"Please show me a demo\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("received"));
        assertEquals(before + 1, inquiries.count());
        mvc.perform(get("/api/public/inquiries")).andExpect(status().isUnauthorized());
    }
    @Test void invalidContactNeverPersists() throws Exception {
        long before = inquiries.count();
        mvc.perform(post("/api/public/inquiries").contentType("application/json")
                .content("{\"name\":\"\",\"email\":\"not-an-email\",\"message\":\"short\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(before, inquiries.count());
    }
}
