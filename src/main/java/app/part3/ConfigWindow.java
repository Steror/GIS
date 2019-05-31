package app.part3;

import app.buffer.LeanBuffer;
import app.intersect.Intersector;
import app.queries.QueryLabModified;
import jdk.nashorn.internal.runtime.regexp.joni.Regex;
import lombok.Getter;
import lombok.Setter;
import org.geotools.data.*;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.util.URLs;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

public class ConfigWindow extends JFrame {

    @Getter
    JFrame frame;

    @Getter
    Double trackLength = 100.0, trackWidth = 100.0, distance1 = 250.0, distance2 = 250.0, averageSlope = 100.0;

    @Getter
    @Setter
    SimpleFeatureSource roadSFS, riverSFS, areaSFS, suitableAreaSFS, intersectedSFS;// heightSFS

    @Getter
    @Setter
    SimpleFeatureCollection roadSFC, riverSFC, areaSFC;// heightSFC
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

    public void removeBufferedArea (SimpleFeatureCollection sfc, Double distance) throws CQLException, IOException {
        LeanBuffer leanBuffer = new LeanBuffer("BUF"+sfc.getSchema().getTypeName(), sfc, distance);
        leanBuffer.buffer();
        SimpleFeatureCollection buffered = leanBuffer.getBuffered();
        Intersector intersector = new Intersector(suitableArea, buffered);
        //suitableArea = suitableAreaSFS.getFeatures();
        //intersector.recalculateLength("Shape_Leng");
        //intersector.recalculateArea("Shape_Area");
        intersector.setPrefixes(pre1, pre2);
        intersector.intersect();
        SimpleFeatureCollection intersected = intersector.getIntersected();
        //intersectedSFS = saveAndLoad(intersected);
        //intersected = intersectedSFS.getFeatures();

        List<SimpleFeature> features = Collections.synchronizedList(new ArrayList<>());
        SimpleFeatureType sft = suitableArea.getSchema();
        String suitableName = suitableArea.getSchema().getTypeName();
        String intersectedName = intersected.getSchema().getTypeName();
        long milis = System.currentTimeMillis();

        try (SimpleFeatureIterator iter = suitableArea.features()) {
            while (iter.hasNext()) {
                SimpleFeature feature = iter.next();
                long goodId = (long) feature.getAttribute("OBJECTID");
                //System.out.println("GOOD: "+goodId);
                //goodId = goodId.replace(suitableName,"");
                boolean isFound = false;
                second: try (SimpleFeatureIterator iter2 = intersected.features()) {
                    while (iter2.hasNext()) {
                        SimpleFeature feature2 = iter2.next();
                        long badId = (long) feature2.getAttribute("AOBJECTID");
                        //System.out.println("BAD: "+badId);
                        //badId = badId.replace(intersectedName,"");
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
        //suitableArea = suitableAreaSFS.getFeatures();
    }

    public SimpleFeatureSource saveAndLoad(SimpleFeatureCollection sfc) throws IOException {

        SimpleFeatureType ft = sfc.getSchema();
        String typeName = ft.getTypeName();

        String fileName = ft.getTypeName();
        File file = new File("DataStore",fileName+".shp");

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
        System.out.println("INFO: Finished export" + LocalDateTime.now());

        return dataStore.getFeatureSource(typeName);

        //Style shpStyle = createDefaultStyle();
        //Layer shpLayer = new FeatureLayer(featureSource, shpStyle);
        //map.addLayer(shpLayer);
        //frame.getMapPane().repaint();
        //return dataStore;
    }
}
