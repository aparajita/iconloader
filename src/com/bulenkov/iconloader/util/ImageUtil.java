/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bulenkov.iconloader.util;

import com.bulenkov.iconloader.JBHiDPIScaledImage;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class ImageUtil {

  public static BufferedImage toBufferedImage(@NotNull Image image) {
    if (image instanceof JBHiDPIScaledImage) {
      Image img = ((JBHiDPIScaledImage) image).getDelegate();

      if (img != null) {
        image = img;
      }
    }

    if (image instanceof BufferedImage) {
      return (BufferedImage) image;
    }

    @SuppressWarnings("UndesirableClassUsage")
    var bufferedImage = new BufferedImage(
      image.getWidth(null),
      image.getHeight(null),
      BufferedImage.TYPE_INT_ARGB
    );
    var g = bufferedImage.createGraphics();
    g.drawImage(image, 0, 0, null);
    g.dispose();
    return bufferedImage;
  }

  public static int getRealWidth(@NotNull Image image) {
    if (image instanceof JBHiDPIScaledImage) {
      Image img = ((JBHiDPIScaledImage) image).getDelegate();

      if (img != null) {
        image = img;
      }
    }

    return image.getWidth(null);
  }

  public static int getRealHeight(@NotNull Image image) {
    if (image instanceof JBHiDPIScaledImage) {
      Image img = ((JBHiDPIScaledImage) image).getDelegate();

      if (img != null) {
        image = img;
      }
    }

    return image.getHeight(null);
  }

  public static Image filter(Image image, ImageFilter filter) {
    if (image == null || filter == null) {
      return image;
    }

    return Toolkit
      .getDefaultToolkit()
      .createImage(
        new FilteredImageSource(toBufferedImage(image).getSource(), filter)
      );
  }
}
