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

package com.bulenkov.iconloader.util;

import java.awt.Component;
import java.awt.Graphics;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Icon;

/**
 * @author Konstantin Bulenkov
 */
public class EmptyIcon implements Icon {

  private static final Map<Integer, Icon> cache = new HashMap<>();
  private final int width;
  private final int height;

  public static Icon create(int size) {
    var icon = cache.get(size);

    if (icon == null && size < 129) {
      cache.put(size, icon = new EmptyIcon(size, size));
    }

    return icon == null ? new EmptyIcon(size, size) : icon;
  }

  public static Icon create(int width, int height) {
    return width == height ? create(width) : new EmptyIcon(width, height);
  }

  public static Icon create(Icon base) {
    return create(base.getIconWidth(), base.getIconHeight());
  }

  public EmptyIcon(int width, int height) {
    this.width = width;
    this.height = height;
  }

  public int getIconWidth() {
    return width;
  }

  public int getIconHeight() {
    return height;
  }

  public void paintIcon(Component component, Graphics g, int i, int j) {}

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof EmptyIcon icon)) {
      return false;
    }

    return height == icon.height && width == icon.width;
  }

  public int hashCode() {
    var sum = width + height;
    return (sum * (sum + 1)) / 2 + width;
  }
}
