package net.ldoin.shinnetai.security;

import java.net.InetAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class IpFilter {

    public static Builder builder() {
        return new Builder();
    }

    public static IpFilter allowAll() {
        return builder().allowAll().build();
    }

    private final boolean defaultAllow;
    private final Set<String> exactAllow;
    private final Set<String> exactDeny;
    private final Set<CidrBlock> cidrAllow;
    private final Set<CidrBlock> cidrDeny;
    private final Predicate<InetAddress> customPredicate;

    private IpFilter(Builder builder) {
        this.defaultAllow = builder.defaultAllow;
        this.exactAllow = Set.copyOf(builder.exactAllow);
        this.exactDeny = Set.copyOf(builder.exactDeny);
        this.cidrAllow = Set.copyOf(builder.cidrAllow);
        this.cidrDeny = Set.copyOf(builder.cidrDeny);
        this.customPredicate = builder.customPredicate;
    }

    public boolean isAllowed(InetAddress address) {
        if (customPredicate != null && !customPredicate.test(address)) {
            return false;
        }

        String host = address.getHostAddress();
        if (exactDeny.contains(host) || matchesCidr(cidrDeny, address)) {
            return false;
        }

        if (exactAllow.contains(host) || matchesCidr(cidrAllow, address)) {
            return true;
        }

        return defaultAllow;
    }

    private static boolean matchesCidr(Set<CidrBlock> blocks, InetAddress address) {
        for (CidrBlock block : blocks) {
            if (block.contains(address)) {
                return true;
            }
        }

        return false;
    }

    private record CidrBlock(byte[] networkBytes, int prefixLen) {

        private static CidrBlock parse(String cidr) {
            String[] parts = cidr.split("/");
            try {
                InetAddress addr = InetAddress.getByName(parts[0]);
                int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : (addr.getAddress().length * 8);
                return new CidrBlock(addr.getAddress(), prefix);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid CIDR: " + cidr, e);
            }
        }

        private boolean contains(InetAddress address) {
            byte[] addrBytes = address.getAddress();
            if (addrBytes.length != networkBytes.length) {
                return false;
            }

            int bitsLeft = prefixLen;
            for (int i = 0; i < networkBytes.length && bitsLeft > 0; i++) {
                int mask = bitsLeft >= 8 ? 0xFF : (0xFF << (8 - bitsLeft)) & 0xFF;
                if ((addrBytes[i] & mask) != (networkBytes[i] & mask)) {
                    return false;
                }

                bitsLeft -= 8;
            }

            return true;
        }
    }

    public static class Builder {

        private boolean defaultAllow = true;
        private final Set<String> exactAllow = ConcurrentHashMap.newKeySet();
        private final Set<String> exactDeny = ConcurrentHashMap.newKeySet();
        private final Set<CidrBlock> cidrAllow = ConcurrentHashMap.newKeySet();
        private final Set<CidrBlock> cidrDeny = ConcurrentHashMap.newKeySet();
        private Predicate<InetAddress> customPredicate;

        public Builder allowAll() {
            this.defaultAllow = true;
            return this;
        }

        public Builder denyAll() {
            this.defaultAllow = false;
            return this;
        }

        public Builder allow(String ipOrCidr) {
            if (ipOrCidr.contains("/")) {
                cidrAllow.add(CidrBlock.parse(ipOrCidr));
            } else {
                exactAllow.add(ipOrCidr);
            }

            return this;
        }

        public Builder deny(String ipOrCidr) {
            if (ipOrCidr.contains("/")) {
                cidrDeny.add(CidrBlock.parse(ipOrCidr));
            } else {
                exactDeny.add(ipOrCidr);
            }

            return this;
        }

        public Builder customPredicate(Predicate<InetAddress> predicate) {
            this.customPredicate = predicate;
            return this;
        }

        public IpFilter build() {
            return new IpFilter(this);
        }
    }
}