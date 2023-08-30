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

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

public class Main {
  private static final int NUM_THREADS = 20;
  private static final int SIZE_IN_PIXELS = 1000;
  private static final double STARTING_ZOOM = 8;
  private static final double ZOOM_IN = 2;
  private static final double ZOOM_OUT = 1 / ZOOM_IN;
  private static final int BLUE = Color.BLUE.getRGB();
  private static final int WHITE = Color.WHITE.getRGB();

  private final JFrame frame = new JFrame("Plotting");
  private final JButton backButton = new JButton("<");
  private final JButton zoomOutButton = new JButton(new String(Character.toChars(0x1f50d)) + "-");
  private final JButton zoomInButton = new JButton(new String(Character.toChars(0x1f50d)) + "+");
  private final JPanel plottingPanel = new PlottingPanel();
  private final JLabel plottingLabel = new JLabel();
  private final JButton saveFileButton = new JButton("Save image file");
  private final Deque<Plotting> plottingStack = new ArrayDeque<>();
  private RenderedImage renderedImage;
  private final List<JComponent> components = new ArrayList<>();

  Main() {
    components.add(backButton);
    components.add(zoomOutButton);
    components.add(zoomInButton);
    components.add(plottingPanel);
    components.add(saveFileButton);

    addListeners();
    show();

    new StartWorker().execute();
  }

