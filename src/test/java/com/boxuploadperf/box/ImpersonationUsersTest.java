package com.boxuploadperf.box;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpersonationUsersTest {

    @Test
    void emptyWhenBlank() {
        assertTrue(ImpersonationUsers.parse(null).isEmpty());
        assertTrue(ImpersonationUsers.parse("  ").isEmpty());
    }

    @Test
    void parsesCommaSeparatedIds() {
        assertEquals(List.of("111", "222", "333"), ImpersonationUsers.parse("111, 222 ,333"));
    }

    @Test
    void dedupesPreservingOrder() {
        assertEquals(List.of("111", "222"), ImpersonationUsers.parse("111,222,111"));
    }

    @Test
    void rejectsNonNumeric() {
        assertThrows(IllegalArgumentException.class, () -> ImpersonationUsers.parse("abc,123"));
    }
}
