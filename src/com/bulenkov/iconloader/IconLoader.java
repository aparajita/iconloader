/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.bulenkov.iconloader;

import com.bulenkov.iconloader.util.ConcurrencyUtil;
import com.bulenkov.iconloader.util.ImageLoader;
import com.bulenkov.iconloader.util.ImageUtil;
import com.bulenkov.iconloader.util.JBImageIcon;
import com.bulenkov.iconloader.util.JBUI;
import com.bulenkov.iconloader.util.Registry;
import com.bulenkov.iconloader.util.RetrievableIcon;
import com.bulenkov.iconloader.util.ScalableIcon;
import com.bulenkov.iconloader.util.Scalr;
import com.bulenkov.iconloader.util.SoftReference;
import com.bulenkov.iconloader.util.StringUtil;
import com.bulenkov.iconloader.util.UIUtil;
import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ImageFilter;
import java.lang.ref.Reference;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UnusedDeclaration")
public final class IconLoader {

  public static boolean STRICT = false;
  private static boolean USE_DARK_ICONS = UIUtil.isUnderDarcula();

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private static final ConcurrentMap<URI, CachedImageIcon> ourIconsCache =
    new ConcurrentHashMap<>(100, 0.9f, 2);

  /**
   * This cache contains mapping between icons and disabled icons.
   */
  private static final Map<Icon, Icon> ourIcon2DisabledIcon = new WeakHashMap<>(
    200
  );

  private static float SCALE = JBUI.scale(1f);
  private static ImageFilter IMAGE_FILTER;