  private void addListeners() {
    backButton.addActionListener(event -> {
      if (plottingStack.size() > 1) {
        plottingStack.removeLast();
        onPlottingChanged();
      }
    });
    zoomOutButton.addActionListener(event -> zoom(ZOOM_OUT));
    zoomInButton.addActionListener(event -> zoom(ZOOM_IN));


    saveFileButton.addActionListener(event -> {
      if (renderedImage != null) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Image files", "jpg", "jpeg", "png", "gif"));
        int state = chooser.showSaveDialog(frame);
        if (state == JFileChooser.APPROVE_OPTION) {
          File file = chooser.getSelectedFile();
          String name = file.getName();
          String formatName = "JPEG";
          if (name.endsWith(".png")) {
            formatName = "PNG";
          } else if (name.endsWith(".gif")) {
            formatName = "GIF";
          }
          try {
            ImageIO.write(renderedImage, formatName, file);
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    });
  }

  private void zoom(double zoomFactor) {
    new ZoomWorker(zoomFactor).execute();
  }

  private void colorControlPanelChanged() {
    plottingPanel.repaint(0L, 0, 0, SIZE_IN_PIXELS, SIZE_IN_PIXELS);
  }

  private static boolean equal(double a, double b, double tolerance) {
    return Math.abs(a-b) <= tolerance;
  }

  class StartWorker extends SwingWorker<Plotting, Object> {
    private final List<JComponent> disabledComponents;

    StartWorker() {
      disabledComponents = disableUI();
    }

    @Override
    public Plotting doInBackground() {
      Plotting.Function line = new Plotting.Function() {
        @Override
        public boolean evaluate(double x, double y, double tolerance) {
          // x + y = 0 --> straight line
          double x1 = -y;
          if (equal(x1, x, tolerance)) {
            return true;
          }
          double y1 = -x;
          if (equal(y1, y, tolerance)) {
            return true;
          }
          return false;
        }
      };
      Plotting.Function circle = new Plotting.Function() {
        @Override
        public boolean evaluate(double x, double y, double tolerance) {
          // x^2 + y^2 = 1600
          double x1 = Math.sqrt(1600 - y*y);
          double x2 = -x1;
          if (equal(x1, x, tolerance) || equal(x2, x, tolerance)) {
            return true;
          }
          double y1 = Math.sqrt(1600 - x*x);
          double y2 = -y1;
          if (equal(y1, y, tolerance) || equal(y2, y, tolerance)) {
            return true;
          }
          return false;
        }
      };
      Plotting.Function ellipse = new Plotting.Function() {
        @Override
        public boolean evaluate(double x, double y, double tolerance) {
          // x^2 + 4y^2 = 36
          double x1 = Math.sqrt(36 - 4*y*y);
          double x2 = -x1;
          if (equal(x1, x, tolerance) || equal(x2, x, tolerance)) {
            return true;
          }
          double y1 = Math.sqrt((36 - x*x) / 4);
          double y2 = -y1;
          if (equal(y1, y, tolerance) || equal(y2, y, tolerance)) {
            return true;
          }
          return false;
        }
      };
      Plotting.Function parabola = new Plotting.Function() {
        @Override
        public boolean evaluate(double x, double y, double tolerance) {
          // x^2 = 4y^2
          double x1 = Math.sqrt(4*y*y);
          double x2 = -x1;
          if (equal(x1, x, tolerance) || equal(x2, x, tolerance)) {
            return true;
          }
          double y1 = Math.sqrt(x*x / 4);
          double y2 = -y1;
          if (equal(y1, y, tolerance) || equal(y2, y, tolerance)) {
            return true;
          }
          return false;
        }
      };
      Plotting.Function roseOfGrandi = new Plotting.Function() {
        @Override
        public boolean evaluate(double x, double y, double tolerance) {
          // (x^2 + y^2)^3 = 4a^2x^2y^2
          double a = 10; // ???
          // TODO(lizlooney): implement this.
          return false;
        }
      };
      return new Plotting(circle, NUM_THREADS, SIZE_IN_PIXELS, STARTING_ZOOM);
    }

    @Override
    protected void done() {
      enableUI(disabledComponents);
      try {
        plottingStack.addLast(get());
        onPlottingChanged();
      } catch (ExecutionException | InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  class ZoomWorker extends SwingWorker<Plotting, Object> {
    private final double zoomFactor;
    private final Plotting plottingBeforeZoom;
    private final List<JComponent> disabledComponents;

    ZoomWorker(double zoomFactor) {
      this.zoomFactor = zoomFactor;
      plottingBeforeZoom = plottingStack.peekLast();
      disabledComponents = disableUI();
    }

    @Override
    public Plotting doInBackground() {
      return plottingBeforeZoom.zoom(zoomFactor);
    }

    @Override
    protected void done() {
      enableUI(disabledComponents);
      try {
        plottingStack.addLast(get());
        onPlottingChanged();
      } catch (ExecutionException | InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private List<JComponent> disableUI() {
    frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    List<JComponent> disabledComponents = new ArrayList<>();
    for (JComponent component : components) {
      if (component.isEnabled()) {
        component.setEnabled(false);
        disabledComponents.add(component);
      }
    }
    return disabledComponents;
  }

  private void enableUI(List<JComponent> disabledComponents) {
    for (JComponent component  : disabledComponents) {
      component.setEnabled(true);
    }
    frame.setCursor(Cursor.getDefaultCursor());
  }

  private void onPlottingChanged() {
    backButton.setEnabled(plottingStack.size() > 1);
    plottingLabel.setText(plottingStack.peekLast().toString());
    plottingPanel.repaint(0L, 0, 0, SIZE_IN_PIXELS, SIZE_IN_PIXELS);
  }

  private void show() {
    JPanel zoomingPanel = createZoomingPanel();

    GridBagLayout gridbag = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();
    frame.setLayout(gridbag);
    // Back button
    c.gridwidth = 1;
    gridbag.setConstraints(backButton, c);
    frame.add(backButton);

    // Zooming panel
    c.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(zoomingPanel, c);
    frame.add(zoomingPanel);

    // Plotting panel
    c.fill = GridBagConstraints.BOTH;
    plottingPanel.setPreferredSize(new Dimension(SIZE_IN_PIXELS, SIZE_IN_PIXELS));
    gridbag.setConstraints(plottingPanel, c);
    frame.add(plottingPanel);
    // Plotting label
    c.fill = GridBagConstraints.HORIZONTAL;
    plottingLabel.setHorizontalAlignment(SwingConstants.CENTER);
    gridbag.setConstraints(plottingLabel, c);
    frame.add(plottingLabel);
    // Save file button
    c.fill = GridBagConstraints.NONE;
    gridbag.setConstraints(saveFileButton, c);
    frame.add(saveFileButton);

    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(1020, 1250);
    frame.setVisible(true);
  }

  private JPanel createZoomingPanel() {
    JPanel zoomingPanel = new JPanel();
    zoomingPanel.setBorder(new TitledBorder("Zooming Panel"));
    GridBagLayout gridbag = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();
    zoomingPanel.setLayout(gridbag);
    c.gridwidth = 1;
    gridbag.setConstraints(zoomOutButton, c);
    zoomingPanel.add(zoomOutButton);
    gridbag.setConstraints(zoomInButton, c);
    zoomingPanel.add(zoomInButton);

    return zoomingPanel;
  }

  class PlottingPanel extends JPanel {
    @Override
    public void paint(Graphics g) {
      super.paint(g);
      if (plottingStack.size() > 0) {
        paint((Graphics2D) g, plottingStack.peekLast());
      }
    }

    public void paint(Graphics2D g2d, Plotting plotting) {
      g2d.setPaint(Color.WHITE);
      g2d.fillRect(0, 0, SIZE_IN_PIXELS, SIZE_IN_PIXELS);

      g2d.setPaint(Color.GRAY);
      // x axis
      g2d.drawLine(SIZE_IN_PIXELS / 2, 0, SIZE_IN_PIXELS / 2, SIZE_IN_PIXELS);
      // y axis
      g2d.drawLine(0, SIZE_IN_PIXELS / 2, SIZE_IN_PIXELS, SIZE_IN_PIXELS / 2);

      g2d.setPaint(Color.BLUE);

      plotting.accept((x, y, value) -> {
        if (value) {
          g2d.fillRect(x, SIZE_IN_PIXELS - 1 - y, 1, 1);
        }
      });
    }

    public RenderedImage produceImage(Plotting plotting) {
      final BufferedImage bi = new BufferedImage(SIZE_IN_PIXELS, SIZE_IN_PIXELS, BufferedImage.TYPE_INT_RGB);
      paint(bi.createGraphics(), plotting);
      return bi;
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        new Main();
      }
    });
  }
}
