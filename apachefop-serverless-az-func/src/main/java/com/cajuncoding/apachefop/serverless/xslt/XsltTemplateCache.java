package com.cajuncoding.apachefop.serverless.xslt;

import net.sf.saxon.s9api.XsltExecutable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * LRU cache for compiled XSLT Templates using Saxon-HE.
 * Thread-safe implementation using synchronized methods.
 */
public class XsltTemplateCache {
    private final Map<String, CacheEntry> cache;
    private final int maxSize;
    private final boolean enabled;
    private final Logger logger;

    private static class CacheEntry {
        final XsltExecutable executable;
        long lastAccessTime;

        CacheEntry(XsltExecutable executable) {
            this.executable = executable;
            this.lastAccessTime = System.currentTimeMillis();
        }

        void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }

    public XsltTemplateCache(int maxSize, boolean enabled, Logger logger) {
        this.maxSize = maxSize;
        this.enabled = enabled;
        this.logger = logger;
        
        // LinkedHashMap with access-order for LRU behavior
        this.cache = new LinkedHashMap<String, CacheEntry>(maxSize + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * Generate SHA-256 hash of XSLT content for cache key.
     */
    public String generateCacheKey(String xsltContent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(xsltContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to hashCode if SHA-256 is not available
            return String.valueOf(xsltContent.hashCode());
        }
    }

    /**
     * Get cached XsltExecutable or null if not found.
     */
    public synchronized XsltExecutable get(String cacheKey) {
        if (!enabled) {
            return null;
        }

        CacheEntry entry = cache.get(cacheKey);
        if (entry != null) {
            entry.updateAccessTime();
            if (logger != null) {
                logger.info("XSLT cache HIT for key: " + cacheKey.substring(0, Math.min(16, cacheKey.length())) + "...");
            }
            return entry.executable;
        }
        
        if (logger != null) {
            logger.info("XSLT cache MISS for key: " + cacheKey.substring(0, Math.min(16, cacheKey.length())) + "...");
        }
        return null;
    }

    /**
     * Put compiled XsltExecutable into cache.
     */
    public synchronized void put(String cacheKey, XsltExecutable executable) {
        if (!enabled || executable == null) {
            return;
        }

        cache.put(cacheKey, new CacheEntry(executable));
        if (logger != null) {
            logger.info("XSLT cached with key: " + cacheKey.substring(0, Math.min(16, cacheKey.length())) + "... (cache size: " + cache.size() + "/" + maxSize + ")");
        }
    }

    /**
     * Clear all cached entries.
     */
    public synchronized void clear() {
        cache.clear();
        if (logger != null) {
            logger.info("XSLT cache cleared");
        }
    }

    /**
     * Get current cache size.
     */
    public synchronized int size() {
        return cache.size();
    }

    /**
     * Check if cache is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
}
