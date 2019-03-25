package org.geotools.tutorial.map;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.*;

import org.geotools.coverage.GridSampleDimension;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.Parameter;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.Hints;
import org.geotools.gce.geotiff.GeoTiffFormat;
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
import org.geotools.swing.data.JParameterListWizard;
import org.geotools.swing.event.MapMouseEvent;
import org.geotools.swing.tool.CursorTool;
import org.geotools.swing.wizard.JWizard;
import org.geotools.util.KVP;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.identity.FeatureId;
import org.opengis.style.ContrastMethod;

@SuppressWarnings("Duplicates")
public class Map {

    private static Point startScreenPos;
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
     * Some default style variables
     */
    private static final Color LINE_COLOUR = Color.RED;
    private static final Color FILL_COLOUR = Color.CYAN;
    private static final Color SELECTED_COLOUR = Color.YELLOW;
    private static final float DEFAULT_OPACITY = 0.0f;
    private static final float OPACITY = 0.5f;
    private static final float LINE_WIDTH = 1.0f;
    private static final float POINT_SIZE = 10.0f;

    private SimpleFeatureSource featureSource;

    private String geometryAttributeName;
    private Map.GeomType geometryType;

    public static void main(String[] args) throws Exception {
        Map me = new Map();
        me.getLayersAndDisplay();
    }

    /**
     * Prompts the user for a GeoTIFF file and a Shapefile and passes them to the displayLayers
     * method
     */
    private void getLayersAndDisplay() throws Exception {
        List<Parameter<?>> list = new ArrayList<>();
        list.add(
                new Parameter<>(
                        "image",
                        File.class,
                        "Image",
                        "GeoTiff or World+Image to display as basemap",
                        new KVP(Parameter.EXT, "tif", Parameter.EXT, "jpg")));
        list.add(
                new Parameter<>(
                        "shape",
                        File.class,
                        "Shapefile",
                        "Shapefile contents to display",
                        new KVP(Parameter.EXT, "shp")));

        JParameterListWizard wizard =
                new JParameterListWizard("Image Lab", "Fill in the following layers", list);
        int finish = wizard.showModalDialog();

        if (finish != JWizard.FINISH) {
            System.exit(0);
        }
        File imageFile = (File) wizard.getConnectionParameters().get("image");
        File shapeFile = (File) wizard.getConnectionParameters().get("shape");
        displayLayers(imageFile, shapeFile);
    }

    /**
     * Displays a GeoTIFF file overlaid with a Shapefile
     *
     * @param rasterFile the GeoTIFF file
     * @param shpFile the Shapefile
     */
    private void displayLayers(File rasterFile, File shpFile) throws Exception {
        AbstractGridFormat format = GridFormatFinder.findFormat(rasterFile);
        // this is a bit hacky but does make more geotiffs work
        Hints hints = new Hints();
        if (format instanceof GeoTiffFormat) {
            hints = new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, Boolean.TRUE);
        }
        reader = format.getReader(rasterFile, hints);

        // Initially display the raster in RGB
        Style rasterStyle = createRGBStyle();

        // Connect to the shapefile
        FileDataStore dataStore = FileDataStoreFinder.getDataStore(shpFile);
        SimpleFeatureSource shapefileSource = dataStore.getFeatureSource();

        FileDataStore store = FileDataStoreFinder.getDataStore(shpFile);
        featureSource = store.getFeatureSource();
        setGeometry();

        // Create a basic style with yellow lines and no fill
        Style shpStyle = SLD.createPolygonStyle(Color.YELLOW, null, 0.0f);

        // Set up a MapContent with the two layers
        final MapContent map = new MapContent();
        map.setTitle("ImageLab");

        Layer rasterLayer = new GridReaderLayer(reader, rasterStyle);
        map.addLayer(rasterLayer);

        Layer shpLayer = new FeatureLayer(shapefileSource, shpStyle);
        map.addLayer(shpLayer);

        // Create a JMapFrame with a menu to choose the display style for the
        frame = new JMapFrame(map);
        frame.enableLayerTable(true);
        frame.setSize(800, 600);
        frame.enableStatusBar(true);
        // frame.enableTool(JMapFrame.Tool.ZOOM, JMapFrame.Tool.PAN, JMapFrame.Tool.RESET);
        frame.enableToolBar(true);

