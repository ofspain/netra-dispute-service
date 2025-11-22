package com.netstra.disputes.services.validation.image;

import java.io.File;

@FunctionalInterface
public interface ImageValidator {

    void validateImage(File file);
}
