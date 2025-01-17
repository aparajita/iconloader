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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Konstantin Bulenkov
 */
public class URLUtil {

  public static final String SCHEME_SEPARATOR = "://";
  public static final String FILE_PROTOCOL = "file";
  public static final String HTTP_PROTOCOL = "http";
  public static final String JAR_PROTOCOL = "jar";
  public static final String JAR_SEPARATOR = "!/";

  public static final Pattern DATA_URI_PATTERN = Pattern.compile(
    "data:([^,;]+/[^,;]+)(;charset=[^,;]+)?(;base64)?,(.+)"
  );

  private URLUtil() {}

  /**
   * Opens a url stream. The semantics is the sames as {@link java.net.URL#openStream()}. The
   * separate method is needed, since jar URLs open jars via JarFactory and thus keep them
   * mapped into memory.
   */

  public static InputStream openStream(URL url) throws IOException {
    var protocol = url.getProtocol();
    var isFile =
      protocol.equals(JAR_PROTOCOL) && !url.getFile().startsWith(HTTP_PROTOCOL);
    return isFile ? openJarStream(url) : url.openStream();
  }

  public static InputStream openResourceStream(final URL url)
    throws IOException {
    try {
      return openStream(url);
    } catch (FileNotFoundException ex) {
      final var protocol = url.getProtocol();
      String file = null;

      if (protocol.equals(FILE_PROTOCOL)) {
        file = url.getFile();
      } else if (protocol.equals(JAR_PROTOCOL)) {
        var pos = url.getFile().indexOf("!");

        if (pos >= 0) {
          file = url.getFile().substring(pos + 1);
        }
      }

      if (file != null && file.startsWith("/")) {
        var resourceStream = URLUtil.class.getResourceAsStream(file);
        if (resourceStream != null) {
          return resourceStream;
        }
      }

      throw ex;
    }
  }

  private static InputStream openJarStream(URL url) throws IOException {
    var paths = splitJarUrl(url.getFile());

    if (paths == null) {
      throw new MalformedURLException(url.getFile());
    }

    @SuppressWarnings({ "resource" })
    final var zipFile = new ZipFile(unquote(paths.first));
    ZipEntry zipEntry = zipFile.getEntry(paths.second);

    if (zipEntry == null) {
      throw new FileNotFoundException(
        "Entry " + paths.second + " not found in " + paths.first
      );
    }

    return new FilterInputStream(zipFile.getInputStream(zipEntry)) {
      @Override
      public void close() throws IOException {
        super.close();
        zipFile.close();
      }
    };
  }

  public static String unquote(String urlString) {
    urlString = urlString.replace('/', File.separatorChar);
    return unescapePercentSequences(urlString);
  }

  public static Pair<String, String> splitJarUrl(String fullPath) {
    var delimiter = fullPath.indexOf(JAR_SEPARATOR);

    if (delimiter >= 0) {
      var resourcePath = fullPath.substring(delimiter + 2);
      var jarPath = fullPath.substring(0, delimiter);

      if (StringUtil.startsWithConcatenation(jarPath, FILE_PROTOCOL, ":")) {
        jarPath = jarPath.substring(FILE_PROTOCOL.length() + 1);
        return Pair.create(jarPath, resourcePath);
      }
    }

    return null;
  }

  public static String unescapePercentSequences(String s) {
    if (s.indexOf('%') == -1) {
      return s;
    }

    var decoded = new StringBuilder();
    final var len = s.length();
    var i = 0;

    while (i < len) {
      var c = s.charAt(i);

      if (c == '%') {
        var bytes = new ArrayList<Integer>();

        while (i + 2 < len && s.charAt(i) == '%') {
          final var d1 = decode(s.charAt(i + 1));
          final var d2 = decode(s.charAt(i + 2));

          if (d1 != -1 && d2 != -1) {
            bytes.add((((d1 & 0xf) << 4) | (d2 & 0xf)));
            i += 3;
          } else {
            break;
          }
        }

        if (!bytes.isEmpty()) {
          final var bytesArray = new byte[bytes.size()];
          for (var j = 0; j < bytes.size(); j++) {
            bytesArray[j] = bytes.get(j).byteValue();
          }

          decoded.append(new String(bytesArray, StandardCharsets.UTF_8));
          continue;
        }
      }

      decoded.append(c);
      i++;
    }

    return decoded.toString();
  }

  private static int decode(char c) {
    if ((c >= '0') && (c <= '9')) {
      return c - '0';
    }

    if ((c >= 'a') && (c <= 'f')) {
      return c - 'a' + 10;
    }

    if ((c >= 'A') && (c <= 'F')) {
      return c - 'A' + 10;
    }

    return -1;
  }

  public static boolean containsScheme(String url) {
    return url.contains(SCHEME_SEPARATOR);
  }

  public static boolean isDataUri(String value) {
    return (
      !value.isEmpty() &&
      value.startsWith(
        "data:",
        value.charAt(0) == '"' || value.charAt(0) == '\'' ? 1 : 0
      )
    );
  }

  /**
   * Extracts byte array from given data:URL string.
   * data:URL will be decoded from base64 if it contains the marker of base64 encoding.
   *
   * @param dataUrl data:URL-like string (may be quoted)
   * @return extracted byte array or {@code null} if it cannot be extracted.
   */

  public static byte[] getBytesFromDataUri(String dataUrl) {
    var matcher = DATA_URI_PATTERN.matcher(
      StringUtil.stripQuotesAroundValue(dataUrl)
    );

    if (matcher.matches()) {
      try {
        var content = matcher.group(4);
        final var charset = StandardCharsets.UTF_8;
        final var bytes = content.getBytes(charset);
        return ";base64".equalsIgnoreCase(matcher.group(3))
          ? Base64Converter.decode(bytes)
          : bytes;
      } catch (IllegalArgumentException e) {
        return null;
      }
    }

    return null;
  }
}
