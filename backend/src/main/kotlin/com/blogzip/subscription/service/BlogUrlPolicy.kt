package com.blogzip.subscription.service

/** Pure URL policy shared by service and focused tests. */
object BlogUrlPolicy {
 fun canonical(raw:String):String {
  require(raw.length<=2048)
  val u=java.net.URI(if(raw.contains("://")) raw else "https://$raw")
  require(u.scheme.lowercase() in setOf("http","https") && !u.host.isNullOrBlank())
  val h=u.host.lowercase().removePrefix("www.").removePrefix("m.")
  val p=if((u.scheme.equals("http",true)&&u.port==80)||(u.scheme.equals("https",true)&&u.port==443))-1 else u.port
  return java.net.URI(u.scheme.lowercase(),null,h,p,u.path.trimEnd('/').ifEmpty{"/"},null,null).toString()
 }
 fun isBlocked(address: java.net.InetAddress):Boolean {
  val bytes=address.address
  if(address.isAnyLocalAddress||address.isLoopbackAddress||address.isLinkLocalAddress||address.isSiteLocalAddress||address.isMulticastAddress) return true
  if (bytes.size == 4) {
   val first = bytes[0].toInt() and 255
   val second = bytes[1].toInt() and 255
   if (first == 0 || first >= 224) return true
   if (first == 100 && second in 64..127) return true
   if (first == 192 && second == 0 && (bytes[2].toInt() and 255) == 0) return true
   if (first == 192 && second == 0 && (bytes[2].toInt() and 255) == 2) return true
   if (first == 198 && second == 18) return true
   if (first == 198 && second == 19) return true
   if (first == 198 && second == 51 && (bytes[2].toInt() and 255) == 100) return true
   if (first == 203 && second == 0 && (bytes[2].toInt() and 255) == 113) return true
  }
  if(bytes.size==16) {
   if ((bytes[0].toInt() and 0xfe)==0xfc) return true
   if (isIpv6SpecialPurpose(bytes)) return true
   // IPv4-mapped IPv6 (including ::ffff:127.0.0.1) must obey IPv4 policy.
   if (bytes.copyOfRange(0, 10).all { it == 0.toByte() } && bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()) {
    return isBlocked(java.net.InetAddress.getByAddress(bytes.copyOfRange(12, 16)))
   }
  }
  return false
 }

 private fun isIpv6SpecialPurpose(bytes: ByteArray): Boolean {
  val first = bytes[0].toInt() and 255
  val second = bytes[1].toInt() and 255
  val third = bytes[2].toInt() and 255
  val fourth = bytes[3].toInt() and 255
  // Only 2000::/3 is global unicast; special-purpose allocations inside it are denied below.
  if (first !in 0x20..0x3f) return true
  if (first == 0x3f && second == 0xff && third and 0xf0 == 0x00) return true
  // 2001::/23 is entirely IANA special-purpose space. It is not a Blog target.
  if (first == 0x20 && second == 0x01 && third and 0xfe == 0x00) return true
  if (first == 0x20 && second == 0x02) return true
  if (first == 0x64 && second == 0xff && third == 0x9b && fourth == 0x01) return true
  if (first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8) return true
  return false
 }
}
