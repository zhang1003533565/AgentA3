package com.example.appbackend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RealtimeTicketServiceTest {

    @Test
    void ticketIsShortLivedCredentialThatCanOnlyBeConsumedOnce() {
        RealtimeTicketService service = new RealtimeTicketService();

        String ticket = service.issue(42L);

        assertNotNull(ticket);
        assertEquals(42L, service.consume(ticket));
        assertNull(service.consume(ticket));
        assertNull(service.consume(""));
    }
}
