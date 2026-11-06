package com.aieyaan.splynt.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties = "spring.web.resources.static-locations=file:frontend/public/")
class PublicPagesHttpTest {
    @Autowired WebApplicationContext context;

    @Test void policyAndSupportArePublicWhileAccountAndAdminRemainPrivate() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/privacy.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Aieyaan Yeasin")));
        mvc.perform(get("/support.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("mailto:Aieyaan.yeasin@gmail.com")));
        mvc.perform(get("/public-pages.css")).andExpect(status().isOk());
        mvc.perform(get("/favicon.svg")).andExpect(status().isOk());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/platform/overview")).andExpect(status().isUnauthorized());
    }
}
