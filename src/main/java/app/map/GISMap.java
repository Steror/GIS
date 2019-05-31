package app.map;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.*;

import app.part3.ConfigWindow;
import org.geotools.coverage.GridSampleDimension;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.data.*;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.GridReaderLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.map.StyleLayer;
import org.geotools.styling.*;
import org.geotools.styling.Stroke;
import org.geotools.swing.JMapFrame;
import org.geotools.swing.action.SafeAction;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.geotools.swing.dialog.JExceptionReporter;
import org.geotools.swing.event.MapMouseEvent;
import org.geotools.swing.tool.CursorTool;
import app.queries.QueryLabModified;
import app.intersect.GroupingBuilder;
import app.intersect.Intersector;
import org.geotools.util.URLs;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.Function;
import org.opengis.filter.identity.FeatureId;
import org.opengis.style.ContrastMethod;
import org.opengis.feature.simple.SimpleFeatureType;

@SuppressWarnings("Duplicates")
public class GISMap {

    private static Point startScreenPos, endScreenPos;
    private StyleFactory sf = CommonFactoryFinder.getStyleFactory();
    private FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

    private JMapFrame frame;
    private GridCoverage2DReader reader;

    private enum GeomType {
        POINT,
        LINE,
        POLYGON
    }

    // Some style variables
    private static final Color LINE_COLOUR = Color.BLACK;
    private static final Color FILL_COLOUR = Color.BLACK;
    private static final Color SELECTEDFILL_COLOUR = Color.CYAN;
    private static final Color SELECTEDLINE_COLOUR = Color.CYAN;
    private static final float DEFAULT_OPACITY = 0.0f;
    private static final float OPACITY = 0.5f;
    private static final float LINE_WIDTH = 1.0f;
    private static final float POINT_SIZE = 2.0f;

    private SimpleFeatureSource featureSource;

    private String geometryAttributeName;
    private GISMap.GeomType geometryType;

    private QueryLabModified queryLab;
    private MapContent map = new MapContent();
    private FileDataStore previousStore;
    private FileDataStore store;
    private SimpleFeatureCollection selectedFeatures;
    private SimpleFeatureCollection intersected;
    private String pre1 = "", pre2 = "A";
    private ConfigWindow config;
    private DefaultTableModel model = new DefaultTableModel(); //model for displaying calculated table

    public static void main(String[] args) throws Exception {
        GISMap myMap = new GISMap();
        myMap.displayLayers();
    }