        JMenuBar menuBar = new JMenuBar();
        frame.setJMenuBar(menuBar);
        JMenu menu = new JMenu("Raster");
        menuBar.add(menu);

        menu.add(
                new SafeAction("Grayscale display") {
                    public void action(ActionEvent e) throws Throwable {
                        Style style = createGreyscaleStyle();
                        if (style != null) {
                            ((StyleLayer) map.layers().get(0)).setStyle(style);
                            frame.repaint();
                        }
                    }
                });

        menu.add(
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

        /*
         * When the user clicks the button we want to enable
         * our custom feature selection tool. Since the only
         * mouse action we are interested in is 'clicked', and
         * we are not creating control icons or cursors here,
         * we can just create our tool as an anonymous sub-class
         * of CursorTool.
         */
        SelectButton.addActionListener(
                e ->
                        frame.getMapPane()
                                .setCursorTool(
                                        new CursorTool() {

                                            @Override
                                            public void onMouseClicked(MapMouseEvent ev) {
                                                selectFeatures(ev);
                                            }
                                        }));
        SelectButton.addActionListener(
                e ->
                        frame.getMapPane()
                                .setCursorTool(
                                        new CursorTool() {

                                            @Override
                                            public void onMousePressed(MapMouseEvent ev) {
                                                System.out.println("Mouse press at: " + ev.getMapPosition());
                                                startScreenPos = ev.getPoint();
                                            }
                                        }));
        SelectButton.addActionListener(
                e ->
                        frame.getMapPane()
                                .setCursorTool(
                                        new CursorTool() {

                                            @Override
                                            public void onMouseReleased(MapMouseEvent ev) {
                                                selectBoxFeatures(startScreenPos, ev);
                                            }
                                        }));
        // Finally display the map frame.
        // When it is closed the app will exit.
        frame.setVisible(true);
    }

    /**
     * Create a Style to display a selected band of the GeoTIFF image as a greyscale layer
     *
     * @return a new Style instance to render the image in greyscale
     */
    private Style createGreyscaleStyle() {
        GridCoverage2D cov = null;
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
        GridCoverage2D cov = null;
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
     * This method is called by our feature selection tool when the user has clicked on the map.
     *
     * @param ev the mouse event being handled
     */
    void selectFeatures(MapMouseEvent ev) {

        System.out.println("Mouse click at: " + ev.getMapPosition());

        /*
         * Construct a 5x5 pixel rectangle centred on the mouse click position
         */
        Point screenPos = ev.getPoint();
        Rectangle screenRect = new Rectangle(screenPos.x - 2, screenPos.y - 2, 5, 5);

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
            SimpleFeatureCollection selectedFeatures = featureSource.getFeatures(filter);

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
     *MousePressed and MouseReleased select
     */
    void selectBoxFeatures(Point startScreenPos, MapMouseEvent ev)
    {
        System.out.println("Mouse release at: " + ev.getMapPosition());

        Point endScreenPos = ev.getPoint();
        Rectangle screenRect = new Rectangle(endScreenPos.x - startScreenPos.x, endScreenPos.y - startScreenPos.x);
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
            SimpleFeatureCollection selectedFeatures = featureSource.getFeatures(filter);

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

        if (IDs.isEmpty()) {
            style = createDefaultStyle();

        } else {
            style = createSelectedStyle(IDs);
        }

        Layer layer = frame.getMapContent().layers().get(1);
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
        Rule selectedRule = createRule(SELECTED_COLOUR, SELECTED_COLOUR, OPACITY);
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
        Fill fill = null;
        Stroke stroke = sf.createStroke(ff.literal(outlineColor), ff.literal(LINE_WIDTH));

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
            geometryType = Map.GeomType.POLYGON;

        } else if (LineString.class.isAssignableFrom(clazz)
                || MultiLineString.class.isAssignableFrom(clazz)) {

            geometryType = Map.GeomType.LINE;

        } else {
            geometryType = Map.GeomType.POINT;
        }
    }
}