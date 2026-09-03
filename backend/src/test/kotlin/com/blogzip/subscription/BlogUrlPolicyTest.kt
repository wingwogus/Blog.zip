package com.blogzip.subscription

import com.blogzip.subscription.service.BlogUrlPolicy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetAddress

class BlogUrlPolicyTest {
 @Test fun canonicalizesHostAndTrackingQuery() {
  assertEquals("https://example.com/post", BlogUrlPolicy.canonical("https://www.example.com/post/?utm_source=x"))
 }
 @Test fun blocksReservedAndPrivateAddresses() {
  assertTrue(BlogUrlPolicy.isBlocked(InetAddress.getByName("127.0.0.1")))
  assertTrue(BlogUrlPolicy.isBlocked(InetAddress.getByName("10.0.0.1")))
  assertTrue(BlogUrlPolicy.isBlocked(InetAddress.getByName("192.0.2.1")))
  assertTrue(BlogUrlPolicy.isBlocked(InetAddress.getByName("2001:db8::1")))
  assertTrue(BlogUrlPolicy.isBlocked(InetAddress.getByName("fc00::1")))
  assertTrue(BlogUrlPolicy.isBlocked(InetAddress.getByName("224.0.0.1")))
  assertTrue(BlogUrlPolicy.isBlocked(InetAddress.getByName("::ffff:127.0.0.1")))
  assertTrue(BlogUrlPolicy.isBlocked(InetAddress.getByName("::ffff:10.0.0.1")))
 }
 @Test fun rejectsBadScheme() { assertThrows(IllegalArgumentException::class.java) { BlogUrlPolicy.canonical("ftp://example.com") } }
}
