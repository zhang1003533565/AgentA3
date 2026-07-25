package com.example.appbackend.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RealtimeTicketService {

    private static final long TICKET_TTL_SECONDS = 30L;
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public String issue(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        removeExpired();
        String value = UUID.randomUUID().toString();
        tickets.put(value, new Ticket(userId, Instant.now().plusSeconds(TICKET_TTL_SECONDS)));
        return value;
    }

    public Long consume(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Ticket ticket = tickets.remove(value);
        if (ticket == null || ticket.expiresAt().isBefore(Instant.now())) {
            return null;
        }
        return ticket.userId();
    }

    private void removeExpired() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record Ticket(Long userId, Instant expiresAt) {
    }
}
