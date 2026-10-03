package com.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UserServiceTest {
    @Test
    void returnsKnownUser() {
        UserService service = new UserService();
        service.add("1", "Asha");
        assertEquals("Asha", service.nameOf("1"));
    }

    @Test
    void computesDistance() {
        assertEquals(3, new UserService().distance("kitten", "sitting"));
    }
}
