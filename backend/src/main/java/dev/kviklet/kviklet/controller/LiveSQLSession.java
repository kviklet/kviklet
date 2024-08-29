package dev.kviklet.kviklet.controller;

public record LiveSQLSession (String executionRequestId, String sql, String tamperProofSignature) {
}
