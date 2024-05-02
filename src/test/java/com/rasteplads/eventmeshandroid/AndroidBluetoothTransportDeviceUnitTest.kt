package com.rasteplads.eventmeshandroid

import org.junit.Assert.*
import org.junit.Test
import rasteplads.util.toByteArray

// Event Mesh Packets Start with the string ff:ff
val PACKET_START = ByteArray(2){0xFF.toByte()}

class AndroidBluetoothTransportDeviceUnitTest {
    private val packet = createPacket(ByteArray(25){0x01.toByte()})
    @Test
    fun createPacket_packet_is_correct_size(){

        // The UUID contains the first 16 bytes, so the ByteArray
        // must have length 13 to make size 29
        assertEquals(13, packet.second.size)
    }
    @Test
    fun createPacket_packet_begins_with_PACKET_START(){
        val firstTwo = packet.first.leastSignificantBits
            .toByteArray()
            .reversed()
            .slice(0..1)
            .toByteArray()

        assertTrue(firstTwo.contentEquals(PACKET_START))
    }
}