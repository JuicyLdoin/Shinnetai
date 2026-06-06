package net.ldoin.shinnetai.debug;

public record TrafficEvent(
        long timestamp,
        TrafficDirection direction,
        String packetClass,
        byte[] rawBytes
) {
}