package org.apache.iotdb.db.index.algorithm;

import static org.apache.iotdb.db.index.common.IndexConstant.DEFAULT_PROP_NAME;
import static org.apache.iotdb.db.index.common.IndexConstant.INDEX_RANGE_STRATEGY;
import static org.apache.iotdb.db.index.common.IndexConstant.INDEX_SLIDE_STEP;
import static org.apache.iotdb.db.index.common.IndexConstant.INDEX_WINDOW_RANGE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.db.index.common.IndexFunc;
import org.apache.iotdb.db.index.common.IndexInfo;
import org.apache.iotdb.db.index.common.IndexManagerException;
import org.apache.iotdb.db.index.common.IndexQueryException;
import org.apache.iotdb.db.index.common.IndexRuntimeException;
import org.apache.iotdb.db.index.common.IndexType;
import org.apache.iotdb.db.index.common.UnsupportedIndexQueryException;
import org.apache.iotdb.db.index.indexrange.IndexRangeStrategy;
import org.apache.iotdb.db.index.indexrange.IndexRangeStrategyType;
import org.apache.iotdb.db.index.io.IndexIOWriter.IndexFlushChunk;
import org.apache.iotdb.db.index.preprocess.Identifier;
import org.apache.iotdb.db.index.preprocess.IndexPreprocessor;
import org.apache.iotdb.db.index.read.func.IndexFuncResult;
import org.apache.iotdb.db.metadata.MManager;
import org.apache.iotdb.db.utils.datastructure.TVList;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.BatchData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Each StorageGroupProcessor contains a IndexProcessor, and each IndexProcessor can contain more
 * than one IIndex. Each type of Index corresponds one IIndex.
 */
public abstract class IoTDBIndex {

  private static final Logger logger = LoggerFactory.getLogger(IoTDBIndex.class);
  protected final String path;
  protected final IndexType indexType;
  protected final long confIndexStartTime;
  protected final Map<String, String> props;
  protected final TSDataType tsDataType;
  private IndexRangeStrategy strategy;
  protected int windowRange;
  protected int slideStep;
  protected IndexPreprocessor indexPreprocessor;

  public IoTDBIndex(String path, IndexInfo indexInfo) {
    try {
      this.tsDataType = MManager.getInstance().getSeriesType(path);
    } catch (MetadataException e) {
      throw new IndexRuntimeException("get type failed. ", e);
    }
    this.path = path;
    this.indexType = indexInfo.getIndexType();
    this.confIndexStartTime = indexInfo.getTime();
    this.props = indexInfo.getProps();
    parsePropsAndInit(this.props);
  }

  private void parsePropsAndInit(Map<String, String> props) {
    // Strategy
    this.strategy = IndexRangeStrategyType
        .getIndexStrategy(props.getOrDefault(INDEX_RANGE_STRATEGY, DEFAULT_PROP_NAME));
    //WindowRange
    this.windowRange =
        props.containsKey(INDEX_WINDOW_RANGE) ? Integer.parseInt(props.get(INDEX_WINDOW_RANGE))
            : IoTDBDescriptor.getInstance().getConfig().getDefaultIndexWindowRange();
    // SlideRange
    this.slideStep = props.containsKey(INDEX_SLIDE_STEP) ?
        Integer.parseInt(props.get(INDEX_SLIDE_STEP)) : this.windowRange;
  }

  /**
   * Each index has its own preprocessor. Through the preprocessor provided by this index,
   * {@linkplain org.apache.iotdb.db.index.IndexFileProcessor IndexFileProcessor} can control the
   * its data process, memory occupation and triggers forceFlush.
   *
   * @param tvList tvList in current FlushTask.
   * @return Preprocessor with new data.
   */
  public IndexPreprocessor startFlushTask(TVList tvList) {
    this.indexPreprocessor.appendNewSrcData(tvList);
    return indexPreprocessor;
  }


  /**
   * Sorry but this method is ugly.
   */
  public IndexPreprocessor startFlushTask(BatchData batchData) {
    this.indexPreprocessor.appendNewSrcData(batchData);
    return indexPreprocessor;
  }

