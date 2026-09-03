package com.blogzip.subscription

import com.blogzip.subscription.repository.*
import com.blogzip.subscription.service.*
import com.github.benmanes.caffeine.cache.Caffeine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.net.InetAddress
import java.net.URI
import com.blogzip.common.error.BusinessException
import com.blogzip.common.error.ErrorCode

class SubscriptionDiscoveryTest {
    private val feed = """<?xml version="1.0"?><rss version="2.0"><channel><title>Fixture blog</title><item><title>One</title></item></channel></rss>"""

    @Test
    fun anyBlockedDnsAddressRejectsLookup() {
        val transport = BlogHttpTransport { _, _, _, _, _ -> fail("blocked DNS set must not be requested") }
        val service = SubscriptionService(
            Mockito.mock(BlogRepository::class.java), Mockito.mock(SubscriptionRepository::class.java), Mockito.mock(BlogFetchStateRepository::class.java),
            Caffeine.newBuilder().build(), Caffeine.newBuilder().build(),
            BlogHostResolver { listOf(InetAddress.getByName("93.184.216.34"), InetAddress.getByName("127.0.0.1")) }, transport,
        )
        val error = assertThrows(BusinessException::class.java) { service.lookup("usr_1", "example.test") }
        assertEquals(ErrorCode.BLOCKED_BLOG_URL, error.errorCode)
    }

    @Test
    fun oversizedResponseMapsToNotSupported() {
        val transport = BlogHttpTransport { _, _, _, _, _ -> BlogHttpResponse(200, null, null, tooLarge = true) }
        val service = SubscriptionService(
            Mockito.mock(BlogRepository::class.java), Mockito.mock(SubscriptionRepository::class.java), Mockito.mock(BlogFetchStateRepository::class.java),
            Caffeine.newBuilder().build(), Caffeine.newBuilder().build(),
            BlogHostResolver { listOf(InetAddress.getByName("93.184.216.34")) }, transport,
        )
        val error = assertThrows(BusinessException::class.java) { service.lookup("usr_1", "example.test") }
        assertEquals(ErrorCode.BLOG_NOT_SUPPORTED, error.errorCode)
    }

    @Test
    fun schemeLessInputAndHtmlAlternateAreDiscoveredWithoutNetwork() {
        val blogs = Mockito.mock(BlogRepository::class.java)
        val subs = Mockito.mock(SubscriptionRepository::class.java)
        val states = Mockito.mock(BlogFetchStateRepository::class.java)
        val transport = BlogHttpTransport { uri, _, _, _, _ ->
            when (uri.path) {
                "/" -> BlogHttpResponse(200, null, "<html><head><link rel='alternate' type='application/rss+xml' href='/feed.xml'></head></html>")
                "/feed.xml" -> BlogHttpResponse(200, null, feed)
                else -> BlogHttpResponse(404, null, null)
            }
        }
        val service = SubscriptionService(blogs, subs, states, Caffeine.newBuilder().build(), Caffeine.newBuilder().build(),
            BlogHostResolver { listOf(InetAddress.getByName("93.184.216.34")) }, transport)

        val result = service.lookup("usr_1", "example.test")

        assertEquals("Fixture blog", result.blog.title)
        assertEquals(1, result.recentPosts.size)
        assertTrue(result.lookupToken.startsWith("lkp_"))
        Mockito.verify(blogs).findByCanonicalUrl("https://example.test/")
    }

    @Test
    fun alternateFeedLinksCannotExceedTheDiscoveryRequestBudget() {
        val requests = mutableListOf<URI>()
        val alternates = (1..100).joinToString("") {
            "<link rel='alternate' type='application/rss+xml' href='/feed-$it.xml'>"
        }
        val transport = BlogHttpTransport { uri, _, _, _, _ ->
            requests += uri
            if (uri.path == "/") BlogHttpResponse(200, null, "<html><head>$alternates</head></html>")
            else BlogHttpResponse(404, null, null)
        }
        val service = SubscriptionService(
            Mockito.mock(BlogRepository::class.java),
            Mockito.mock(SubscriptionRepository::class.java),
            Mockito.mock(BlogFetchStateRepository::class.java),
            Caffeine.newBuilder().build(),
            Caffeine.newBuilder().build(),
            BlogHostResolver { listOf(InetAddress.getByName("93.184.216.34")) },
            transport,
        )

        val error = assertThrows(BusinessException::class.java) { service.lookup("usr_1", "example.test") }

        assertEquals(ErrorCode.BLOG_NOT_REACHABLE, error.errorCode)
        assertEquals(10, requests.size)
        assertTrue(requests.drop(1).all { it.path in setOf("/feed-1.xml", "/feed-2.xml", "/feed-3.xml", "/feed-4.xml", "/feed-5.xml", "/rss", "/feed", "/rss.xml", "/atom.xml", "/index.xml") })
    }

