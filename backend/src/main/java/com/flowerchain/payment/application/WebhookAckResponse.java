package com.flowerchain.payment.application;

/** Acknowledgement returned to the payment provider (OpenAPI schema {@code WebhookAckResponse}). */
public record WebhookAckResponse(String eventId, WebhookOutcome outcome) {}
