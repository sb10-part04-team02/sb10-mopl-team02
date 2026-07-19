package com.team02.mopl.global.websocket.redis;

import com.fasterxml.jackson.databind.JsonNode;

public record StompFanOutMessage(String destination, JsonNode payload) {}