    @Test
    fun declaredAlternateFeedIsPreferredOverConventionalPath() {
        val requestedPaths = mutableListOf<String>()
        val atom = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"><title>Declared feed</title><entry><title>Atom post</title></entry></feed>"""
        val transport = BlogHttpTransport { uri, _, _, _, _ ->
            requestedPaths += uri.path
            when (uri.path) {
                "/" -> BlogHttpResponse(200, null, "<html><head><link rel='alternate' type='application/atom+xml' href='/all-posts.xml'></head></html>")
                "/all-posts.xml" -> BlogHttpResponse(200, null, atom)
                "/rss" -> BlogHttpResponse(200, null, feed)
                else -> BlogHttpResponse(404, null, null)
            }
        }
        val service = service(transport)

        val result = service.lookup("usr_1", "example.test")

        assertEquals("Declared feed", result.blog.title)
        assertEquals(listOf("/", "/all-posts.xml"), requestedPaths)
    }

    @Test
    fun platformFeedIsTriedBeforeTheInputPage() {
        val requestedUris = mutableListOf<URI>()
        val transport = BlogHttpTransport { uri, _, _, _, _ ->
            requestedUris += uri
            if (uri.host == "api.velog.io") BlogHttpResponse(200, null, feed)
            else fail("input page must not be requested when the platform feed succeeds")
        }
        val service = service(transport)

        val result = service.lookup("usr_1", "velog.io/@friend")

        assertEquals("Fixture blog", result.blog.title)
        assertEquals(listOf("api.velog.io"), requestedUris.map { it.host })
    }

    @Test
    fun wholeBlogAlternateIsPreferredOverCategoryAlternate() {
        val requestedPaths = mutableListOf<String>()
        val transport = BlogHttpTransport { uri, _, _, _, _ ->
            requestedPaths += uri.path
            when (uri.path) {
                "/" -> BlogHttpResponse(200, null, "<html><head><link rel='alternate' type='application/rss+xml' href='/category/dev.xml'><link rel='alternate' type='application/rss+xml' href='/all.xml'></head></html>")
                "/all.xml" -> BlogHttpResponse(200, null, feed)
                "/category/dev.xml" -> BlogHttpResponse(200, null, """<?xml version="1.0"?><rss version="2.0"><channel><title>Category feed</title><item><title>Only category</title></item></channel></rss>""")
                else -> BlogHttpResponse(404, null, null)
            }
        }
        val service = service(transport)

        val result = service.lookup("usr_1", "example.test")

        assertEquals("Fixture blog", result.blog.title)
        assertEquals(listOf("/", "/all.xml"), requestedPaths)
    }

    @Test
    fun redirectHopsConsumeTheSharedDiscoveryRequestBudget() {
        val requests = mutableListOf<URI>()
        val transport = BlogHttpTransport { uri, _, _, _, _ ->
            requests += uri
            if (uri.path.startsWith("/redirect-") || uri.path == "/") {
                BlogHttpResponse(302, "/redirect-${requests.size}", null)
            } else {
                BlogHttpResponse(404, null, null)
            }
        }
        val service = service(transport)

        val error = assertThrows(BusinessException::class.java) { service.lookup("usr_1", "example.test") }

        assertEquals(ErrorCode.BLOG_NOT_REACHABLE, error.errorCode)
        assertEquals(9, requests.size)
    }

