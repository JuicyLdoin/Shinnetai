package net.ldoin.shinnetai.protocol;

import java.util.EnumSet;
import java.util.Set;

public enum ShinnetaiFeature {

    COMPRESSION(1L),
    TRAFFIC_LOG(2L),
    RELIABLE_DELIVERY(4L);

    public static long toFlags(ShinnetaiFeature... features) {
        long flags = 0;
        for (ShinnetaiFeature f : features) {
            flags |= f.flag;
        }

        return flags;
    }

    public static long toFlags(Set<ShinnetaiFeature> features) {
        long flags = 0;
        for (ShinnetaiFeature f : features) {
            flags |= f.flag;
        }

        return flags;
    }

    public static Set<ShinnetaiFeature> fromFlags(long flags) {
        Set<ShinnetaiFeature> set = EnumSet.noneOf(ShinnetaiFeature.class);
        for (ShinnetaiFeature f : values()) {
            if ((flags & f.flag) != 0) {
                set.add(f);
            }
        }

        return set;
    }

    private final long flag;

    ShinnetaiFeature(long flag) {
        this.flag = flag;
    }

    public long flag() {
        return flag;
    }
}