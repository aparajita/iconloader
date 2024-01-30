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

import java.awt.BorderLayout;
import java.awt.HeadlessException;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

public class IconLoaderDemoFrame extends JFrame {

  public static void main(String[] args) {
    new IconLoaderDemoFrame();
  }

  public IconLoaderDemoFrame() throws HeadlessException {
    super("IconLoader Demo");
    setSize(300, 100);
    final var panel = new JPanel(new BorderLayout());
    getContentPane().add(panel);
    var bottom = new JPanel(new BorderLayout());
    panel.add(bottom, BorderLayout.SOUTH);
    var disabledButton = new JButton(
      "Print disabled",
      IconLoader.getIcon("/icons/print.png", IconLoaderDemoFrame.class)
    );
    disabledButton.setEnabled(false);
    var enabledButton = new JButton(
      "Print enabled",
      IconLoader.getIcon("/icons/print.png", IconLoaderDemoFrame.class)
    );
    bottom.add(disabledButton, BorderLayout.WEST);
    bottom.add(enabledButton, BorderLayout.EAST);

    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setVisible(true);
  }
}
