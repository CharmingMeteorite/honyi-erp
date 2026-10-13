package com.haucheng.erp.common;

public record Pagination(int page, int size, int offset) {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    public static Pagination of(Integer page, Integer size) {
        int normalizedPage = page == null || page < 1 ? 1 : page;
        int normalizedSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new Pagination(normalizedPage, normalizedSize, (normalizedPage - 1) * normalizedSize);
    }
}
