package com.google.code.sfvcheckftplet.service;

import com.google.code.sfvcheckftplet.SessionWriter;
import com.google.code.sfvcheckftplet.Status;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.CRC32;
import org.apache.ftpserver.ftplet.FtpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrcService
{
  private static final Logger logger = LoggerFactory.getLogger(CrcService.class);
  private CrcCache crcCache;

  public CrcService()
  {
    this.crcCache = new CrcCache();
  }

  public void init() {
    this.crcCache.init();
  }

  public void printStatus(SessionWriter writer) throws IllegalStateException, FtpException {
    this.crcCache.printStatus(writer);
  }

  public void shutdown() {
    this.crcCache.shutdown();
  }

  public Status checkNewFile(File file)
    throws IOException
  {
    Map files = this.crcCache.getCrcInfo(file.getParentFile());
    Status status;
    if (files != null) {
      String sfvHex = (String)files.get(file.getName());
      if (sfvHex != null) {
        long checksum = checksum(file, false);
        //Status status;
        if (hexToLong(sfvHex) == checksum) {
          status = Status.OK;
        }
        else
        {
          logger.warn("FAIL " + longToHex(Long.valueOf(checksum)) + " != " + sfvHex);
          status = Status.FAIL;
        }
      }
      else
      {
        status = Status.UNKNOWN;
      }

    }
    else
    {
      logger.debug("No sfv file for " + file.getParentFile().getAbsolutePath());
      status = Status.UNKNOWN;
    }
    return status;
  }

  public Status rescanFile(SessionWriter writer, File file, String sfvHex, boolean forced) throws IOException, FtpException
  {
    Status status = null;
    if (file.exists()) {
      long checksum = checksum(file, forced);
      if (hexToLong(sfvHex) == checksum) {
        File ifbad = new File(file.getParentFile(), file.getName() + "-BAD");
        if (ifbad.exists()) {
          ifbad.delete();
        }
        File ifmissing = new File(file.getParentFile(), file.getName() + "-MISSING");
        if (ifmissing.exists()) {
          ifmissing.delete();
        }
        status = Status.OK;
        writer.println("File: " + file.getName() + " " + sfvHex);
      } else {
        logger.warn("FAIL " + longToHex(Long.valueOf(checksum)) + " != " + sfvHex);
        status = Status.FAIL;
        writer.println("FAIL " + file.getName());
        File renamed = new File(file.getParentFile(), file.getName() + "-BAD");
        if (renamed.exists()) {
          renamed.delete();
        }
        renamed.createNewFile();
        File ifmissing = new File(file.getParentFile(), file.getName() + "-MISSING");
        if (ifmissing.exists())
          ifmissing.delete();
      }
    }
    else {
      File bad = new File(file.getParentFile(), file.getName() + "-BAD");
      if (bad.exists()) {
        bad.delete();
      }
      status = Status.MISSING;
    }
    return status;
  }

  public long checksum(File file, boolean force)
    throws IOException
  {
    Long checksum = null;
    if (!force)
      checksum = this.crcCache.getFileCrc(file);
    else {
      logger.debug("Forced (re)calculation of checksum");
    }
    if (checksum == null) {
      long startTime = System.currentTimeMillis();
      InputStream fis = null;
      try
      {
        fis = new FileInputStream(file);
        CRC32 crc32 = new CRC32();
        byte[] buf = new byte[2048];
        int len;
        while ((len = fis.read(buf)) >= 0) {
          crc32.update(buf, 0, len);
        }
        checksum = Long.valueOf(crc32.getValue());
      } finally {
        if (fis != null) {
          fis.close();
        }
      }
      long elapse = System.currentTimeMillis() - startTime;
      logger.debug("Calculating crc for " + file.getName() + " took " + elapse + " msec");
      this.crcCache.putFileCrc(file, checksum);
    }

    return checksum.longValue();
  }

  public Map<String, String> getCrcInfo(File folder) {
    return this.crcCache.getCrcInfo(folder);
  }

  public Map<String, String> parseSfv(File sfvFile, boolean force) throws IOException {
    Map files = null;
    if (!force)
      files = this.crcCache.getCrcInfo(sfvFile.getParentFile());
    else {
      logger.debug("Forced (re)parse of sfv");
    }

    if (files == null) {
      files = new LinkedHashMap();
      Scanner scanner = null;
      try {
        scanner = new Scanner(sfvFile);

        while (scanner.hasNextLine()) {
          String strLine = scanner.nextLine();
          if ((!strLine.startsWith(";")) && (strLine.length() != 0)) {
            int last = strLine.lastIndexOf(' ');
            String file = strLine.substring(0, last);
            String hex = strLine.substring(last + 1);
            files.put(file, hex);
          }
        }
      } finally {
        if (scanner != null) {
          scanner.close();
        }
      }
      this.crcCache.putCrcInfo(sfvFile.getParentFile(), files);
    }
    return files;
  }

  public void clearData(File file) {
    if (FileTools.isSfv(file)) {
      this.crcCache.removeCrcInfo(file.getParentFile());
    }
    this.crcCache.removeFileCrc(file);
  }

  private long hexToLong(String hex)
  {
    return Long.valueOf(hex, 16).longValue();
  }

  private String longToHex(Long hex) {
    String val = Long.toHexString(hex.longValue());

    if (val.length() < 8) {
      StringBuilder str = new StringBuilder();
      for (int i = 0; i < 8 - val.length(); i++) {
        str.append("0");
      }
      str.append(val);
      val = str.toString();
    }
    return val;
  }
}
