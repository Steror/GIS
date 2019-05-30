package app.part3;

import app.buffer.LeanBuffer;
import app.intersect.Intersector;
import jdk.nashorn.internal.runtime.regexp.joni.Regex;
import lombok.Getter;
import lombok.Setter;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.filter.text.cql2.CQLException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConfigWindow extends JFrame {

    @Getter
    JFrame frame;

    @Getter
    Double trackLength = 100.0, trackWidth = 100.0, distance1 = 100.0, distance2 = 1000.0, averageSlope = 100.0;

    @Getter
    @Setter
    SimpleFeatureSource roadSFS, riverSFS, areaSFS;// heightSFS

    @Getter
    @Setter
    SimpleFeatureCollection roadSFC, riverSFC, areaSFC;// heightSFC
    @Getter
    SimpleFeatureCollection suitableArea, unsuitableArea;

    private String pre1 = "", pre2 = "A";

    public ConfigWindow() {
        frame = new JFrame();
        frame.setTitle("Configure operation");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.PAGE_AXIS));
        JLabel lengthLabel = new JLabel("L1: Track Length");
        JTextField trackLengthJTF = new JTextField("100");
        JLabel widthLabel = new JLabel("L2: Track Width");
        JTextField trackWidthJTF = new JTextField("100");
        JLabel distance1Label = new JLabel("D1: Distance from roads and rivers");
        JTextField distance1JTF = new JTextField("100");
        JLabel distance2Label = new JLabel("D2: Distance from bodies of water, community gardens and urbanised areas");
        JTextField distance2JTF = new JTextField("100");
        JLabel slopeLabel = new JLabel("A1: Average track slope");
        JTextField averageSlopeJTF = new JTextField("100");
        frame.getContentPane().add(lengthLabel);
        frame.getContentPane().add(trackLengthJTF);

        frame.getContentPane().add(widthLabel);
        frame.getContentPane().add(trackWidthJTF);

        frame.getContentPane().add(distance1Label);
        frame.getContentPane().add(distance1JTF);

        frame.getContentPane().add(distance2Label);
        frame.getContentPane().add(distance2JTF);

        frame.getContentPane().add(slopeLabel);
        frame.getContentPane().add(averageSlopeJTF);


        JButton okButton = new JButton("OK");
        okButton.addActionListener(
                e -> {
                    frame.setVisible(false);
                    trackLength = Double.parseDouble(trackLengthJTF.getText());
                    trackWidth = Double.parseDouble(trackWidthJTF.getText());
                    distance1 = Double.parseDouble(distance1JTF.getText());
                    distance2 = Double.parseDouble(distance2JTF.getText());
                    averageSlope = Double.parseDouble(averageSlopeJTF.getText());
                });
        frame.getContentPane().add(okButton);

        frame.pack();
    }

    public void findSuitableArea (SimpleFeatureCollection sfc) {
        List<SimpleFeature> features = Collections.synchronizedList(new ArrayList<>());
        SimpleFeatureType sft = sfc.getSchema();

        try (SimpleFeatureIterator iter = sfc.features()) {
            while (iter.hasNext()) {
                SimpleFeature feature = iter.next();
                String string = (String) feature.getAttribute("GKODAS");
                if (!(string.matches("hd.*") || string.matches("ms4") || string.matches("pu0"))) {
                    features.add(feature);
                }
            }
        }
        suitableArea = new ListFeatureCollection(sft, features);
    }

    public void findUnsuitableArea (SimpleFeatureCollection sfc) {
        List<SimpleFeature> features = Collections.synchronizedList(new ArrayList<>());
        SimpleFeatureType sft = sfc.getSchema();

        try (SimpleFeatureIterator iter = sfc.features()) {
            while (iter.hasNext()) {
                SimpleFeature feature = iter.next();
                String string = (String) feature.getAttribute("GKODAS");
                if (string.matches("hd.*") || string.matches("ms4") || string.matches("pu0")) {
                    features.add(feature);
                }
            }
        }
        unsuitableArea = new ListFeatureCollection(sft, features);
    }

    public void removeBufferedArea (SimpleFeatureCollection sfc, Double distance) throws CQLException, IOException {
        LeanBuffer leanBuffer = new LeanBuffer("BUF"+sfc.getSchema().getTypeName(), sfc, distance);
        leanBuffer.buffer();
        SimpleFeatureCollection buffered = leanBuffer.getBuffered();
        Intersector intersector = new Intersector(this.suitableArea, buffered);
        intersector.recalculateLength("Shape_Leng");
        intersector.recalculateArea("Shape_Area");
        intersector.setPrefixes(pre1, pre2);
        intersector.intersect();
        SimpleFeatureCollection intersected = intersector.getIntersected();
        suitableArea = intersected;

        findSuitableArea(intersected);
    }
}
