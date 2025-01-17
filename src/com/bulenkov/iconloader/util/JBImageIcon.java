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

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import javax.swing.ImageIcon;
import org.jetbrains.annotations.NotNull;

/**
 * HiDPI-aware image icon
 *
 * @author Konstantin Bulenkov
 */
public class JBImageIcon extends ImageIcon {

  public JBImageIcon(@NotNull Image image) {
    super(image);
  }

  @Override
  public synchronized void paintIcon(
    final Component c,
    final Graphics g,
    final int x,
    final int y
  ) {
    final var observer = getImageObserver();
    UIUtil.drawImage(g, getImage(), x, y, observer == null ? c : observer);
  }
}
