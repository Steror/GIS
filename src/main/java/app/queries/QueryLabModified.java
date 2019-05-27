package app.queries;

import lombok.Getter;
import lombok.Setter;
import org.geotools.data.*;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.swing.action.SafeAction;
import org.geotools.swing.data.JDataStoreWizard;
import org.geotools.swing.table.FeatureCollectionTableModel;
import org.geotools.swing.wizard.JWizard;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Map;

/**
 * The Query Lab is an excuse to try out Filters and Expressions on your own data with a table to
 * show the results.
 *
 * <p>Remember when programming that you have other options then the CQL parser, you can directly
 * make a Filter using CommonFactoryFinder.getFilterFactory2().
 */
@SuppressWarnings("serial")
public class QueryLabModified extends JFrame{
    @Getter
    @Setter
    public DataStore dataStore;
    @Getter
    @Setter
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
        frame.setSize(800, 600);
        frame.setVisible(true);
    }

    public QueryLabModified() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        getContentPane().setLayout(new BorderLayout());

        text = new JTextField(80);
        text.setText("include"); // include selects everything!
        getContentPane().add(text, BorderLayout.NORTH);
        text.addKeyListener(new KeyAdapter() {
                                public void keyReleased(KeyEvent e) {
                                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                                        try {
                                            filterFeatures();
                                        } catch (Exception ex) {
                                            ex.printStackTrace();
                                        }
                                    }
                                }
                            });
        table = new JTable();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setPreferredScrollableViewportSize(new Dimension(500, 200));

        JScrollPane scrollPane = new JScrollPane(table);
        getContentPane().add(scrollPane, BorderLayout.CENTER);

        JMenuBar menubar = new JMenuBar();
        setJMenuBar(menubar);

        featureTypeCBox = new JComboBox<>();
        menubar.add(featureTypeCBox);

        JMenu dataMenu = new JMenu("Data");
        menubar.add(dataMenu);

        menubar.add(dataMenu);
        setSize(1300, 300);//pack();

        dataMenu.add(
                new SafeAction("Count") {
                    public void action(ActionEvent e) throws Throwable {
                        countFeatures();
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

    public SimpleFeatureCollection getSelectedFeatures()
    {
        return this.selectedFeatures;
    }

    public SimpleFeatureCollection getFilteredSelectedFeatures()
    {
        return this.filteredSelectedFeatures;
    }

}