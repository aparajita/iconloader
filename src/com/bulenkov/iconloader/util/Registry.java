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

/**
 * @author Konstantin Bulenkov
 */
public class Registry {

  public static boolean is(String key) {
    final var value = System.getProperty(key);
    return "true".equalsIgnoreCase(value);
  }

  public static Float getFloat(String key) {
    try {
      return Float.parseFloat(System.getProperty(key));
    } catch (Exception e) {
      return null;
    }
  }
}
