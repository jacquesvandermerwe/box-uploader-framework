package com.boxuploadperf.box;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoxAccessTokenHolderTest {

    @Test
    void parsesTokenAndExpiresIn() {
        BoxAccessTokenHolder holder = new BoxAccessTokenHolder();
        holder.applyTokenResponse("{\"access_token\":\"abc\",\"expires_in\":7200}");
        assertEquals("abc", holder.accessToken());
        assertFalse(holder.needsProactiveRefresh());
    }

    @Test
    void defaultsExpiresInWhenMissing() {
        BoxAccessTokenHolder holder = new BoxAccessTokenHolder();
        holder.applyTokenResponse("{\"access_token\":\"abc\"}");
        assertEquals("abc", holder.accessToken());
        assertFalse(holder.needsProactiveRefresh());
    }

    @Test
    void needsRefreshWhenNoToken() {
        BoxAccessTokenHolder holder = new BoxAccessTokenHolder();
        assertTrue(holder.needsProactiveRefresh());
    }

    @Test
    void needsRefreshWhenAlreadyExpired() {
        BoxAccessTokenHolder holder = new BoxAccessTokenHolder();
        holder.applyTokenResponse("{\"access_token\":\"abc\",\"expires_in\":0}");
        assertTrue(holder.needsProactiveRefresh());
    }
}
