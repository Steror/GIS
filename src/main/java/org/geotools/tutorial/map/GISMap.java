package org.geotools.tutorial.map;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import javax.swing.*;
import java.util.*;

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
import org.geotools.factory.Hints;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.gce.geotiff.GeoTiffReader;
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
import org.geotools.swing.event.MapMouseEvent;
import org.geotools.swing.tool.CursorTool;
import org.geotools.tutorial.filter.QueryLabModified;
import org.geotools.tutorial.intersect.Intersector;
import org.geotools.util.URLs;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
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

    /*
     * Convenient constants for the type of feature geometry in the shapefile
     */
    private enum GeomType {
        POINT,
        LINE,
        POLYGON
    }

    /*
     * Some style variables
     */
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

    public static void main(String[] args) throws Exception {
        GISMap myMap = new GISMap();
        myMap.displayLayers();
    }


    /**
     * Displays a GeoTIFF file overlaid with a Shapefile
     */
    private void displayLayers() throws Exception {

        // Create a JMapFrame with a menu to choose the display style for the layers
        map.setTitle("GIS Application");
        frame = new JMapFrame(map);
        frame.enableLayerTable(true);
        frame.setSize(800, 600);
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
        QueryButton.addActionListener(e -> {
        // display the query frame when the button is pressed
            if(store != null)
            initQueryLabModified();
            queryLab.setVisible(true);

        });
        JButton ZoomSelectedButton = new JButton("Zoom to selected");
        toolBar.addSeparator();
        toolBar.add(ZoomSelectedButton);
        ZoomSelectedButton.addActionListener(
                e -> { zoomToSelected(); });

        JButton IntersectSelectedButton = new JButton("Intersect");
        toolBar.addSeparator();
        toolBar.add(IntersectSelectedButton);
        IntersectSelectedButton.addActionListener(
                e -> {
                    try {
                        intersectSelected();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });

        JButton QueryIntersectedButton = new JButton("Query intersected");
        toolBar.addSeparator();
        toolBar.add(QueryIntersectedButton);
        QueryIntersectedButton.addActionListener(
                e -> {
                    try {
                        queryLab.filterSelectedFeatures(intersected);
                    } catch (Exception ex) {
                        ex.printStackTrace();
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

        // Finally display the map frame.
        // When it is closed the app will exit.
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
        SimpleFeatureSource shapeFileSource = store.getFeatureSource();

        featureSource = store.getFeatureSource();

        // Create a default style
        Style shpStyle = createDefaultStyle();

        Layer shpLayer = new FeatureLayer(shapeFileSource, shpStyle);
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
            queryLab.filterSelectedFeatures(selectedFeatures);

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
        intersector.setPrefixes("", "");

        intersector.intersect();
        intersected = intersector.getIntersected();
        queryLab.filterSelectedFeatures(intersected);

    }

    public DataStore exportToShapefile(SimpleFeatureCollection sfc)
            throws IOException {
        // existing feature source from MemoryDataStore
        //SimpleFeatureSource featureSource = memory.getFeatureSource(typeName);

        SimpleFeatureType ft = sfc.getSchema();
        String typeName = ft.getTypeName();

        String fileName = ft.getTypeName();
        File file = new File("C:\\Users\\Steror\\Documents\\ArcGIS\\Map2\\MyShape", fileName + ".shp");

        Map<String, java.io.Serializable> creationParams = new HashMap<>();
        creationParams.put("url", URLs.fileToUrl(file));

        FileDataStoreFactorySpi factory = FileDataStoreFinder.getDataStoreFactory("shp");
        DataStore dataStore = factory.createNewDataStore(creationParams);

        dataStore.createSchema(ft);

        // The following workaround to write out the prj is no longer needed
        // ((ShapefileDataStore)dataStore).forceSchemaCRS(ft.getCoordinateReferenceSystem());

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
}