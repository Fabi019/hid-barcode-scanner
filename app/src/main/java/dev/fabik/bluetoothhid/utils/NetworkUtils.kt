package dev.fabik.bluetoothhid.utils

import java.net.Inet4Address
import java.net.NetworkInterface

fun localIpAddresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces()
        ?.asSequence()
        ?.filter { it.isUp && !it.isLoopback }
        ?.flatMap { it.inetAddresses.asSequence() }
        ?.filterIsInstance<Inet4Address>()
        ?.filter { !it.isLoopbackAddress }
        ?.mapNotNull { it.hostAddress }
        ?.toList()
}.getOrNull() ?: emptyList()

fun Int?.isTcpMode(): Boolean =
    this == ConnectionMode.TCP_SERVER.ordinal || this == ConnectionMode.TCP_CLIENT.ordinal
