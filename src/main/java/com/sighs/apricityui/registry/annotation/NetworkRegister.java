package com.sighs.apricityui.registry.annotation;

import com.sighs.apricityui.instance.network.NetworkType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//网络通信注册注解
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NetworkRegister {
    NetworkType type() default NetworkType.COMMON;
}
