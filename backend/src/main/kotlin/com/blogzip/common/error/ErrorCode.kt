package com.blogzip.common.error

/**
 * 에러 코드 레지스트리.
 *
 * 형식은 `DOMAIN_NNN`이며 번호는 재사용하지 않는다.
 * 코드가 없어져도 번호를 비워 둔다.
 *
 * 전체 규약: docs/decisions/010-api-response-contract.md
 * 클라이언트 분기는 [code]로만 한다. [messageKey]가 해석된 문구로 분기하지 않는다.
 */
enum class ErrorCode(
    val code: String,
    val messageKey: String,
    val status: Int,
) {
    // COMMON / RESOURCE
    INVALID_INPUT("COMMON_001", "error.invalid_input", 400),
    INVALID_JSON("COMMON_002", "error.invalid_json", 400),
    INVALID_CURSOR("COMMON_003", "error.invalid_cursor", 400),
    TOO_MANY_REQUESTS("COMMON_004", "error.too_many_requests", 429),
    INTERNAL_ERROR("COMMON_999", "error.internal_error", 500),
    RESOURCE_NOT_FOUND("RESOURCE_001", "error.resource_not_found", 404),

    // AUTH / USER - docs/specs/auth.md
    UNAUTHORIZED("AUTH_001", "error.unauthorized", 401),
    FORBIDDEN("AUTH_002", "error.forbidden", 403),
    DUPLICATE_EMAIL("AUTH_003", "error.duplicate_email", 409),
    INVALID_CREDENTIALS("AUTH_004", "error.invalid_credentials", 401),
    INVALID_REFRESH_TOKEN("AUTH_005", "error.invalid_refresh_token", 401),
    TOO_MANY_LOGIN_ATTEMPTS("AUTH_006", "error.too_many_login_attempts", 429),
    MALFORMED_JWT("AUTH_007", "error.malformed_jwt", 400),
    USER_NOT_FOUND("USER_001", "error.user_not_found", 404),

    // BLOG - docs/specs/blog-subscription.md, docs/decisions/003-blog-discovery.md
    INVALID_BLOG_URL("BLOG_001", "error.invalid_blog_url", 400),
    BLOCKED_BLOG_URL("BLOG_002", "error.blocked_blog_url", 400),
    BLOG_NOT_REACHABLE("BLOG_003", "error.blog_not_reachable", 404),
    BLOG_NOT_SUPPORTED("BLOG_004", "error.blog_not_supported", 422),
    BLOG_LOOKUP_EXPIRED("BLOG_005", "error.blog_lookup_expired", 400),
    TOO_MANY_LOOKUP_REQUESTS("BLOG_006", "error.too_many_lookup_requests", 429),

    // SUBSCRIPTION - docs/specs/blog-subscription.md, docs/specs/subscription-management.md
    SUBSCRIPTION_NOT_FOUND("SUBSCRIPTION_001", "error.subscription_not_found", 404),
    ALREADY_SUBSCRIBED("SUBSCRIPTION_002", "error.already_subscribed", 409),

    // POST - docs/specs/feed.md
    POST_NOT_FOUND("POST_001", "error.post_not_found", 404),

    // OWNERSHIP - docs/specs/blog-ownership.md
    OWNERSHIP_NOT_FOUND("OWNERSHIP_001", "error.ownership_not_found", 404),
    BLOG_ALREADY_OWNED("OWNERSHIP_002", "error.blog_already_owned", 409),
    USER_ALREADY_OWNS_BLOG("OWNERSHIP_003", "error.user_already_owns_blog", 409),
    VERIFICATION_NOT_FOUND("OWNERSHIP_004", "error.verification_not_found", 404),
    VERIFICATION_EXPIRED("OWNERSHIP_005", "error.verification_expired", 409),
    VERIFICATION_CODE_NOT_FOUND("OWNERSHIP_006", "error.verification_code_not_found", 422),
    TOO_MANY_VERIFICATION_ATTEMPTS("OWNERSHIP_007", "error.too_many_verification_attempts", 429),
    ;
}