    private void displayLayers() throws Exception {
        map.setTitle("GIS Application");
        frame = new JMapFrame(map);
        frame.enableLayerTable(true);
        frame.setSize(800, 600);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.enableStatusBar(true);
        frame.enableToolBar(true);
        queryLab = new QueryLabModified();
        queryLab.setTitle("Query");
        config = new ConfigWindow();

        JMenuBar menuBar = new JMenuBar();
        frame.setJMenuBar(menuBar);

        JMenu layerMenu = new JMenu("Layer");
        menuBar.add(layerMenu);
        layerMenu.add(
                new SafeAction("Add shape file") {
                    public void action(ActionEvent e) throws Throwable {
                        addShapeLayer();
                    }
                });
        layerMenu.add(
                new SafeAction("Add raster file") {
                    public void action(ActionEvent e) throws Throwable {
                        addRasterLayer();
                    }
                });

        JMenu rasterMenu = new JMenu("Raster");
        menuBar.add(rasterMenu);
        rasterMenu.add(
                new SafeAction("Grayscale display") {
                    public void action(ActionEvent e) throws Throwable {
                        Style style = createGreyscaleStyle();
                        if (style != null) {
                            ((StyleLayer) map.layers().get(0)).setStyle(style);
                            frame.repaint();
                        }
                    }
                });
        rasterMenu.add(
                new SafeAction("RGB display") {
                    public void action(ActionEvent e) throws Throwable {
                        Style style = createRGBStyle();
                        if (style != null) {
                            ((StyleLayer) map.layers().get(0)).setStyle(style);
                            frame.repaint();
                        }
                    }
                });

        JMenu saveMenu = new JMenu("Save");
        menuBar.add(saveMenu);
        saveMenu.add(
                new SafeAction("Save selected features") {
                    public void action(ActionEvent e) throws Throwable {
                        exportToShapefile(selectedFeatures);
                    }
                });
        saveMenu.add(
                new SafeAction("Save intersected features") {
                    public void action(ActionEvent e) throws Throwable {
                        exportToShapefile(intersected);
                    }
                });
        JMenu geoMenu = new JMenu("Geoprocessing");
        menuBar.add(geoMenu);
        geoMenu.add(
                new SafeAction("Intersect") {
                    public void action(ActionEvent e) {
                            try {
                                intersectSelected();
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                });
        geoMenu.add(
                new SafeAction("Query intersected") {
                    public void action(ActionEvent e) {
                        try {
                            queryLab.showSelectedFeatures(intersected);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                });
        JMenu part2Menu = new JMenu("Part 2");
        menuBar.add(part2Menu);
        part2Menu.add(
                new SafeAction("Part 2.1 Dissolve and calculate") {
                    public void action(ActionEvent e) {
                        try {
                            FeatureLayer featureLayer = (FeatureLayer) frame.getMapContent().layers().get(frame.getMapContent().layers().size() - 1);
                            FeatureSource featureSource = featureLayer.getFeatureSource();
                            SimpleFeatureCollection sfc = (SimpleFeatureCollection) featureSource.getFeatures();
                            calculateRatio(sfc);
                            //calculateRatio(intersected);
                            queryLab.table.setModel(model);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                });
        JMenu part3Menu = new JMenu("Part 3");
        menuBar.add(part3Menu);
        part3Menu.add(
                new SafeAction("Open config window") {
                    public void action(ActionEvent e) {
                        config.getFrame().setVisible(true);
                    }
                });
        part3Menu.add(
                new SafeAction("Set layer as roads") {
                    public void action(ActionEvent e) {
                        config.setRoadSFS(featureSource);
                    }
                });
        part3Menu.add(
                new SafeAction("Set layer as rivers") {
                    public void action(ActionEvent e) {
                        config.setRiverSFS(featureSource);
                    }
                });
        part3Menu.add(
                new SafeAction("Set layer as areas") {
                    public void action(ActionEvent e) {
                        FeatureLayer featureLayer = (FeatureLayer) frame.getMapContent().layers().get(frame.getMapContent().layers().size() - 1);
                        config.setAreaSFS((SimpleFeatureSource) featureLayer.getFeatureSource());
                    }
                });

        JToolBar toolBar = frame.getToolBar();
        JButton SelectButton = new JButton("Select");
        toolBar.addSeparator();
        toolBar.add(SelectButton);
        JButton ZoomSelectedButton = new JButton("Zoom to selected");
        toolBar.addSeparator();
        toolBar.add(ZoomSelectedButton);
        ZoomSelectedButton.addActionListener(
                e -> zoomToSelected());

        JButton QueryButton = new JButton("Query");
        toolBar.addSeparator();
        toolBar.add(QueryButton);

        SelectButton.addActionListener(
                e ->
                        frame.getMapPane()
                                .setCursorTool(
                                        new CursorTool() {
                                            @Override
                                            public void onMousePressed(MapMouseEvent ev) {
                                                System.out.println("Mouse press at: " + ev.getWorldPos());
                                                startScreenPos = ev.getPoint();
                                            }
                                            @Override
                                            public void onMouseReleased(MapMouseEvent ev) {
                                                System.out.println("Mouse release at: " + ev.getWorldPos());
                                                endScreenPos = ev.getPoint();
                                                selectBoxFeatures();
                                            }
                                        }));

        JButton SelectConditionAreaButton = new JButton("Select Condition Area");
        toolBar.addSeparator();
        toolBar.add(SelectConditionAreaButton);
        SelectConditionAreaButton.addActionListener(
                e ->
                        frame.getMapPane()
                                .setCursorTool(
                                        new CursorTool() {
                                            @Override
                                            public void onMousePressed(MapMouseEvent ev) {
                                                System.out.println("Mouse press at: " + ev.getWorldPos());
                                                startScreenPos = ev.getPoint();
                                            }
                                            @Override
                                            public void onMouseReleased(MapMouseEvent ev) {
                                                System.out.println("Mouse release at: " + ev.getWorldPos());
                                                endScreenPos = ev.getPoint();
                                                selectConditionAreaFeatures();
                                            }
                                        }));

        queryLab.ShowMap.addActionListener(
                e -> showMap());
        queryLab.FilterSelected.addActionListener(
                e -> { SimpleFeatureCollection sfc = queryLab.getSelectedFeatures();
                DataStore ds = null;
                    try {
                        ds = exportToDefaultShapefile(sfc);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    queryLab.setSelectedDataStore(ds);
                    try {
                        queryLab.filterSelectedFeatures(queryLab.selectedFeatures);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } });
        QueryButton.addActionListener(e -> {
        // display the query frame when the button is pressed
            if(queryLab.getDataStore() != store)
            initQueryLabModified();
            queryLab.setVisible(true);

        });

        map.getCoordinateReferenceSystem();
        frame.getMapPane().repaint();
        frame.setVisible(true);
    }

    private void selectConditionAreaFeatures() {
        Filter filter = getBBoxFilter();
        try {
            config.setRoadSFC(config.getRoadSFS().getFeatures(filter));
            config.setRiverSFC(config.getRiverSFS().getFeatures(filter));
            config.setAreaSFC(config.getAreaSFS().getFeatures(filter));
            config.findSuitableArea(config.getAreaSFC());
            config.findUnsuitableArea(config.getAreaSFC());
            config.removeBufferedArea(config.getUnsuitableArea(), config.getDistance2());
            config.removeBufferedArea(config.getRiverSFC(), config.getDistance1());
            config.removeBufferedArea(config.getRoadSFC(), config.getDistance1());
            exportToShapefile(config.getSuitableArea());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private Filter getBBoxFilter() {
        int x1, x2, y1, y2;
        if(endScreenPos.x > startScreenPos.x){
            x1 = startScreenPos.x;
            x2 = endScreenPos.x;}
        else{
            x1 = endScreenPos.x;
            x2 = startScreenPos.x;}
        if(endScreenPos.y > startScreenPos.y){
            y1 = endScreenPos.y;
            y2 = startScreenPos.y;}
        else{
            y1 = startScreenPos.y;
            y2 = endScreenPos.y;}
        Rectangle screenRect = new Rectangle(x1, y2, x2 - x1 + 2, y1 - y2 + 2);

        /*
         * Transform the screen rectangle into bounding box in the coordinate
         * reference system of our map context. Note: we are using a naive method
         * here but GeoTools also offers other, more accurate methods.
         */
        AffineTransform screenToWorld = frame.getMapPane().getScreenToWorldTransform();
        Rectangle2D worldRect = screenToWorld.createTransformedShape(screenRect).getBounds2D();
        ReferencedEnvelope bbox =
                new ReferencedEnvelope(
                        worldRect, frame.getMapContent().getCoordinateReferenceSystem());

        /*
         * Create a Filter to select features that intersect with
         * the bounding box
         */
        return ff.intersects(ff.property(geometryAttributeName), ff.literal(bbox));
    }

    private void addRasterLayer() {

        File file = JFileDataStoreChooser.showOpenFile("jpg", null);
        if (file == null) {
            return;
        }

        AbstractGridFormat format = GridFormatFinder.findFormat(file);
        reader = format.getReader(file); //add second parameter hints for top code

        Style rasterStyle = createRGBStyle();
        Layer rasterLayer = new GridReaderLayer(reader, rasterStyle);
        map.addLayer(rasterLayer);
        frame.getMapPane().repaint();
    }

    private void addShapeLayer() throws Exception {

        File file = JFileDataStoreChooser.showOpenFile("shp", null);
        if (file == null) {
            return;
        }

        previousStore = store;
        store = FileDataStoreFinder.getDataStore(file);

        featureSource = store.getFeatureSource();

        Style shpStyle = createDefaultStyle();

        Layer shpLayer = new FeatureLayer(featureSource, shpStyle);
        map.addLayer(shpLayer);
        frame.getMapPane().repaint();
    }

    private Style createGreyscaleStyle() {
        GridCoverage2D cov;
        try {
            cov = reader.read(null);
        } catch (IOException giveUp) {
            throw new RuntimeException(giveUp);
        }
        int numBands = cov.getNumSampleDimensions();
        Integer[] bandNumbers = new Integer[numBands];
        for (int i = 0; i < numBands; i++) {
            bandNumbers[i] = i + 1;
        }
        Object selection =
                JOptionPane.showInputDialog(
                        frame,
                        "Band to use for greyscale display",
                        "Select an image band",
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        bandNumbers,
                        1);
        if (selection != null) {
            int band = ((Number) selection).intValue();
            return createGreyscaleStyle(band);
        }
        return null;
    }

    private Style createGreyscaleStyle(int band) {
        ContrastEnhancement ce = sf.contrastEnhancement(ff.literal(1.0), ContrastMethod.NORMALIZE);
        SelectedChannelType sct = sf.createSelectedChannelType(String.valueOf(band), ce);

        RasterSymbolizer sym = sf.getDefaultRasterSymbolizer();
        ChannelSelection sel = sf.channelSelection(sct);
        sym.setChannelSelection(sel);

        return SLD.wrapSymbolizers(sym);
    }

    private Style createRGBStyle() {
        GridCoverage2D cov;
        try {
            cov = reader.read(null);
        } catch (IOException giveUp) {
            throw new RuntimeException(giveUp);
        }
        // We need at least three bands to create an RGB style
        int numBands = cov.getNumSampleDimensions();
        if (numBands < 3) {
            return null;
        }
        // Get the names of the bands
        String[] sampleDimensionNames = new String[numBands];
        for (int i = 0; i < numBands; i++) {
            GridSampleDimension dim = cov.getSampleDimension(i);
            sampleDimensionNames[i] = dim.getDescription().toString();
        }
        final int RED = 0, GREEN = 1, BLUE = 2;
        int[] channelNum = {-1, -1, -1};
        // We examine the band names looking for "red...", "green...", "blue...".
        // Note that the channel numbers we record are indexed from 1, not 0.
        for (int i = 0; i < numBands; i++) {
            String name = sampleDimensionNames[i].toLowerCase();
            if (name != null) {
                if (name.matches("red.*")) {
                    channelNum[RED] = i + 1;
                } else if (name.matches("green.*")) {
                    channelNum[GREEN] = i + 1;
                } else if (name.matches("blue.*")) {
                    channelNum[BLUE] = i + 1;
                }
            }
        }
        // If we didn't find named bands "red...", "green...", "blue..."
        // we fall back to using the first three bands in order
        if (channelNum[RED] < 0 || channelNum[GREEN] < 0 || channelNum[BLUE] < 0) {
            channelNum[RED] = 1;
            channelNum[GREEN] = 2;
            channelNum[BLUE] = 3;
        }
        // Now we create a RasterSymbolizer using the selected channels
        SelectedChannelType[] sct = new SelectedChannelType[cov.getNumSampleDimensions()];
        ContrastEnhancement ce = sf.contrastEnhancement(ff.literal(1.0), ContrastMethod.NORMALIZE);
        for (int i = 0; i < 3; i++) {
            sct[i] = sf.createSelectedChannelType(String.valueOf(channelNum[i]), ce);
        }
        RasterSymbolizer sym = sf.getDefaultRasterSymbolizer();
        ChannelSelection sel = sf.channelSelection(sct[RED], sct[GREEN], sct[BLUE]);
        sym.setChannelSelection(sel);

        return SLD.wrapSymbolizers(sym);
    }

    void selectBoxFeatures()
    {
        Filter filter = getBBoxFilter();
        /*
         * Use the filter to identify the selected features
         */
        try {
            FeatureLayer featureLayer = (FeatureLayer) frame.getMapContent().layers().get(frame.getMapContent().layers().size() - 1);
            FeatureSource featureSource = featureLayer.getFeatureSource();
            selectedFeatures = (SimpleFeatureCollection) featureSource.getFeatures(filter);
            initQueryLabModified();
            queryLab.showSelectedFeatures(selectedFeatures);

            Set<FeatureId> IDs = new HashSet<>();
            try (SimpleFeatureIterator iter = selectedFeatures.features()) {
                while (iter.hasNext()) {
                    SimpleFeature feature = iter.next();
                    IDs.add(feature.getIdentifier());

                    System.out.println("   " + feature.getIdentifier());
                }
            }

            if (IDs.isEmpty()) {
                System.out.println("   no feature selected");
            }

            displaySelectedFeatures(IDs);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void displaySelectedFeatures(Set<FeatureId> IDs) {
        Style style;
        FeatureLayer featureLayer = (FeatureLayer) frame.getMapContent().layers().get(frame.getMapContent().layers().size() - 1);
        featureSource = (SimpleFeatureSource) featureLayer.getFeatureSource();

        if (IDs.isEmpty()) {
            style = createDefaultStyle();

        } else {
            style = createSelectedStyle(IDs);
        }

        Layer layer = frame.getMapContent().layers().get(frame.getMapContent().layers().size() - 1);
        ((FeatureLayer) layer).setStyle(style);
        frame.getMapPane().repaint();
    }

    private Style createDefaultStyle() {
        Rule rule = createRule(LINE_COLOUR, FILL_COLOUR, DEFAULT_OPACITY);

        FeatureTypeStyle fts = sf.createFeatureTypeStyle();
        fts.rules().add(rule);

        Style style = sf.createStyle();
        style.featureTypeStyles().add(fts);
        return style;
    }

    private Style createSelectedStyle(Set<FeatureId> IDs) {
        Rule selectedRule = createRule(SELECTEDLINE_COLOUR, SELECTEDFILL_COLOUR, OPACITY);
        selectedRule.setFilter(ff.id(IDs));

        Rule otherRule = createRule(LINE_COLOUR, FILL_COLOUR, DEFAULT_OPACITY);
        otherRule.setElseFilter(true);

        FeatureTypeStyle fts = sf.createFeatureTypeStyle();
        fts.rules().add(selectedRule);
        fts.rules().add(otherRule);

        Style style = sf.createStyle();
        style.featureTypeStyles().add(fts);
        return style;
    }

    private Rule createRule(Color outlineColor, Color fillColor, float fillOpacity) {
        Symbolizer symbolizer = null;
        Fill fill;
        Stroke stroke = sf.createStroke(ff.literal(outlineColor), ff.literal(LINE_WIDTH));

        setGeometry();
        switch (geometryType) {
            case POLYGON:
                fill = sf.createFill(ff.literal(fillColor), ff.literal(fillOpacity));
                symbolizer = sf.createPolygonSymbolizer(stroke, fill, geometryAttributeName);
                break;

            case LINE:
                symbolizer = sf.createLineSymbolizer(stroke, geometryAttributeName);
                break;

            case POINT:
                fill = sf.createFill(ff.literal(fillColor), ff.literal(OPACITY));

                Mark mark = sf.getCircleMark();
                mark.setFill(fill);
                mark.setStroke(stroke);

                Graphic graphic = sf.createDefaultGraphic();
                graphic.graphicalSymbols().clear();
                graphic.graphicalSymbols().add(mark);
                graphic.setSize(ff.literal(POINT_SIZE));

                symbolizer = sf.createPointSymbolizer(graphic, geometryAttributeName);
        }

        Rule rule = sf.createRule();
        rule.symbolizers().add(symbolizer);
        return rule;
    }

    private void setGeometry() {
        GeometryDescriptor geomDesc = featureSource.getSchema().getGeometryDescriptor();

        geometryAttributeName = geomDesc.getLocalName();

        Class<?> clazz = geomDesc.getType().getBinding();

        if (org.locationtech.jts.geom.Polygon.class.isAssignableFrom(clazz) || MultiPolygon.class.isAssignableFrom(clazz)) {
            geometryType = GISMap.GeomType.POLYGON;

        } else if (LineString.class.isAssignableFrom(clazz)
                || MultiLineString.class.isAssignableFrom(clazz)) {

            geometryType = GISMap.GeomType.LINE;

        } else {
            geometryType = GISMap.GeomType.POINT;
        }
    }

    private void initQueryLabModified(){
        queryLab.setDataStore(store);
        try {
            queryLab.updateUI();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showMap() {
        selectedFeatures = queryLab.getSelectedFeatures();
        Set<FeatureId> IDs = new HashSet<>();
        try (SimpleFeatureIterator iter = selectedFeatures.features()) {
            while (iter.hasNext()) {
                SimpleFeature feature = iter.next();
                IDs.add(feature.getIdentifier());
            }
        }
        displaySelectedFeatures(IDs);
    }

    public void zoomToSelected()
    {
        ReferencedEnvelope referencedEnvelope = selectedFeatures.getBounds();
        frame.getMapPane().setDisplayArea(referencedEnvelope);
    }

    public void intersectSelected() throws Exception {
        FeatureLayer featureLayer = (FeatureLayer) frame.getMapContent().layers().get(frame.getMapContent().layers().size() - 2);
        FeatureSource featureSource = featureLayer.getFeatureSource();
        SimpleFeatureCollection backgroundFeatures = (SimpleFeatureCollection) featureSource.getFeatures();

        featureLayer = (FeatureLayer) frame.getMapContent().layers().get(frame.getMapContent().layers().size() - 1);
        featureSource = featureLayer.getFeatureSource();
        SimpleFeatureCollection foregroundFeatures = (SimpleFeatureCollection) featureSource.getFeatures();

        //Intersector intersector = new Intersector(selectedFeatures, backgroundFeatures);
        Intersector intersector = new Intersector(foregroundFeatures, backgroundFeatures);
        intersector.recalculateLength("Shape_Leng");
        intersector.recalculateArea("Shape_Area");
        intersector.setPrefixes(pre1, pre2);

        intersector.intersect();
        intersected = intersector.getIntersected();
    }

    public DataStore exportToShapefile(SimpleFeatureCollection sfc)
            throws IOException {
        // existing feature source from MemoryDataStore
        //SimpleFeatureSource featureSource = memory.getFeatureSource(typeName);

        SimpleFeatureType ft = sfc.getSchema();
        String typeName = ft.getTypeName();

        String fileName = ft.getTypeName();
        JFileChooser f = new JFileChooser();
        f.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        f.showSaveDialog(null);
        File file = new File(f.getSelectedFile(),fileName+".shp");

        Map<String, java.io.Serializable> creationParams = new HashMap<>();
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
        return dataStore;
    }

    public DataStore exportToDefaultShapefile(SimpleFeatureCollection sfc)
            throws IOException {

        SimpleFeatureType ft = sfc.getSchema();
        String typeName = ft.getTypeName();

        String fileName = ft.getTypeName();
        File file = new File("DataStore",fileName+".shp");

        Map<String, java.io.Serializable> creationParams = new HashMap<>();
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
        return dataStore;
    }

    public void calculateRatio(SimpleFeatureCollection sfc) throws IOException {
        FeatureLayer featureLayer = (FeatureLayer) frame.getMapContent().layers().get(frame.getMapContent().layers().size() - 2);
        FeatureSource featureSource = featureLayer.getFeatureSource();
        SimpleFeatureCollection backgroundFeatures = (SimpleFeatureCollection) featureSource.getFeatures();

        model = new DefaultTableModel(new String[] {"Administracinis vienetas",
                "<- Plotas", "Teritorija", "<- Plotas", "Santykis (%)"}, 0);

        String[] grouping = GroupingBuilder.build(pre1+"GKODAS",
                new String[] {"LIKE 'hd%'", "= 'ms0'", "= 'pu0'", "= 'ms4'"});
        String[] titles = new String[] {"Hidrografijos teritorijos",
                "Medžiais ir krūmais apaugusios teritorijos",
                "Užstatytos teritorijos", "Pramoninių sodų masyvai"};

        Function sum = ff.function("Collection_Sum", ff.property(pre1+"Shape_Area"));
        long milis = System.currentTimeMillis();
        System.out.println("INFO: Calculation has started   " + LocalDateTime.now());

        SimpleFeatureIterator iter = backgroundFeatures.features();
        try {
            while (iter.hasNext()) {
                SimpleFeature feature = iter.next();
                String title = (String) feature.getAttribute("VARDAS");
                for (int i=0 ; i<grouping.length ; i++) {
                    Filter filter = CQL.toFilter(pre2+"VARDAS = '"+title+"' AND "+grouping[i]);
                    SimpleFeatureCollection filteredCol = sfc.subCollection(filter);
                    double result = (Double) sum.evaluate(filteredCol);
                    double muniArea = (Double) feature.getAttribute("Plotas");
                    model.addRow(new String[] {title, String.format("%.12f" ,muniArea),
                            titles[i], String.format("%.12f" ,result),
                            String.format("%.12f" ,result*100/muniArea)});
                }
            }

        } catch (CQLException ex) {
            JExceptionReporter.showDialog(ex);
        } finally {
            iter.close();
        }
        System.out.println("INFO: Calculation has ended  " + LocalDateTime.now());
        System.out.println("INFO: Calculation:  " + (System.currentTimeMillis()-milis)/1000d + "s");
    }

    private DefaultTableModel part3() throws IOException {
        DefaultTableModel model = new DefaultTableModel(new String[] {"Administracinis vienetas",
                "<- Plotas", "Teritorija", "<- Plotas", "Santykis (%)"}, 0);

        FeatureLayer featureLayer = (FeatureLayer) frame.getMapContent().layers().get(frame.getMapContent().layers().size() - 2);
        FeatureSource featureSource = featureLayer.getFeatureSource();
        SimpleFeatureCollection muniesCol = (SimpleFeatureCollection) featureSource.getFeatures();

        featureLayer = (FeatureLayer) frame.getMapContent().layers().get(frame.getMapContent().layers().size() - 1);
        featureSource = featureLayer.getFeatureSource();
        SimpleFeatureCollection territoriesCol = (SimpleFeatureCollection) featureSource.getFeatures();

        String muniesPrefix = muniesCol.getSchema().getName().getLocalPart();
        String territoriesPrefix = territoriesCol.getSchema().getName().getLocalPart();

        Intersector intersector = new Intersector(territoriesCol, muniesCol);
        intersector.recalculateArea("Shape_Area");
        intersector.setName(territoriesPrefix+"_intersected");
        intersector.intersect();
        SimpleFeatureCollection intersected = intersector.getIntersected();
        frame.getMapContent().addLayer(new FeatureLayer(intersected,
                SLD.createSimpleStyle(intersected.getSchema())));

        String[] grouping = GroupingBuilder.build(territoriesPrefix + "_GKODAS",
                new String[] {"LIKE 'hd%'", "= 'ms0'", "= 'pu0'", "= 'ms4'"});
        String[] titles = new String[] {"Hidrografijos teritorijos",
                "Medžiais ir krūmais apaugusios teritorijos",
                "Užstatytos teritorijos", "Pramoninių sodų masyvai"};

        Function function = ff.function("Collection_Sum", ff.property(territoriesPrefix + "_SHAPE_area"));

        SimpleFeatureIterator iter = muniesCol.features();
        try {
            while (iter.hasNext()) {
                SimpleFeature feature = iter.next();
                String title = (String) feature.getAttribute("SAV");
                for (int i=0 ; i<grouping.length ; i++) {
                    Filter filter = CQL.toFilter(muniesPrefix+"_SAV = '"+title+"' AND "+grouping[i]);
                    double result = (Double) function.evaluate(intersected.subCollection(filter));
                    double muniArea = (Double) feature.getAttribute("PLOT");
                    model.addRow(new String[] {title, String.valueOf(muniArea),
                            titles[i], String.valueOf(result),
                            String.valueOf(result*100/muniArea)});
                }
            }
        } catch (CQLException ex) {
            JExceptionReporter.showDialog(ex);
        } finally {
            iter.close();
        }

        return model;
    }
}