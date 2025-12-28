package com.netstra.disputes.services.imaging;

import java.io.File;

@FunctionalInterface
public interface ImageValidator {

    void validateImage(File file);
}
