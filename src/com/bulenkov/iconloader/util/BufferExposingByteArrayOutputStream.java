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

/**
 * @author Konstantin Bulenkov
 */
public class BufferExposingByteArrayOutputStream
  extends AsyncByteArrayOutputStream {

  public BufferExposingByteArrayOutputStream() {}

  public BufferExposingByteArrayOutputStream(int size) {
    super(size);
  }

  public byte[] getInternalBuffer() {
    return myBuffer;
  }

  public int backOff(int size) {
    assert size >= 0 : size;
    myCount -= size;
    assert myCount >= 0 : myCount;
    return myCount;
  }
}
