package com.rootcause.foshol.intake.domain;

import com.rootcause.foshol.intake.domain.vo.ImageId;

public class ImageNotFoundException extends RuntimeException {

    private final ImageId imageId;

    public ImageNotFoundException(ImageId imageId) {
        super("image not found: " + imageId);
        this.imageId = imageId;
    }

    public ImageId imageId() {
        return imageId;
    }
}
