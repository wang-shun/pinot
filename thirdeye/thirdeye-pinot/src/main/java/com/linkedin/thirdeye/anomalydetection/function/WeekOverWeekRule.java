package com.linkedin.thirdeye.anomalydetection.function;

import com.linkedin.thirdeye.anomalydetection.model.data.SeasonalDataModel;
import com.linkedin.thirdeye.anomalydetection.model.detection.SimpleThresholdDetectionModel;
import com.linkedin.thirdeye.anomalydetection.model.merge.SimplePercentageMergeModel;
import com.linkedin.thirdeye.anomalydetection.model.prediction.SeasonalAveragePredictionModel;
import com.linkedin.thirdeye.anomalydetection.model.transform.MovingAverageSmoothingFunction;
import com.linkedin.thirdeye.anomalydetection.model.transform.TransformationFunction;
import com.linkedin.thirdeye.datalayer.dto.AnomalyFunctionDTO;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;

public class WeekOverWeekRule extends AbstractModularizedAnomalyFunction {
  public static final String BASELINE = "baseline";
  public static final String ENABLE_SMOOTHING = "enableSmoothing";

  @Override
  public void init(AnomalyFunctionDTO spec) throws Exception {
    super.init(spec);
    this.init(this.properties);
  }

  public void init(Properties properties) {
    this.properties = properties;

    String baselineProp = this.properties.getProperty(BASELINE);
    if (StringUtils.isNotBlank(baselineProp)) {
      this.initPropertiesForDataModel(baselineProp);
    }
    dataModel = new SeasonalDataModel();
    dataModel.init(this.properties);

    if (this.properties.containsKey(ENABLE_SMOOTHING)) {
      TransformationFunction movingAverageSoothingFunction = new MovingAverageSmoothingFunction();
      movingAverageSoothingFunction.init(this.properties);
      currentTimeSeriesTransformationChain.add(movingAverageSoothingFunction);
      baselineTimeSeriesTransformationChain.add(movingAverageSoothingFunction);
    }

    predictionModel = new SeasonalAveragePredictionModel();
    predictionModel.init(this.properties);

    detectionModel = new SimpleThresholdDetectionModel();
    detectionModel.init(this.properties);

    mergeModel = new SimplePercentageMergeModel();
    mergeModel.init(this.properties);
  }

  /**
   * Parses the human readable string of baseline property and sets up SEASONAL_PERIOD and
   * SEASONAL_SIZE.
   *
   * The string should be given in this regex format: [wW][/o][0-9]?[wW]. For example, this string
   * "Wo2W" means comparing the current week with the 2 week prior.
   *
   * If the string ends with "Avg", then the property becomes week-over-weeks-average. For instance,
   * "W/4wAvg" means comparing the current week with the average of the past 4 weeks.
   *
   * @param baselineProp The human readable string of baseline property.
   */
  private void initPropertiesForDataModel(String baselineProp) {
    // The basic settings for w/w
    this.properties.setProperty(SeasonalDataModel.SEASONAL_PERIOD, "1");
    this.properties.setProperty(SeasonalDataModel.SEASONAL_SIZE, "7");
    this.properties.setProperty(SeasonalDataModel.SEASONAL_UNIT, "DAYS");
    // Change the setting for different w/w types
    if (StringUtils.isBlank(baselineProp)) {
      return;
    }
    String intString = parseWowString(baselineProp);
    if (baselineProp.endsWith("Avg")) { // Week-Over-Weeks_Average
      // example: "w/4wAvg" --> SeasonalDataModel.SEASONAL_PERIOD = "4"
      this.properties.setProperty(SeasonalDataModel.SEASONAL_PERIOD, intString);
    } else { // Week-Over-Week
      // example: "w/2w" --> SeasonalDataModel.SEASONAL_SIZE = "14"
      this.properties.setProperty(SeasonalDataModel.SEASONAL_SIZE, intString);
    }
  }

  /**
   * Returns the first integer of a string; returns 1 if no integer could be found.
   *
   * Examples:
   * 1. "w/w": returns 1
   * 2. "Wo4W": returns 4
   * 3. "W/343wABCD": returns 343
   * 4. "2abc": returns 2
   * 5. "A Random string 34 and it is 54 a long one": returns 34
   *
   * @param wowString a string.
   * @return the integer of a WoW string.
   */
  public static String parseWowString(String wowString) {
    if (StringUtils.isBlank(wowString)) {
      return "1";
    }

    char[] chars = wowString.toCharArray();
    int head = -1;
    for (int idx = 0; idx < chars.length; ++idx) {
      if ('0' <= chars[idx] && chars[idx] <= '9') {
        head = idx;
        break;
      }
    }
    if (head < 0) {
      return "1";
    }
    int tail = head + 1;
    for (; tail < chars.length; ++tail) {
      if (chars[tail] <= '0' || '9' <= chars[tail]) {
        break;
      }
    }
    return wowString.substring(head, tail);
  }
}