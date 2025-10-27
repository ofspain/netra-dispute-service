package com.netstra.disputes.transitions.guards.bootstrap.annotation;

import com.netra.commons.enums.DisputeMode;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DisputeModeGuard {
    DisputeMode value();
}