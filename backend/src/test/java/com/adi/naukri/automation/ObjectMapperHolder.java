package com.adi.naukri.automation;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared Jackson ObjectMapper for test classes.
 *
 * Author: Adikarthik Gupta C B
 */
final class ObjectMapperHolder {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private ObjectMapperHolder() {}
}
