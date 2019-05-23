package org.geotools.tutorial.filter;

import org.geotools.data.*;
import org.geotools.data.postgis.PostgisNGDataStoreFactory;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.swing.action.SafeAction;
import org.geotools.swing.data.JDataStoreWizard;
import org.geotools.swing.table.FeatureCollectionTableModel;
import org.geotools.swing.wizard.JWizard;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.geotools.tutorial.map.GISMap;
import org.opengis.filter.identity.FeatureId;

/**
 * The Query Lab is an excuse to try out Filters and Expressions on your own data with a table to
 * show the results.
 *
 * <p>Remember when programming that you have other options then the CQL parser, you can directly
 * make a Filter using CommonFactoryFinder.getFilterFactory2().
 */
@SuppressWarnings("serial")
public class QueryLabModified extends JFrame{
    public DataStore dataStore;
    public DataStore selectedDataStore;
    public AbstractButton ShowMap;
    public AbstractButton FilterSelected;
    private JComboBox<String> featureTypeCBox;
    public JTable table;
    private JTextField text;
    public SimpleFeatureCollection selectedFeatures;
    public SimpleFeatureCollection filteredSelectedFeatures;

    public static void main(String[] args) throws Exception {
        JFrame frame = new QueryLabModified();
        frame.setTitle("Query");
        frame.setVisible(true);
    }

    public QueryLabModified() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        getContentPane().setLayout(new BorderLayout());

        text = new JTextField(80);
        text.setText("include"); // include selects everything!
        getContentPane().add(text, BorderLayout.NORTH);

        table = new JTable();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setModel(new DefaultTableModel(5, 5));
        table.setPreferredScrollableViewportSize(new Dimension(500, 200));

        JScrollPane scrollPane = new JScrollPane(table);
        getContentPane().add(scrollPane, BorderLayout.CENTER);

        JMenuBar menubar = new JMenuBar();
        setJMenuBar(menubar);

        JMenu fileMenu = new JMenu("File");
        menubar.add(fileMenu);

        featureTypeCBox = new JComboBox<>();
        menubar.add(featureTypeCBox);

        JMenu dataMenu = new JMenu("Data");
        menubar.add(dataMenu);

        menubar.add(dataMenu);
        pack();
        fileMenu.add(
                new SafeAction("Open shapefile...") {
                    public void action(ActionEvent e) throws Throwable {
                        connect(new ShapefileDataStoreFactory());
                    }
                });
        fileMenu.add(
                new SafeAction("Connect to PostGIS database...") {
                    public void action(ActionEvent e) throws Throwable {
                        connect(new PostgisNGDataStoreFactory());
                    }
                });
        fileMenu.add(
                new SafeAction("Connect to DataStore...") {
                    public void action(ActionEvent e) throws Throwable {
                        connect(null);
                    }
                });
        fileMenu.addSeparator();
        fileMenu.add(
                new SafeAction("Exit") {
                    public void action(ActionEvent e) throws Throwable {
                        System.exit(0);
                    }
                });

        dataMenu.add(
                new SafeAction("Get features") {
                    public void action(ActionEvent e) throws Throwable {
                        filterFeatures();
                    }
                });
        dataMenu.add(
                new SafeAction("Count") {
                    public void action(ActionEvent e) throws Throwable {
                        countFeatures();
                    }
                });
        dataMenu.add(
                new SafeAction("Geometry") {
                    public void action(ActionEvent e) throws Throwable {
                        queryFeatures();
                    }
                });
        ShowMap = new JButton("Show on map");
        dataMenu.add(ShowMap);
        FilterSelected = new JButton("Query selected");
        dataMenu.add(FilterSelected);
    }

    private void connect(DataStoreFactorySpi format) throws Exception {
        JDataStoreWizard wizard = new JDataStoreWizard(format);
        int result = wizard.showModalDialog();
        if (result == JWizard.FINISH) {
            Map<String, Object> connectionParameters = wizard.getConnectionParameters();
            dataStore = DataStoreFinder.getDataStore(connectionParameters);
            if (dataStore == null) {
                JOptionPane.showMessageDialog(null, "Could not connect - check parameters");
            }
            updateUI();
        }
    }

    public void updateUI() throws Exception {
        ComboBoxModel<String> cbm = new DefaultComboBoxModel<>(dataStore.getTypeNames());
        featureTypeCBox.setModel(cbm);

        table.setModel(new DefaultTableModel(5, 5));
    }

    public void filterFeatures() throws Exception {
        String typeName = (String) featureTypeCBox.getSelectedItem();
        SimpleFeatureSource source = dataStore.getFeatureSource(typeName);

        Filter filter = CQL.toFilter(text.getText());
        selectedFeatures = source.getFeatures(filter);
        FeatureCollectionTableModel model = new FeatureCollectionTableModel(selectedFeatures);
        table.setModel(model);
    }

    public void filterSelectedFeatures(SimpleFeatureCollection sfc) throws Exception {
        SimpleFeatureType ft = sfc.getSchema();
        String typeName = ft.getTypeName();
        SimpleFeatureSource source = selectedDataStore.getFeatureSource(typeName);
        Filter filter = CQL.toFilter(text.getText());
        //filteredSelectedFeatures = selectedFeatures.subCollection(filter);
        filteredSelectedFeatures = source.getFeatures(filter);
        FeatureCollectionTableModel model = new FeatureCollectionTableModel(filteredSelectedFeatures);
        table.setModel(model);
    }

    public void showSelectedFeatures(SimpleFeatureCollection selectedFeatures) {
        this.selectedFeatures = selectedFeatures;
        FeatureCollectionTableModel model = new FeatureCollectionTableModel(selectedFeatures);
        table.setModel(model);
    }

    private void countFeatures() throws Exception {
        String typeName = (String) featureTypeCBox.getSelectedItem();
        SimpleFeatureSource source = dataStore.getFeatureSource(typeName);

        Filter filter = CQL.toFilter(text.getText());
        SimpleFeatureCollection features = source.getFeatures(filter);

        int countFiltered = selectedFeatures.size();
        int count = features.size();
        if(selectedFeatures != null)
            JOptionPane.showMessageDialog(text, "Number of selected features: " + countFiltered + " / " + count);
        else
            JOptionPane.showMessageDialog(text, "Number of selected features: " + count);
    }

    private void queryFeatures() throws Exception {
        String typeName = (String) featureTypeCBox.getSelectedItem();
        SimpleFeatureSource source = dataStore.getFeatureSource(typeName);

        FeatureType schema = source.getSchema();
        String name = schema.getGeometryDescriptor().getLocalName();

        Filter filter = CQL.toFilter(text.getText());

        Query query = new Query(typeName, filter, new String[]{name});

        selectedFeatures = source.getFeatures(query);

        FeatureCollectionTableModel model = new FeatureCollectionTableModel(selectedFeatures);
        table.setModel(model);

    }

    public void setDataStore(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public SimpleFeatureCollection getSelectedFeatures()
    {
        return this.selectedFeatures;
    }

    public SimpleFeatureCollection getFilteredSelectedFeatures()
    {
        return this.filteredSelectedFeatures;
    }

    public void setSelectedDataStore(DataStore dataStore) { this.selectedDataStore = dataStore;}
}