    @Test
    fun feedlessHtmlReturnsNotSupported() {
        val transport = BlogHttpTransport { _, _, _, _, _ -> BlogHttpResponse(200, null, "<html><head></head><body>no feed</body></html>") }
        val service = service(transport)

        val error = assertThrows(BusinessException::class.java) { service.lookup("usr_1", "example.test") }

        assertEquals(ErrorCode.BLOG_NOT_SUPPORTED, error.errorCode)
    }

    @Test
    fun redirectToBlockedTargetIsRejectedBeforeRequest() {
        val blogs = Mockito.mock(BlogRepository::class.java)
        val subs = Mockito.mock(SubscriptionRepository::class.java)
        val states = Mockito.mock(BlogFetchStateRepository::class.java)
        val requests = mutableListOf<URI>()
        val transport = BlogHttpTransport { uri, _, _, _, _ ->
            requests += uri
            if (uri.path == "/") BlogHttpResponse(302, "http://127.0.0.1/private", null)
            else BlogHttpResponse(200, null, feed)
        }
        val service = SubscriptionService(blogs, subs, states, Caffeine.newBuilder().build(), Caffeine.newBuilder().build(),
            BlogHostResolver { host -> if (host == "example.test") listOf(InetAddress.getByName("93.184.216.34")) else listOf(InetAddress.getByName("127.0.0.1")) }, transport)

        assertThrows(com.blogzip.common.error.BusinessException::class.java) { service.lookup("usr_1", "example.test") }
        assertEquals(1, requests.size)
        assertEquals("example.test", requests.last().host)
    }

    @Test
    fun blankFeedTitleFallsBackToTheBlogHost() {
        val blogs = Mockito.mock(BlogRepository::class.java)
        val subs = Mockito.mock(SubscriptionRepository::class.java)
        val states = Mockito.mock(BlogFetchStateRepository::class.java)
        val untitledFeed = """<?xml version="1.0"?><rss version="2.0"><channel><item><title>One</title></item></channel></rss>"""
        val transport = BlogHttpTransport { uri, _, _, _, _ ->
            when (uri.path) {
                "/" -> BlogHttpResponse(200, null, "<html><head><link rel='alternate' type='application/rss+xml' href='/feed.xml'></head></html>")
                "/feed.xml" -> BlogHttpResponse(200, null, untitledFeed)
                else -> BlogHttpResponse(404, null, null)
            }
        }
        val service = SubscriptionService(
            blogs,
            subs,
            states,
            Caffeine.newBuilder().build(),
            Caffeine.newBuilder().build(),
            BlogHostResolver { listOf(InetAddress.getByName("93.184.216.34")) },
            transport,
        )

        assertEquals("example.test", service.lookup("usr_1", "example.test").blog.title)
    }

    @Test
    fun genericBlogUsingRssPathRemainsGeneric() {
        val transport = BlogHttpTransport { uri, _, _, _, _ ->
            when (uri.path) {
                "/" -> BlogHttpResponse(200, null, "<html></html>")
                "/rss" -> BlogHttpResponse(200, null, feed)
                else -> BlogHttpResponse(404, null, null)
            }
        }
        val service = SubscriptionService(
            Mockito.mock(BlogRepository::class.java),
            Mockito.mock(SubscriptionRepository::class.java),
            Mockito.mock(BlogFetchStateRepository::class.java),
            Caffeine.newBuilder().build(),
            Caffeine.newBuilder().build(),
            BlogHostResolver { listOf(InetAddress.getByName("93.184.216.34")) },
            transport,
        )

        val result = service.lookup("usr_1", "example.test")

        assertEquals("GENERIC", result.blog.platform)
        assertEquals("개인 블로그", result.blog.platformLabel)
    }

    @Test
    fun knownTistoryHostUsingRssPathIsLabelledTistory() {
        val transport = BlogHttpTransport { uri, _, _, _, _ ->
            when (uri.path) {
                "/" -> BlogHttpResponse(200, null, "<html></html>")
                "/rss" -> BlogHttpResponse(200, null, feed)
                else -> BlogHttpResponse(404, null, null)
            }
        }
        val service = SubscriptionService(
            Mockito.mock(BlogRepository::class.java),
            Mockito.mock(SubscriptionRepository::class.java),
            Mockito.mock(BlogFetchStateRepository::class.java),
            Caffeine.newBuilder().build(),
            Caffeine.newBuilder().build(),
            BlogHostResolver { listOf(InetAddress.getByName("93.184.216.34")) },
            transport,
        )

        val result = service.lookup("usr_1", "name.tistory.com")

        assertEquals("TISTORY", result.blog.platform)
        assertEquals("Tistory", result.blog.platformLabel)
    }