  private static final ImageIcon EMPTY_ICON = new ImageIcon(
    UIUtil.createImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)
  ) {
    @NonNls
    public String toString() {
      return "Empty icon " + super.toString();
    }
  };

  private static final AtomicBoolean ourIsActivated = new AtomicBoolean(true);
  private static final AtomicBoolean ourIsSaveRealIconPath = new AtomicBoolean(
    false
  );
  public static final Component ourComponent = new Component() {};

  private IconLoader() {}

  @NotNull
  public static Icon getIcon(@NotNull final Image image) {
    return new JBImageIcon(image);
  }

  public static void setUseDarkIcons(boolean useDarkIcons) {
    USE_DARK_ICONS = useDarkIcons;
    clearCache();
  }

  public static void setScale(float scale) {
    if (scale != SCALE) {
      SCALE = scale;
      clearCache();
    }
  }

  public static void setFilter(ImageFilter filter) {
    if (!Registry.is("color.blindness.icon.filter")) {
      filter = null;
    }

    if (IMAGE_FILTER != filter) {
      IMAGE_FILTER = filter;
      clearCache();
    }
  }

  private static void clearCache() {
    ourIconsCache.clear();
    ourIcon2DisabledIcon.clear();
  }

  //TODO[kb] support iconsets
  //public static Icon getIcon(@NotNull final String path, @NotNull final String darkVariantPath) {
  //  return new InvariantIcon(getIcon(path), getIcon(darkVariantPath));
  //}

  @Nullable
  private static Icon getReflectiveIcon(
    @NotNull String path,
    ClassLoader classLoader
  ) {
    try {
      @NonNls
      var pckg = path.startsWith("AllIcons.")
        ? "com.intellij.icons."
        : "icons.";

      var cur = Class.forName(
        pckg + path.substring(0, path.lastIndexOf('.')).replace('.', '$'),
        true,
        classLoader
      );

      var field = cur.getField(path.substring(path.lastIndexOf('.') + 1));
      return (Icon) field.get(null);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Might return null if icon was not found.
   * Use only if you expected null return value, otherwise see IconLoader.getIcon(String)
   */
  @Nullable
  public static Icon getIcon(
    @NotNull String path,
    @NotNull final Class<?> aClass
  ) {
    final var icon = findIcon(path, aClass);

    if (icon == null) {
      System.err.println(
        "Icon cannot be found in '" + path + "', aClass='" + aClass + "'"
      );
    }

    return icon;
  }

  public static void activate() {
    ourIsActivated.set(true);
  }

  public static void disable() {
    ourIsActivated.set(false);
  }

  public static boolean isLoaderDisabled() {
    return !ourIsActivated.get();
  }

  /**
   * This method is for test purposes only
   */
  static void enableSaveRealIconPath() {
    ourIsSaveRealIconPath.set(true);
  }

  /**
   * Might return null if icon was not found.
   * Use only if you expected null return value, otherwise see {@link IconLoader#getIcon(String, Class)}
   */
  @Nullable
  public static Icon findIcon(
    @NotNull final String path,
    @NotNull final Class<?> aClass
  ) {
    return findIcon(path, aClass, false);
  }

  @Nullable
  public static Icon findIcon(
    @NotNull String path,
    @NotNull final Class<?> aClass,
    boolean computeNow
  ) {
    return findIcon(path, aClass, computeNow, STRICT);
  }

  @Nullable
  public static Icon findIcon(
    @NotNull String path,
    @NotNull final Class<?> aClass,
    boolean computeNow,
    boolean strict
  ) {
    path = patchPath(path);

    if (isReflectivePath(path)) {
      return getReflectiveIcon(path, aClass.getClassLoader());
    }

    var myURL = aClass.getResource(path);

    if (myURL == null) {
      if (strict) throw new RuntimeException(
        "Can't find icon in '" + path + "' near " + aClass
      );

      return null;
    }

    final var icon = findIcon(myURL);

    if (icon instanceof CachedImageIcon) {
      ((CachedImageIcon) icon).myOriginalPath = path;
      ((CachedImageIcon) icon).myClassLoader = aClass.getClassLoader();
    }

    return icon;
  }

  private static String patchPath(@NotNull String path) {
    // If we are running on a Retina display, try to find @2x image.
    if (UIUtil.isRetina()) {
      var paths = StringUtil.split(path, ".");

      if (paths.size() > 1) {
        var ext = paths.getLast();

        if (!ext.equals("svg")) {
          paths.set(paths.size() - 2, paths.get(paths.size() - 2) + "@2x");
          path = StringUtil.join(paths, ".");
        }
      }
    }

    return path;
  }

  private static boolean isReflectivePath(@NotNull String path) {
    var paths = StringUtil.split(path, ".");
    return paths.size() > 1 && paths.getFirst().endsWith("Icons");
  }

  @Nullable
  public static Icon findIcon(URL url) {
    return findIcon(url, true);
  }

  @Nullable
  public static Icon findIcon(URL url, boolean useCache) {
    if (url == null) {
      return null;
    }

    try {
      var icon = ourIconsCache.get(new URI(url.toString()));

      if (icon == null) {
        icon = new CachedImageIcon(url);

        if (useCache) {
          icon =
            ConcurrencyUtil.cacheOrGet(
              ourIconsCache,
              new URI(url.toString()),
              icon
            );
        }
      }

      return icon;
    } catch (URISyntaxException e) {
      return null;
    }
  }

  @Nullable
  public static Icon findIcon(
    @NotNull String path,
    @NotNull ClassLoader classLoader
  ) {
    path = patchPath(path);
    if (isReflectivePath(path)) {
      return getReflectiveIcon(path, classLoader);
    }

    if (!StringUtil.startsWithChar(path, '/')) {
      return null;
    }

    final var url = classLoader.getResource(path.substring(1));
    final var icon = findIcon(url);

    if (icon instanceof CachedImageIcon) {
      ((CachedImageIcon) icon).myOriginalPath = path;
      ((CachedImageIcon) icon).myClassLoader = classLoader;
    }

    return icon;
  }

  @Nullable
  private static ImageIcon checkIcon(final Image image, @NotNull URL url) {
    if (image == null || image.getHeight(LabelHolder.ourFakeComponent) < 1) { // image wasn't loaded or broken
      return null;
    }

    final var icon = getIcon(image);
    return !isGoodSize(icon) ? EMPTY_ICON : (ImageIcon) icon;
  }

  public static boolean isGoodSize(@NotNull final Icon icon) {
    return icon.getIconWidth() > 0 && icon.getIconHeight() > 0;
  }

  /**
   * Gets (creates if necessary) disabled icon based on the passed one.
   *
   * @return <code>ImageIcon</code> constructed from disabled image of passed icon.
   */
  @Nullable
  public static Icon getDisabledIcon(Icon icon) {
    if (icon instanceof LazyIcon) {
      icon = ((LazyIcon) icon).getOrComputeIcon();
    }

    if (icon == null) {
      return null;
    }

    var disabledIcon = ourIcon2DisabledIcon.get(icon);

    if (disabledIcon == null) {
      if (!isGoodSize(icon)) {
        return EMPTY_ICON;
      }

      final var scale = UIUtil.isRetina() ? 2 : 1;
      var image = new BufferedImage(
        scale * icon.getIconWidth(),
        scale * icon.getIconHeight(),
        BufferedImage.TYPE_INT_ARGB
      );

      final var graphics = image.createGraphics();
      graphics.setColor(UIUtil.TRANSPARENT_COLOR);
      graphics.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());
      graphics.scale(scale, scale);
      icon.paintIcon(LabelHolder.ourFakeComponent, graphics, 0, 0);
      graphics.dispose();

      var img = ImageUtil.filter(image, UIUtil.getGrayFilter());

      if (UIUtil.isRetina()) {
        img = RetinaImage.createFrom(img);
      }

      disabledIcon = new JBImageIcon(img);
      ourIcon2DisabledIcon.put(icon, disabledIcon);
    }

    return disabledIcon;
  }

  public static Icon getTransparentIcon(@NotNull final Icon icon) {
    return getTransparentIcon(icon, 0.5f);
  }

  public static Icon getTransparentIcon(
    @NotNull final Icon icon,
    final float alpha
  ) {
    return new RetrievableIcon() {
      @Override
      public Icon retrieveIcon() {
        return icon;
      }

      @Override
      public int getIconHeight() {
        return icon.getIconHeight();
      }

      @Override
      public int getIconWidth() {
        return icon.getIconWidth();
      }

      @Override
      public void paintIcon(
        final Component c,
        final Graphics g,
        final int x,
        final int y
      ) {
        final var g2 = (Graphics2D) g;
        final var saveComposite = g2.getComposite();
        g2.setComposite(
          AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, alpha)
        );
        icon.paintIcon(c, g2, x, y);
        g2.setComposite(saveComposite);
      }
    };
  }

  /**
   * Gets a snapshot of the icon, immune to changes made by these calls:
   * {@link IconLoader#setScale(float)}, {@link IconLoader#setFilter(ImageFilter)}, {@link IconLoader#setUseDarkIcons(boolean)}
   *
   * @param icon the source icon
   * @return the icon snapshot
   */
  @NotNull
  public static Icon getIconSnapshot(@NotNull Icon icon) {
    return icon instanceof CachedImageIcon
      ? ((CachedImageIcon) icon).getRealIcon()
      : icon;
  }

  public static final class CachedImageIcon implements ScalableIcon {

    private volatile Object myRealIcon;
    public String myOriginalPath;
    private ClassLoader myClassLoader;

    @NotNull
    private final URL myUrl;

    private volatile boolean dark;
    private volatile float scale;
    private final int numberOfPatchers = 0;

    private volatile ImageFilter filter;
    private final MyScaledIconsCache myScaledIconsCache =
      new MyScaledIconsCache();

    public CachedImageIcon(@NotNull URL url) {
      myUrl = url;
      dark = USE_DARK_ICONS;
      scale = SCALE;
      filter = IMAGE_FILTER;
    }

    @NotNull
    private synchronized ImageIcon getRealIcon() {
      if (
        isLoaderDisabled() &&
        (myRealIcon == null ||
          dark != USE_DARK_ICONS ||
          scale != SCALE ||
          filter != IMAGE_FILTER)
      ) {
        return EMPTY_ICON;
      }

      if (!isValid()) {
        myRealIcon = null;
        dark = USE_DARK_ICONS;
        scale = SCALE;
        filter = IMAGE_FILTER;
        myScaledIconsCache.clear();
      }

      var realIcon = myRealIcon;

      if (realIcon instanceof Icon) {
        return (ImageIcon) realIcon;
      }

      ImageIcon icon;

      if (realIcon instanceof Reference) {
        //noinspection unchecked
        icon = ((Reference<ImageIcon>) realIcon).get();

        if (icon != null) {
          return icon;
        }
      }

      var image = ImageLoader.loadFromUrl(myUrl, true, filter);
      icon = checkIcon(image, myUrl);

      if (icon != null) {
        if (icon.getIconWidth() < 50 && icon.getIconHeight() < 50) {
          realIcon = icon;
        } else {
          realIcon = new SoftReference<>(icon);
        }

        myRealIcon = realIcon;
      }

      return icon == null ? EMPTY_ICON : icon;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isValid() {
      return dark == USE_DARK_ICONS && scale == SCALE && filter == IMAGE_FILTER;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      getRealIcon().paintIcon(c, g, x, y);
    }

    @Override
    public int getIconWidth() {
      return getRealIcon().getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return getRealIcon().getIconHeight();
    }

    @Override
    public String toString() {
      return myUrl.toString();
    }

    @Override
    public Icon scale(float scaleFactor) {
      if (scaleFactor == 1f) {
        return this;
      }

      if (!isValid()) {
        getRealIcon(); // force state update & cache reset
      }

      var icon = myScaledIconsCache.getScaledIcon(scaleFactor);
      return icon != null ? icon : this;
    }

    private class MyScaledIconsCache {

      // Map {false -> image}, {true -> image@2x}
      private final Map<Boolean, SoftReference<Image>> origImagesCache =
        Collections.synchronizedMap(new HashMap<>(2));

      private static final int SCALED_ICONS_CACHE_LIMIT = 5;

      // Map {effective scale -> icon}
      private final Map<Float, SoftReference<Icon>> scaledIconsCache =
        Collections.synchronizedMap(
          new LinkedHashMap<>(SCALED_ICONS_CACHE_LIMIT) {
            @Override
            public boolean removeEldestEntry(
              Map.Entry<Float, SoftReference<Icon>> entry
            ) {
              return size() > SCALED_ICONS_CACHE_LIMIT;
            }
          }
        );

      public Image getOrigImage(boolean retina) {
        var img = SoftReference.dereference(origImagesCache.get(retina));

        if (img == null) {
          img =
            ImageLoader.loadFromUrl(
              myUrl,
              UIUtil.isUnderDarcula(),
              retina,
              filter
            );
          origImagesCache.put(retina, new SoftReference<>(img));
        }

        return img;
      }

      public Icon getScaledIcon(float scale) {
        var effectiveScale = scale * JBUI.scale(1f);
        var icon = SoftReference.dereference(
          scaledIconsCache.get(effectiveScale)
        );

        if (icon == null) {
          var needRetinaImage = (effectiveScale >= 1.5f || UIUtil.isRetina());
          var image = getOrigImage(needRetinaImage);

          if (image != null) {
            var iconImage = getRealIcon().getImage();
            var width = (int) (ImageUtil.getRealWidth(iconImage) * scale);
            var height = (int) (ImageUtil.getRealHeight(iconImage) * scale);

            var resizedImage = Scalr.resize(
              ImageUtil.toBufferedImage(image),
              Scalr.Method.ULTRA_QUALITY,
              width,
              height
            );

            if (UIUtil.isRetina()) {
              resizedImage =
                (BufferedImage) RetinaImage.createFrom(resizedImage);
            }

            icon = getIcon(resizedImage);
            scaledIconsCache.put(effectiveScale, new SoftReference<>(icon));
          }
        }

        return icon;
      }

      public void clear() {
        scaledIconsCache.clear();
        origImagesCache.clear();
      }
    }
  }

  public abstract static class LazyIcon implements Icon {

    private boolean myWasComputed;
    private Icon myIcon;
    private boolean isDarkVariant = USE_DARK_ICONS;
    private float scale = SCALE;
    //    private int numberOfPatchers = 0;
    private ImageFilter filter = IMAGE_FILTER;

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      final var icon = getOrComputeIcon();

      if (icon != null) {
        icon.paintIcon(c, g, x, y);
      }
    }

    @Override
    public int getIconWidth() {
      final var icon = getOrComputeIcon();
      return icon != null ? icon.getIconWidth() : 0;
    }

    @Override
    public int getIconHeight() {
      final var icon = getOrComputeIcon();
      return icon != null ? icon.getIconHeight() : 0;
    }

    protected final synchronized Icon getOrComputeIcon() {
      if (
        !myWasComputed ||
        isDarkVariant != USE_DARK_ICONS ||
        scale != SCALE ||
        filter != IMAGE_FILTER
        /*|| numberOfPatchers != ourPatchers.size()*/
      ) {
        isDarkVariant = USE_DARK_ICONS;
        scale = SCALE;
        filter = IMAGE_FILTER;
        myWasComputed = true;
        //        numberOfPatchers = ourPatchers.size();
        myIcon = compute();
      }

      return myIcon;
    }

    public final void load() {
      getIconWidth();
    }

    protected abstract Icon compute();
  }

  private static class LabelHolder {

    /**
     * To get disabled icon with paint it into the image. Some icons require
     * not null component to paint.
     */
    private static final JComponent ourFakeComponent = new JLabel();
  }
}
