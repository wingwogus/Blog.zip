package com.blogzip.subscription

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress

class RatePolicyTest {
 @Test fun blocksZeroAndUlaAndReserved() {
  assertTrue(com.blogzip.subscription.service.BlogUrlPolicy.isBlocked(InetAddress.getByName("0.1.2.3")))
  assertTrue(com.blogzip.subscription.service.BlogUrlPolicy.isBlocked(InetAddress.getByName("fc00::1")))
 }
}
