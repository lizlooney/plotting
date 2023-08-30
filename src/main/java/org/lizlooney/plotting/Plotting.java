/*
 * Copyright 2023 Liz Looney
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lizlooney.plotting;

public class Plotting {
  private final Function plottingFunction;
  private final int numThreads;
  private final int sizeInPixels;
  private final double size;
  private final double pixelsPerUnit;
  private final double xOffset;
  private final double yOffset;
  private final Object valuesLock = new Object();
  private final boolean[] values;

  public Plotting(Function plottingFunction, int numThreads, int sizeInPixels, double pixelsPerUnit) {
    this.plottingFunction = plottingFunction;
    this.numThreads = numThreads;
    this.sizeInPixels = sizeInPixels;
    this.pixelsPerUnit = pixelsPerUnit;
    size = convertPixelsToUnits(sizeInPixels);
    xOffset = 0 - size / 2;
    yOffset = 0 - size / 2;
    values = new boolean[sizeInPixels * sizeInPixels];

    calculatePixelValues();
  }

  public Plotting zoom(double zoomFactor) {
    return new Plotting(plottingFunction, numThreads, sizeInPixels, zoomFactor * pixelsPerUnit);
  }

  private void calculatePixelValues() {
    Thread[] threads = new Thread[numThreads];
    for (int i = 0; i < numThreads; i++) {
      final int threadNumber = i;
      threads[i] = new Thread(() -> calculateValuesForThread(threadNumber));
      threads[i].start();
    }

    for (int i = 0; i < numThreads; i++) {
      try {
        threads[i].join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private double convertPixelsToUnits(double pixels) {
    return pixels / pixelsPerUnit;
  }

  private void calculateValuesForThread(int threadNumber) {
    double halfPixel = convertPixelsToUnits(0.5);

    int i = 0;
    for (int yInPixels = 0; yInPixels < sizeInPixels; yInPixels++) {
      for (int xInPixels = 0; xInPixels < sizeInPixels; xInPixels++) {
        if (numThreads == 1 || i % numThreads == threadNumber) {
          double xCenter = xOffset + convertPixelsToUnits(xInPixels);
          double yCenter = yOffset + convertPixelsToUnits(yInPixels);

          if (plottingFunction.evaluate(xCenter, yCenter, halfPixel)) {
            synchronized (valuesLock) {
              values[i] = true;
            }
          }
        }
        i++;
      }
    }
  }

  public interface Visitor {
    void visit(int x, int y, boolean value);
  }

  public void accept(Visitor visitor) {
    synchronized (valuesLock) {
      int i = 0;
      for (int y = 0; y < sizeInPixels; y++) {
        for (int x = 0; x < sizeInPixels; x++) {
          visitor.visit(x, y, values[i]);
          i++;
        }
      }
    }
  }

  public interface Function {
    boolean evaluate(double x, double y, double tolerance);
  }
}
