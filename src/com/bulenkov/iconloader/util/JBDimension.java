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

import java.awt.Dimension;
import javax.swing.plaf.UIResource;

/**
 * @author Konstantin Bulenkov
 */
public class JBDimension extends Dimension {

  public final float originalScale = JBUI.scale(1f);

  public JBDimension(int width, int height) {
    super(scale(width), scale(height));
  }

  private static int scale(int size) {
    return size == -1 ? -1 : JBUI.scale(size);
  }

  public static JBDimension create(Dimension from) {
    return from instanceof JBDimension
      ? ((JBDimension) from)
      : new JBDimension(from.width, from.height);
  }

  public JBDimensionUIResource asUIResource() {
    return new JBDimensionUIResource(this);
  }

  public static class JBDimensionUIResource
    extends JBDimension
    implements UIResource {

    public JBDimensionUIResource(JBDimension size) {
      super(0, 0);
      width = size.width;
      height = size.height;
    }
  }

  public JBDimension withWidth(int width) {
    var size = new JBDimension(0, 0);
    size.width = scale(width);
    size.height = height;
    return size;
  }

  public JBDimension withHeight(int height) {
    var size = new JBDimension(0, 0);
    size.width = width;
    size.height = scale(height);
    return size;
  }
}
