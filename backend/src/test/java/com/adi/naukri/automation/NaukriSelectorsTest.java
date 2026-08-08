package com.adi.naukri.automation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that every static String field in NaukriSelectors is non-blank.
 *
 * Author: Adikarthik Gupta C B
 */
class NaukriSelectorsTest {

    @Test
    void all_selectors_non_blank() throws Exception {
        for (Field f : NaukriSelectors.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                String v = (String) f.get(null);
                assertNotNull(v, f.getName() + " must not be null");
                assertFalse(v.isBlank(), f.getName() + " must not be blank");
            }
        }
    }
}
