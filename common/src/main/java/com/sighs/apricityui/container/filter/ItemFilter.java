package com.sighs.apricityui.container.filter;

import java.util.Objects;

/**
 * A composable predicate for filtering items or other values.
 *
 * @param <T> value type accepted by this filter
 */
@FunctionalInterface
public interface ItemFilter<T> {
    ItemFilter<Object> ANY = value -> true;
    ItemFilter<Object> NONE = value -> false;

    boolean test(T value);

    default ItemFilter<T> and(ItemFilter<? super T> other) {
        Objects.requireNonNull(other, "other");
        return value -> test(value) && other.test(value);
    }

    default ItemFilter<T> or(ItemFilter<? super T> other) {
        Objects.requireNonNull(other, "other");
        return value -> test(value) || other.test(value);
    }

    default ItemFilter<T> negate() {
        return value -> !test(value);
    }

    @SuppressWarnings("unchecked")
    static <T> ItemFilter<T> any() {
        return (ItemFilter<T>) ANY;
    }

    @SuppressWarnings("unchecked")
    static <T> ItemFilter<T> none() {
        return (ItemFilter<T>) NONE;
    }

    @SafeVarargs
    static <T> ItemFilter<T> allOf(ItemFilter<? super T>... filters) {
        Objects.requireNonNull(filters, "filters");
        ItemFilter<? super T>[] copy = filters.clone();
        for (ItemFilter<? super T> filter : copy) {
            Objects.requireNonNull(filter, "filter");
        }
        return value -> {
            for (ItemFilter<? super T> filter : copy) {
                if (!filter.test(value)) {
                    return false;
                }
            }
            return true;
        };
    }

    @SafeVarargs
    static <T> ItemFilter<T> anyOf(ItemFilter<? super T>... filters) {
        Objects.requireNonNull(filters, "filters");
        ItemFilter<? super T>[] copy = filters.clone();
        for (ItemFilter<? super T> filter : copy) {
            Objects.requireNonNull(filter, "filter");
        }
        return value -> {
            for (ItemFilter<? super T> filter : copy) {
                if (filter.test(value)) {
                    return true;
                }
            }
            return false;
        };
    }

    static <T> ItemFilter<T> not(ItemFilter<? super T> filter) {
        Objects.requireNonNull(filter, "filter");
        return value -> !filter.test(value);
    }
}
