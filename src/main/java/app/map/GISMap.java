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
import org.geotools.tutorial.intersect.GroupingBuilder;
import org.geotools.tutorial.intersect.Intersector;
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

    QueryLabModified queryLab;
    MapContent map = new MapContent();
    FileDataStore previousStore;
    FileDataStore store;
    public SimpleFeatureCollection selectedFeatures;
    SimpleFeatureCollection intersected;
    String pre1 = "", pre2 = "A";
    ConfigWindow config;

    DefaultTableModel model = new DefaultTableModel(); //model for displaying calculated table

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
                            } catch (IOException ex) {
                                ex.printStackTrace();
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
                        if (config == null) config = new ConfigWindow();
                        else config.getFrame().setVisible(true);
                    }
                });
        /*
         * Before making the map frame visible we add a new button to its
         * toolbar for our custom feature selection tool
         */
        JToolBar toolBar = frame.getToolBar();
        JButton SelectButton = new JButton("Select");
        toolBar.addSeparator();
        toolBar.add(SelectButton);

        JButton QueryButton = new JButton("Query");
        toolBar.addSeparator();
        toolBar.add(QueryButton);

        /*
         * When the user clicks the button we want to enable
         * our custom feature selection tool.
         */
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
                                                selectBoxFeatures(ev);
                                            }
                                        }));
        queryLab = new QueryLabModified();
        queryLab.setTitle("Query");
        queryLab.ShowMap.addActionListener(
                e -> { showMap(); });
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
        JButton ZoomSelectedButton = new JButton("Zoom to selected");
        toolBar.addSeparator();
        toolBar.add(ZoomSelectedButton);
        ZoomSelectedButton.addActionListener(
                e -> { zoomToSelected(); });

        map.getCoordinateReferenceSystem();
        frame.getMapPane().repaint();
        frame.setVisible(true);
    }

    private void addRasterLayer() {

        File file = JFileDataStoreChooser.showOpenFile("jpg", null);
        if (file == null) {
            return;
        }

        AbstractGridFormat format = GridFormatFinder.findFormat(file);
        // this is a bit hacky but does make more geotiffs work
//        Hints hints = new Hints();
//        if (format instanceof GeoTiffFormat) {
//            hints = new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, Boolean.TRUE);
//        }
        reader = format.getReader(file); //add second parameter hints for top code

        // Initially display the raster in RGB
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

        // Create a default style
        Style shpStyle = createDefaultStyle();

        Layer shpLayer = new FeatureLayer(featureSource, shpStyle);
        map.addLayer(shpLayer);
        frame.getMapPane().repaint();
    }

    /**
     * Create a Style to display a selected band of the GeoTIFF image as a greyscale layer
     *
     * @return a new Style instance to render the image in greyscale
     */
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

    /**
     * Create a Style to display the specified band of the GeoTIFF image as a greyscale layer.
     *
     * <p>This method is a helper for createGreyScale() and is also called directly by the
     * displayLayers() method when the application first starts.
     *
     * @param band the image band to use for the greyscale display
     * @return a new Style instance to render the image in greyscale
     */
    private Style createGreyscaleStyle(int band) {
        ContrastEnhancement ce = sf.contrastEnhancement(ff.literal(1.0), ContrastMethod.NORMALIZE);
        SelectedChannelType sct = sf.createSelectedChannelType(String.valueOf(band), ce);

        RasterSymbolizer sym = sf.getDefaultRasterSymbolizer();
        ChannelSelection sel = sf.channelSelection(sct);
        sym.setChannelSelection(sel);

        return SLD.wrapSymbolizers(sym);
    }

    /**
     * This method examines the names of the sample dimensions in the provided coverage looking for
     * "red...", "green..." and "blue..." (case insensitive match). If these names are not found it
     * uses bands 1, 2, and 3 for the red, green and blue channels. It then sets up a raster
     * symbolizer and returns this wrapped in a Style.
     *
     * @return a new Style object containing a raster symbolizer set up for RGB image
     */
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

    /**
     *MousePressed and MouseReleased select
     */
    void selectBoxFeatures(MapMouseEvent ev)
    {
        System.out.println("Mouse release at: " + ev.getWorldPos());
        int x1, x2, y1, y2;

        endScreenPos = ev.getPoint();

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
        Filter filter = ff.intersects(ff.property(geometryAttributeName), ff.literal(bbox));

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
    /**
     * Sets the display to paint selected features yellow and unselected features in the default
     * style.
     *
     * @param IDs identifiers of currently selected features
     */
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
    /**
     * Create a default Style for feature display
     */
    private Style createDefaultStyle() {
        Rule rule = createRule(LINE_COLOUR, FILL_COLOUR, DEFAULT_OPACITY);

        FeatureTypeStyle fts = sf.createFeatureTypeStyle();
        fts.rules().add(rule);

        Style style = sf.createStyle();
        style.featureTypeStyles().add(fts);
        return style;
    }

    /**
     * Create a Style where features with given IDs are painted yellow, while others are painted
     * with the default colors.
     */
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

    /**
     * Helper for createXXXStyle methods. Creates a new Rule containing a Symbolizer tailored to the
     * geometry type of the features that we are displaying.
     */
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

    /**
     * Retrieve information about the feature geometry
     */
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

    public void showMap()
    {
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

        //part4(intersected);
    }

//    private void part4(SimpleFeatureCollection territoriesIntersected) {
//        DefaultTableModel model = new DefaultTableModel(new String[] {
//                "Administracinis vientas", "Teritorija", "<- Plotas",
//                "Statinių plotas", "Santykis (%)"}, 0);
//
//        String territoriesIntersectedPrefix = territoriesIntersected.getSchema().getName().getLocalPart();
//        String muniesPrefix = muniesCol.getSchema().getName().getLocalPart();
//        String territoriesPrefix = territoriesCol.getSchema().getName().getLocalPart();
//        String buildingsPrefix = buildingsCol.getSchema().getName().getLocalPart();
//
//        Intersector intersector = new Intersector(buildingsCol, territoriesIntersected);
//        intersector.recalculateArea("SHAPE_area");
//        intersector.setName(buildingsPrefix+"_intersected");
//        intersector.intersect();
//        SimpleFeatureCollection intersected = intersector.getIntersected();
//
//        mainFrame.getMapContent().addLayer(new FeatureLayer(intersected,
//                SLD.createSimpleStyle(intersected.getSchema())));
//
//        String[] muniesGrouping = GroupingBuilder.build(muniesCol,
//                "SAV",territoriesIntersectedPrefix+"_"+muniesPrefix+"_SAV");
//        String[] territoriesGrouping = GroupingBuilder.build(territoriesIntersectedPrefix
//                        +"_"+territoriesPrefix+"_GKODAS",
//                new String[] {"LIKE 'hd%'", "= 'ms0'", "= 'pu0'", "= 'ms4'"});
//        String[] grouping = GroupingBuilder.multiply(muniesGrouping, territoriesGrouping);
//
//        DefaultTableModel modelTerritories = (DefaultTableModel) tableTerritories.getModel();
//
//        Function function = ff.function("Collection_Sum", ff.property(buildingsPrefix+"_SHAPE_area"));
//
//        try {
//            for (int i=0 ; i<grouping.length ; i++) {
//                Filter filter = CQL.toFilter(grouping[i]);
//                SimpleFeatureCollection filteredSubCol = intersected.subCollection(filter);
//                double result = 0;
//                if (!filteredSubCol.isEmpty()) {
//                    result = ((Double) function.evaluate(filteredSubCol)).doubleValue();
//                }
//                double territoryArea = Double.parseDouble((String) modelTerritories.getValueAt(i, 3));
//                model.addRow(new String[] {(String) modelTerritories.getValueAt(i, 0),
//                        (String) modelTerritories.getValueAt(i, 2),
//                        (String) modelTerritories.getValueAt(i, 3),
//                        String.valueOf(result),
//                        String.valueOf(result*100/territoryArea)});
//            }
//        } catch (CQLException ex) {
//            JExceptionReporter.showDialog(ex);
//        }
//
//        tableBuildings.setModel(model);
//    }
}