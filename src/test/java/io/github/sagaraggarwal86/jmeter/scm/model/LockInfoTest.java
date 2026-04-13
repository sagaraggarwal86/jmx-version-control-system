package io.github.sagaraggarwal86.jmeter.scm.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sagaraggarwal86.jmeter.scm.storage.FileOperations;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LockInfoTest {

    @Test
    void constructorSetsAllFields() {
        LocalDateTime now = LocalDateTime.now();
        LockInfo info = new LockInfo(1234, "myhost", now, "5.6.3");

        assertEquals(1234, info.getPid());
        assertEquals("myhost", info.getHostname());
        assertEquals(now, info.getTimestamp());
        assertEquals("5.6.3", info.getJmeterVersion());
    }

    @Test
    void constructorRejectsNullHostname() {
        assertThrows(NullPointerException.class, () ->
                new LockInfo(1, null, LocalDateTime.now(), "5.6.3"));
    }

    @Test
    void constructorRejectsNullTimestamp() {
        assertThrows(NullPointerException.class, () ->
                new LockInfo(1, "host", null, "5.6.3"));
    }

    @Test
    void constructorRejectsNullJmeterVersion() {
        assertThrows(NullPointerException.class, () ->
                new LockInfo(1, "host", LocalDateTime.now(), null));
    }

    @Test
    void toStringContainsKeyFields() {
        LockInfo info = new LockInfo(9999, "server01", LocalDateTime.of(2026, 1, 15, 10, 30), "5.6.3");
        String str = info.toString();
        assertTrue(str.contains("9999"));
        assertTrue(str.contains("server01"));
    }

    @Test
    void jsonRoundTrip() throws Exception {
        ObjectMapper mapper = FileOperations.objectMapper();

        LocalDateTime time = LocalDateTime.of(2026, 3, 15, 14, 30, 0);
        LockInfo original = new LockInfo(42, "testhost", time, "5.6.3");

        String json = mapper.writeValueAsString(original);
        LockInfo deserialized = mapper.readValue(json, LockInfo.class);

        assertEquals(original.getPid(), deserialized.getPid());
        assertEquals(original.getHostname(), deserialized.getHostname());
        assertEquals(original.getTimestamp(), deserialized.getTimestamp());
        assertEquals(original.getJmeterVersion(), deserialized.getJmeterVersion());
    }

    @Test
    void jsonContainsExpectedFields() throws Exception {
        ObjectMapper mapper = FileOperations.objectMapper();

        LockInfo info = new LockInfo(100, "node1", LocalDateTime.of(2026, 6, 1, 8, 0), "5.6.3");
        String json = mapper.writeValueAsString(info);

        assertTrue(json.contains("\"pid\""));
        assertTrue(json.contains("\"hostname\""));
        assertTrue(json.contains("\"timestamp\""));
        assertTrue(json.contains("\"jmeterVersion\""));
    }

    @Test
    void zeroPidIsAllowed() {
        LockInfo info = new LockInfo(0, "host", LocalDateTime.now(), "5.6.3");
        assertEquals(0, info.getPid());
    }

    @Test
    void negativePidIsAllowed() {
        LockInfo info = new LockInfo(-1, "host", LocalDateTime.now(), "5.6.3");
        assertEquals(-1, info.getPid());
    }
}
