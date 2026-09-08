package com.project.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

public class ForwardedIpTrustFilter extends OncePerRequestFilter {
    public static final String CLIENT_IP_ATTRIBUTE = ForwardedIpTrustFilter.class.getName() + ".clientIp";
    public static final int FILTER_ORDER = org.springframework.core.Ordered.HIGHEST_PRECEDENCE;
    private final List<String> trustedCidrs;

    public ForwardedIpTrustFilter(List<String> trustedCidrs) {
        this.trustedCidrs = trustedCidrs == null ? List.of() : List.copyOf(trustedCidrs);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean trusted = trustedCidrs.stream().anyMatch(cidr -> contains(cidr, request.getRemoteAddr()));
        String forwarded = request.getHeader("X-Forwarded-For");
        boolean validForwarded = trusted && validForwardedAddresses(forwarded);
        String clientIp = validForwarded ? firstForwardedAddress(forwarded) : request.getRemoteAddr();
        String resolvedClientIp = clientIp;
        HttpServletRequest sanitized = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if (!validForwarded && "x-forwarded-for".equalsIgnoreCase(name)) return null;
                return super.getHeader(name);
            }

            @Override
            public java.util.Enumeration<String> getHeaders(String name) {
                if (!validForwarded && "x-forwarded-for".equalsIgnoreCase(name)) return Collections.emptyEnumeration();
                return super.getHeaders(name);
            }

            @Override
            public java.util.Enumeration<String> getHeaderNames() {
                if (validForwarded) return super.getHeaderNames();
                return Collections.enumeration(Collections.list(super.getHeaderNames()).stream()
                        .filter(name -> !"x-forwarded-for".equalsIgnoreCase(name))
                        .toList());
            }
        };
        sanitized.setAttribute(CLIENT_IP_ATTRIBUTE, resolvedClientIp);
        chain.doFilter(sanitized, response);
    }

    private static String firstForwardedAddress(String value) {
        if (value == null || value.isBlank()) return null;
        return value.split(",", 2)[0].trim();
    }

    private static boolean validForwardedAddresses(String value) {
        if (value == null || value.isBlank()) return false;
        String[] addresses = value.split(",", -1);
        if (addresses.length > 16) return false;
        for (String address : addresses) {
            String candidate = address.trim();
            if (parseLiteral(candidate) == null) return false;
        }
        return true;
    }

    private static boolean contains(String cidr, String address) {
        try {
            String[] parts = cidr.split("/", 2);
            byte[] network = parseLiteral(parts[0]);
            byte[] candidate = parseLiteral(address);
            int bits = Integer.parseInt(parts[1]);
            if (network == null || candidate == null || network.length != candidate.length || bits < 0 || bits > network.length * 8) return false;
            for (int i = 0; i < network.length && bits > 0; i++, bits -= 8) {
                int mask = bits >= 8 ? 0xff : (0xff << (8 - bits)) & 0xff;
                if ((network[i] & mask) != (candidate[i] & mask)) return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static byte[] parseLiteral(String value) {
        if (value == null) return null;
        if (value.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) {
            String[] octets = value.split("\\.");
            byte[] address = new byte[4];
            for (int i = 0; i < octets.length; i++) {
                if (octets[i].length() > 1 && octets[i].startsWith("0")) return null;
                int octet = Integer.parseInt(octets[i]);
                if (octet > 255) return null;
                address[i] = (byte) octet;
            }
            return address;
        }
        if (!value.matches("[0-9A-Fa-f:]+") || !value.contains(":")) return null;
        try {
            return InetAddress.getByName(value).getAddress();
        } catch (Exception ignored) {
            return null;
        }
    }
}