    @Test
    fun genericPostUrlsReuseTheDiscoveredBlogRoot() {
        val transport = BlogHttpTransport { uri, _, _, _, _ ->
            when (uri.path) {
                "/post-one", "/post-two" -> BlogHttpResponse(200, null, "<html><head><link rel='alternate' type='application/rss+xml' href='/rss'></head></html>")
                "/rss" -> BlogHttpResponse(200, null, feed)
                else -> BlogHttpResponse(404, null, null)
            }
        }
        val service = SubscriptionService(
            Mockito.mock(BlogRepository::class.java), Mockito.mock(SubscriptionRepository::class.java),
            Mockito.mock(BlogFetchStateRepository::class.java), Caffeine.newBuilder().build(), Caffeine.newBuilder().build(),
            BlogHostResolver { listOf(InetAddress.getByName("93.184.216.34")) }, transport,
        )

        assertEquals("https://example.test/", service.lookup("usr_1", "example.test/post-one").blog.siteUrl)
        assertEquals("https://example.test/", service.lookup("usr_1", "example.test/post-two").blog.siteUrl)
    }

    @Test
    fun pathHostedGenericBlogsKeepDistinctBases() {
        val transport = BlogHttpTransport { uri, _, _, _, _ ->
            when (uri.path) {
                "/a/post" -> BlogHttpResponse(200, null, "<html><head><link rel='alternate' type='application/rss+xml' href='/a/rss'></head></html>")
                "/b/post" -> BlogHttpResponse(200, null, "<html><head><link rel='alternate' type='application/rss+xml' href='/b/rss'></head></html>")
                "/a/rss", "/b/rss" -> BlogHttpResponse(200, null, feed)
                else -> BlogHttpResponse(404, null, null)
            }
        }
        val service = SubscriptionService(
            Mockito.mock(BlogRepository::class.java), Mockito.mock(SubscriptionRepository::class.java),
            Mockito.mock(BlogFetchStateRepository::class.java), Caffeine.newBuilder().build(), Caffeine.newBuilder().build(),
            BlogHostResolver { listOf(InetAddress.getByName("93.184.216.34")) }, transport,
        )

        assertEquals("https://example.test/a", service.lookup("usr_1", "example.test/a/post").blog.siteUrl)
        assertEquals("https://example.test/b", service.lookup("usr_1", "example.test/b/post").blog.siteUrl)
    }

    @Test
    fun externalSharedFeedDoesNotMergeDistinctGenericSites() {
        val transport = BlogHttpTransport { uri, _, _, _, _ ->
            when (uri.host) {
                "one.test", "two.test" -> BlogHttpResponse(200, null, "<html><head><link rel='alternate' type='application/rss+xml' href='https://shared.test/feed.xml'></head></html>")
                "shared.test" -> BlogHttpResponse(200, null, feed)
                else -> BlogHttpResponse(404, null, null)
            }
        }
        val service = SubscriptionService(
            Mockito.mock(BlogRepository::class.java), Mockito.mock(SubscriptionRepository::class.java),
            Mockito.mock(BlogFetchStateRepository::class.java), Caffeine.newBuilder().build(), Caffeine.newBuilder().build(),
            BlogHostResolver { listOf(InetAddress.getByName("93.184.216.34")) }, transport,
        )

        assertEquals("https://one.test/", service.lookup("usr_1", "one.test/post").blog.siteUrl)
        assertEquals("https://two.test/", service.lookup("usr_1", "two.test/post").blog.siteUrl)
    }

    private fun service(transport: BlogHttpTransport) = SubscriptionService(
        Mockito.mock(BlogRepository::class.java),
        Mockito.mock(SubscriptionRepository::class.java),
        Mockito.mock(BlogFetchStateRepository::class.java),
        Caffeine.newBuilder().build(),
        Caffeine.newBuilder().build(),
        BlogHostResolver { listOf(InetAddress.getByName("93.184.216.34")) },
        transport,
    )
}
