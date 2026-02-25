package com.sighs.apricityui.registry.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AsyncTaskClass {
    String id();
    int maxQueueSize() default 256;
    int applyBudgetPerTick() default 1;
    long applyTimeBudgetNs() default 1_500_000L;
    String workerThreadName() default "ApricityUI-Worker";
}
