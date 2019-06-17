package app.part3;

import app.buffer.LeanBuffer;
import app.intersect.Intersector;
import lombok.Getter;
import lombok.Setter;
import org.geotools.data.*;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.util.URLs;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

public class ConfigWindow extends JFrame {

    @Getter
    JFrame frame;

    @Getter
    Double trackLength = 100.0, trackWidth = 100.0, distance1 = 50.0, distance2 = 50.0, averageSlope = 0.15;

    @Getter
    @Setter
    SimpleFeatureSource roadSFS, riverSFS, areaSFS, slopeSFS, peakSFS, suitableAreaSFS, intersectedSFS;

    @Getter
    @Setter
    SimpleFeatureCollection roadSFC, riverSFC, areaSFC, slopeSFC, peakSFC;
    @Getter
    @Setter
    SimpleFeatureCollection suitableArea, suitableArea2, unsuitableArea, intersected;

    private String pre1 = "A", pre2 = "B";

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
        JTextField distance1JTF = new JTextField("50");
        JLabel distance2Label = new JLabel("D2: Distance from bodies of water, community gardens and urbanised areas");
        JTextField distance2JTF = new JTextField("50");
        JLabel slopeLabel = new JLabel("A1: Average track slope");
        JTextField averageSlopeJTF = new JTextField("0.15");
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
        try {
            suitableAreaSFS = saveAndLoad(suitableArea);
            suitableArea = suitableAreaSFS.getFeatures();
        } catch (IOException e) {
            e.printStackTrace();
        }

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

    public void removeBufferedArea (SimpleFeatureCollection sfc, Double distance) throws IOException {
        LeanBuffer leanBuffer = new LeanBuffer("BUF"+sfc.getSchema().getTypeName(), sfc, distance);
        leanBuffer.buffer();
        SimpleFeatureCollection buffered = leanBuffer.getBuffered();
        saveAndLoad(buffered);
        Intersector intersector = new Intersector(suitableArea, buffered);
        intersector.recalculateLength("Shape_Leng");
        intersector.recalculateArea("Shape_Area");
        intersector.setPrefixes(pre1, pre2);
        intersector.intersect();
        SimpleFeatureCollection intersected = intersector.getIntersected();

        List<SimpleFeature> features = Collections.synchronizedList(new ArrayList<>());
        SimpleFeatureType sft = suitableArea.getSchema();
        long milis = System.currentTimeMillis();

        try (SimpleFeatureIterator iter = suitableArea.features()) {
            while (iter.hasNext()) {
                SimpleFeature feature = iter.next();
                long goodId = (long) feature.getAttribute("OBJECTID");
                boolean isFound = false;
                second: try (SimpleFeatureIterator iter2 = intersected.features()) {
                    while (iter2.hasNext()) {
                        SimpleFeature feature2 = iter2.next();
                        long badId = (long) feature2.getAttribute(pre1+"OBJECTID");
                        if (badId == goodId) {
                            isFound = true;
                            break second;
                        }
                    }
                }
                if (!isFound)
                    features.add(feature);
            }
        }
        System.out.println("INFO: remove buffered  " + sfc.getSchema().getTypeName() + "   layer");
        System.out.println("INFO: remove buffered: " + (System.currentTimeMillis()-milis)/1000d + "s");
        suitableArea = new ListFeatureCollection(sft, features);
        //suitableAreaSFS = saveAndLoad(suitableArea);
    }

    public void removeSlopeArea (SimpleFeatureCollection sfc, Double averageSlope) throws IOException {
        Intersector intersector = new Intersector(suitableArea, sfc);
        intersector.recalculateLength("Shape_Leng");
        intersector.recalculateArea("Shape_Area");
        intersector.setPrefixes(pre1, pre2);
        intersector.intersect();
        SimpleFeatureCollection intersected = intersector.getIntersected();

        List<SimpleFeature> features = Collections.synchronizedList(new ArrayList<>());
        SimpleFeatureType sft = intersected.getSchema();
        long milis = System.currentTimeMillis();

        try (SimpleFeatureIterator iter = intersected.features()) {
            while (iter.hasNext()) {
                SimpleFeature feature = iter.next();
                double slope = (double) feature.getAttribute(pre2+"Slope");
                if (slope >= averageSlope)
                    features.add(feature);
            }
        }
        System.out.println("INFO: remove smaller slopes: " + (System.currentTimeMillis()-milis)/1000d + "s");
        suitableArea = new ListFeatureCollection(sft, features);
        //suitableAreaSFS = saveAndLoad(suitableArea);
    }

    public void findPeaks (SimpleFeatureCollection peaks) throws IOException {
        Intersector intersector = new Intersector(suitableArea, peaks);
        intersector.recalculateLength("Shape_Leng");
        intersector.recalculateArea("Shape_Area");
        intersector.setPrefixes(pre1, pre2);
        intersector.intersect();
        SimpleFeatureCollection intersected = intersector.getIntersected();

        System.out.println("INFO: found peaks inside area: " + intersected.size());
        saveAndLoad(intersected);
    }

    public SimpleFeatureSource saveAndLoad(SimpleFeatureCollection sfc) throws IOException {

        SimpleFeatureType ft = sfc.getSchema();
        String typeName = ft.getTypeName();

        String fileName = ft.getTypeName();
        File file = new File(FileSystemView.getFileSystemView().getDefaultDirectory().getPath()+"\\A Part 3"+"\\1 Rezultatai",fileName+".shp");

        Map<String, Serializable> creationParams = new HashMap<>();
        creationParams.put("url", URLs.fileToUrl(file));

        FileDataStoreFactorySpi factory = FileDataStoreFinder.getDataStoreFactory("shp");
        DataStore dataStore = factory.createNewDataStore(creationParams);

        dataStore.createSchema(ft);

        SimpleFeatureStore featureStore = (SimpleFeatureStore) dataStore.getFeatureSource(typeName);

        Transaction t = new DefaultTransaction();
        try {
            featureStore.addFeatures(sfc);  // grab all features
            t.commit(); // write it out
        } catch (IOException eek) {
            eek.printStackTrace();
            try {
                t.rollback();
            } catch (IOException doubleEeek) {
                // rollback failed?
            }
        } finally {
            t.close();
        }
        System.out.println("INFO: Finished export " + LocalDateTime.now());

        return dataStore.getFeatureSource(typeName);
    }
}
