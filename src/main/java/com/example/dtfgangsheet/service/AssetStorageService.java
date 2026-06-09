package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.model.ImageAsset;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

public interface AssetStorageService {
    ImageAsset load(String source);
    void cleanup(List<ImageAsset> assets);
    long peekLocalSize(String source);
    BufferedImage decodeForRender(ImageAsset asset, int w, int h) throws IOException;
}