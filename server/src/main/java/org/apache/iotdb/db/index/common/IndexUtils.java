package org.apache.iotdb.db.index.common;

import java.io.IOException;
import org.apache.iotdb.db.rescon.TVListAllocator;
import org.apache.iotdb.db.utils.TestOnly;
import org.apache.iotdb.db.utils.datastructure.TVList;
import org.apache.iotdb.db.utils.datastructure.primitive.PrimitiveList;
import org.apache.iotdb.tsfile.exception.NotImplementedException;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.TimeValuePair;

public class IndexUtils {

  public static int getDataTypeSize(TVList srcData) {
    return getDataTypeSize(srcData.getDataType());
  }

  public static int getDataTypeSize(PrimitiveList srcData) {
    return getDataTypeSize(srcData.getTsDataType());
  }

  public static int getDataTypeSize(TSDataType dataType) {
    switch (dataType) {
      case INT32:
      case FLOAT:
        return 4;
      case INT64:
      case DOUBLE:
        return 8;
      default:
        throw new NotImplementedException(dataType.toString());
    }
  }

  public static double getDoubleFromAnyType(TVList srcData, int idx) {
    switch (srcData.getDataType()) {
      case INT32:
        return srcData.getInt(idx);
      case INT64:
        return srcData.getLong(idx);
      case FLOAT:
        return srcData.getFloat(idx);
      case DOUBLE:
        return srcData.getDouble(idx);
      default:
        throw new NotImplementedException(srcData.getDataType().toString());
    }
  }

  public static float getFloatFromAnyType(TVList srcData, int idx) {
    switch (srcData.getDataType()) {
      case INT32:
        return srcData.getInt(idx);
      case INT64:
        return srcData.getLong(idx);
      case FLOAT:
        return srcData.getFloat(idx);
      case DOUBLE:
        return (float) srcData.getDouble(idx);
      default:
        throw new NotImplementedException(srcData.getDataType().toString());
    }
  }

  public static double getValueRange(TVList srcData, int offset, int length) {
    double minValue = Double.MAX_VALUE;
    double maxValue = Double.MIN_VALUE;
    for (int idx = offset; idx < offset + length; idx++) {
      if (idx >= srcData.size()) {
        break;
      }
      double v = getDoubleFromAnyType(srcData, idx);
      if (v < minValue) {
        minValue = v;
      }
      if (v > maxValue) {
        maxValue = v;
      }
    }
    return maxValue - minValue;
  }

  public static double[] parseNumericPattern(String patternStr) {
    String[] ns = patternStr.split(",");
    double[] res = new double[ns.length];
    for (int i = 0; i < ns.length; i++) {
      res[i] = Double.parseDouble(ns[i]);
    }
    return res;
  }

  public static String removeQuotation(String v) {
    int start = 0;
    int end = v.length();
    if (v.startsWith("\'") || v.startsWith("\"")) {
      start = 1;
    }
    if (v.endsWith("\'") || v.endsWith("\"")) {
      end = v.length() - 1;
    }
    return v.substring(start, end);
  }

  @TestOnly
  public static String tvListToStr(TVList tvList) {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    for (int i = 0; i < tvList.size(); i++) {
      TimeValuePair pair = tvList.getTimeValuePair(i);
      switch (tvList.getDataType()) {
        case INT32:
          sb.append(String.format("[%d,%d],", pair.getTimestamp(), pair.getValue().getInt()));
          break;
        case INT64:
          sb.append(String.format("[%d,%d],", pair.getTimestamp(), pair.getValue().getLong()));
          break;
        case FLOAT:
          sb.append(String.format("[%d,%.2f],", pair.getTimestamp(), pair.getValue().getFloat()));
          break;
        case DOUBLE:
          sb.append(String.format("[%d,%.2f],", pair.getTimestamp(), pair.getValue().getDouble()));
          break;
        default:
          throw new NotImplementedException(tvList.getDataType().toString());
      }
    }
    sb.append("}");
    return sb.toString();
  }


  /**
   * Align {@code src} into equally spaced sequences of {@code alignedSize}
   *
   * @param src to be transformed
   * @param alignedSize target size
   * @return a list with the same length as target
   */
  public static TVList alignUniform(TVList src, int alignedSize) {
    TVList res = TVListAllocator.getInstance().allocate(src.getDataType());
    if (src.size() == 0) {
      return res;
    }
    long interval = (src.getLastTime() - src.getTime(0)) / (alignedSize - 1);

    int idx = 0;
    long newDelta;
    for (int i = 0; i < alignedSize; i++) {
      long timestamp = src.getTime(0) + i * interval;
      long minDelta = Math.abs(src.getTime(idx) - timestamp);
      while (idx < src.size() - 1
          && (newDelta = Math.abs(src.getTime(idx + 1) - timestamp)) < minDelta) {
        minDelta = newDelta;
        idx++;
      }
      putAnyValue(src, idx, res, timestamp);
    }
    return res;
  }

  public static void putAnyValue(TVList src, int srcIdx, TVList target) {
    assert src.getDataType() == target.getDataType();
    assert srcIdx < src.size();
    long time = src.getTime(srcIdx);
    switch (src.getDataType()) {
      case BOOLEAN:
        target.putBoolean(time, src.getBoolean(srcIdx));
        break;
      case INT32:
        target.putInt(time, src.getInt(srcIdx));
        break;
      case INT64:
        target.putLong(time, src.getLong(srcIdx));
        break;
      case FLOAT:
        target.putFloat(time, src.getFloat(srcIdx));
        break;
      case DOUBLE:
        target.putDouble(time, src.getDouble(srcIdx));
        break;
      case TEXT:
        target.putBinary(time, src.getBinary(srcIdx));
        break;
    }
  }

  public static void putAnyValue(TVList src, int srcIdx, TVList target, long time) {
    assert src.getDataType() == target.getDataType() && srcIdx < src.size();
    switch (src.getDataType()) {
      case BOOLEAN:
        target.putBoolean(time, src.getBoolean(srcIdx));
        break;
      case INT32:
        target.putInt(time, src.getInt(srcIdx));
        break;
      case INT64:
        target.putLong(time, src.getLong(srcIdx));
        break;
      case FLOAT:
        target.putFloat(time, src.getFloat(srcIdx));
        break;
      case DOUBLE:
        target.putDouble(time, src.getDouble(srcIdx));
        break;
      case TEXT:
        target.putBinary(time, src.getBinary(srcIdx));
        break;
    }
  }
}
