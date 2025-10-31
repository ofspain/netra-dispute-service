package com.netstra.disputes.transitions.bootstrap.annotation;

import com.netra.commons.enums.DisputeMode;
import java.lang.annotation.*;

//a marker to be double conscious
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DisputeModeBootStrapComponent {
    DisputeMode value();
}