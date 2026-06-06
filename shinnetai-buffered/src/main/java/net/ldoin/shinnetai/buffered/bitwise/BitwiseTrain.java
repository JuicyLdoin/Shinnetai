package net.ldoin.shinnetai.buffered.bitwise;

import net.ldoin.shinnetai.buffered.util.ByteUtil;

import java.util.Arrays;

public class BitwiseTrain {

    private byte[] bytes;
    private int index;

    public BitwiseTrain(byte[] bytes) {
        this.bytes = bytes;
    }

    public BitwiseTrain(int bits) {
        bytes = new byte[ByteUtil.calculateBytesNeeded(bits)];
    }

    public BitwiseTrain incrementIndexByBytes(int bytes) {
        return incrementIndexByBits(bytes * 8);
    }

    public BitwiseTrain incrementIndexByBits(int bits) {
        int newIndex = index + bits;
        if (newIndex > bytes.length * 8) {
            throw new IndexOutOfBoundsException("BitwiseTrain overflow: advancing to bit " + newIndex + " exceeds buffer capacity of " + (bytes.length * 8) + " bits");
        }

        index = newIndex;
        return this;
    }

    public byte current() {
        return bytes[currentIndex()];
    }

    public int currentIndex() {
        return index / 8;
    }

    public int currentIndexInByte() {
        return index % 8;
    }

    public int availableInCurrent() {
        return 8 - (index % 8);
    }

    public byte[] bytesFor(int bits) {
        int size = availableInCurrent();
        while (size < bits) {
            size += 8;
        }

        size = ByteUtil.calculateBytesNeeded(size);

        byte[] bytes = new byte[size];
        bytes[0] = current();

        int index = currentIndex();
        for (int i = 1; i < size; i++) {
            bytes[i] = this.bytes[++index];
        }

        return bytes;
    }

    public long readBits(int bits) {
        long result = 0;
        int bitsRemaining = bits;
        int bitOffset = currentIndexInByte();
        int byteIdx = currentIndex();
        int shift = 0;
        while (bitsRemaining > 0) {
            if (byteIdx >= bytes.length) {
                throw new IndexOutOfBoundsException("BitwiseTrain read overflow: byte " + byteIdx + " >= buffer size " + bytes.length);
            }

            int bitsToRead = Math.min(bitsRemaining, 8 - bitOffset);
            long mask = ByteUtil.BIT_MASKS.get(bitsToRead);
            result |= ((bytes[byteIdx] >>> bitOffset) & mask) << shift;
            shift += bitsToRead;
            bitsRemaining -= bitsToRead;
            bitOffset = 0;
            byteIdx++;
        }

        incrementIndexByBits(bits);
        return result;
    }

    public void writeBits(long value, int bits) {
        int bitsRemaining = bits;
        int bitOffset = currentIndexInByte();
        int byteIdx = currentIndex();
        while (bitsRemaining > 0) {
            if (byteIdx >= bytes.length) {
                throw new IndexOutOfBoundsException("BitwiseTrain write overflow: byte " + byteIdx + " >= buffer size " + bytes.length);
            }

            int bitsToWrite = Math.min(bitsRemaining, 8 - bitOffset);
            long mask = ByteUtil.BIT_MASKS.get(bitsToWrite);
            bytes[byteIdx] |= (byte) ((value & mask) << bitOffset);
            value >>>= bitsToWrite;
            bitsRemaining -= bitsToWrite;
            bitOffset = 0;
            byteIdx++;
        }

        incrementIndexByBits(bits);
    }

    public byte[] bytes() {
        return bytes;
    }

    public BitwiseTrain expand(int bits) {
        bytes = Arrays.copyOf(bytes, bytes.length + ByteUtil.calculateBytesNeeded(bits));
        return this;
    }
}