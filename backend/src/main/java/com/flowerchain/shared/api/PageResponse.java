package com.flowerchain.shared.api;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> of(List<T> allItems, int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(100, Math.max(1, size));
        int fromIndex = Math.min(clampedPage * clampedSize, allItems.size());
        int toIndex = Math.min(fromIndex + clampedSize, allItems.size());
        List<T> slice = allItems.subList(fromIndex, toIndex);
        int totalPages = allItems.isEmpty() ? 0 : (int) Math.ceil((double) allItems.size() / clampedSize);
        return new PageResponse<>(slice, clampedPage, clampedSize, allItems.size(), totalPages);
    }
}
