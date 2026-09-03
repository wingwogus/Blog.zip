package com.blogzip.subscription.controller

import com.blogzip.common.web.ApiResponse
import com.blogzip.subscription.service.*
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class SubscriptionController(private val service: SubscriptionService) {
    data class LookupRequest(@field:NotBlank val url: String)

    data class CreateRequest(
        val lookupToken: String?,
        @field:NotBlank val friendName: String,
    )

    @PostMapping("/blogs/lookup")
    fun lookup(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: LookupRequest,
    ): ApiResponse<LookupResult> = ApiResponse.ok(service.lookup(userId, request.url))

    @PostMapping("/subscriptions")
    fun create(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: CreateRequest,
    ): ResponseEntity<ApiResponse<SubscriptionResult>> =
        ResponseEntity.status(201).body(ApiResponse.ok(service.create(userId, request.lookupToken.orEmpty(), request.friendName)))

    @DeleteMapping("/subscriptions/{id}")
    fun delete(
        @AuthenticationPrincipal userId: String,
        @PathVariable id: String,
    ): ResponseEntity<Void> {
        service.delete(userId, id)
        return ResponseEntity.noContent().build()
    }
}
