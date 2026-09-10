package com.electronics.store.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VnPayIpnResponse(@JsonProperty("RspCode") String responseCode,
                              @JsonProperty("Message") String message) {
}
