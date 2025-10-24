package com.netstra.disputes.services.validation.image;

import utilities.FileUtils;

import java.io.File;

@FunctionalInterface
public interface ImageValidator {

    void validateImage(File file);
}
