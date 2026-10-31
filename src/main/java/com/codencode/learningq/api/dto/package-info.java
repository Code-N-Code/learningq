/**
 * API request/response DTO conventions:
 * <ul>
 *   <li>Use dedicated DTOs, never JPA entities, as controller inputs/outputs.</li>
 *   <li>Apply Jakarta validation constraints and use {@code @Valid @RequestBody}.</li>
 *   <li>Java camelCase properties use the application's snake_case JSON naming.</li>
 *   <li>Do not include credentials in toString(), logs, or validation messages.
 *       Credential DTOs must redact their toString(), including when using records.</li>
 *   <li>Return only public profile fields; never serialize password hashes.</li>
 * </ul>
 * Authentication DTOs and endpoint-specific validation arrive with their features.
 */
package com.codencode.learningq.api.dto;
