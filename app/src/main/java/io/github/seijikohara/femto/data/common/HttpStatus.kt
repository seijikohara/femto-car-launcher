package io.github.seijikohara.femto.data.common

// HttpURLConnection predates RFC 6585 and has no constant for 429. Shared by
// every client under data/ that honours a server's throttle.
internal const val HTTP_TOO_MANY_REQUESTS = 429
