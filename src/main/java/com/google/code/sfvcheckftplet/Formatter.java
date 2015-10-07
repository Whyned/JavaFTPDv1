package com.google.code.sfvcheckftplet;

public class Formatter
{
  public String progressBar(double percentage, int length)
  {
    if (length < 3) {
      throw new IllegalArgumentException("Minimum size is 3");
    }
    int count = (int)Math.floor(percentage * (length - 2));
    StringBuilder builder = new StringBuilder("[");
    for (int i = 0; i < length - 2; i++) {
      if (i < count)
        builder.append('#');
      else {
        builder.append(':');
      }
    }
    builder.append(']');
    return builder.toString();
  }
}
