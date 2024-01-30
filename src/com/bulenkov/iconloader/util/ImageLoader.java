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

import com.bulenkov.iconloader.IconLoader;
import com.bulenkov.iconloader.RetinaImage;
import java.awt.Component;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.image.ImageFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentMap;
import javax.swing.Icon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class ImageLoader implements Serializable {

  //  private static final Log LOG = Logger.getLogger("#com.intellij.util.ImageLoader");

  private static final ConcurrentMap<String, Image> ourCache =
    new ConcurrentSoftValueHashMap<>();

  private static class ImageDesc {

    public enum Type {
      PNG,

      //      SVG {
      //        @Override
      //        public Image load(URL url, InputStream is, float scale) throws IOException {
      //          return SVGLoader.load(url, is, scale);
      //        }
      //      },

      UNDEFINED;

      public Image load(URL url, InputStream stream, float scale) {
        return ImageLoader.load(stream, (int) scale);
      }
    }

    public final String path;
    public final @Nullable Class<?> cls; // resource class if present
    public final float scale; // initial scale factor
    public final Type type;
    public final boolean original; // path is not altered

    public ImageDesc(String path, Class<?> cls, float scale, Type type) {
      this(path, cls, scale, type, false);
    }

    public ImageDesc(
      String path,
      @Nullable Class<?> cls,
      float scale,
      Type type,
      boolean original
    ) {
      this.path = path;
      this.cls = cls;
      this.scale = scale;
      this.type = type;
      this.original = original;
    }

    @Nullable
    public Image load() throws IOException, URISyntaxException {
      String cacheKey = null;
      InputStream stream = null;
      URL url = null;

      if (cls != null) {
        //noinspection IOResourceOpenedButNotSafelyClosed
        stream = cls.getResourceAsStream(path);

        if (stream == null) {
          return null;
        }
      }

      if (stream == null) {
        url = new URI(path).toURL();
        var connection = url.openConnection();

        if (connection instanceof HttpURLConnection) {
          if (!original) {
            return null;
          }

          connection.addRequestProperty("User-Agent", "IntelliJ");

          cacheKey = path;
          var image = ourCache.get(cacheKey);

          if (image != null) {
            return image;
          }
        }

        stream = connection.getInputStream();
      }

      var image = type.load(url, stream, scale);

      if (image != null && cacheKey != null) {
        ourCache.put(cacheKey, image);
      }

      return image;
    }

    @Override
    public String toString() {
      return path + ", scale: " + scale + ", type: " + type;
    }
  }

  private static class ImageDescList extends ArrayList<ImageDesc> {

    private ImageDescList() {}

    @Nullable
    public Image load() {
      return load(ImageConverterChain.create());
    }

    @Nullable
    public Image load(@NotNull ImageConverterChain converters) {
      for (ImageDesc desc : this) {
        try {
          var image = desc.load();

          if (image == null) {
            continue;
          }

          //          LOG.debug("Loaded image: " + desc);
          return converters.convert(image, desc);
        } catch (IOException ignore) {} catch (URISyntaxException e) {
          throw new RuntimeException(e);
        }
      }

      return null;
    }

    public static ImageDescList create(
      @NotNull String file,
      @Nullable Class<?> cls,
      boolean dark,
      boolean retina,
      boolean allowFloatScaling
    ) {
      ImageDescList vars = new ImageDescList();
      if (retina || dark) {
        final var name = getNameWithoutExtension(file);
        final var ext = getExtension(file);
        var scale = calcScaleFactor(allowFloatScaling);

        // TODO: allow SVG images to freely scale on Retina

        //        if (Registry.is("ide.svg.icon") && dark) {
        //          vars.add(new ImageDesc(name + "_dark.svg", cls, UIUtil.isRetina() ? 2f : scale, ImageDesc.Type.SVG));
        //        }
        //
        //        if (Registry.is("ide.svg.icon")) {
        //          vars.add(new ImageDesc(name + ".svg", cls, UIUtil.isRetina() ? 2f : scale, ImageDesc.Type.SVG));
        //        }

        if (dark && retina) {
          vars.add(
            new ImageDesc(name + "@2x_dark." + ext, cls, 2f, ImageDesc.Type.PNG)
          );
        }

        if (dark) {
          vars.add(
            new ImageDesc(name + "_dark." + ext, cls, 1f, ImageDesc.Type.PNG)
          );
        }

        if (retina) {
          vars.add(
            new ImageDesc(name + "@2x." + ext, cls, 2f, ImageDesc.Type.PNG)
          );
        }
      }

      vars.add(new ImageDesc(file, cls, 1f, ImageDesc.Type.PNG, true));
      return vars;
    }
  }

  private interface ImageConverter {
    Image convert(@Nullable Image source, ImageDesc desc);
  }

  private static class ImageConverterChain extends ArrayList<ImageConverter> {

    private ImageConverterChain() {}

    public static ImageConverterChain create() {
      return new ImageConverterChain();
    }

    public ImageConverterChain withFilter(final ImageFilter filter) {
      return with((source, desc) -> ImageUtil.filter(source, filter));
    }

    public ImageConverterChain withRetina() {
      return with((source, desc) -> {
        if (source != null && UIUtil.isRetina() && desc.scale > 1) {
          return RetinaImage.createFrom(source, (int) desc.scale, ourComponent);
        }

        return source;
      });
    }

    public ImageConverterChain with(ImageConverter f) {
      add(f);
      return this;
    }

    public Image convert(Image image, ImageDesc desc) {
      for (var f : this) {
        image = f.convert(image, desc);
      }

      return image;
    }
  }

  public static final Component ourComponent = new Component() {};

  private static boolean waitForImage(Image image) {
    if (image == null) {
      return false;
    }

    if (image.getWidth(null) > 0) {
      return true;
    }

    MediaTracker mediatracker = new MediaTracker(ourComponent);
    mediatracker.addImage(image, 1);

    try {
      mediatracker.waitForID(1, 5000);
    } catch (InterruptedException ex) {
      ex.printStackTrace();
    }

    return !mediatracker.isErrorID(1);
  }

  @Nullable
  public static Image loadFromUrl(@NotNull URL url) {
    return loadFromUrl(url, true);
  }

  @Nullable
  public static Image loadFromUrl(@NotNull URL url, boolean allowFloatScaling) {
    return loadFromUrl(url, allowFloatScaling, null);
  }

  @Nullable
  public static Image loadFromUrl(
    @NotNull URL url,
    boolean allowFloatScaling,
    ImageFilter filter
  ) {
    final var scaleFactor = calcScaleFactor(allowFloatScaling);

    // We can't check all 3rd party plugins and convince the authors to add @2x icons.
    // (scaleFactor > 1.0) != isRetina() => we should scale images manually.
    // Note we never scale images on Retina displays because scaling is handled by the system.
    final var scaleImages = (scaleFactor > 1.0f) && !UIUtil.isRetina();

    // For any scale factor > 1.0, always prefer retina images, because downscaling
    // retina images provides a better result than upscaling non-retina images.
    final var loadRetinaImages = UIUtil.isRetina() || scaleImages;

    return ImageDescList
      .create(
        url.toString(),
        null,
        UIUtil.isUnderDarcula(),
        loadRetinaImages,
        allowFloatScaling
      )
      .load(
        ImageConverterChain
          .create()
          .withFilter(filter)
          .withRetina()
          .with((source, desc) -> {
            if (
              source != null && scaleImages
              /*&& desc.type != ImageDesc.Type.SVG*/
            ) {
              if (desc.path.contains("@2x")) return scaleImage(
                source,
                scaleFactor / 2.0f
              ); // divide by 2.0 as Retina images are 2x the resolution.
              else return scaleImage(source, scaleFactor);
            }

            return source;
          })
      );
  }

  private static float calcScaleFactor(boolean allowFloatScaling) {
    var scaleFactor = allowFloatScaling
      ? JBUI.scale(1f)
      : JBUI.scale(1f) > 1.5f ? 2f : 1f;
    assert scaleFactor >=
    1.0f : "By design, only scale factors >= 1.0 are supported";
    return scaleFactor;
  }

  @NotNull
  private static Image scaleImage(Image image, float scale) {
    var w = image.getWidth(null);
    var h = image.getHeight(null);

    if (w <= 0 || h <= 0) {
      return image;
    }

    var width = (int) (scale * w);
    var height = (int) (scale * h);

    // Using "QUALITY" instead of "ULTRA_QUALITY" results in images that are less blurry
    // because ultra quality performs a few more passes when scaling, which introduces blurriness
    // when the scaling factor is relatively small (i.e. <= 3.0f) -- which is the case here.
    return Scalr.resize(
      ImageUtil.toBufferedImage(image),
      Scalr.Method.QUALITY,
      width,
      height
    );
  }

  @Nullable
  public static Image loadFromUrl(URL url, boolean dark, boolean retina) {
    return loadFromUrl(url, dark, retina, null);
  }

  @Nullable
  public static Image loadFromUrl(
    URL url,
    boolean dark,
    boolean retina,
    ImageFilter filter
  ) {
    return ImageDescList
      .create(url.toString(), null, dark, retina, true)
      .load(ImageConverterChain.create().withFilter(filter).withRetina());
  }

  @Nullable
  public static Image loadFromResource(
    @NonNls @NotNull String path,
    @NotNull Class<?> aClass
  ) {
    return ImageDescList
      .create(
        path,
        aClass,
        UIUtil.isUnderDarcula(),
        UIUtil.isRetina() || JBUI.scale(1.0f) >= 1.5f,
        true
      )
      .load(ImageConverterChain.create().withRetina());
  }

  public static Image loadFromStream(@NotNull final InputStream inputStream) {
    return loadFromStream(inputStream, 1);
  }

  public static Image loadFromStream(
    @NotNull final InputStream inputStream,
    final int scale
  ) {
    return loadFromStream(inputStream, scale, null);
  }

  public static Image loadFromStream(
    @NotNull final InputStream inputStream,
    final int scale,
    ImageFilter filter
  ) {
    var image = load(inputStream, scale);
    var desc = new ImageDesc("", null, scale, ImageDesc.Type.UNDEFINED);
    return ImageConverterChain
      .create()
      .withFilter(filter)
      .withRetina()
      .convert(image, desc);
  }

  private static Image load(
    @NotNull final InputStream inputStream,
    final int scale
  ) {
    if (scale <= 0) {
      throw new IllegalArgumentException("Scale must be 1 or greater");
    }

    try (var outputStream = new BufferExposingByteArrayOutputStream()) {
      try (inputStream) {
        var buffer = new byte[1024];

        while (true) {
          final var n = inputStream.read(buffer);

          if (n < 0) {
            break;
          }

          outputStream.write(buffer, 0, n);
        }
      }

      var image = Toolkit
        .getDefaultToolkit()
        .createImage(outputStream.getInternalBuffer(), 0, outputStream.size());
      waitForImage(image);
      return image;
    } catch (Exception ex) {
      ex.printStackTrace();
    }

    return null;
  }

  public static boolean isGoodSize(final Icon icon) {
    return IconLoader.isGoodSize(icon);
  }

  @NotNull
  public static String getNameWithoutExtension(@NotNull String name) {
    var i = name.lastIndexOf('.');

    if (i != -1) {
      name = name.substring(0, i);
    }

    return name;
  }

  @NotNull
  public static String getExtension(@NotNull String fileName) {
    var index = fileName.lastIndexOf('.');

    if (index < 0) {
      return "";
    }

    return fileName.substring(index + 1);
  }
}
