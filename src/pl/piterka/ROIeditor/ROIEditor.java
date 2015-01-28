/*
 The MIT License (MIT)

 Copyright (c) 2015 Piotr Kawęcki

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */
package pl.piterka.ROIeditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

import javax.swing.*;

public class ROIEditor extends JPanel implements MouseListener, ActionListener {

    //ROI data [ROIName - List of points]
    private final HashMap<String, List<PointData>> roiPoints;

    //current image
    private BufferedImage currImg;
    private File currImgFile;

    //selected ROI
    private String currROIName;

    //ui elements
    private final JTextField text;
    private final JLabel wIcon;
    private final JLabel dsc;

    //images list
    private final List<File> images;

    //main function
    //Init UI
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        } catch (UnsupportedLookAndFeelException | IllegalAccessException | InstantiationException | ClassNotFoundException ex) {
            Logger.getLogger(ROIEditor.class.getName()).log(Level.SEVERE, null, ex);
        }

        UIManager.put("swing.boldMetal", Boolean.FALSE);

        //Schedule a job for the event dispatch thread:
        //creating and showing this application's UI.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                //Create and set up the window.
                JFrame frame = new JFrame("TabliceAGH by piterka");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                //Create and set up the content pane.
                JComponent newContentPane = new ROIEditor();
                newContentPane.setOpaque(true); //content panes must be opaque
                frame.setContentPane(newContentPane);

                //Display the window.
                frame.pack();
                frame.setVisible(true);
            }
        });
    }

    private void setCurrROI(String roi) {
        currROIName = roi;
        if (!roiPoints.containsKey(roi)) {
            List<PointData> pointList = new ArrayList<>();
            roiPoints.put(roi, pointList);
        }
    }

    //constructor
    public ROIEditor() {
        //init layout
        super(new BorderLayout());

        //init roiPoints
        this.roiPoints = new HashMap<>();

        //set default ROI
        setCurrROI("DEFAULT");

        //load avaible images
        images = new ArrayList<>();
        loadImages();

        //create image container
        wIcon = new JLabel() {
            @Override
            public void paintComponent(Graphics g) {
                super.paintComponent(g);

                //draw all points and lines
                for (String obj : roiPoints.keySet()) {
                    PointData first = null;
                    PointData last = null;

                    if (currROIName.equals(obj)) {
                        g.setColor(Color.red);
                    } else {
                        g.setColor(Color.gray);
                    }

                    for (PointData p : roiPoints.get(obj)) {
                        if (last != null) {
                            g.drawLine(last.getX(), last.getY(), p.getX(), p.getY());
                        }
                        if (first == null) {
                            JLabel dsc = new JLabel(obj);
                            dsc.setLocation(new Point(p.getX() + 15, p.getY() + 15));
                            first = p;
                        }
                        g.fillOval(p.getX() - 5, p.getY() - 5, 10, 10);
                        last = p;
                    }

                    if (last != null && first != null) {
                        g.drawLine(last.getX(), last.getY(), first.getX(), first.getY());
                    }
                }

            }
        };
        //add mouse listener to image
        wIcon.addMouseListener(ROIEditor.this);

        //initialize scrollpane for image
        JScrollPane scrollPane = new JScrollPane(wIcon);

        //navigation panel
        JPanel nav = new JPanel(new FlowLayout());

        //add image desc to layout
        dsc = new JLabel();
        dsc.setForeground(Color.RED);
        dsc.setBounds(40, 30, 200, 30);
        nav.add(dsc);

        //previous image button
        JButton prev = new JButton("Prev");
        prev.addActionListener(ROIEditor.this);
        prev.setActionCommand("IMG_PREV");
        nav.add(prev);

        //next image button
        JButton next = new JButton("Next");
        next.addActionListener(ROIEditor.this);
        next.setActionCommand("IMG_NEXT");
        nav.add(next);

        //input ROI
        text = new JTextField("DEFAULT");
        text.setMinimumSize(new Dimension(200, 50));
        nav.add(text);

        //set ROI button
        JButton set = new JButton("SET");
        set.addActionListener(ROIEditor.this);
        set.setActionCommand("SET_TEXT");
        nav.add(set);

        //add navigation to layout
        add(nav, BorderLayout.PAGE_END);

        //load first image if it exists
        loadImage(images.size() > 0 ? images.get(0) : null);

        //add scrollpane to layout
        add(scrollPane, BorderLayout.CENTER);

        //init window size
        setPreferredSize(new Dimension(800, 450));
        //border
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    saveCurrROI();
                } catch (IOException ex) {
                    Logger.getLogger(ROIEditor.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    //Function selects previous image from list
    private void prevImage() {
        int curId = currImgFile != null ? images.indexOf(currImgFile) : 0;
        if (curId - 1 >= 0) {
            loadImage(images.get(curId - 1));
            return;
        }
        if (!images.isEmpty()) {
            loadImage(images.get(images.size() - 1));
        }
    }

    //Function selects next image from list
    private void nextImage() {
        int curId = currImgFile != null ? images.indexOf(currImgFile) : 0;
        if (curId + 1 < images.size()) {
            loadImage(images.get(curId + 1));
            return;
        }
        if (!images.isEmpty()) {
            loadImage(images.get(0));
        }
    }

    /*
     Load all images from all children dirs (recursive)
     */
    private void loadImages() {
        loadImages(new File("."));
    }

    private void loadImages(File dir) {
        if (dir.listFiles() == null) {
            return;
        }
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                loadImages(f);
            }
            if (f.getName().endsWith(".jpg")) {
                images.add(f);
            }
        }
    }

    //refresh UI
    private void refreshLook() {
        wIcon.removeAll();
        if (currImg == null || currImgFile == null) {
            wIcon.setText("Brak zdjec! :[");
        } else {
            //show desc
            for (String obj : roiPoints.keySet()) {
                for (PointData p : roiPoints.get(obj)) {
                    JLabel roiDsc = new JLabel(obj);
                    if (currROIName.equals(obj)) {
                        roiDsc.setForeground(Color.red);
                    } else {
                        roiDsc.setForeground(Color.gray);
                    }
                    roiDsc.setBounds(p.getX() + 20, p.getY() - 30, 150, 30);
                    wIcon.add(roiDsc);
                    break;
                }
            }
            wIcon.setIcon(new ImageIcon(currImg));
        }

        wIcon.updateUI();
    }

    //save current ROI data
    private void saveCurrROI() throws IOException {
        if (currImgFile != null) {
            File data = new File(currImgFile.getParentFile().getAbsolutePath() + File.separator + currImgFile.getName().split("\\.")[0] + "_MetaData.txt");
            if (data.exists()) {
                data.delete();
            }
            data.createNewFile();
            int a = 0;
            try {
                BufferedWriter out = new BufferedWriter(new FileWriter(data));
                for (String obj : roiPoints.keySet()) {
                    String x = "";
                    for (PointData pd : roiPoints.get(obj)) {
                        if (x.length() > 0) {
                            x += " ";
                        }
                        x += pd.getX() + " " + pd.getY();
                    }
                    if (x.length() > 0) {
                        out.write(obj.toUpperCase() + ":\t" + x + "\n");
                        a++;
                    }
                }
                out.close();
            } catch (IOException e) {
            }
            if (a == 0) { //remove file if no data
                data.delete();
            } else {
                System.out.println("Save image data: " + currImgFile.getName() + " in: " + data.getName());
            }
        }

    }

    private void loadImage(File f) {
        try {
            //save previous image metadata
            saveCurrROI();

            //clear roiPoints for next image
            roiPoints.clear();

            //image is null?
            if (f == null) {
                currImgFile = null;
                currImg = null;
                dsc.setText("---");
                refreshLook();
                return;
            }

            System.out.println("Load image: " + f.getName());
            currImgFile = f;
            currImg = ImageIO.read(currImgFile);
            dsc.setText(currImgFile.getName());

            //load metadata if exist
            File data = new File(f.getParentFile().getAbsolutePath() + File.separator + f.getName().split("\\.")[0] + "_MetaData.txt");
            if (data.exists()) {
                try {
                    BufferedReader out = new BufferedReader(new FileReader(data));
                    String ln;
                    while ((ln = out.readLine()) != null) {
                        ln = ln.trim();
                        String[] spl1 = ln.split("\t");
                        String obj = spl1[0].substring(0, spl1[0].length() - 1);
                        setCurrROI(obj);
                        String[] spl2 = spl1[1].trim().split(" ");
                        for (int i = 0; i < spl2.length / 2; i++) {
                            PointData pd = new PointData(Integer.parseInt(spl2[i * 2]), Integer.parseInt(spl2[i * 2 + 1]));
                            roiPoints.get(obj).add(pd);
                        }
                    }
                    out.close();
                } catch (IOException e) {
                }
            }

            //update ROI (and make sure that roiPoints initialized)
            if (!text.getText().equalsIgnoreCase("DEFAULT")) {
                setCurrROI(text.getText().toUpperCase());
                text.setText(currROIName);
            } else {
                setCurrROI(currROIName.toUpperCase());
                text.setText(currROIName);
            }

            //refresh UI
            refreshLook();
        } catch (Exception ex) {//catch all exceptions
            currImgFile = null;
            currImg = null;
            dsc.setText("ERROR: " + ex.getMessage());
            refreshLook();
            Logger.getLogger(ROIEditor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //mouse clicked event - add/remove points from ROI
    @Override
    public void mouseClicked(MouseEvent e) {
        if (currImgFile == null || currImg == null) {
            return;
        }

        if (e.getButton() == 1) {
            boolean found = false;
            for (PointData p : roiPoints.get(currROIName)) {
                if (Math.sqrt(Math.pow(p.getX() - e.getPoint().getX(), 2.0) + Math.pow(p.getY() - e.getPoint().getY(), 2.0)) <= 10) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                roiPoints.get(currROIName).add(new PointData(e.getPoint().getX(), e.getPoint().getY()));
                refreshLook();
            }
        } else {
            PointData toDel = null;
            for (PointData p : roiPoints.get(currROIName)) {
                if (Math.sqrt(Math.pow(p.getX() - e.getPoint().getX(), 2.0) + Math.pow(p.getY() - e.getPoint().getY(), 2.0)) <= 5) {
                    toDel = p;
                    break;
                }
            }
            if (toDel != null) {
                roiPoints.get(currROIName).remove(toDel);
                refreshLook();
            }
        }
    }

    //not used
    @Override
    public void mousePressed(MouseEvent e) {
    }

    //not used
    @Override
    public void mouseReleased(MouseEvent e) {
    }

    //not used
    @Override
    public void mouseEntered(MouseEvent e) {
    }

    //not used
    @Override
    public void mouseExited(MouseEvent e) {
    }

    //button actions listener
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equalsIgnoreCase("IMG_NEXT")) {
            nextImage();
        }
        if (e.getActionCommand().equalsIgnoreCase("IMG_PREV")) {
            prevImage();
        }
        if (e.getActionCommand().equalsIgnoreCase("SET_TEXT")) {
            setCurrROI(text.getText().toUpperCase());
            text.setText(currROIName);
            refreshLook();
        }
    }

}
