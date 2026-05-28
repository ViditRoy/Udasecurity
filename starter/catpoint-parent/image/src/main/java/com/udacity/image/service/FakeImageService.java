package com.udacity.image.service;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Service that tries to guess if an image displays a cat.
 * Used for testing and demo purposes — returns a random result.
 */
public class FakeImageService implements ImageService {
    private final Random r = new Random();

    @Override
    public boolean imageContainsCat(BufferedImage image, float confidenceThreshold) {
        return r.nextBoolean();
    }
}
