package com.electronics.store.dto.request;

import com.electronics.store.entity.OrderStatus;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;

import java.io.IOException;

public record UpdateOrderStatusRequest(
        @NotNull(message = "Order status is required")
        @JsonDeserialize(using = StatusDeserializer.class) OrderStatus status
) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported order status field: " + name);
    }

    // Reject numeric enum ordinals without changing JSON behavior for other APIs.
    public static class StatusDeserializer extends JsonDeserializer<OrderStatus> {
        @Override
        public OrderStatus deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return (OrderStatus) context.handleUnexpectedToken(OrderStatus.class, parser);
            }
            try {
                return OrderStatus.valueOf(parser.getText());
            } catch (IllegalArgumentException ex) {
                return (OrderStatus) context.handleWeirdStringValue(OrderStatus.class, parser.getText(),
                        "Unsupported order status");
            }
        }
    }
}