  /**
   * An index should determine which preprocessor it uses and hook it to {@linkplain
   * IoTDBIndex}.indexProcessor. An index should determine which preprocessor it uses and connect to
   * the IndexProcessor.
   *
   * This method is called when IoTDBIndex is created and accept the previous overlapped data in
   * type of {@code ByteBuffer}.
   *
   * Note that, the implementation should call {@code deserializePrevious(ByteBuffer byteBuffer)}
   * after initialize the preprocessor.
   */
  public abstract void initPreprocessor(ByteBuffer previous);

  /**
   * Given a tvList with {@code tvListStartIdx}, we want to know whether to build index for tvList
   * from {@code tvListStartIdx} to the end，regardless of whether it will be truncated by {@code
   * forceFlush}.
   */
  public boolean checkNeedIndex(TVList sortedTVList, int offset) {
    return strategy.needBuildIndex(sortedTVList, offset, confIndexStartTime);
  }


  /**
   * When this function is called, it means that a new point has been pre-processed.  The index can
   * organize the newcomer in real time, or delay to build it until {@linkplain #flush}
   */
  public abstract boolean buildNext() throws IndexManagerException;

  /**
   * @return the byte-like chunk data and its description information
   */
  public abstract IndexFlushChunk flush() throws IndexManagerException;

  /**
   * clear and release the occupied memory. The preprocessor has been cleared in IoTDBIndex, so
   * remember invoke {@code super.clear()} and then add yourself.
   *
   * This method is called when completing a sub-flush.  Note that one flush task will trigger
   * multiple sub-brush tasks due to the memory control.
   *
   * @return how much memory was freed.
   */
  public long clear() {
    return indexPreprocessor == null ? 0 : indexPreprocessor.clear();
  }

  /**
   * Not that, this method will remove all data and feature. If this method is called, all other
   * methods will be invalid.
   */
  public void closeAndRelease() {
    clear();
    indexPreprocessor.closeAndRelease();
  }

  /**
   * This method is called when a flush task totally finished.
   */
  public void endFlushTask() {
    indexPreprocessor.clearProcessedSrcData();
  }

  /**
   * This method serialize information of index and preprocessor into an {@code OutputStream}. It's
   * called when the index file will be cloase. The information will be back in type of {@code
   * ByteBuffer} when next creation.
   */
  public ByteBuffer serialize() throws IOException {
    return indexPreprocessor.serializePrevious();
  }

  /**
   * return how much memory is increased for each point processed. It's an amortized estimation,
   * which should consider both {@linkplain IndexPreprocessor#getAmortizedSize()} and the <b>index
   * expansion rate</b>.
   */
  public int getAmortizedSize() {
    return indexPreprocessor == null ? 0 : indexPreprocessor.getAmortizedSize();
  }

  /**
   * Initial parameters by query, check if all query conditions and function types are supported
   *
   * @param queryConditions query conditions
   * @throws org.apache.iotdb.db.index.common.IllegalIndexParamException when conditions or funcs
   * are not supported
   */
  public abstract void initQuery(Map<String, String> queryConditions,
      List<IndexFuncResult> indexFuncResults) throws UnsupportedIndexQueryException;


  /**
   * query on path with parameters, return the candidate list. return null is regarded as Nothing to
   * be pruned.
   *
   * TODO It's not gentle enough. null should have close definition as empty list.
   *
   * @return null means nothing to be pruned
   */
  public abstract List<Identifier> queryByIndex(ByteBuffer indexChunkData)
      throws IndexManagerException;


  /**
   * IndexPreprocessor has preprocess a new sequence, produce L1, L2 or L3 features as user's
   * configuration. Calculates functions you support and fill into {@code funcResult}.
   *
   * Returns {@code true} if all calculations of this function have been completed. If so, this
   * AggregateResult will not be called next time.
   *
   * @throws UnsupportedOperationException If you meet an unsupported AggregateResult
   */
  public abstract int postProcessNext(List<IndexFuncResult> indexFuncResults)
      throws IndexQueryException;

  /**
   * the file is no more needed. Stop ongoing construction and flush operations, clear memory
   * directly and delete the index file.
   */
  public abstract void delete();


  public IndexType getIndexType() {
    return indexType;
  }

}