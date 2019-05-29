package app.part3;

import app.buffer.LeanBuffer;
import app.intersect.Intersector;
import lombok.Getter;
import lombok.Setter;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.filter.text.cql2.CQLException;
import org.opengis.filter.Filter;

import javax.swing.*;
import java.io.IOException;

public class ConfigWindow extends JFrame {

    @Getter
    JFrame frame;

    @Getter
    Double trackLength = 100.0, trackWidth = 100.0, distance1 = 100.0, distance2 = 100.0, averageSlope = 100.0;

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

    public void findSuitableArea () throws CQLException, IOException {
        Filter filter = CQL.toFilter("NOT (GKODAS LIKE 'hd%' OR GKODAS = 'ms4' OR GKODAS = 'pu0')");
        this.suitableArea = getAreaSFS().getFeatures(filter);
    }

    public void findUnsuitableArea () throws CQLException, IOException {
        Filter filter = CQL.toFilter("GKODAS LIKE 'hd%' OR GKODAS = 'ms4' OR GKODAS = 'pu0'");
        this.unsuitableArea = getAreaSFS().getFeatures(filter);
    }

    public void removeBufferredArea (SimpleFeatureCollection sfc, Double distance) throws CQLException, IOException {
        LeanBuffer leanBuffer = new LeanBuffer("BUF"+sfc.getSchema().getTypeName(), sfc, distance); //Double.parseDouble(distance2.getText())
        leanBuffer.buffer();
        SimpleFeatureCollection buffered = leanBuffer.getBuffered();
        Intersector intersector = new Intersector(this.suitableArea, buffered);
        intersector.setPrefixes(pre1, pre2);
        intersector.intersect();
        SimpleFeatureCollection intersected = intersector.getIntersected();
        Filter filter = CQL.toFilter("NOT (GKODAS LIKE 'hd%' OR GKODAS = 'ms4' OR GKODAS = 'pu0')");
        this.suitableArea = intersected.subCollection(filter);
    }
}
