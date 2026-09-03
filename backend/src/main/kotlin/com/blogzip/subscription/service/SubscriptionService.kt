package com.blogzip.subscription.service

import com.blogzip.common.error.BusinessException
import com.blogzip.common.error.ErrorCode
import com.blogzip.common.id.IdPrefix
import com.blogzip.subscription.domain.Blog
import com.blogzip.subscription.domain.BlogFetchState
import com.blogzip.subscription.domain.Subscription
import com.blogzip.subscription.repository.BlogFetchStateRepository
import com.blogzip.subscription.repository.BlogRepository
import com.blogzip.subscription.repository.SubscriptionRepository
import com.github.benmanes.caffeine.cache.Cache
import org.jsoup.Jsoup
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.apache.hc.client5.http.DnsResolver
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.core5.util.Timeout
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import java.net.*
import java.net.HttpURLConnection
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

data class PostPreview(val title: String, val publishedAt: Instant?)
data class BlogInfo(val title: String, val siteUrl: String, val platform: String, val platformLabel: String, val id: String? = null)
data class LookupResult(val blog: BlogInfo, val recentPosts: List<PostPreview>, val alreadySubscribed: Boolean, val currentFriendName: String?, val lookupToken: String)
data class SubscriptionResult(val id: String, val friendName: String, val blog: BlogInfo, val createdAt: Instant)
/** Network seams keep focused discovery tests on local fixtures while production still enforces SSRF checks. */
fun interface BlogHostResolver { fun resolve(host: String): List<InetAddress> }
data class BlogHttpResponse(val status: Int, val location: String?, val body: String?, val tooLarge: Boolean = false)
fun interface BlogHttpTransport { fun request(uri: URI, address: InetAddress, hostHeader: String, connectTimeoutMs: Int, readTimeoutMs: Int): BlogHttpResponse }

@org.springframework.stereotype.Component
class DefaultBlogHostResolver : BlogHostResolver {
    override fun resolve(host: String): List<InetAddress> = InetAddress.getAllByName(host).toList()
}

@org.springframework.stereotype.Component
class DefaultBlogHttpTransport : BlogHttpTransport {
    override fun request(uri: URI, address: InetAddress, hostHeader: String, connectTimeoutMs: Int, readTimeoutMs: Int): BlogHttpResponse {
        val resolver = object : DnsResolver {
            override fun resolve(host: String): Array<InetAddress> = if (host.equals(uri.host, true)) arrayOf(address) else InetAddress.getAllByName(host)
            override fun resolveCanonicalHostname(host: String): String = host
        }
        val manager = PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(resolver).build()
        val config = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs.toLong()))
            .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMs.toLong()))
            .build()
        HttpClients.custom().setConnectionManager(manager).setDefaultRequestConfig(config).disableRedirectHandling().build().use { client ->
            val request = HttpGet(uri).apply {
                setHeader("Host", hostHeader)
                setHeader("User-Agent", "BlogZip/1.0")
            }
            return client.execute(request) { response ->
                val status = response.code
                val location = response.getFirstHeader("Location")?.value
                if (status !in 200..299) return@execute BlogHttpResponse(status, location, null)
                val bytes = response.entity?.content?.readNBytes(2 * 1024 * 1024 + 1) ?: ByteArray(0)
                BlogHttpResponse(status, location, if (bytes.size > 2 * 1024 * 1024) null else bytes.toString(Charsets.UTF_8), bytes.size > 2 * 1024 * 1024)
            }
        }
    }
}
private data class LookupEntry(val ownerId: String, val canonicalUrl: String, val siteUrl: String, val feedUrl: String, val title: String, val platform: String)
private data class Feed(val title: String, val posts: List<PostPreview>)

