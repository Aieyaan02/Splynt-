package com.aieyaan.splynt.clover;

import static org.junit.jupiter.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import com.aieyaan.splynt.tenant.*;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties={"splynt.integrations.clover.client-id=webhook-test-app",
        "splynt.integrations.clover.webhook-auth-code=local-webhook-test-only"})
class CloverWebhookTest {
    @Autowired CloverWebhookController webhook;
    @Autowired CloverOAuthCredentialRepository credentials;
    @Autowired StoreRepository stores;
    @Autowired OrganizationRepository organizations;
    private CloverOAuthCredential connection() {
        String id=UUID.randomUUID().toString();
        var org=organizations.saveAndFlush(new Organization("Test",id));
        var store=stores.saveAndFlush(new Store(org,"Test",id));
        var c=new CloverOAuthCredential(id,"encrypted-test-access","encrypted-test-refresh",null,null);
        c.assignStore(store.getId()); c.markAuthorized();
        return credentials.saveAndFlush(c);
    }
    private void send(String auth,String app,String merchant,String object,String type,long ts) {
        var body=new JsonMapper().readTree("{\"appId\":\""+app+"\",\"merchants\":{\""+merchant+
            "\":[{\"objectId\":\""+object+"\",\"type\":\""+type+"\",\"ts\":"+ts+"}]}}");
        webhook.receive(auth,body);
    }
    @Test void onlyAuthenticatedMatchingUninstallDisconnectsItsMerchant() {
        var c=connection(); var other=connection();
        long ts=System.currentTimeMillis()+1000;
        assertThrows(ResponseStatusException.class,()->send("wrong","webhook-test-app",c.getMerchantId(),"A:webhook-test-app","DELETE",ts));
        assertThrows(ResponseStatusException.class,()->send("local-webhook-test-only","other-app",c.getMerchantId(),"A:webhook-test-app","DELETE",ts));
        assertTrue(credentials.findById(c.getId()).orElseThrow().isConnected());
        send("local-webhook-test-only","webhook-test-app",c.getMerchantId(),"I:item","DELETE",ts);
        assertTrue(credentials.findById(c.getId()).orElseThrow().isConnected());
        send("local-webhook-test-only","webhook-test-app",c.getMerchantId(),"A:webhook-test-app","DELETE",ts);
        send("local-webhook-test-only","webhook-test-app",c.getMerchantId(),"A:webhook-test-app","DELETE",ts);
        var result=credentials.findById(c.getId()).orElseThrow();
        assertFalse(result.isConnected()); assertEquals("",result.getAccessToken()); assertEquals("",result.getRefreshToken());
        assertTrue(credentials.findById(other.getId()).orElseThrow().isConnected());
    }
    @Test void delayedUninstallCannotDisableLaterAuthorization() {
        var c=connection();
        send("local-webhook-test-only","webhook-test-app",c.getMerchantId(),"A:webhook-test-app","DELETE",
                OffsetDateTime.now().minusDays(1).toInstant().toEpochMilli());
        assertTrue(credentials.findById(c.getId()).orElseThrow().isConnected());
    }
}
