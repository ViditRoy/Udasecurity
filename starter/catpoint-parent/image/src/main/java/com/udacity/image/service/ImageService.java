package com.udacity.image.service;

import java.awt.image.BufferedImage;

/**
 * Interface describing the behavior of an image analysis service that can identify cats.
 * Abstraction allows the SecurityService to be tested independently of any concrete
 * image service implementation (e.g. FakeImageService or AwsImageService).
 */
public interface ImageService {

    /**
     * Returns true if the provided image contains a cat.
     *
     * @param image                Image to scan
     * @param confidenceThreshold  Minimum confidence threshold to consider for cat detection.
     *                             For example, 90.0f would require 90% confidence minimum.
     * @return true if a cat is detected, false otherwise
     */
    boolean imageContainsCat(BufferedImage image, float confidenceThreshold);
}