@Service
class SubscriptionService(
    private val blogs: BlogRepository,
    private val subscriptions: SubscriptionRepository,
    private val states: BlogFetchStateRepository,
    @Qualifier("blogLookupCache") private val lookupCache: Cache<String, Any>,
    @Qualifier("blogLookupRateCache") private val rateCache: Cache<String, Any>,
    private val hostResolver: BlogHostResolver = BlogHostResolver { InetAddress.getAllByName(it).toList() },
    private val httpTransport: BlogHttpTransport,
    private val transactionManager: PlatformTransactionManager? = null,
) {
    fun lookup(userId: String, rawUrl: String): LookupResult {
        val minute = Instant.now().epochSecond / 60
        val counter = rateCache.get("rate:$userId:$minute") { AtomicInteger() } as AtomicInteger
        if (counter.incrementAndGet() > 10) throw BusinessException(ErrorCode.TOO_MANY_LOOKUP_REQUESTS)
        val site = normalize(rawUrl)
        val platform = detectPlatform(site.host)
        val discovered = discover(site, platform) ?: throw BusinessException(ErrorCode.BLOG_NOT_SUPPORTED)
        val blogUrl = canonicalBlogUrl(site, platform, URI(discovered.first))
        val token = "lkp_" + UUID.randomUUID().toString().replace("-", "")
        lookupCache.put(token, LookupEntry(userId, blogUrl.toString(), blogUrl.toString(), discovered.first, discovered.second.title.ifBlank { site.host }, platform))
        val existing = blogs.findByCanonicalUrl(blogUrl.toString())?.let { subscriptions.findByUserIdAndBlogId(userId, it.getId()) }
        return LookupResult(
            BlogInfo(discovered.second.title.ifBlank { site.host }, blogUrl.toString(), platform, label(platform)),
            discovered.second.posts,
            existing != null,
            existing?.friendName,
            token,
        )
    }

    @Transactional
    fun create(userId: String, token: String, friendName: String): SubscriptionResult {
        val name = friendName.trim()
        if (name.isEmpty() || name.length > 20) throw BusinessException(ErrorCode.INVALID_INPUT)
        val entry = lookupCache.getIfPresent(token) as? LookupEntry
        if (entry == null || entry.ownerId != userId) throw BusinessException(ErrorCode.BLOG_LOOKUP_EXPIRED)
        val blog = blogs.findByCanonicalUrl(entry.canonicalUrl) ?: createBlog(entry)
        if (subscriptions.findByUserIdAndBlogId(userId, blog.getId()) != null) throw BusinessException(ErrorCode.ALREADY_SUBSCRIBED)
        val subscription = try {
            subscriptions.saveAndFlush(Subscription(userId, blog.getId(), name))
        } catch (_: org.springframework.dao.DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.ALREADY_SUBSCRIBED)
        }
        lookupCache.invalidate(token)
        return SubscriptionResult(IdPrefix.SUBSCRIPTION.encode(subscription.getId()), subscription.friendName, BlogInfo(blog.title, blog.siteUrl, blog.platform, label(blog.platform), IdPrefix.BLOG.encode(blog.getId())), subscription.createdAt)
    }

    private fun findBlogAfterRace(canonicalUrl: String): Blog? =
        transactionManager?.let {
            TransactionTemplate(it).apply { propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW }
                .execute { blogs.findByCanonicalUrl(canonicalUrl) }
        }
            ?: blogs.findByCanonicalUrl(canonicalUrl)

    private fun createBlog(entry: LookupEntry): Blog {
        val work = {
            blogs.saveAndFlush(Blog(entry.canonicalUrl, entry.siteUrl, entry.feedUrl, entry.title, entry.platform)).also { states.save(BlogFetchState(it.getId())) }
        }
        return try {
            transactionManager?.let {
                TransactionTemplate(it).apply { propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW }
                    .execute { work() }!!
            } ?: work()
        } catch (e: org.springframework.dao.DataIntegrityViolationException) {
            // The failed insert has rolled back; perform the race winner read separately.
            findBlogAfterRace(entry.canonicalUrl) ?: throw e
        }
    }

    @Transactional
    fun delete(userId: String, externalId: String) {
        val id = IdPrefix.SUBSCRIPTION.decodeOrNull(externalId) ?: throw BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND)
        val subscription = subscriptions.findByIdAndUserId(id, userId) ?: throw BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND)
        subscriptions.delete(subscription)
    }

    private fun normalize(raw: String): URI {
        if (raw.length > 2048) throw BusinessException(ErrorCode.INVALID_BLOG_URL)
        val candidate = if (raw.contains("://")) raw else "https://$raw"
        val parsed = try { URI(candidate) } catch (_: Exception) { throw BusinessException(ErrorCode.INVALID_BLOG_URL) }
        val host = parsed.host ?: throw BusinessException(ErrorCode.INVALID_BLOG_URL)
        if (parsed.scheme.lowercase() !in setOf("http", "https")) throw BusinessException(ErrorCode.INVALID_BLOG_URL)
        validateHost(host)
        val canonicalHost = host.lowercase().removePrefix("www.").removePrefix("m.")
        val port = if ((parsed.scheme.equals("http", true) && parsed.port == 80) || (parsed.scheme.equals("https", true) && parsed.port == 443)) -1 else parsed.port
        val path = when {
            canonicalHost.endsWith("blog.naver.com") -> "/" + parsed.path.trim('/').substringBefore('/').ifEmpty { "" }
            canonicalHost == "velog.io" -> "/" + parsed.path.trim('/').substringBefore('/').ifEmpty { "" }
            canonicalHost.endsWith(".tistory.com") -> "/"
            else -> parsed.path.trimEnd('/').ifEmpty { "/" }
        }
        val query = parsed.query?.split('&')
            ?.filter { part ->
                val key = part.substringBefore('=').lowercase()
                key !in setOf("utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "gclid", "fbclid")
            }
            ?.joinToString("&")
            ?.ifEmpty { null }
        return URI(parsed.scheme.lowercase(), null, canonicalHost, port, path, query, null)
    }

    private fun validateHost(host: String) {
        if (host.equals("localhost", true) || host.endsWith(".localhost") || host.endsWith(".internal")) throw BusinessException(ErrorCode.BLOCKED_BLOG_URL)
        try {
            hostResolver.resolve(host).forEach { address ->
                if (BlogUrlPolicy.isBlocked(address)) throw BusinessException(ErrorCode.BLOCKED_BLOG_URL)
            }
        } catch (e: BusinessException) { throw e } catch (_: Exception) { /* DNS failure is reported as unreachable during fetch */ }
    }

    private fun detectPlatform(host: String) = when {
        host.endsWith("blog.naver.com") -> "NAVER"
        host == "velog.io" -> "VELOG"
        host.endsWith(".tistory.com") -> "TISTORY"
        else -> "GENERIC"
    }

    /**
     * Generic inputs often point at individual posts. The discovered feed gives us the reliable
     * blog base: a conventional feed suffix is removed while a path-hosted blog keeps its path.
     * Known platforms retain their account-level path established by [normalize].
     */
    private fun canonicalBlogUrl(site: URI, platform: String, feedUrl: URI): URI {
        if (platform != "GENERIC") return site
        if (!site.host.equals(feedUrl.host, ignoreCase = true) || site.port != feedUrl.port) {
            return URI(site.scheme, null, site.host, site.port, "/", null, null)
        }
        val feedPath = feedUrl.path.trimEnd('/')
        val suffix = listOf("/rss.xml", "/atom.xml", "/index.xml", "/feed.xml", "/rss", "/feed")
            .firstOrNull { feedPath.endsWith(it, ignoreCase = true) }
        val basePath = suffix?.let { feedPath.removeSuffix(it).ifEmpty { "/" } } ?: feedPath.ifEmpty { "/" }
        return URI(site.scheme, null, site.host, site.port, basePath, null, null)
    }

    private fun label(platform: String) = mapOf("NAVER" to "네이버 블로그", "VELOG" to "Velog", "TISTORY" to "Tistory", "GENERIC" to "개인 블로그")[platform]!!

    private fun discover(site: URI, platform: String): Pair<String, Feed>? {
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        val candidates = mutableListOf<String>()
        when (platform) {
            "NAVER" -> candidates += "https://rss.blog.naver.com/${site.path.trim('/').substringAfterLast('/')}.xml"
            "VELOG" -> candidates += "https://api.velog.io/rss/@${site.path.substringAfter("@", "")}"
            "TISTORY" -> candidates += "${site.scheme}://${site.host}/rss"
            // Connected Tistory custom domains use the same stable endpoint.
            "GENERIC" -> candidates += "${site.scheme}://${site.host}/rss"
        }
        var reachable = false
        val html = try { fetch(site, 0, deadline).also { reachable = it != null } } catch (e: BusinessException) { if (e.errorCode == ErrorCode.BLOG_NOT_REACHABLE) null else throw e }
        if (html != null && !html.isFeed) {
            Jsoup.parse(html.body, site.toString()).select("link[rel=alternate]").filter { it.attr("type").lowercase() in setOf("application/rss+xml", "application/atom+xml") }.forEach { candidates += site.resolve(it.attr("href")).toString() }
        }
        candidates += listOf("/rss", "/feed", "/rss.xml", "/atom.xml", "/index.xml").map { site.resolve(it).toString() }
        for (candidate in candidates.distinct()) {
            val response = try { fetch(URI(candidate), 0, deadline).also { reachable = reachable || it != null } } catch (e: BusinessException) { if (e.errorCode == ErrorCode.BLOG_NOT_REACHABLE) null else throw e } ?: continue
            if (response.isFeed) {
                try { return candidate to parseFeed(response.body) }
                catch (_: Exception) { continue }
            }
        }
        if (!reachable) throw BusinessException(ErrorCode.BLOG_NOT_REACHABLE)
        return null
    }

    private data class Response(val body: String, val isFeed: Boolean)
    private fun fetch(uri: URI, redirects: Int, deadline: Long): Response? {
        if (redirects > 3 || System.nanoTime() >= deadline) throw BusinessException(ErrorCode.BLOG_NOT_REACHABLE)
        if (uri.scheme.lowercase() !in setOf("http", "https")) throw BusinessException(ErrorCode.INVALID_BLOG_URL)
        val host = uri.host ?: throw BusinessException(ErrorCode.INVALID_BLOG_URL)
        val addresses = try { hostResolver.resolve(host) }
            catch (_: UnknownHostException) { throw BusinessException(ErrorCode.BLOG_NOT_REACHABLE) }
            catch (_: Exception) { throw BusinessException(ErrorCode.BLOG_NOT_REACHABLE) }
        if (addresses.any { BlogUrlPolicy.isBlocked(it) }) throw BusinessException(ErrorCode.BLOCKED_BLOG_URL)
        val address = addresses.first { !BlogUrlPolicy.isBlocked(it) }
        val remainingMs = ((deadline - System.nanoTime()) / 1_000_000).coerceAtLeast(1)
        val response = try { httpTransport.request(uri, address, if (uri.port == -1) host else "$host:${uri.port}", minOf(3000, remainingMs.toInt()), minOf(5000, remainingMs.toInt())) }
            catch (_: SocketTimeoutException) { throw BusinessException(ErrorCode.BLOG_NOT_REACHABLE) }
            catch (_: java.io.IOException) { return null }
        if (response.status in 300..399) {
            val location = response.location ?: throw BusinessException(ErrorCode.BLOG_NOT_REACHABLE)
            return fetch(uri.resolve(location), redirects + 1, deadline)
        }
        if (response.tooLarge) throw BusinessException(ErrorCode.BLOG_NOT_SUPPORTED)
        if (response.status !in 200..299 || response.body == null) return null
        val body = response.body
        return Response(body, body.contains("<rss", true) || body.contains("<feed", true))
    }

    private fun parseFeed(body: String): Feed {
        val input = SyndFeedInput().apply { setAllowDoctypes(false) }
        val feed = input.build(XmlReader(body.byteInputStream()))
        val posts = feed.entries.orEmpty().take(3).map { PostPreview(it.title.orEmpty(), it.publishedDate?.toInstant()) }
        return Feed(feed.title.orEmpty(), posts)
    }
}
